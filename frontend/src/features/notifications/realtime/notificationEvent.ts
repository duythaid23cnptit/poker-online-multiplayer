import type { NotificationEvent, SocialNotificationType } from '../types/notification'

const socialTypes = new Set<SocialNotificationType>([
  'FRIEND_REQUEST_RECEIVED', 'FRIEND_REQUEST_ACCEPTED', 'FRIEND_REQUEST_REJECTED', 'FRIEND_REMOVED',
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

export function parseNotificationEvent(body: string): NotificationEvent | null {
  let value: unknown
  try { value = JSON.parse(body) } catch { return null }
  if (!isRecord(value) || value.protocolVersion !== 1 || typeof value.eventId !== 'string'
    || typeof value.type !== 'string' || typeof value.occurredAt !== 'string' || !isRecord(value.payload)) return null
  const payload = value.payload
  if (value.type === 'FRIEND_STATUS_CHANGED') {
    if (typeof payload.userId !== 'number'
      || (payload.presenceStatus !== 'ONLINE' && payload.presenceStatus !== 'OFFLINE' && payload.presenceStatus !== 'IN_GAME')) return null
    return value as unknown as NotificationEvent
  }
  if (!socialTypes.has(value.type as SocialNotificationType) || !isRecord(payload.player)
    || typeof payload.player.userId !== 'number' || typeof payload.player.displayName !== 'string'
    || (payload.player.avatarUrl !== null && typeof payload.player.avatarUrl !== 'string')
    || (payload.requestId !== null && typeof payload.requestId !== 'number')) return null
  return value as unknown as NotificationEvent
}
