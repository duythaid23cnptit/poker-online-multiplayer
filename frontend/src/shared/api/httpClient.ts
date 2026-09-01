import { appConfig } from '../config/env'
import { ApiClientError, parseBackendError, safeErrorMessage } from './apiError'

export interface AuthenticationStrategy {
  getAccessToken: () => string | null
  refreshAccessToken: () => Promise<string>
  onAuthenticationFailure: () => void
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  authenticated?: boolean
}

type FetchImplementation = typeof fetch

export class HttpClient {
  private authentication?: AuthenticationStrategy

  constructor(
    private readonly baseUrl: string,
    private readonly fetchImplementation: FetchImplementation = fetch,
  ) { }

  configureAuthentication(strategy: AuthenticationStrategy): void {
    this.authentication = strategy
  }

  request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    return this.execute<T>(path, options, true)
  }

  private async execute<T>(path: string, options: RequestOptions, allowRefresh: boolean): Promise<T> {
    const headers = new Headers({ Accept: 'application/json' })
    let accessToken: string | null = null
    if (options.body !== undefined) headers.set('Content-Type', 'application/json')
    if (options.authenticated) {
      accessToken = this.authentication?.getAccessToken() || null
      if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    }

    let response: Response
    try {
      response = await this.fetchImplementation.call(globalThis, this.url(path), {
        method: options.method || 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: options.signal,
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error
      throw new ApiClientError(0, 'NETWORK_ERROR', safeErrorMessage(0))
    }

    if (response.status === 401 && options.authenticated && allowRefresh && this.authentication) {
      const currentAccessToken = this.authentication.getAccessToken()
      if (currentAccessToken && currentAccessToken !== accessToken) {
        return this.execute<T>(path, options, false)
      }
      try {
        await this.authentication.refreshAccessToken()
      } catch {
        this.authentication.onAuthenticationFailure()
        throw new ApiClientError(401, 'SESSION_EXPIRED', safeErrorMessage(401))
      }
      return this.execute<T>(path, options, false)
    }

    if (!response.ok) {
      const payload = await this.readJson(response)
      const backendError = parseBackendError(payload)
      if (response.status === 401 && options.authenticated) {
        this.authentication?.onAuthenticationFailure()
      }
      throw new ApiClientError(
        response.status,
        backendError?.code || `HTTP_${response.status}`,
        safeErrorMessage(response.status, backendError?.code),
        backendError?.fieldErrors || [],
      )
    }

    if (response.status === 204) return undefined as T
    const payload = await this.readJson(response)
    return payload as T
  }

  private url(path: string): string {
    const suffix = path.startsWith('/') ? path : `/${path}`
    return `${this.baseUrl}${suffix}`
  }

  private async readJson(response: Response): Promise<unknown> {
    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('json')) return null
    try {
      return await response.json()
    } catch {
      return null
    }
  }
}

export const apiClient = new HttpClient(appConfig.apiBaseUrl)
