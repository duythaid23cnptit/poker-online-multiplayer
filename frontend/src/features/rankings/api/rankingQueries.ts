import { queryOptions } from '@tanstack/react-query'
import { rankingApi } from './rankingApi'

export const rankingKeys = {
  all: ['rankings'] as const,
  current: () => [...rankingKeys.all, 'me'] as const,
  leaderboard: (page: number, size: number) => [...rankingKeys.all, 'leaderboard', page, size] as const,
  history: (page: number, size: number) => [...rankingKeys.all, 'history', page, size] as const,
}

export function currentRankingQueryOptions() {
  return queryOptions({ queryKey: rankingKeys.current(), queryFn: ({ signal }) => rankingApi.current(signal) })
}

export function leaderboardQueryOptions(page = 0, size = 20) {
  return queryOptions({
    queryKey: rankingKeys.leaderboard(page, size),
    queryFn: ({ signal }) => rankingApi.leaderboard(page, size, signal),
    staleTime: 30_000,
  })
}

export function rankingHistoryQueryOptions(page = 0, size = 12) {
  return queryOptions({
    queryKey: rankingKeys.history(page, size),
    queryFn: ({ signal }) => rankingApi.history(page, size, signal),
    staleTime: 30_000,
  })
}
