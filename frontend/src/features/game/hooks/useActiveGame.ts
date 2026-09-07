import { useQuery } from '@tanstack/react-query'
import { activeMineQueryOptions } from '../api/gameQueries'

export function useActiveGame() {
  return useQuery(activeMineQueryOptions())
}
