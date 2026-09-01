import { describe, expect, it, vi } from 'vitest'
import { ApiClientError } from './apiError'
import { HttpClient, type AuthenticationStrategy } from './httpClient'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function authenticationStrategy(
  overrides: Partial<AuthenticationStrategy> = {},
): AuthenticationStrategy {
  return {
    getAccessToken: () => 'access-token',
    refreshAccessToken: async () => 'refreshed-token',
    onAuthenticationFailure: () => undefined,
    ...overrides,
  }
}

describe('HttpClient', () => {
  it('invokes the native fetch implementation with the global context', async () => {
    const fetchWithContext = vi.fn(function (this: typeof globalThis) {
      expect(this).toBe(globalThis)
      return Promise.resolve(jsonResponse({ ok: true }))
    })
    const client = new HttpClient('/api/v1', fetchWithContext as unknown as typeof fetch)

    await expect(client.request('/health')).resolves.toEqual({ ok: true })
    expect(fetchWithContext).toHaveBeenCalledOnce()
  })

  it('joins the base URL, serializes JSON, and supplies the access-token header', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ id: 42 }))
    const client = new HttpClient('https://api.poker.test/api/v1', fetchMock)
    client.configureAuthentication(authenticationStrategy())

    await expect(
      client.request<{ id: number }>('/profiles/me', {
        method: 'PUT',
        body: { displayName: 'River Ace' },
        authenticated: true,
      }),
    ).resolves.toEqual({ id: 42 })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, request] = fetchMock.mock.calls[0]
    const headers = new Headers(request?.headers)
    expect(url).toBe('https://api.poker.test/api/v1/profiles/me')
    expect(request?.method).toBe('PUT')
    expect(request?.body).toBe(JSON.stringify({ displayName: 'River Ace' }))
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(headers.get('Authorization')).toBe('Bearer access-token')
  })

  it('forwards AbortSignal without wrapping it', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(jsonResponse({ ok: true }))
    const client = new HttpClient('/api/v1', fetchMock)
    const controller = new AbortController()

    await client.request('/health', { signal: controller.signal })

    expect(fetchMock.mock.calls[0][1]?.signal).toBe(controller.signal)
  })

  it('refreshes once after an authenticated 401 and retries with the new token', async () => {
    let accessToken = 'expired-token'
    const refreshAccessToken = vi.fn(async () => {
      accessToken = 'fresh-token'
      return accessToken
    })
    const onAuthenticationFailure = vi.fn()
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ code: 'INVALID_ACCESS_TOKEN', message: 'Expired' }, 401))
      .mockResolvedValueOnce(jsonResponse({ username: 'player' }))
    const client = new HttpClient('/api/v1', fetchMock)
    client.configureAuthentication(
      authenticationStrategy({
        getAccessToken: () => accessToken,
        refreshAccessToken,
        onAuthenticationFailure,
      }),
    )

    await expect(
      client.request<{ username: string }>('/auth/session', { authenticated: true }),
    ).resolves.toEqual({ username: 'player' })

    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe(
      'Bearer fresh-token',
    )
    expect(onAuthenticationFailure).not.toHaveBeenCalled()
  })

  it('retries with an access token refreshed by another request without starting a second refresh', async () => {
    let accessToken = 'expired-token'
    const refreshAccessToken = vi.fn(async () => 'unexpected-token')
    const fetchMock = vi.fn<typeof fetch>()
      .mockImplementationOnce(async () => {
        accessToken = 'already-refreshed-token'
        return jsonResponse({ code: 'INVALID_ACCESS_TOKEN', message: 'Expired' }, 401)
      })
      .mockResolvedValueOnce(jsonResponse({ username: 'player' }))
    const client = new HttpClient('/api/v1', fetchMock)
    client.configureAuthentication(authenticationStrategy({
      getAccessToken: () => accessToken,
      refreshAccessToken,
    }))

    await expect(client.request('/private', { authenticated: true })).resolves.toEqual({
      username: 'player',
    })

    expect(refreshAccessToken).not.toHaveBeenCalled()
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe(
      'Bearer already-refreshed-token',
    )
  })

  it('does not clear a valid refreshed session when the retried request fails for another reason', async () => {
    let accessToken = 'expired-token'
    const refreshAccessToken = vi.fn(async () => {
      accessToken = 'fresh-token'
      return accessToken
    })
    const onAuthenticationFailure = vi.fn()
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({ code: 'INVALID_ACCESS_TOKEN', message: 'Expired' }, 401))
      .mockResolvedValueOnce(jsonResponse({ code: 'SERVER_FAILURE', message: 'Internal detail' }, 500))
    const client = new HttpClient('/api/v1', fetchMock)
    client.configureAuthentication(authenticationStrategy({
      getAccessToken: () => accessToken,
      refreshAccessToken,
      onAuthenticationFailure,
    }))

    await expect(client.request('/private', { authenticated: true })).rejects.toMatchObject({
      status: 500,
      code: 'SERVER_FAILURE',
    })
    expect(refreshAccessToken).toHaveBeenCalledOnce()
    expect(onAuthenticationFailure).not.toHaveBeenCalled()
  })

  it('never enters a refresh loop when the single retry is also unauthorized', async () => {
    const refreshAccessToken = vi.fn(async () => 'fresh-token')
    const onAuthenticationFailure = vi.fn()
    const unauthorized = () =>
      jsonResponse({ code: 'INVALID_ACCESS_TOKEN', message: 'Still unauthorized' }, 401)
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(unauthorized())
    const client = new HttpClient('/api/v1', fetchMock)
    client.configureAuthentication(
      authenticationStrategy({ refreshAccessToken, onAuthenticationFailure }),
    )

    await expect(client.request('/private', { authenticated: true })).rejects.toMatchObject({
      status: 401,
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(onAuthenticationFailure).toHaveBeenCalledTimes(1)
  })

  it('clears authentication state when refresh itself fails', async () => {
    const refreshAccessToken = vi.fn().mockRejectedValue(new Error('refresh rejected'))
    const onAuthenticationFailure = vi.fn()
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ code: 'INVALID_ACCESS_TOKEN', message: 'Expired' }, 401))
    const client = new HttpClient('/api/v1', fetchMock)
    client.configureAuthentication(
      authenticationStrategy({ refreshAccessToken, onAuthenticationFailure }),
    )

    await expect(client.request('/private', { authenticated: true })).rejects.toMatchObject({
      status: 401,
      code: 'SESSION_EXPIRED',
      message: 'Your session has expired. Please sign in again.',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(onAuthenticationFailure).toHaveBeenCalledTimes(1)
  })

  it('keeps backend implementation details out of normalized errors', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          code: 'DATABASE_FAILURE',
          message: 'SQL constraint chk_friendships_status in table friendships',
        },
        500,
      ),
    )
    const client = new HttpClient('/api/v1', fetchMock)

    try {
      await client.request('/failure')
      throw new Error('Expected request to fail')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiClientError)
      if (!(error instanceof ApiClientError)) throw error
      expect(error.code).toBe('DATABASE_FAILURE')
      expect(error.message).toBe('The server is temporarily unavailable. Please try again.')
      expect(error.message).not.toContain('SQL')
      expect(error.message).not.toContain('friendships')
    }
  })
})
