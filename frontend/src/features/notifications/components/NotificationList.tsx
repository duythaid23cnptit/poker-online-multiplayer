import { useNotificationStore } from '../hooks/notificationStore'
import type { NotificationEvent } from '../types/notification'
import { formatTimestamp } from '../../../shared/lib/format'
import { EmptyState } from '../../../shared/ui/EmptyState'

function eventCopy(event: NotificationEvent): string {
  if (event.type === 'FRIEND_STATUS_CHANGED') return `Player #${event.payload.userId} is now ${event.payload.presenceStatus.toLowerCase()}.`
  const name = event.payload.player.displayName
  if (event.type === 'FRIEND_REQUEST_RECEIVED') return `${name} sent you a friend request.`
  if (event.type === 'FRIEND_REQUEST_ACCEPTED') return `${name} accepted your friend request.`
  if (event.type === 'FRIEND_REQUEST_REJECTED') return `${name} declined your friend request.`
  return `${name} is no longer on your friends list.`
}

export function NotificationList({ limit = 5, compact = false }: { limit?: number; compact?: boolean }) {
  const allEvents = useNotificationStore((state) => state.events)
  const events = allEvents.slice(0, limit)
  if (!events.length) {
    return <EmptyState variant={compact ? 'compact' : 'inline'} motif="suit" title="No live notifications" description="Friend activity received during this session will appear here." />
  }
  return (
    <ol className="divide-y divide-border/70">
      {events.map((event) => (
        <li key={event.eventId} className={`flex gap-3 ${compact ? 'py-3' : 'py-4'}`}>
          <span className="mt-1 size-2 shrink-0 rounded-full bg-accent shadow-[0_0_0_4px_rgb(88_214_163_/_0.08)]" aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <p className="text-sm leading-5 text-text">{eventCopy(event)}</p>
            <time className="mt-1 block text-xs text-muted" dateTime={event.occurredAt}>{formatTimestamp(event.occurredAt)}</time>
          </div>
        </li>
      ))}
    </ol>
  )
}
