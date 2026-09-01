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
import { useRooms } from '../hooks/useRooms'
import { useLobbyRealtime } from '../realtime/useLobbyRealtime'
import type { RoomDetail } from '../types/room'

export function RoomsPage() {
  useLobbyRealtime()
  const rooms = useRooms()
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const [type, setType] = useState<'ALL' | 'PUBLIC' | 'PRIVATE'>('ALL')
  const [joinedRoom, setJoinedRoom] = useState<RoomDetail | null>(null)
  const joinId = Number(searchParams.get('join'))
  const selectedRoom = rooms.data?.find((room) => room.id === joinId)
  const creating = searchParams.get('create') === '1'
  const filteredRooms = useMemo(() => (rooms.data || []).filter((room) =>
    (type === 'ALL' || room.roomType === type) && room.name.toLowerCase().includes(search.trim().toLowerCase()),
  ), [rooms.data, search, type])
  return (
    <main className="app-page">
      <PageHeader title="Find your next table" description="Browse real waiting rooms, create a table, or join as a player or spectator." actions={<Button type="button" onClick={() => setSearchParams({ create: '1' })}>Create room</Button>} />
      <section className="mt-8 grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(22rem,0.65fr)]">
        <Card className="dashboard-card min-w-0 p-5 sm:p-6">
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_10rem]">
            <Input aria-label="Search rooms" placeholder="Search room names" value={search} onChange={(event) => setSearch(event.target.value)} />
            <select aria-label="Filter room access" className="select-control" value={type} onChange={(event) => setType(event.target.value as typeof type)}>
              <option value="ALL">All access</option><option value="PUBLIC">Public</option><option value="PRIVATE">Private</option>
            </select>
          </div>
          {rooms.isPending && <LoadingState variant="rows" label="Loading waiting rooms…" />}
          {rooms.isError && <div className="mt-5"><ErrorState message={getErrorMessage(rooms.error)} onRetry={() => void rooms.refetch()} /></div>}
          {!rooms.isPending && !rooms.isError && !filteredRooms.length && <div className="mt-4"><EmptyState variant="compact" motif="cards" title="No matching rooms" description={search || type !== 'ALL' ? 'Adjust the filters to widen your search.' : 'No tables are waiting yet. Host the first one.'} action={<Button type="button" onClick={() => setSearchParams({ create: '1' })}>Create room</Button>} /></div>}
          <div className="mt-5 grid gap-3 md:grid-cols-2">
            {filteredRooms.map((room) => <article key={room.id} className={`room-card ${selectedRoom?.id === room.id ? 'room-card-selected' : ''}`}>
              <div className="flex items-start justify-between gap-3"><div className="min-w-0"><h2 className="truncate font-bold text-text">{room.name}</h2><p className="mt-1 text-xs text-muted">Room #{room.id}</p></div><StatusBadge tone={room.passwordRequired ? 'warning' : 'success'}>{room.roomType}</StatusBadge></div>
              <dl className="mt-4 grid grid-cols-3 gap-2 text-xs"><div><dt>Blinds</dt><dd>{formatNumber(room.smallBlind)} / {formatNumber(room.bigBlind)}</dd></div><div><dt>Players</dt><dd>{room.seatedPlayers} / {room.maxPlayers}</dd></div><div><dt>Buy-in</dt><dd>{formatNumber(room.buyIn)}</dd></div></dl>
              <Button type="button" variant="secondary" className="mt-4 w-full" onClick={() => setSearchParams({ join: String(room.id) })}>Join options</Button>
            </article>)}
          </div>
        </Card>
        <Card className="dashboard-card h-fit p-5 sm:p-6 xl:sticky xl:top-24">
          {joinedRoom ? <JoinedRoomPanel detail={joinedRoom} onLeft={() => setJoinedRoom(null)} />
            : creating ? <CreateRoomPanel onCreated={setJoinedRoom} />
              : selectedRoom ? <JoinRoomPanel room={selectedRoom} onJoined={setJoinedRoom} />
                : <EmptyState variant="inline" motif="suit" title="Choose your table" description="Select a waiting room to review your available join options." />}
        </Card>
      </section>
    </main>
  )
}
