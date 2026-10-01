import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber, formatTimestamp } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { Input } from '../../../shared/ui/Input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { adminMutations, useAdminGame, useAdminGames, useAdminHands, useAdminMutation } from '../hooks/useAdmin'
import type { AdminGameFilters, AdminGameStatus } from '../types/admin'
import { AdminPagination } from './AdminPagination'
import { ModerationPrompt } from './ModerationPrompt'

export function AdminGamesPanel() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<AdminGameStatus | ''>('')
  const [roomId, setRoomId] = useState('')
  const [userId, setUserId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [selected, setSelected] = useState<number | null>(null)
  const [handsPage, setHandsPage] = useState(0)
  const [confirming, setConfirming] = useState(false)
  const filters: AdminGameFilters = {
    page, size: 20, status: status || undefined,
    roomId: roomId ? Number(roomId) : undefined, userId: userId ? Number(userId) : undefined,
    from: from ? new Date(from).toISOString() : undefined, to: to ? new Date(to).toISOString() : undefined,
  }
  const games = useAdminGames(filters)
  const detail = useAdminGame(selected)
  const hands = useAdminHands(selected, handsPage)
  const moderation = useAdminMutation((value: { id: number; reason?: string }) => adminMutations.terminateGame(value))

  return <div className="admin-section-grid">
    <section className="admin-list-panel">
      <div className="admin-filter-grid admin-game-filters">
        <label>Status<select value={status} onChange={(event) => { setStatus(event.target.value as AdminGameStatus | ''); setPage(0) }}><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="FINISHED">Finished</option><option value="ABORTED">Aborted</option></select></label>
        <label>Room ID<Input type="number" min={1} value={roomId} onChange={(event) => { setRoomId(event.target.value); setPage(0) }} /></label>
        <label>Player ID<Input type="number" min={1} value={userId} onChange={(event) => { setUserId(event.target.value); setPage(0) }} /></label>
        <label>From<Input type="datetime-local" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0) }} /></label>
        <label>To<Input type="datetime-local" value={to} onChange={(event) => { setTo(event.target.value); setPage(0) }} /></label>
      </div>
      {games.isPending && <LoadingState variant="table" label="Loading game sessions..." />}
      {games.isError && <ErrorState message={getErrorMessage(games.error)} onRetry={() => void games.refetch()} />}
      {games.data && !games.data.items.length && <EmptyState variant="compact" motif="cards" title="No sessions found" description="Try changing the current filters." />}
      {games.data?.items.length ? <div className="overflow-x-auto" role="region" aria-label="Admin game sessions" tabIndex={0}><table className="data-table"><thead><tr><th>Session</th><th>Room</th><th>Status</th><th>Players</th><th>Hands</th><th></th></tr></thead><tbody>{games.data.items.map((game) => <tr key={game.gameSessionId} className={selected === game.gameSessionId ? 'admin-selected-row' : undefined} aria-selected={selected === game.gameSessionId}><td><strong>#{game.gameSessionId}</strong><small>{formatTimestamp(game.startedAt)}</small></td><td>#{game.roomId}</td><td><StatusBadge tone={game.status === 'ACTIVE' ? 'success' : 'muted'}>{game.status}</StatusBadge></td><td>{game.participantCount}</td><td>{game.handCount}</td><td><Button type="button" variant="ghost" onClick={() => { setSelected(game.gameSessionId); setHandsPage(0) }}>Inspect</Button></td></tr>)}</tbody></table></div> : null}
      {games.data && <AdminPagination page={page} size={20} total={games.data.total} onPage={setPage} />}
    </section>
    <aside className="admin-detail-panel">
      {selected === null && <EmptyState variant="inline" motif="cards" title="Select a game session" description="Review session participants, persisted hands, and active-game controls." />}
      {selected !== null && (detail.isPending || hands.isPending) && <LoadingState label="Loading game detail..." />}
      {selected !== null && (detail.isError || hands.isError) && <ErrorState message={getErrorMessage(detail.error || hands.error)} onRetry={() => { void detail.refetch(); void hands.refetch() }} />}
      {selected !== null && detail.data && hands.data && <>
        <div className="admin-detail-heading"><div><p>Game session #{detail.data.gameSessionId}</p><h2>{detail.data.roomName}</h2><span>Room #{detail.data.roomId} · started {formatTimestamp(detail.data.startedAt)}</span></div><StatusBadge tone={detail.data.status === 'ACTIVE' ? 'success' : 'muted'}>{detail.data.status}</StatusBadge></div>
        {detail.data.status === 'ACTIVE' && <Button type="button" variant="danger" onClick={() => { moderation.reset(); setConfirming(true) }}>Terminate game</Button>}
        {moderation.isSuccess && !confirming && <div className="form-success" role="status">Termination request accepted.</div>}
        {confirming && <ModerationPrompt title={`Terminate session #${detail.data.gameSessionId}`} description="If a hand is active, settlement completes first and termination is deferred to that boundary." pending={moderation.isPending} error={moderation.error} onCancel={() => setConfirming(false)} onConfirm={(reason) => moderation.mutate({ id: detail.data.gameSessionId, reason }, { onSuccess: () => setConfirming(false) })} />}
        <h3 className="admin-subheading mt-5">Participants</h3>
        {!detail.data.participants.length ? <p className="admin-safe-detail">No persisted participants.</p> : <ul className="admin-participant-list">{detail.data.participants.map((player) => <li key={player.userId}><span><strong>@{player.username}</strong><small>Player #{player.userId} · {formatNumber(player.handsPlayed)} hands</small></span><strong className={player.netChips >= 0 ? 'text-success' : 'text-danger'}>{player.netChips >= 0 ? '+' : ''}{formatNumber(player.netChips)}</strong></li>)}</ul>}
        <h3 className="admin-subheading mt-5">Persisted hands</h3>
        {!hands.data.items.length ? <p className="admin-safe-detail">No hands persisted for this session.</p> : <div className="overflow-x-auto" role="region" aria-label="Session hands" tabIndex={0}><table className="data-table"><thead><tr><th>Hand</th><th>Result</th><th>Pot awarded</th><th>Board</th></tr></thead><tbody>{hands.data.items.map((hand) => <tr key={hand.handId}><td>#{hand.handNumber}</td><td>{hand.endReason || hand.finalPhase || 'In progress'}</td><td>{formatNumber(hand.totalPotAwarded)}</td><td>{hand.boardCards || '—'}</td></tr>)}</tbody></table></div>}
        <AdminPagination page={handsPage} size={20} total={hands.data.total} onPage={setHandsPage} />
      </>}
    </aside>
  </div>
}
