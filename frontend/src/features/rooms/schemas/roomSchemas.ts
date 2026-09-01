import { z } from 'zod'

export const createRoomSchema = z.object({
  name: z.string().trim().min(1, 'Enter a room name').max(100, 'Room name must be 100 characters or fewer'),
  roomType: z.enum(['PUBLIC', 'PRIVATE']),
  maxPlayers: z.number().int().min(6, 'Choose at least 6 seats').max(9, 'Choose at most 9 seats'),
  smallBlind: z.number().int().min(1, 'Small blind must be at least 1'),
  bigBlind: z.number().int().min(2, 'Big blind must be at least 2'),
  buyIn: z.number().int().min(1, 'Buy-in must be at least 1'),
  password: z.string().max(72, 'Password must be 72 characters or fewer'),
}).superRefine((value, context) => {
  if (value.bigBlind <= value.smallBlind) {
    context.addIssue({ code: 'custom', path: ['bigBlind'], message: 'Big blind must exceed small blind' })
  }
  if (value.roomType === 'PRIVATE' && value.password.length < 8) {
    context.addIssue({ code: 'custom', path: ['password'], message: 'Private room password must be 8–72 characters' })
  }
  if (value.roomType === 'PUBLIC' && value.password.length > 0) {
    context.addIssue({ code: 'custom', path: ['password'], message: 'Public rooms cannot use a password' })
  }
})

export type CreateRoomFormValues = z.infer<typeof createRoomSchema>

export const joinRoomSchema = z.object({
  spectator: z.boolean(),
  seatNumber: z.number().int().min(1, 'Seat starts at 1').max(9, 'Seat cannot exceed 9').nullable(),
  password: z.string().max(72, 'Password must be 72 characters or fewer'),
}).superRefine((value, context) => {
  if (!value.spectator && value.seatNumber === null) {
    context.addIssue({ code: 'custom', path: ['seatNumber'], message: 'Choose a seat to join as a player' })
  }
})

export type JoinRoomFormValues = z.infer<typeof joinRoomSchema>
