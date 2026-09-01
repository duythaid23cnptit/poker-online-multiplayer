import { apiClient } from '../../../shared/api/httpClient'
import type { CurrentRanking, RankingHistory, RankingPage } from '../types/ranking'

export const rankingApi = {
  current(signal?: AbortSignal) {
    return apiClient.request<CurrentRanking>('/rankings/me', { authenticated: true, signal })
  },
  leaderboard(page = 0, size = 20, signal?: AbortSignal) {
    return apiClient.request<RankingPage>(`/rankings/leaderboard?page=${page}&size=${size}`, {
      authenticated: true, signal,
    })
  },
  history(page = 0, size = 12, signal?: AbortSignal) {
    return apiClient.request<RankingHistory[]>(`/rankings/me/history?page=${page}&size=${size}`, {
      authenticated: true, signal,
    })
  },
}
