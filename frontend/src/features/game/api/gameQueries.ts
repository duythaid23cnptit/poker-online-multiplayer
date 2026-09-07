import { queryOptions } from '@tanstack/react-query'
import { ApiClientError } from '../../../shared/api/apiError'
import { gameApi } from './gameApi'

export const gameKeys = {
  all: ['games'] as const,
  activeMine: () => [...gameKeys.all, 'active', 'me'] as const,
}

export function activeMineQueryOptions() {
  return queryOptions({
    queryKey: gameKeys.activeMine(),
    queryFn: async ({ signal }) => {
      try {
        return await gameApi.activeMine(signal)
      } catch (error) {
        if (error instanceof ApiClientError && error.status === 404) return null
        throw error
      }
    },
    staleTime: 15_000,
    refetchOnMount: 'always',
    refetchOnReconnect: 'always',
    retry: (failureCount, error) => !(error instanceof ApiClientError && error.status === 404) && failureCount < 2,
  })
}
