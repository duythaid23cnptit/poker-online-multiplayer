import { queryOptions } from '@tanstack/react-query'
import { statisticsApi } from './statisticsApi'

export const statisticsKeys = {
  all: ['statistics'] as const,
  current: () => [...statisticsKeys.all, 'current'] as const,
  summary: () => [...statisticsKeys.all, 'summary'] as const,
  daily: (from: string, to: string) => [...statisticsKeys.all, 'daily', from, to] as const,
  weekly: (from: string, to: string) => [...statisticsKeys.all, 'weekly', from, to] as const,
}

export function currentStatisticsQueryOptions() {
  return queryOptions({
    queryKey: statisticsKeys.current(),
    queryFn: ({ signal }) => statisticsApi.current(signal),
    staleTime: 30_000,
  })
}

export function analyticsSummaryQueryOptions() {
  return queryOptions({
    queryKey: statisticsKeys.summary(),
    queryFn: ({ signal }) => statisticsApi.summary(signal),
    staleTime: 30_000,
  })
}

export function dailyAnalyticsQueryOptions(from: string, to: string) {
  return queryOptions({
    queryKey: statisticsKeys.daily(from, to),
    queryFn: ({ signal }) => statisticsApi.daily(from, to, signal),
    staleTime: 30_000,
  })
}

export function weeklyAnalyticsQueryOptions(from: string, to: string) {
  return queryOptions({
    queryKey: statisticsKeys.weekly(from, to),
    queryFn: ({ signal }) => statisticsApi.weekly(from, to, signal),
    staleTime: 30_000,
  })
}
