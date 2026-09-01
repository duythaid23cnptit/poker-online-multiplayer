import { apiClient } from '../../../shared/api/httpClient'
import type { CurrentUser, UpdateProfileRequest } from '../types/profile'

export const profileApi = {
  current(signal?: AbortSignal) {
    return apiClient.request<CurrentUser>('/me', { authenticated: true, signal })
  },
  update(request: UpdateProfileRequest, signal?: AbortSignal) {
    return apiClient.request<CurrentUser>('/me', {
      method: 'PATCH', body: request, authenticated: true, signal,
    })
  },
}
