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

export function parseRoomGameStartedEvent(body: string): RoomGameStartedEvent | null {
  try {
    const parsed = roomGameStartedEventSchema.safeParse(JSON.parse(body))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}
