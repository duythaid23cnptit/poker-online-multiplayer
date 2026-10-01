import { beforeEach, describe, expect, it, vi } from 'vitest'
import { profileApi } from '../../profile/api/profileApi'
import { profileKeys } from '../../profile/api/profileQueries'
import { createTestQueryClient, currentUserFixture, deferred } from '../../../test/testUtils'
import { authApi } from '../api/authApi'
import {
  clearAuthSession,
  refreshAccessToken,
  restoreSession,
} from './sessionCoordinator'
import { useSessionStore } from './sessionStore'
import { tokenVault } from './tokenVault'

vi.mock('../api/authApi', () => ({
  authApi: { refresh: vi.fn() },
}))

vi.mock('../../profile/api/profileApi', () => ({
  profileApi: { current: vi.fn() },
}))

const accessResponse = {
  accessToken: 'restored-access-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
} as const

describe('sessionCoordinator', () => {
  beforeEach(() => {
    vi.mocked(authApi.refresh).mockReset()
    vi.mocked(profileApi.current).mockReset()
    sessionStorage.clear()
    useSessionStore.setState({ status: 'CHECKING_SESSION', accessToken: null, epoch: 0 })
  })

  it('becomes unauthenticated without calling the backend when no refresh token exists', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(['sensitive'], 'cached')

    await restoreSession(queryClient)

    expect(authApi.refresh).not.toHaveBeenCalled()
    expect(profileApi.current).not.toHaveBeenCalled()
    expect(useSessionStore.getState().status).toBe('UNAUTHENTICATED')
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('refreshes once, loads the authoritative profile, and marks the session authenticated', async () => {
    tokenVault.storeRefreshToken('refresh-token')
    vi.mocked(authApi.refresh).mockResolvedValue(accessResponse)
    vi.mocked(profileApi.current).mockResolvedValue(currentUserFixture)
    const queryClient = createTestQueryClient()

    await restoreSession(queryClient)

    expect(authApi.refresh).toHaveBeenCalledWith('refresh-token')
    expect(profileApi.current).toHaveBeenCalledOnce()
    expect(useSessionStore.getState().status).toBe('AUTHENTICATED')
    expect(useSessionStore.getState().accessToken).toBe('restored-access-token')
    expect(queryClient.getQueryData(profileKeys.current())).toEqual(currentUserFixture)
  })

  it('clears persisted and cached state when restoration fails', async () => {
    tokenVault.storeRefreshToken('invalid-refresh-token')
    vi.mocked(authApi.refresh).mockRejectedValue(new Error('invalid refresh'))
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(['sensitive'], 'cached')

    await restoreSession(queryClient)

    expect(tokenVault.readRefreshToken()).toBeNull()
    expect(useSessionStore.getState().status).toBe('UNAUTHENTICATED')
    expect(useSessionStore.getState().accessToken).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('shares one refresh request across simultaneous callers', async () => {
    tokenVault.storeRefreshToken('refresh-token')
    const pending = deferred<typeof accessResponse>()
    vi.mocked(authApi.refresh).mockReturnValue(pending.promise)

    const first = refreshAccessToken()
    const second = refreshAccessToken()
    pending.resolve(accessResponse)

    await expect(Promise.all([first, second])).resolves.toEqual([
      'restored-access-token',
      'restored-access-token',
    ])
    expect(authApi.refresh).toHaveBeenCalledTimes(1)
  })

  it('does not resurrect a session when refresh finishes after logout', async () => {
    tokenVault.storeRefreshToken('refresh-token')
    const pending = deferred<typeof accessResponse>()
    vi.mocked(authApi.refresh).mockReturnValue(pending.promise)
    const queryClient = createTestQueryClient()

    const refresh = refreshAccessToken()
    clearAuthSession(queryClient)
    pending.resolve(accessResponse)

    await expect(refresh).rejects.toThrow('Session changed during refresh')
    expect(useSessionStore.getState().status).toBe('UNAUTHENTICATED')
    expect(useSessionStore.getState().accessToken).toBeNull()
    expect(tokenVault.readRefreshToken()).toBeNull()
  })
})
