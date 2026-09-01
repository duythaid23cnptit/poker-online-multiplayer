import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderResult } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom'
import type { CurrentUser } from '../features/profile/types/profile'

export const currentUserFixture: CurrentUser = {
  id: 42,
  username: 'river_reader',
  email: 'river@example.com',
  role: 'PLAYER',
  accountStatus: 'ACTIVE',
  accountChips: 12_500,
  displayName: 'River Reader',
  avatarUrl: 'https://images.example.com/river.png',
  onlineStatus: 'ONLINE',
}

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Number.POSITIVE_INFINITY },
      mutations: { retry: false },
    },
  })
}

interface RenderWithAppContextOptions {
  queryClient?: QueryClient
  initialEntries?: MemoryRouterProps['initialEntries']
}

export function renderWithAppContext(
  ui: ReactElement,
  options: RenderWithAppContextOptions = {},
): RenderResult & { queryClient: QueryClient } {
  const queryClient = options.queryClient ?? createTestQueryClient()
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={options.initialEntries ?? ['/']}>
        {ui}
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...result, queryClient }
}

export function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
