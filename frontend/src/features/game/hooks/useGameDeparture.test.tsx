import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { gameKeys } from '../api/gameQueries'
import { roomKeys } from '../../rooms/api/roomQueries'
import { useGameDiscoveryStore } from './gameDiscoveryStore'
import { useGameDeparture } from './useGameDeparture'

const mocks = vi.hoisted(() => ({
  activeMine: vi.fn(),
  navigate: vi.fn(),
}))

vi.mock('../api/gameApi', () => ({ gameApi: { activeMine: mocks.activeMine } }))
vi.mock('react-router-dom', async (importOriginal) => ({
  ...await importOriginal<typeof import('react-router-dom')>(),
  useNavigate: () => mocks.navigate,
}))

const gameId = '11111111-1111-4111-8111-111111111111'

describe('useGameDeparture navigation ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useGameDiscoveryStore.getState().clear()
  })

  it('navigates to Rooms exactly once after repeated finalization responses', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    queryClient.setQueryData(gameKeys.activeMine(), { roomId: 7, gameId })
    queryClient.setQueryData(roomKeys.membershipLookup([7]), [{ room: { id: 7 }, members: [] }])
    useGameDiscoveryStore.getState().discover(7, gameId)
    mocks.activeMine.mockResolvedValue(null)
    const wrapper = ({ children }: PropsWithChildren) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>

    const view = renderHook(() => useGameDeparture(7, gameId, true), { wrapper })

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/app/rooms', { replace: true }))
    expect(queryClient.getQueryData(gameKeys.activeMine())).toBeNull()
    expect(queryClient.getQueryData(roomKeys.membershipLookup([7]))).toBeUndefined()
    expect(useGameDiscoveryStore.getState()).toMatchObject({ roomId: null, gameId: null })

    await act(async () => { await queryClient.refetchQueries({ queryKey: gameKeys.activeMine() }) })
    expect(mocks.activeMine.mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(mocks.navigate).toHaveBeenCalledTimes(1)
    view.unmount()
  })
})
