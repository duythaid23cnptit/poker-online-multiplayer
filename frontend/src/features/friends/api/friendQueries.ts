import { queryOptions } from '@tanstack/react-query'
import { friendApi } from './friendApi'

export const friendKeys = {
  all: ['friends'] as const,
  list: () => [...friendKeys.all, 'accepted'] as const,
  requests: (direction: 'incoming' | 'outgoing') => [...friendKeys.all, 'requests', direction] as const,
}

export function friendListQueryOptions() {
  return queryOptions({
    queryKey: friendKeys.list(),
    queryFn: ({ signal }) => friendApi.list(signal),
    staleTime: 30_000,
  })
}

export function friendRequestsQueryOptions(direction: 'incoming' | 'outgoing') {
  return queryOptions({
    queryKey: friendKeys.requests(direction),
    queryFn: ({ signal }) => friendApi.requests(direction, signal),
    staleTime: 15_000,
  })
}
