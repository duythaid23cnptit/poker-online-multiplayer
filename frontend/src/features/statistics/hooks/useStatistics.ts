import { useQuery } from '@tanstack/react-query'
import {
  analyticsSummaryQueryOptions,
  currentStatisticsQueryOptions,
  dailyAnalyticsQueryOptions,
  weeklyAnalyticsQueryOptions,
} from '../api/statisticsQueries'

export function useCurrentStatistics() {
  return useQuery(currentStatisticsQueryOptions())
}

export function useAnalyticsSummary() {
  return useQuery(analyticsSummaryQueryOptions())
}

export function useDailyAnalytics(from: string, to: string) {
  return useQuery(dailyAnalyticsQueryOptions(from, to))
}

export function useWeeklyAnalytics(from: string, to: string) {
  return useQuery(weeklyAnalyticsQueryOptions(from, to))
}
