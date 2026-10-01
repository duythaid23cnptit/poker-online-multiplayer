import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatTimestamp } from '../../../shared/lib/format'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { Input } from '../../../shared/ui/Input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useAdminAudit } from '../hooks/useAdmin'
import type { AdminActionType, AdminAuditFilters, AdminTargetType } from '../types/admin'
import { AdminPagination } from './AdminPagination'

function targetLabel(targetType: AdminTargetType, targetId: number | null) {
  if (targetId === null) return targetType.replaceAll('_', ' ')
  const label = targetType === 'USER' ? 'User' : targetType === 'ROOM' ? 'Room' : 'Game session'
  return `${label} #${targetId}`
}

export function AdminAuditPanel() {
  const [page, setPage] = useState(0)
  const [adminUserId, setAdminUserId] = useState('')
  const [targetId, setTargetId] = useState('')
  const [actionType, setActionType] = useState<AdminActionType | ''>('')
  const [targetType, setTargetType] = useState<AdminTargetType | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const filters: AdminAuditFilters = { page, size: 20, adminUserId: adminUserId ? Number(adminUserId) : undefined, targetId: targetId ? Number(targetId) : undefined, actionType: actionType || undefined, targetType: targetType || undefined, from: from ? new Date(from).toISOString() : undefined, to: to ? new Date(to).toISOString() : undefined }
  const audit = useAdminAudit(filters)
  return <section className="admin-list-panel">
    <div className="admin-filter-grid">
      <label>Admin user ID<Input type="number" min={1} value={adminUserId} onChange={(event) => { setAdminUserId(event.target.value); setPage(0) }} /></label>
      <label>Action<select value={actionType} onChange={(event) => { setActionType(event.target.value as AdminActionType | ''); setPage(0) }}><option value="">All actions</option>{['USER_SUSPENDED', 'USER_REACTIVATED', 'PLAYER_REMOVED_FROM_ROOM', 'ROOM_CLOSED', 'GAME_TERMINATED'].map((value) => <option key={value}>{value}</option>)}</select></label>
      <label>Target type<select value={targetType} onChange={(event) => { setTargetType(event.target.value as AdminTargetType | ''); setPage(0) }}><option value="">All targets</option><option value="USER">User</option><option value="ROOM">Room</option><option value="GAME_SESSION">Game session</option></select></label>
      <label>Target ID<Input type="number" min={1} value={targetId} onChange={(event) => { setTargetId(event.target.value); setPage(0) }} /></label>
      <label>From<Input type="datetime-local" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0) }} /></label>
      <label>To<Input type="datetime-local" value={to} onChange={(event) => { setTo(event.target.value); setPage(0) }} /></label>
    </div>
    {audit.isPending && <LoadingState variant="table" label="Loading audit records..." />}
    {audit.isError && <ErrorState message={getErrorMessage(audit.error)} onRetry={() => void audit.refetch()} />}
    {audit.data && !audit.data.items.length && <EmptyState variant="compact" motif="chip" title="No audit records" description="Completed moderation actions will appear here." />}
    {audit.data?.items.length ? <div className="overflow-x-auto" role="region" aria-label="Admin audit log" tabIndex={0}><table className="data-table"><thead><tr><th>Time</th><th>Admin</th><th>Action</th><th>Target</th><th>Reason</th><th>Details</th></tr></thead><tbody>{audit.data.items.map((item) => <tr key={item.id}><td>{formatTimestamp(item.createdAt)}</td><td>Admin #{item.adminUserId}</td><td>{item.actionType.replaceAll('_', ' ')}</td><td>{targetLabel(item.targetType, item.targetId)}</td><td>{item.reason || '—'}</td><td>{Object.keys(item.metadata).length ? <details className="admin-audit-details"><summary>View</summary><dl>{Object.entries(item.metadata).map(([key, value]) => <div key={key}><dt>{key.replaceAll('_', ' ')}</dt><dd>{String(value)}</dd></div>)}</dl></details> : '—'}</td></tr>)}</tbody></table></div> : null}
    {audit.data && <AdminPagination page={page} size={20} total={audit.data.total} onPage={setPage} />}
  </section>
}
