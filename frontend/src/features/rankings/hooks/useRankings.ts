import { useQuery } from '@tanstack/react-query'
import { currentRankingQueryOptions, leaderboardQueryOptions, rankingHistoryQueryOptions } from '../api/rankingQueries'

export function useCurrentRanking() { return useQuery(currentRankingQueryOptions()) }
export function useLeaderboard(page = 0, size = 20) { return useQuery(leaderboardQueryOptions(page, size)) }
export function useRankingHistory(page = 0, size = 12) { return useQuery(rankingHistoryQueryOptions(page, size)) }
