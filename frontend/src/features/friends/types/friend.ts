export type FriendshipStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'
export type PresenceStatus = 'ONLINE' | 'IN_GAME' | 'OFFLINE'

export interface SafePlayerSummary {
  userId: number
  displayName: string
  avatarUrl: string | null
}

export interface FriendshipView {
  requestId: number
  status: FriendshipStatus
  createdAt: string
  respondedAt: string | null
  otherPlayer: SafePlayerSummary
  presenceStatus?: PresenceStatus
}
