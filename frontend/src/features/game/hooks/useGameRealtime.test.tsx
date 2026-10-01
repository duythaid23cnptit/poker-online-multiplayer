import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { stompSession } from '../../../shared/realtime/stompSession'
import { gameApi } from '../api/gameApi'
import type { GameSnapshot } from '../types/game'
import { useGameRealtime } from './useGameRealtime'

const gameId = '11111111-1111-4111-8111-111111111111'
function snapshot(version: number, phase: GameSnapshot['publicState']['phase']): GameSnapshot {
  return { roomId: 7, gameId, gameSessionId: 2, version, participant: true, holeCards: [], turn: null, timer: null,
    publicState: { handId: 3, handNumber: 1, phase, dealerSeat: 1, smallBlindSeat: 1, bigBlindSeat: 2,
      currentTurnUserId: null, currentBet: 0, minimumRaise: 10, communityCards: [], players: [
        { userId: 42, seat: 1, tableChips: 500, currentBet: 0, totalCommitted: 0, participation: 'ACTIVE', connected: true, leaving: false },
      ], handCompleted: false, sessionFinished: false } }
}
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }

describe('useGameRealtime recovery', () => {
  let connection: (connected: boolean) => void
  const listeners = new Map<string, (body: string) => void>()

  beforeEach(() => {
    listeners.clear()
    vi.restoreAllMocks()
    vi.spyOn(stompSession, 'listen').mockImplementation((destination, listener) => { listeners.set(destination, listener); return vi.fn() })
    vi.spyOn(stompSession, 'listenConnection').mockImplementation((listener) => { connection = listener; listener(false); return vi.fn() })
  })

  it('hydrates once on entry and rehydrates from the authoritative snapshot after STOMP reconnect', async () => {
    vi.spyOn(gameApi, 'snapshot').mockResolvedValueOnce(snapshot(1, 'PRE_FLOP')).mockResolvedValueOnce(snapshot(4, 'TURN'))
    const { result } = renderHook(() => useGameRealtime(7, gameId))
    await waitFor(() => expect(result.current.snapshotPending).toBe(false))
    expect(result.current.state.version).toBe(1)

    act(() => connection(true))
    await waitFor(() => expect(gameApi.snapshot).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(result.current.state.version).toBe(4))
    expect(result.current.state.publicState?.phase).toBe('TURN')
  })

  it('buffers newer public events while reconnect hydration is in flight', async () => {
    const recovery = deferred<GameSnapshot>()
    vi.spyOn(gameApi, 'snapshot').mockResolvedValueOnce(snapshot(1, 'PRE_FLOP')).mockReturnValueOnce(recovery.promise)
    const { result } = renderHook(() => useGameRealtime(7, gameId))
    await waitFor(() => expect(result.current.snapshotPending).toBe(false))
    act(() => connection(true))
    await waitFor(() => expect(gameApi.snapshot).toHaveBeenCalledTimes(2))
    act(() => listeners.get(`/topic/game/${gameId}`)?.(JSON.stringify({
      eventId: '123e4567-e89b-42d3-a456-426614174000', type: 'GAME_STATE_UPDATE', timestamp: '2026-09-08T00:00:00Z',
      roomId: 7, gameId, version: 5, payload: snapshot(5, 'RIVER').publicState,
    })))
    act(() => recovery.resolve(snapshot(4, 'TURN')))
    await waitFor(() => expect(result.current.state.version).toBe(5))
    expect(result.current.state.publicState?.phase).toBe('RIVER')
  })
})
