import { describe, expect, it } from 'vitest'
import { parseNotificationEvent } from './notificationEvent'

describe('parseNotificationEvent', () => {
  it('accepts a supported presence event', () => {
    const event = parseNotificationEvent(JSON.stringify({ protocolVersion: 1, eventId: 'evt-1', type: 'FRIEND_STATUS_CHANGED', occurredAt: '2026-01-01T00:00:00Z', payload: { userId: 9, presenceStatus: 'ONLINE' } }))
    expect(event?.type).toBe('FRIEND_STATUS_CHANGED')
  })

  it('rejects unknown or malformed messages', () => {
    expect(parseNotificationEvent('{bad-json')).toBeNull()
    expect(parseNotificationEvent(JSON.stringify({ protocolVersion: 1, eventId: 'evt-2', type: 'CHAT_MESSAGE_CREATED', occurredAt: '2026-01-01T00:00:00Z', payload: {} }))).toBeNull()
  })
})
