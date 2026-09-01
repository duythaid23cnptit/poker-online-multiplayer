import { QueryClient } from '@tanstack/react-query'
import { ApiClientError } from './apiError'

export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiClientError && error.status > 0 && error.status < 500) return false
          return failureCount < 1
        },
      },
      mutations: { retry: false },
    },
  })
}

export const appQueryClient = createAppQueryClient()
