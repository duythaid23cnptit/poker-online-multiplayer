import { useQuery } from '@tanstack/react-query'
import { currentProfileQueryOptions } from '../api/profileQueries'

export function useCurrentProfile() {
  return useQuery(currentProfileQueryOptions())
}
