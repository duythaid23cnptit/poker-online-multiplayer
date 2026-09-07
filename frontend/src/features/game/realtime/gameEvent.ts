import type { GameEventType, GameRealtimeEvent } from '../types/game'

const types = new Set<GameEventType>(['GAME_STARTED','HAND_STARTED','HOLE_CARDS','COMMUNITY_CARDS','YOUR_TURN','PLAYER_ACTION','TIMER_UPDATE','GAME_STATE_UPDATE','SHOWDOWN','GAME_RESULT','HAND_FINISHED','COMMAND_ERROR'])
function record(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null }
export function parseGameEvent(body: string): GameRealtimeEvent | null {
  let value: unknown
  try { value = JSON.parse(body) } catch { return null }
  if (!record(value) || typeof value.eventId !== 'string' || typeof value.type !== 'string' || !types.has(value.type as GameEventType)
    || typeof value.timestamp !== 'string' || typeof value.roomId !== 'number' || typeof value.gameId !== 'string'
    || typeof value.version !== 'number' || !record(value.payload)) return null
  return value as unknown as GameRealtimeEvent
}
