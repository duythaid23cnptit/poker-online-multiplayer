import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber, formatTimestamp } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { Input } from '../../../shared/ui/Input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { adminMutations, useAdminMutation, useAdminUser, useAdminUsers } from '../hooks/useAdmin'
import type { AdminAccountStatus, AdminRole, AdminUserFilters } from '../types/admin'
import { AdminPagination } from './AdminPagination'
import { ModerationPrompt } from './ModerationPrompt'

type UserAction = { id: number; kind: 'suspend' | 'reactivate'; name: string }

export function AdminUsersPanel() {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<AdminAccountStatus | ''>('')
  const [role, setRole] = useState<AdminRole | ''>('')
  const [selected, setSelected] = useState<number | null>(null)
  const [action, setAction] = useState<UserAction | null>(null)
  const filters: AdminUserFilters = { page, size: 20, search: search.trim() || undefined, status: status || undefined, role: role || undefined }
  const users = useAdminUsers(filters)
  const detail = useAdminUser(selected)
  const moderation = useAdminMutation((value: UserAction & { reason?: string }) => value.kind === 'suspend'
    ? adminMutations.suspend({ id: value.id, reason: value.reason })
    : adminMutations.reactivate({ id: value.id, reason: value.reason }))

  return <div className="admin-section-grid">
    <section className="admin-list-panel">
      <div className="admin-filter-grid">
        <label>Search users<Input value={search} placeholder="Username, email, or display name" onChange={(event) => { setSearch(event.target.value); setPage(0) }} /></label>
        <label>Status<select value={status} onChange={(event) => { setStatus(event.target.value as AdminAccountStatus | ''); setPage(0) }}><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="LOCKED">Locked</option></select></label>
        <label>Role<select value={role} onChange={(event) => { setRole(event.target.value as AdminRole | ''); setPage(0) }}><option value="">All roles</option><option value="PLAYER">Player</option><option value="ADMIN">Admin</option></select></label>
      </div>
      {users.isPending && <LoadingState variant="table" label="Loading users..." />}
      {users.isError && <ErrorState message={getErrorMessage(users.error)} onRetry={() => void users.refetch()} />}
      {users.data && !users.data.items.length && <EmptyState variant="compact" motif="chip" title="No users found" description="Try changing the current filters." />}
      {users.data?.items.length ? <div className="overflow-x-auto" role="region" aria-label="Admin users" tabIndex={0}><table className="data-table"><thead><tr><th>User</th><th>Status</th><th>Role</th><th>Account chips</th><th>Rating</th><th></th></tr></thead><tbody>{users.data.items.map((user) => <tr key={user.userId} className={selected === user.userId ? 'admin-selected-row' : undefined} aria-selected={selected === user.userId}><td><strong>{user.displayName}</strong><small>@{user.username} · #{user.userId}</small></td><td><StatusBadge tone={user.accountStatus === 'ACTIVE' ? 'success' : 'warning'}>{user.accountStatus}</StatusBadge></td><td>{user.role}</td><td>{formatNumber(user.accountChips)}</td><td>{formatNumber(user.rating)}</td><td><Button type="button" variant="ghost" onClick={() => setSelected(user.userId)}>Inspect</Button></td></tr>)}</tbody></table></div> : null}
      {users.data && <AdminPagination page={page} size={20} total={users.data.total} onPage={setPage} />}
    </section>
    <aside className="admin-detail-panel">
      {selected === null && <EmptyState variant="inline" motif="cards" title="Select a user" description="Open an account to review profile, play, ranking, and moderation data." />}
      {selected !== null && detail.isPending && <LoadingState label="Loading user detail..." />}
      {selected !== null && detail.isError && <ErrorState message={getErrorMessage(detail.error)} onRetry={() => void detail.refetch()} />}
      {selected !== null && detail.data && <>
        <div className="admin-detail-heading"><div><p>User #{detail.data.userId}</p><h2>{detail.data.displayName}</h2><span>@{detail.data.username} · joined {formatTimestamp(detail.data.createdAt)}</span></div><StatusBadge tone={detail.data.accountStatus === 'ACTIVE' ? 'success' : 'warning'}>{detail.data.accountStatus}</StatusBadge></div>
        <dl className="admin-detail-metrics"><div><dt>Account chips</dt><dd>{formatNumber(detail.data.accountChips)}</dd></div><div><dt>Rating</dt><dd>{formatNumber(detail.data.rating)}</dd></div><div><dt>Games</dt><dd>{formatNumber(detail.data.totalGames)}</dd></div><div><dt>Hands</dt><dd>{formatNumber(detail.data.totalHands)}</dd></div><div><dt>Win rate</dt><dd>{detail.data.winRate.toFixed(2)}%</dd></div><div><dt>Net chips</dt><dd>{formatNumber(detail.data.netChip)}</dd></div></dl>
        <p className="admin-safe-detail">{detail.data.email || 'No email'} · {detail.data.onlineStatus.replace('_', ' ')}</p>
        <Button type="button" variant={detail.data.accountStatus === 'ACTIVE' ? 'danger' : 'secondary'} onClick={() => { moderation.reset(); setAction({ id: detail.data.userId, kind: detail.data.accountStatus === 'ACTIVE' ? 'suspend' : 'reactivate', name: detail.data.displayName }) }}>{detail.data.accountStatus === 'ACTIVE' ? 'Suspend account' : 'Reactivate account'}</Button>
        {moderation.isSuccess && !action && <div className="form-success" role="status">Account status updated.</div>}
        {action && <ModerationPrompt title={`${action.kind === 'suspend' ? 'Suspend' : 'Reactivate'} ${action.name}`} description={action.kind === 'suspend' ? 'This immediately blocks REST and realtime access.' : 'This restores access to the account.'} pending={moderation.isPending} error={moderation.error} onCancel={() => setAction(null)} onConfirm={(reason) => moderation.mutate({ ...action, reason }, { onSuccess: () => setAction(null) })} />}
        <div className="mt-5"><h3 className="admin-subheading">Recent rating history</h3>{detail.data.recentRankingHistory.length ? <ul className="admin-history-list">{detail.data.recentRankingHistory.map((item) => <li key={item.gameSessionId}><span>Session #{item.gameSessionId}</span><strong className={item.ratingDelta >= 0 ? 'text-success' : 'text-danger'}>{item.ratingDelta >= 0 ? '+' : ''}{item.ratingDelta}</strong></li>)}</ul> : <p className="admin-safe-detail">No rated sessions.</p>}</div>
      </>}
    </aside>
  </div>
}
