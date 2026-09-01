import type { PresenceStatus, SafePlayerSummary } from '../../friends/types/friend'

export type SocialNotificationType =
  | 'FRIEND_REQUEST_RECEIVED'
  | 'FRIEND_REQUEST_ACCEPTED'
  | 'FRIEND_REQUEST_REJECTED'
  | 'FRIEND_REMOVED'

export interface SocialNotificationEvent {
  protocolVersion: 1
  eventId: string
  type: SocialNotificationType
  occurredAt: string
  payload: { requestId: number | null; player: SafePlayerSummary }
}

export interface FriendPresenceEvent {
  protocolVersion: 1
  eventId: string
  type: 'FRIEND_STATUS_CHANGED'
  occurredAt: string
  payload: { userId: number; presenceStatus: PresenceStatus }
}

export type NotificationEvent = SocialNotificationEvent | FriendPresenceEvent
