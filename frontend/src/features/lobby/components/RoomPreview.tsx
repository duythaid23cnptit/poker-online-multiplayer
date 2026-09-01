import { Link } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import type { RoomSummary } from '../../rooms/types/room'
import { formatNumber } from '../../../shared/lib/format'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'

export function RoomPreview({ rooms }: { rooms: RoomSummary[] }) {
  if (!rooms.length) return <EmptyState variant="compact" motif="cards" title="No rooms are waiting" description="Create the first room when you are ready to host a table." action={<Link className="empty-action-link" to={`${routes.rooms}?create=1`}>Create a room</Link>} />
  return (
    <div className="overflow-x-auto">
      <table className="data-table min-w-[34rem]">
        <thead><tr><th>Room</th><th>Blinds</th><th>Players</th><th>Access</th></tr></thead>
        <tbody>{rooms.slice(0, 5).map((room) => (
          <tr key={room.id}>
            <td><Link className="font-bold text-text hover:text-accent" to={`${routes.rooms}?join=${room.id}`}>{room.name}</Link></td>
            <td>{formatNumber(room.smallBlind)} / {formatNumber(room.bigBlind)}</td>
            <td>{room.seatedPlayers} / {room.maxPlayers}</td>
            <td><StatusBadge tone={room.passwordRequired ? 'warning' : 'success'}>{room.roomType}</StatusBadge></td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  )
}
