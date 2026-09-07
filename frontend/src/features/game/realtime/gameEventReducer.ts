import type { GameRealtimeEvent, GameStatePayload, GameViewState, PlayingCardValue, PlayerActionPayload, TimerPayload, TurnPayload, GameResultPayload } from '../types/game'

type HandIdentity = { handId: number; handNumber?: number }
type HandPosition = 'OLDER' | 'SAME' | 'NEWER'

function record(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null }

function eventHand(event: GameRealtimeEvent): HandIdentity | null {
  if (!record(event.payload)) return null
  if (event.type === 'HAND_STARTED' || event.type === 'GAME_STARTED' || event.type === 'GAME_STATE_UPDATE' || event.type === 'HAND_FINISHED') {
    return typeof event.payload.handId === 'number' && typeof event.payload.handNumber === 'number'
      ? { handId: event.payload.handId, handNumber: event.payload.handNumber } : null
  }
  if (event.type === 'HOLE_CARDS' || event.type === 'YOUR_TURN' || event.type === 'TIMER_UPDATE'
    || event.type === 'SHOWDOWN' || event.type === 'GAME_RESULT') {
    return typeof event.payload.handId === 'number' ? { handId: event.payload.handId } : null
  }
  return null
}

function compareHandPosition(current: GameStatePayload | null, incoming: HandIdentity | null): HandPosition {
  if (!current) return 'NEWER'
  if (!incoming) return 'SAME'
  if (incoming.handNumber !== undefined) {
    if (incoming.handNumber < current.handNumber) return 'OLDER'
    if (incoming.handNumber > current.handNumber) return 'NEWER'
  }
  return incoming.handId === current.handId ? 'SAME' : 'OLDER'
}

function clearHandTransient(state: GameViewState): GameViewState {
  return { ...state, holeCards: [], turn: null, timer: null, result: null }
}

export function initialGameState(roomId: number, gameId: string): GameViewState { return { roomId, gameId, version: 0, seenEventIds: [], publicState: null, holeCards: [], turn: null, timer: null, result: null, lastAction: null, commandError: null } }
export function reduceGameEvent(state: GameViewState, event: GameRealtimeEvent): GameViewState {
  if (event.roomId !== state.roomId || event.gameId !== state.gameId || state.seenEventIds.includes(event.eventId)) return state
  const position = compareHandPosition(state.publicState, eventHand(event))
  if (position === 'OLDER' || (position === 'SAME' && event.version < state.version)) return state
  const baseline = position === 'NEWER' ? clearHandTransient(state) : state
  const next = { ...baseline, version: position === 'NEWER' ? event.version : Math.max(state.version, event.version), seenEventIds: [...state.seenEventIds.slice(-99), event.eventId] }
  if (event.type === 'GAME_STATE_UPDATE') { next.publicState = event.payload as GameStatePayload; if (next.publicState.handCompleted) { next.turn = null; next.timer = null } }
  if (event.type === 'HAND_STARTED') { const payload = event.payload as { handId:number; handNumber:number; dealerSeat:number; smallBlindSeat:number; bigBlindSeat:number; players:GameStatePayload['players'] }; next.publicState = { handId:payload.handId, handNumber:payload.handNumber, phase:'PRE_FLOP', dealerSeat:payload.dealerSeat, smallBlindSeat:payload.smallBlindSeat, bigBlindSeat:payload.bigBlindSeat, currentTurnUserId:null, currentBet:0, minimumRaise:0, communityCards:[], players:payload.players, handCompleted:false, sessionFinished:false }; next.holeCards=[]; next.result=null; next.turn=null; next.timer=null }
  if (event.type === 'HOLE_CARDS') next.holeCards = (event.payload as { cards: PlayingCardValue[] }).cards
  if (event.type === 'COMMUNITY_CARDS' && next.publicState) next.publicState = { ...next.publicState, phase:(event.payload as {phase:GameStatePayload['phase']}).phase, communityCards:(event.payload as {cards:PlayingCardValue[]}).cards }
  if (event.type === 'YOUR_TURN') { next.turn = event.payload as TurnPayload; next.commandError = null }
  if (event.type === 'TIMER_UPDATE') next.timer = event.payload as TimerPayload
  if (event.type === 'PLAYER_ACTION') { next.lastAction = event.payload as PlayerActionPayload; next.commandError = null; next.turn = null }
  if (event.type === 'GAME_RESULT') {
    const result = event.payload as GameResultPayload
    next.result = result
    if (next.publicState?.handId === result.handId) {
      next.publicState = { ...next.publicState, phase: 'FINISHED', currentTurnUserId: null,
        players: result.finalPlayers, handCompleted: true }
      next.turn = null
      next.timer = null
    }
  }
  if (event.type === 'COMMAND_ERROR') next.commandError = event.payload as GameViewState['commandError']
  return next
}

export function hydrateGameSnapshot(state: GameViewState, snapshot: import('../types/game').GameSnapshot): GameViewState {
  if (snapshot.roomId !== state.roomId || snapshot.gameId !== state.gameId) return state
  const position = compareHandPosition(state.publicState, snapshot.publicState)
  if (position === 'OLDER' || (position === 'SAME' && snapshot.version < state.version)) return state
  return { ...state, version: snapshot.version, publicState: snapshot.publicState, holeCards: snapshot.holeCards,
    turn: snapshot.turn, timer: snapshot.timer, result: null, lastAction: null, commandError: null }
}
