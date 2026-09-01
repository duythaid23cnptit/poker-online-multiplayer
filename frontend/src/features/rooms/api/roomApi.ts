import { apiClient } from '../../../shared/api/httpClient'
import type { CreateRoomRequest, JoinRoomRequest, RoomDetail, RoomSummary } from '../types/room'

export const roomApi = {
  list(signal?: AbortSignal) {
    return apiClient.request<RoomSummary[]>('/rooms', { authenticated: true, signal })
  },
  detail(roomId: number, signal?: AbortSignal) {
    return apiClient.request<RoomDetail>(`/rooms/${roomId}`, { authenticated: true, signal })
  },
  create(request: CreateRoomRequest) {
    return apiClient.request<RoomDetail>('/rooms', { method: 'POST', body: request, authenticated: true })
  },
  join(roomId: number, request: JoinRoomRequest) {
    return apiClient.request<RoomDetail>(`/rooms/${roomId}/join`, {
      method: 'POST', body: request, authenticated: true,
    })
  },
  leave(roomId: number) {
    return apiClient.request<RoomDetail>(`/rooms/${roomId}/leave`, {
      method: 'POST', authenticated: true,
    })
  },
}
