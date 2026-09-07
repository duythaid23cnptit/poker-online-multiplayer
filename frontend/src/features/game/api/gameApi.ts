import { apiClient } from '../../../shared/api/httpClient'
import type { ActiveGame, GameDeparture, GameSnapshot } from '../types/game'

export const gameApi = {
  start(roomId: number) {
    return apiClient.request<ActiveGame>(`/games/rooms/${roomId}/start`, {
      method: 'POST', authenticated: true,
    })
  },
  snapshot(gameId: string, signal?: AbortSignal) {
    return apiClient.request<GameSnapshot>(`/games/${gameId}/snapshot`, { authenticated: true, signal })
  },
  activeByRoom(roomId: number, signal?: AbortSignal) {
    return apiClient.request<ActiveGame>(`/games/active/room/${roomId}`, { authenticated: true, signal })
  },
  activeMine(signal?: AbortSignal) {
    return apiClient.request<ActiveGame>('/games/active/me', { authenticated: true, signal })
  },
  leave(gameId: string) {
    return apiClient.request<GameDeparture>(`/games/${gameId}/leave`, {
      method: 'POST',
      authenticated: true,
    })
  },
}
