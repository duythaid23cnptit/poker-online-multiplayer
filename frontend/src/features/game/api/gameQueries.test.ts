import { QueryClient } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import type { ActiveGame } from '../types/game'
import { activeMineQueryOptions, gameKeys } from './gameQueries'

const mocks = vi.hoisted(() => ({ activeMine: vi.fn() }))

vi.mock('./gameApi', () => ({ gameApi: { activeMine: mocks.activeMine } }))

const activeGame: ActiveGame = {
  roomId: 12,
  gameId: '11111111-1111-4111-8111-111111111111',
  gameSessionId: 10,
  handId: 20,
  handNumber: 3,
}

describe('activeMineQueryOptions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses a stable key and returns the active runtime game', async () => {
    mocks.activeMine.mockResolvedValue(activeGame)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    await expect(queryClient.fetchQuery(activeMineQueryOptions())).resolves.toEqual(activeGame)
    expect(activeMineQueryOptions().queryKey).toEqual(gameKeys.activeMine())
  })

  it('normalizes no active game from 404 to null', async () => {
    mocks.activeMine.mockRejectedValue(new ApiClientError(404, 'HTTP_404', 'Not found'))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    await expect(queryClient.fetchQuery(activeMineQueryOptions())).resolves.toBeNull()
    expect(mocks.activeMine).toHaveBeenCalledTimes(1)
  })
})
