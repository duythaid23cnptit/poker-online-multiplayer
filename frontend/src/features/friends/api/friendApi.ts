import { apiClient } from '../../../shared/api/httpClient'
import type { FriendshipView } from '../types/friend'

export const friendApi = {
  list(signal?: AbortSignal) {
    return apiClient.request<FriendshipView[]>('/friends', { authenticated: true, signal })
  },
  requests(direction: 'incoming' | 'outgoing', signal?: AbortSignal) {
    return apiClient.request<FriendshipView[]>(`/friend-requests?direction=${direction}`, {
      authenticated: true, signal,
    })
  },
  send(recipientUserId: number) {
    return apiClient.request<FriendshipView>('/friend-requests', {
      method: 'POST', body: { recipientUserId }, authenticated: true,
    })
  },
  accept(requestId: number) {
    return apiClient.request<FriendshipView>(`/friend-requests/${requestId}/accept`, {
      method: 'POST', authenticated: true,
    })
  },
  reject(requestId: number) {
    return apiClient.request<FriendshipView>(`/friend-requests/${requestId}/reject`, {
      method: 'POST', authenticated: true,
    })
  },
  remove(friendId: number) {
    return apiClient.request<void>(`/friends/${friendId}`, {
      method: 'DELETE', authenticated: true,
    })
  },
}
