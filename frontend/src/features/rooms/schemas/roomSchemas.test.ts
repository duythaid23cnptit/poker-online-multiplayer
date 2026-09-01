import { describe, expect, it } from 'vitest'
import { createRoomSchema, joinRoomSchema } from './roomSchemas'

describe('room schemas', () => {
  it('enforces frozen blind and private-password rules', () => {
    expect(createRoomSchema.safeParse({ name: 'Private table', roomType: 'PRIVATE', maxPlayers: 6, smallBlind: 10, bigBlind: 10, buyIn: 500, password: 'short' }).success).toBe(false)
    expect(createRoomSchema.safeParse({ name: 'Private table', roomType: 'PRIVATE', maxPlayers: 6, smallBlind: 5, bigBlind: 10, buyIn: 500, password: 'eight888' }).success).toBe(true)
  })

  it('requires a seat only for a seated player', () => {
    expect(joinRoomSchema.safeParse({ spectator: false, seatNumber: null, password: '' }).success).toBe(false)
    expect(joinRoomSchema.safeParse({ spectator: true, seatNumber: null, password: '' }).success).toBe(true)
  })
})
