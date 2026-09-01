import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { useNotificationStore } from '../hooks/notificationStore'
import { NotificationList } from './NotificationList'

describe('NotificationList', () => {
  afterEach(() => useNotificationStore.getState().clear())

  it('selects the stable event collection and derives the visible slice after selection', () => {
    render(<NotificationList limit={1} compact />)
    expect(screen.getByRole('heading', { name: 'No live notifications' })).toBeVisible()

    act(() => useNotificationStore.getState().add({
      protocolVersion: 1,
      eventId: 'event-1',
      type: 'FRIEND_STATUS_CHANGED',
      occurredAt: '2026-09-01T00:00:00Z',
      payload: { userId: 42, presenceStatus: 'ONLINE' },
    }))

    expect(screen.getByText('Player #42 is now online.')).toBeVisible()
  })
})
