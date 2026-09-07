import { describe, expect, it } from 'vitest'
import { parseRoomGameStartedEvent } from './roomEvent'

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
})
