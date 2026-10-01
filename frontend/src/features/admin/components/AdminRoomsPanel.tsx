import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber, formatTimestamp } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { Input } from '../../../shared/ui/Input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { adminMutations, useAdminMutation, useAdminRoom, useAdminRooms } from '../hooks/useAdmin'
import type { AdminRoomFilters, AdminRoomStatus, AdminRoomType } from '../types/admin'
import { AdminPagination } from './AdminPagination'
import { ModerationPrompt } from './ModerationPrompt'

type RoomAction = { kind: 'close'; id: number; name: string } | { kind: 'remove'; roomId: number; userId: number; name: string }

export function AdminRoomsPanel() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<AdminRoomStatus | ''>('')
  const [roomType, setRoomType] = useState<AdminRoomType | ''>('')
  const [selected, setSelected] = useState<number | null>(null)
  const [action, setAction] = useState<RoomAction | null>(null)
  const filters: AdminRoomFilters = { page, size: 20, search: search.trim() || undefined, status: status || undefined, roomType: roomType || undefined }
  const rooms = useAdminRooms(filters)
  const detail = useAdminRoom(selected)
  const moderation = useAdminMutation((value: RoomAction & { reason?: string }) => value.kind === 'close'
    ? adminMutations.closeRoom({ id: value.id, reason: value.reason })
    : adminMutations.removePlayer({ roomId: value.roomId, userId: value.userId, reason: value.reason }))

  return <div className="admin-section-grid">
    <section className="admin-list-panel">
      <div className="admin-filter-grid">
        <label>Search rooms<Input value={search} placeholder="Room name" onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></label>
        <label>Status<select value={status} onChange={(event) => { setStatus(event.target.value as AdminRoomStatus | ''); setPage(0) }}><option value="">All statuses</option>{['WAITING', 'PLAYING', 'FINISHED', 'CLOSED'].map((value) => <option key={value}>{value}</option>)}</select></label>
        <label>Type<select value={roomType} onChange={(event) => { setRoomType(event.target.value as AdminRoomType | ''); setPage(0) }}><option value="">All types</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option></select></label>
      </div>
      {rooms.isPending && <LoadingState variant="table" label="Loading rooms..." />}
      {rooms.isError && <ErrorState message={getErrorMessage(rooms.error)} onRetry={() => void rooms.refetch()} />}
      {rooms.data && !rooms.data.items.length && <EmptyState variant="compact" motif="chip" title="No rooms found" description="Try changing the current filters." />}
      {rooms.data?.items.length ? <div className="overflow-x-auto" role="region" aria-label="Admin rooms" tabIndex={0}><table className="data-table"><thead><tr><th>Room</th><th>Status</th><th>Type</th><th>Seats</th><th>Spectators</th><th></th></tr></thead><tbody>{rooms.data.items.map((room) => <tr key={room.roomId} className={selected === room.roomId ? 'admin-selected-row' : undefined} aria-selected={selected === room.roomId}><td><strong>{room.name}</strong><small>Room #{room.roomId}</small></td><td><StatusBadge tone={room.status === 'PLAYING' ? 'success' : room.status === 'WAITING' ? 'accent' : 'muted'}>{room.status}</StatusBadge></td><td>{room.roomType}</td><td>{room.seatedPlayers}/{room.capacity}</td><td>{room.spectators}</td><td><Button type="button" variant="ghost" onClick={() => setSelected(room.roomId)}>Inspect</Button></td></tr>)}</tbody></table></div> : null}
      {rooms.data && <AdminPagination page={page} size={20} total={rooms.data.total} onPage={setPage} />}
    </section>
    <aside className="admin-detail-panel">
      {selected === null && <EmptyState variant="inline" motif="cards" title="Select a room" description="Inspect its configuration, active membership, and current session." />}
      {selected !== null && detail.isPending && <LoadingState label="Loading room detail..." />}
      {selected !== null && detail.isError && <ErrorState message={getErrorMessage(detail.error)} onRetry={() => void detail.refetch()} />}
      {selected !== null && detail.data && <>
        <div className="admin-detail-heading"><div><p>Room #{detail.data.roomId}</p><h2>{detail.data.name}</h2><span>Host @{detail.data.ownerUsername} · created {formatTimestamp(detail.data.createdAt)}</span></div><StatusBadge tone={detail.data.status === 'PLAYING' ? 'success' : detail.data.status === 'WAITING' ? 'accent' : 'muted'}>{detail.data.status}</StatusBadge></div>
        <dl className="admin-detail-metrics"><div><dt>Capacity</dt><dd>{detail.data.capacity}</dd></div><div><dt>Blinds</dt><dd>{detail.data.smallBlind}/{detail.data.bigBlind}</dd></div><div><dt>Buy-in</dt><dd>{formatNumber(detail.data.buyIn)}</dd></div><div><dt>Active session</dt><dd>{detail.data.activeGameSessionId ?? 'None'}</dd></div></dl>
        <div className="flex flex-wrap gap-2"><Button type="button" variant="danger" disabled={detail.data.status === 'CLOSED'} onClick={() => { moderation.reset(); setAction({ kind: 'close', id: detail.data.roomId, name: detail.data.name }) }}>Close room</Button></div>
        {moderation.isSuccess && !action && <div className="form-success" role="status">Room moderation applied.</div>}
        {action && <ModerationPrompt title={action.kind === 'close' ? `Close ${action.name}` : `Remove @${action.name}`} description={action.kind === 'close' ? 'An active game must be terminated before its room can close.' : 'An active-hand removal may be deferred until settlement.'} pending={moderation.isPending} error={moderation.error} onCancel={() => setAction(null)} onConfirm={(reason) => moderation.mutate({ ...action, reason }, { onSuccess: () => setAction(null) })} />}
        <h3 className="admin-subheading mt-5">Participants</h3>
        {!detail.data.participants.length ? <p className="admin-safe-detail">No membership history.</p> : <ul className="admin-participant-list">{detail.data.participants.map((member) => <li key={`${member.userId}-${member.joinedAt}`}><span><strong>@{member.username}</strong><small>{member.seatNumber ? `Seat ${member.seatNumber}` : 'Spectator'} · {member.playerState} · {formatNumber(member.tableChips)} chips{member.leftAt ? ' · left' : ''}</small></span>{!member.leftAt && <Button type="button" variant="ghost" onClick={() => { moderation.reset(); setAction({ kind: 'remove', roomId: detail.data.roomId, userId: member.userId, name: member.username }) }}>Remove</Button>}</li>)}</ul>}
      </>}
    </aside>
  </div>
}
