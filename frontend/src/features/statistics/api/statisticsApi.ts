import { apiClient } from '../../../shared/api/httpClient'
import type { AnalyticsSummary, DailyAnalytics, PlayerStatistics, WeeklyAnalytics } from '../types/statistics'

function dateRange(path: string, from: string, to: string) {
  const query = new URLSearchParams({ from, to })
  return `${path}?${query.toString()}`
}

export const statisticsApi = {
  current(signal?: AbortSignal) {
    return apiClient.request<PlayerStatistics>('/players/me/statistics', { authenticated: true, signal })
  },
  daily(from: string, to: string, signal?: AbortSignal) {
    return apiClient.request<DailyAnalytics[]>(dateRange('/analytics/me/daily', from, to), {
      authenticated: true, signal,
    })
  },
  weekly(from: string, to: string, signal?: AbortSignal) {
    return apiClient.request<WeeklyAnalytics[]>(dateRange('/analytics/me/weekly', from, to), {
      authenticated: true, signal,
    })
  },
  summary(signal?: AbortSignal) {
    return apiClient.request<AnalyticsSummary>('/analytics/me/summary', { authenticated: true, signal })
  },
}
