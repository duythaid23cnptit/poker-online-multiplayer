import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { stompSession } from '../../../shared/realtime/stompSession'
import { initialGameState } from '../realtime/gameEventReducer'
import type { GameViewState, PlayerActionPayload, PokerActionType } from '../types/game'
import { useGameActions } from './useGameActions'

const gameId = '11111111-1111-4111-8111-111111111111'
const firstTurnId = '22222222-2222-4222-8222-222222222222'
const secondTurnId = '33333333-3333-4333-8333-333333333333'

function state(turnId = firstTurnId, handId = 10): GameViewState {
  return {
    ...initialGameState(7, gameId),
    turn: {
      handId,
      turnId,
      legalActions: ['FOLD', 'CHECK'] as PokerActionType[],
      callAmount: 0,
      minimumTarget: 0,
      maximumTarget: 0,
      tableChips: 500,
    },
  }
}

function playerAction(clientActionId: string | null): PlayerActionPayload {
  return {
    userId: 42,
    seat: 1,
    actionType: 'CHECK',
    amount: 0,
    resultingCurrentBet: 0,
    resultingTableChips: 500,
    clientActionId,
    automatic: false,
  }
}

function sentClientActionId(send: ReturnType<typeof vi.spyOn>): string {
  return (send.mock.calls[0][1] as { clientActionId: string }).clientActionId
}

afterEach(() => vi.restoreAllMocks())

describe('useGameActions command acknowledgement', () => {
  it('clears matching PLAYER_ACTION permanently across unrelated actions and a new hand', async () => {
    const send = vi.spyOn(stompSession, 'send').mockReturnValue(true)
    const initial = state()
    const { result, rerender } = renderHook(({ gameState }) => useGameActions(gameId, gameState), {
      initialProps: { gameState: initial },
    })
    act(() => result.current.act('CHECK'))
    const commandId = sentClientActionId(send)
    expect(result.current.pending).toBe(true)

    rerender({ gameState: { ...initial, lastAction: playerAction(commandId) } })
    await waitFor(() => expect(result.current.pending).toBe(false))

    rerender({ gameState: { ...state(secondTurnId), lastAction: playerAction('unrelated-command') } })
    expect(result.current.pending).toBe(false)
    rerender({ gameState: { ...state(secondTurnId, 11), lastAction: null, commandError: null } })
    expect(result.current.pending).toBe(false)
  })

  it('clears matching COMMAND_ERROR permanently and exposes the authoritative server error', async () => {
    const send = vi.spyOn(stompSession, 'send').mockReturnValue(true)
    const initial = state()
    const { result, rerender } = renderHook(({ gameState }) => useGameActions(gameId, gameState), {
      initialProps: { gameState: initial },
    })
    act(() => result.current.act('CHECK'))
    const commandId = sentClientActionId(send)

    rerender({ gameState: { ...initial, commandError: { code: 'STALE_TURN', clientActionId: commandId } } })

    await waitFor(() => expect(result.current.pending).toBe(false))
    expect(result.current.error).toBe('That turn has already advanced.')
  })

  it('does not clear a pending command for unrelated PLAYER_ACTION or COMMAND_ERROR events', () => {
    const send = vi.spyOn(stompSession, 'send').mockReturnValue(true)
    const initial = state()
    const { result, rerender } = renderHook(({ gameState }) => useGameActions(gameId, gameState), {
      initialProps: { gameState: initial },
    })
    act(() => result.current.act('CHECK'))

    rerender({ gameState: { ...initial, lastAction: playerAction('another-action') } })
    expect(result.current.pending).toBe(true)
    rerender({ gameState: { ...initial, commandError: { code: 'ACTION_REJECTED', clientActionId: 'another-command' } } })
    expect(result.current.pending).toBe(true)
  })

  it('does not enter pending state when the STOMP send fails locally', () => {
    vi.spyOn(stompSession, 'send').mockReturnValue(false)
    const { result } = renderHook(() => useGameActions(gameId, state()))

    act(() => result.current.act('CHECK'))

    expect(result.current.pending).toBe(false)
    expect(result.current.error).toBe('Connection unavailable. Your action was not sent.')
  })
})
