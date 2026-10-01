import { describe, expect, it } from 'vitest'
import { parseRoomEvent, parseRoomGameStartedEvent } from './roomEvent'

describe('room game discovery parser', () => {
  it('accepts the versioned authoritative room envelope', () => {
    const event = parseRoomGameStartedEvent(JSON.stringify({ protocolVersion: 1,
      eventId: '123e4567-e89b-12d3-a456-426614174000', type: 'GAME_STARTED', occurredAt: '2026-09-01T00:00:00Z',
      scope: { roomId: 9 }, payload: { gameId: '123e4567-e89b-42d3-a456-426614174000', gameSessionId: 22, handId: 31, handNumber: 4 } }))
    expect(event?.payload.gameId).toBe('123e4567-e89b-42d3-a456-426614174000')
  })

  it('does not mistake PLAYER_READY for game discovery', () => {
    expect(parseRoomGameStartedEvent(JSON.stringify({ protocolVersion: 1, type: 'PLAYER_READY' }))).toBeNull()
  })

  it('accepts every authoritative room lifecycle type but rejects chat and invented types', () => {
    const base = { protocolVersion: 1, eventId: '123e4567-e89b-12d3-a456-426614174000', occurredAt: '2026-09-01T00:00:00Z', scope: { roomId: 9 }, payload: { userId: 7 } }
    for (const type of ['ROOM_CREATED', 'ROOM_UPDATED', 'ROOM_CLOSED', 'PLAYER_COUNT_CHANGED', 'PLAYER_JOINED', 'PLAYER_LEFT', 'PLAYER_READY', 'PLAYER_UNREADY', 'PLAYER_DISCONNECTED', 'PLAYER_RECONNECTED', 'GAME_STARTED']) {
      expect(parseRoomEvent(JSON.stringify({ ...base, type }))?.type).toBe(type)
    }
    expect(parseRoomEvent(JSON.stringify({ ...base, type: 'CHAT_MESSAGE' }))).toBeNull()
    expect(parseRoomEvent(JSON.stringify({ ...base, type: 'INVENTED' }))).toBeNull()
  })
})
