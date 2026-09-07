import { queryOptions } from '@tanstack/react-query'
import { ApiClientError } from '../../../shared/api/apiError'
import { roomApi } from './roomApi'

export const roomKeys = {
  all: ['rooms'] as const,
  list: () => [...roomKeys.all, 'list'] as const,
  detail: (roomId: number) => [...roomKeys.all, 'detail', roomId] as const,
  memberships: () => [...roomKeys.all, 'memberships'] as const,
  membershipLookup: (roomIds: number[]) => [...roomKeys.memberships(), roomIds] as const,
}

export function roomListQueryOptions() {
  return queryOptions({
    queryKey: roomKeys.list(),
    queryFn: ({ signal }) => roomApi.list(signal),
    staleTime: 15_000,
  })
}

export function roomDetailQueryOptions(roomId: number, enabled = true) {
  return queryOptions({
    queryKey: roomKeys.detail(roomId),
    queryFn: ({ signal }) => roomApi.detail(roomId, signal),
    enabled: enabled && Number.isSafeInteger(roomId) && roomId > 0,
    staleTime: 0,
    refetchOnMount: 'always',
    refetchOnReconnect: 'always',
  })
}

export function existingRoomMembershipsQueryOptions(roomIds: number[]) {
  const stableRoomIds = [...new Set(roomIds)].filter((roomId) => Number.isSafeInteger(roomId) && roomId > 0).sort((left, right) => left - right)
  return queryOptions({
    queryKey: roomKeys.membershipLookup(stableRoomIds),
    queryFn: async ({ signal }) => {
      const details = await Promise.all(stableRoomIds.map(async (roomId) => {
        try {
          return await roomApi.detail(roomId, signal)
        } catch (error) {
          if (error instanceof ApiClientError && (error.status === 403 || error.status === 404)) return null
          throw error
        }
      }))
      return details.filter((detail) => detail !== null)
    },
    enabled: stableRoomIds.length > 0,
    staleTime: 0,
    refetchOnMount: 'always',
    refetchOnReconnect: 'always',
  })
}
