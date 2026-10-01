import { z } from 'zod'

const gameStartedPayloadSchema = z.object({
  gameId: z.string().uuid(),
  gameSessionId: z.number().int().positive(),
  handId: z.number().int().positive(),
  handNumber: z.number().int().positive(),
})

const roomGameStartedEventSchema = z.object({
  protocolVersion: z.literal(1),
  eventId: z.string().uuid(),
  type: z.literal('GAME_STARTED'),
  occurredAt: z.string(),
  scope: z.object({ roomId: z.number().int().positive() }),
  payload: gameStartedPayloadSchema,
})

export type RoomGameStartedEvent = z.infer<typeof roomGameStartedEventSchema>

const roomEventTypes = new Set([
  'ROOM_CREATED', 'ROOM_UPDATED', 'ROOM_CLOSED', 'PLAYER_COUNT_CHANGED', 'PLAYER_JOINED',
  'PLAYER_LEFT', 'PLAYER_READY', 'PLAYER_UNREADY', 'PLAYER_DISCONNECTED', 'PLAYER_RECONNECTED',
  'GAME_STARTED',
])

export interface RoomRealtimeEvent {
  protocolVersion: 1
  eventId: string
  type: string
  occurredAt: string
  scope: { roomId: number }
  payload: unknown
}

export function parseRoomEvent(body: string): RoomRealtimeEvent | null {
  try {
    const value = JSON.parse(body) as Partial<RoomRealtimeEvent>
    if (value.protocolVersion !== 1 || typeof value.eventId !== 'string' || typeof value.type !== 'string'
      || !roomEventTypes.has(value.type) || typeof value.occurredAt !== 'string'
      || typeof value.scope?.roomId !== 'number' || !('payload' in value)) return null
    return value as RoomRealtimeEvent
  } catch {
    return null
  }
}

export function parseRoomGameStartedEvent(body: string): RoomGameStartedEvent | null {
  try {
    const parsed = roomGameStartedEventSchema.safeParse(JSON.parse(body))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}
