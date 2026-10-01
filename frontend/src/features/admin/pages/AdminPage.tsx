import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber, formatTimestamp } from '../../../shared/lib/format'
import { Card } from '../../../shared/ui/Card'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { AdminAuditPanel } from '../components/AdminAuditPanel'
import { AdminGamesPanel } from '../components/AdminGamesPanel'
import { AdminRoomsPanel } from '../components/AdminRoomsPanel'
import { AdminUsersPanel } from '../components/AdminUsersPanel'
import { useAdminOverview } from '../hooks/useAdmin'

export type AdminSection = 'overview' | 'users' | 'rooms' | 'games' | 'audit'

export function AdminPage({ section = 'overview' }: { section?: AdminSection }) {
  const overview = useAdminOverview()
  return <main className="app-page admin-page">
    <PageHeader title="Administration" description="Monitor authoritative platform state and apply audited moderation controls." />
    {section === 'overview' && overview.isPending && <LoadingState variant="dashboard" label="Loading platform overview..." />}
    {section === 'overview' && overview.isError && <ErrorState message={getErrorMessage(overview.error)} onRetry={() => void overview.refetch()} />}
    {overview.data && <>
      {section === 'overview' && <section className="admin-overview" aria-label="Platform overview">
        {[
          ['Users', overview.data.totalUsers, `${formatNumber(overview.data.activeUsers)} active`],
          ['Open rooms', overview.data.openRooms, `${formatNumber(overview.data.totalRooms)} total`],
          ['Active games', overview.data.activeGameSessions, `${formatNumber(overview.data.completedGameSessions)} completed`],
          ['Hands played', overview.data.handsPlayed, `${formatNumber(overview.data.totalChipsInAccounts)} account chips`],
        ].map(([label, value, note]) => <Card className="dashboard-card admin-overview-card" key={label}><span>{label}</span><strong>{formatNumber(Number(value))}</strong><small>{note}</small></Card>)}
      </section>}
      {section === 'overview' && <>
        <section className="admin-operations" aria-label="Operational snapshot">
          <div className="admin-operations-heading"><div><p>Operational snapshot</p><h2>Platform activity at a glance</h2></div><span>Authoritative counts</span></div>
          <div className="admin-operations-grid">
            <div><span>Room pipeline</span><strong>{formatNumber(overview.data.openRooms)} open</strong><small>{formatNumber(overview.data.totalRooms)} total rooms</small></div>
            <div><span>Game pipeline</span><strong>{formatNumber(overview.data.activeGameSessions)} active</strong><small>{formatNumber(overview.data.completedGameSessions)} completed sessions</small></div>
            <div><span>Account liquidity</span><strong>{formatNumber(overview.data.totalChipsInAccounts)}</strong><small>chips held in accounts</small></div>
          </div>
        </section>
        <p className="admin-generated">Snapshot generated {formatTimestamp(overview.data.generatedAt)}</p>
      </>}
    </>}
      {section === 'users' && <AdminUsersPanel />}
      {section === 'rooms' && <AdminRoomsPanel />}
      {section === 'games' && <AdminGamesPanel />}
      {section === 'audit' && <AdminAuditPanel />}
  </main>
}
