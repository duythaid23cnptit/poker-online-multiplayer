import { z } from 'zod'

export const sendFriendRequestSchema = z.object({
  recipientUserId: z.number().int().positive('Enter a valid player ID'),
})

export type SendFriendRequestFormValues = z.infer<typeof sendFriendRequestSchema>
