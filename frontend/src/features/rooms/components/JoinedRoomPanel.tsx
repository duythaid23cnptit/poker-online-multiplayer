import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useLeaveRoom } from '../hooks/useRooms'
import type { RoomDetail } from '../types/room'

export function JoinedRoomPanel({ detail, onLeft }: { detail: RoomDetail; onLeft: () => void }) {
  const leave = useLeaveRoom()
  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><p className="text-xs font-black uppercase tracking-[0.18em] text-success">Active membership</p><h2 className="mt-2 text-xl font-black text-text">{detail.room.name}</h2></div>
        <StatusBadge tone="success">Joined</StatusBadge>
      </div>
      <p className="mt-3 text-sm leading-6 text-secondary">The backend returned this authoritative room snapshot. Gameplay controls remain outside Phase 11B.</p>
      <ul className="mt-5 space-y-2">
        {detail.members.map((member) => <li key={member.userId} className="flex items-center justify-between gap-3 rounded-control border border-border/70 bg-surface-elevated/45 px-3 py-2.5">
          <span className="min-w-0"><strong className="block truncate text-sm text-text">{member.username}</strong><small className="text-xs text-muted">{member.seatNumber ? `Seat ${member.seatNumber}` : 'Spectator'} · {member.state}</small></span>
          <span className="text-xs font-bold text-accent">{formatNumber(member.tableChips)} chips</span>
        </li>)}
      </ul>
      {leave.isError && <div className="form-alert mt-4" role="alert">{getErrorMessage(leave.error)}</div>}
      <Button type="button" variant="secondary" className="mt-5 w-full" loading={leave.isPending} loadingLabel="Leaving room…" onClick={() => leave.mutate(detail.room.id, { onSuccess: onLeft })}>Leave waiting room</Button>
    </div>
  )
}
