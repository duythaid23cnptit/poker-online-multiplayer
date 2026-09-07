import { describe, expect, it } from 'vitest'
import { parseGameEvent } from './gameEvent'
import { hydrateGameSnapshot, initialGameState, reduceGameEvent } from './gameEventReducer'
import type { GameRealtimeEvent, GameSnapshot, GameStatePayload } from '../types/game'

const gameId = '11111111-1111-4111-8111-111111111111'
const base = { timestamp: '2026-09-01T00:00:00Z', roomId: 7, gameId }

function event(type: GameRealtimeEvent['type'], payload: unknown, version: number, eventId: string): GameRealtimeEvent {
  return { ...base, eventId, type, version, payload }
}

function publicState(handId: number, handNumber: number, phase: GameStatePayload['phase'] = 'PRE_FLOP'): GameStatePayload {
  return { handId, handNumber, phase, dealerSeat: 1, smallBlindSeat: 1, bigBlindSeat: 2,
    currentTurnUserId: 1, currentBet: 10, minimumRaise: 20, communityCards: [], players: [],
    handCompleted: false, sessionFinished: false }
}

function handStarted(handId: number, handNumber: number, version: number, eventId: string) {
  return event('HAND_STARTED', { handId, handNumber, dealerSeat: 1, smallBlindSeat: 1,
    bigBlindSeat: 2, players: [] }, version, eventId)
}

function snapshot(handId: number, handNumber: number, version: number): GameSnapshot {
  return { roomId: 7, gameId, gameSessionId: 2, version, participant: false, holeCards: [],
    turn: null, timer: null, publicState: publicState(handId, handNumber, 'FLOP') }
}

