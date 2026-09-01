import { queryOptions } from '@tanstack/react-query'
import { profileApi } from './profileApi'

export const profileKeys = {
  all: ['profile'] as const,
  current: () => [...profileKeys.all, 'me'] as const,
}

export function currentProfileQueryOptions() {
  return queryOptions({
    queryKey: profileKeys.current(),
    queryFn: ({ signal }) => profileApi.current(signal),
    staleTime: 60_000,
  })
}
