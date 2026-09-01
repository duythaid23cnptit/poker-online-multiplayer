import { queryOptions } from '@tanstack/react-query'
import { roomApi } from './roomApi'

export const roomKeys = {
  all: ['rooms'] as const,
  list: () => [...roomKeys.all, 'list'] as const,
  detail: (roomId: number) => [...roomKeys.all, 'detail', roomId] as const,
}

export function roomListQueryOptions() {
  return queryOptions({
    queryKey: roomKeys.list(),
    queryFn: ({ signal }) => roomApi.list(signal),
    staleTime: 15_000,
  })
}