describe('game event reconciliation', () => {
  it('rejects unknown and malformed frames safely', () => {
    expect(parseGameEvent('{bad')).toBeNull()
    expect(parseGameEvent(JSON.stringify({ ...base, eventId: 'e1', version: 3,
      type: 'INVENTED', payload: {} }))).toBeNull()
  })

  it('rejects a same-hand lower version and accepts a same-hand newer version', () => {
    let state = hydrateGameSnapshot(initialGameState(7, gameId), snapshot(50, 5, 8))
    const stale = reduceGameEvent(state, event('GAME_STATE_UPDATE', publicState(50, 5, 'TURN'), 7, 'stale'))
    expect(stale).toBe(state)

    state = reduceGameEvent(state, event('GAME_STATE_UPDATE', publicState(50, 5, 'RIVER'), 9, 'newer'))
    expect(state.version).toBe(9)
    expect(state.publicState?.phase).toBe('RIVER')
  })

  it('preserves duplicate event-id protection', () => {
    let state = reduceGameEvent(initialGameState(7, gameId), handStarted(50, 5, 8, 'same-id'))
    const duplicate = reduceGameEvent(state, event('GAME_STATE_UPDATE', publicState(50, 5, 'RIVER'), 9, 'same-id'))
    expect(duplicate).toBe(state)
  })

  it('accepts a new hand with a reset version and then accepts its authoritative turn', () => {
    let state = hydrateGameSnapshot(initialGameState(7, gameId), snapshot(50, 5, 8))
    state = reduceGameEvent(state, handStarted(60, 6, 1, 'hand-6'))
    expect(state.publicState?.handNumber).toBe(6)
    expect(state.version).toBe(1)

    state = reduceGameEvent(state, event('YOUR_TURN', { handId: 60,
      turnId: '123e4567-e89b-42d3-a456-426614174000',
      legalActions: ['RAISE', 'CALL', 'ALL_IN', 'FOLD'], callAmount: 5,
      minimumTarget: 20, maximumTarget: 505, tableChips: 500 }, 1, 'turn-6'))

    expect(state.turn).toEqual({ handId: 60, turnId: '123e4567-e89b-42d3-a456-426614174000',
      legalActions: ['RAISE', 'CALL', 'ALL_IN', 'FOLD'], callAmount: 5,
      minimumTarget: 20, maximumTarget: 505, tableChips: 500 })
  })

  it('ignores an older-hand event even when its numeric version is higher', () => {
    let state = reduceGameEvent(initialGameState(7, gameId), handStarted(60, 6, 1, 'hand-6'))
    const older = reduceGameEvent(state, event('GAME_STATE_UPDATE', publicState(50, 5, 'RIVER'), 99, 'late-5'))
    expect(older).toBe(state)
    expect(state.publicState?.handId).toBe(60)
  })

  it('hydrates a newer-hand snapshot despite its reset version', () => {
    const handFive = hydrateGameSnapshot(initialGameState(7, gameId), snapshot(50, 5, 8))
    const handSix = hydrateGameSnapshot(handFive, snapshot(60, 6, 1))
    expect(handSix.publicState?.handNumber).toBe(6)
    expect(handSix.version).toBe(1)
  })

  it('does not let an older-hand snapshot regress a newer hand', () => {
    const handSix = hydrateGameSnapshot(initialGameState(7, gameId), snapshot(60, 6, 1))
    expect(hydrateGameSnapshot(handSix, snapshot(50, 5, 99))).toBe(handSix)
  })

  it('new HAND_STARTED clears prior hand cards, turn, timer, and result', () => {
    let state = reduceGameEvent(initialGameState(7, gameId), handStarted(50, 5, 8, 'hand-5'))
    state = reduceGameEvent(state, event('HOLE_CARDS', { handId: 50,
      cards: [{ rank: 'ACE', suit: 'SPADES' }] }, 8, 'cards-5'))
    state = reduceGameEvent(state, event('YOUR_TURN', { handId: 50, turnId: 'turn-5',
      legalActions: ['CHECK'], callAmount: 0, minimumTarget: 20, maximumTarget: 500,
      tableChips: 500 }, 8, 'turn-5'))
    state = reduceGameEvent(state, event('TIMER_UPDATE', { handId: 50, turnId: 'turn-5',
      currentTurnSeat: 1, remainingSeconds: 20, deadline: '2026-09-01T00:00:20Z' }, 8, 'timer-5'))
    state = reduceGameEvent(state, event('GAME_RESULT', { handId: 50, awards: [],
      uncalledReturns: [], finalPlayers: [], endReason: 'SHOWDOWN' }, 8, 'result-5'))

    state = reduceGameEvent(state, handStarted(60, 6, 1, 'hand-6'))
    expect(state.holeCards).toEqual([])
    expect(state.turn).toBeNull()
    expect(state.timer).toBeNull()
    expect(state.result).toBeNull()
  })

  it('keeps the completed board, awards, and authoritative final stacks until HAND_STARTED', () => {
    const finished = { ...publicState(50, 5, 'RIVER'), communityCards: [
      { rank: 'ACE', suit: 'SPADES' } as const,
      { rank: 'KING', suit: 'HEARTS' } as const,
    ], players: [
      { userId: 1, seat: 1, tableChips: 400, currentBet: 100, participation: 'ACTIVE' as const, connected: true, leaving: false },
      { userId: 2, seat: 2, tableChips: 400, currentBet: 100, participation: 'ALL_IN' as const, connected: true, leaving: false },
    ] }
    let state = reduceGameEvent(initialGameState(7, gameId), event('GAME_STATE_UPDATE', finished, 8, 'river'))
    const finalPlayers = finished.players.map((player) => ({ ...player,
      tableChips: player.userId === 1 ? 800 : 0, currentBet: 0 }))
    state = reduceGameEvent(state, event('GAME_RESULT', { handId: 50, awards: [{
      potIndex: 0, potType: 'MAIN', potAmount: 200, winnerUserIds: [1], winnerPayouts: { '1': 200 },
    }], uncalledReturns: [], finalPlayers, endReason: 'SHOWDOWN' }, 9, 'result'))
    state = reduceGameEvent(state, event('HAND_FINISHED', {
      handId: 50, handNumber: 5, endReason: 'SHOWDOWN',
    }, 9, 'finished'))

    expect(state.publicState?.communityCards).toEqual(finished.communityCards)
    expect(state.publicState?.players).toEqual(finalPlayers)
    expect(state.publicState?.handCompleted).toBe(true)
    expect(state.result?.awards[0].potAmount).toBe(200)

    state = reduceGameEvent(state, handStarted(60, 6, 1, 'next-hand'))
    expect(state.publicState?.communityCards).toEqual([])
    expect(state.result).toBeNull()
  })

  it('keeps private hole cards separate from public state', () => {
    const state = reduceGameEvent(initialGameState(7, gameId), event('HOLE_CARDS', { handId: 9,
      cards: [{ rank: 'ACE', suit: 'SPADES' }, { rank: 'KING', suit: 'HEARTS' }] }, 3, 'cards'))
    expect(state.holeCards).toHaveLength(2)
    expect(state.publicState).toBeNull()
  })
})
