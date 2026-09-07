import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { Input } from '../../../shared/ui/Input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { CreateRoomPanel } from '../components/CreateRoomPanel'
import { JoinRoomPanel } from '../components/JoinRoomPanel'
import { JoinedRoomPanel } from '../components/JoinedRoomPanel'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'
import { useExistingRoomMemberships, useRooms } from '../hooks/useRooms'
import { useLobbyRealtime } from '../realtime/useLobbyRealtime'

export function RoomsPage() {
  useLobbyRealtime()
  const rooms = useRooms()
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const [type, setType] = useState<'ALL' | 'PUBLIC' | 'PRIVATE'>('ALL')
  const joinId = Number(searchParams.get('join'))
  const selectedRoom = rooms.data?.find((room) => room.id === joinId)
  const profile = useCurrentProfile()
  const membershipRoomIds = [
    ...(rooms.data ?? []).map((room) => room.id),
    ...(Number.isSafeInteger(joinId) && joinId > 0 ? [joinId] : []),
  ]
  const memberships = useExistingRoomMemberships(membershipRoomIds)
  const restoredMemberships = memberships.data?.filter((detail) =>
    detail.members.some((member) => member.userId === profile.data?.id)) ?? []
  const joinedRoom = Number.isSafeInteger(joinId) && joinId > 0
    ? restoredMemberships.find((detail) => detail.room.id === joinId)
    : restoredMemberships[0]
  const membershipCheckPending = rooms.isPending || profile.isPending
    || (membershipRoomIds.length > 0 && memberships.isPending)
  const hasActiveMembership = restoredMemberships.length > 0
  const canCreate = !membershipCheckPending && !memberships.isError && !hasActiveMembership
  const creating = searchParams.get('create') === '1' && canCreate
  const filteredRooms = useMemo(() => (rooms.data || []).filter((room) =>
    (type === 'ALL' || room.roomType === type) && room.name.toLowerCase().includes(search.trim().toLowerCase()),
  ), [rooms.data, search, type])
  return (
    <main className="app-page rooms-page">
      <PageHeader title="Find your next table" description="Find a table, take a seat, or watch the action." actions={!creating && canCreate && <Button type="button" onClick={() => setSearchParams({ create: '1' })}>Create room</Button>} />
      {profile.data && <p className="rooms-balance">Account chips <strong>{formatNumber(profile.data.accountChips)}</strong><span>Available for table buy-ins</span></p>}
      <section className="rooms-layout">
        <Card className="dashboard-card min-w-0 p-5 sm:p-6">
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_10rem]">
            <Input aria-label="Search rooms" placeholder="Search room names" value={search} onChange={(event) => setSearch(event.target.value)} />
            <select aria-label="Filter room access" className="select-control" value={type} onChange={(event) => setType(event.target.value as typeof type)}>
              <option value="ALL">All access</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option>
            </select>
          </div>
          {rooms.isPending && <LoadingState variant="rows" label="Loading waiting rooms…" />}
          {rooms.isError && <div className="mt-5"><ErrorState message={getErrorMessage(rooms.error)} onRetry={() => void rooms.refetch()} /></div>}
          {!rooms.isPending && !rooms.isError && !filteredRooms.length && <div className="mt-4"><EmptyState variant="compact" motif="cards" title="No matching rooms" description={search || type !== 'ALL' ? 'Adjust the filters to widen your search.' : 'No tables are waiting yet. Host the first one.'} /></div>}
          <div className="mt-5 grid gap-3 md:grid-cols-2">
            {filteredRooms.map((room) => <article key={room.id} className={`room-card ${selectedRoom?.id === room.id ? 'room-card-selected' : ''}`}>
              <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h2 className="truncate font-bold text-text">{room.name}</h2><p className="mt-1 text-xs text-muted">Room #{room.id}</p></div><StatusBadge tone={room.passwordRequired ? 'warning' : 'success'}>{room.roomType}</StatusBadge></div>
              <dl className="mt-4 grid grid-cols-3 gap-2 text-xs"><div><dt>Blinds</dt><dd>{formatNumber(room.smallBlind)} / {formatNumber(room.bigBlind)}</dd></div><div><dt>Players</dt><dd>{room.seatedPlayers} / {room.maxPlayers}</dd></div><div><dt>Buy-in</dt><dd>{formatNumber(room.buyIn)}</dd></div></dl>
              <Button type="button" variant="secondary" className="mt-4 w-full" onClick={() => setSearchParams({ join: String(room.id) })}>{restoredMemberships.some((detail) => detail.room.id === room.id) ? 'Joined · View table' : 'Join options'}</Button>
            </article>)}
          </div>
        </Card>
        <Card className="dashboard-card rooms-detail h-fit p-5 sm:p-6">
          {membershipCheckPending ? <LoadingState label="Checking active room membership…" />
            : creating ? <CreateRoomPanel onCreated={(detail) => setSearchParams({ join: String(detail.room.id) })} />
              : memberships.isError ? <ErrorState message={getErrorMessage(memberships.error)} onRetry={() => void memberships.refetch()} />
                : joinedRoom ? <JoinedRoomPanel detail={joinedRoom} onLeft={() => setSearchParams({})} />
                  : selectedRoom ? <JoinRoomPanel room={selectedRoom} onJoined={(detail) => setSearchParams({ join: String(detail.room.id) })} />
                    : <EmptyState variant="inline" motif="suit" title="Choose your table" description="Select a waiting room to review your available join options." />}
        </Card>
      </section>
    </main>
  )
}
