import { apiClient } from '../../../shared/api/httpClient'
import type { CurrentUser } from '../../profile/types/profile'
import type { AccessTokenResponse, AuthResponse, LoginRequest, RegisterRequest } from '../types/auth'

export const authApi = {
  register(request: RegisterRequest, signal?: AbortSignal) {
    return apiClient.request<CurrentUser>('/auth/register', {
      method: 'POST', body: request, signal,
    })
  },
  login(request: LoginRequest, signal?: AbortSignal) {
    return apiClient.request<AuthResponse>('/auth/login', {
      method: 'POST', body: request, signal,
    })
  },
  refresh(refreshToken: string) {
    return apiClient.request<AccessTokenResponse>('/auth/refresh', {
      method: 'POST', body: { refreshToken },
    })
  },
  logout(refreshToken: string) {
    return apiClient.request<void>('/auth/logout', {
      method: 'POST', body: { refreshToken }, authenticated: true,
    })
  },
}
