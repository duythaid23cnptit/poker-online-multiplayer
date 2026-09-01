import { Link } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import type { FriendshipView } from '../../friends/types/friend'
import { Avatar } from '../../../shared/ui/Avatar'
import { EmptyState } from '../../../shared/ui/EmptyState'

export function FriendsPreview({ friends }: { friends: FriendshipView[] }) {
  const online = friends.filter((friend) => friend.presenceStatus === 'ONLINE' || friend.presenceStatus === 'IN_GAME')
  if (!online.length) return <EmptyState variant="compact" motif="suit" title="Your table is quiet" description="Online and in-game friends will appear here." action={<Link className="empty-action-link" to={routes.friends}>Manage friends</Link>} />
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-2 xl:grid-cols-3">
      {online.slice(0, 6).map((friend) => (
        <Link key={friend.otherPlayer.userId} to={routes.friends} className="group rounded-control border border-border/80 bg-surface-elevated/55 p-3 transition hover:border-accent/30 hover:bg-surface-hover">
          <Avatar displayName={friend.otherPlayer.displayName} src={friend.otherPlayer.avatarUrl} size="sm" />
          <p className="mt-2 truncate text-sm font-bold text-text group-hover:text-accent">{friend.otherPlayer.displayName}</p>
          <p className="mt-1 text-xs text-success">{friend.presenceStatus === 'IN_GAME' ? 'In game' : 'Online'}</p>
        </Link>
      ))}
    </div>
  )
}
