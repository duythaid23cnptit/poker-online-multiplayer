import { apiClient } from '../../../shared/api/httpClient'
import type { ChatMessage } from '../types/chat'

export const chatApi = {
  history(roomId: number, limit = 50, signal?: AbortSignal) {
    return apiClient.request<ChatMessage[]>(`/rooms/${roomId}/chat/messages?limit=${limit}`, {
      authenticated: true,
      signal,
    })
  },
}
