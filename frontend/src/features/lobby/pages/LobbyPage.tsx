import { Link } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'
import { useFriends } from '../../friends/hooks/useFriends'
import { useLeaderboard } from '../../rankings/hooks/useRankings'
import { useRooms } from '../../rooms/hooks/useRooms'
import { useLobbyRealtime } from '../../rooms/realtime/useLobbyRealtime'
import { getErrorMessage } from '../../../shared/api/apiError'
import { Card } from '../../../shared/ui/Card'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { FriendsPreview } from '../components/FriendsPreview'
import { LeaderboardPreview } from '../components/LeaderboardPreview'
import { RoomPreview } from '../components/RoomPreview'
import { NotificationList } from '../../notifications/components/NotificationList'

export function LobbyPage() {
  useLobbyRealtime()
  const profile = useCurrentProfile()
  const rooms = useRooms()
  const friends = useFriends()
  const rankings = useLeaderboard(0, 5)
  if (profile.isPending || rooms.isPending || friends.isPending || rankings.isPending) return <main className="app-page"><LoadingState variant="dashboard" label="Preparing the lobby…" /></main>
  const firstError = profile.error || rooms.error || friends.error || rankings.error
  if (firstError) return <ErrorState message={getErrorMessage(firstError)} onRetry={() => {
    void profile.refetch(); void rooms.refetch(); void friends.refetch(); void rankings.refetch()
  }} />
  return (
    <main className="app-page">
      <PageHeader
        title={`Good to see you, ${profile.data?.displayName || 'player'}.`}
        description="Find a waiting room, reconnect with friends, and see how the global field is developing."
        actions={<>
          <Link className="action-link-secondary" to={routes.rooms}>Browse rooms</Link>
          <Link className="action-link-primary" to={`${routes.rooms}?create=1`}>Create room</Link>
        </>}
      />
      <section className="lobby-grid mt-7">
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Live lobby</p><h2>Waiting rooms</h2></div><Link to={routes.rooms}>View all →</Link></div>
          <div className="mt-4"><RoomPreview rooms={rooms.data || []} /></div>
        </Card>
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Global field</p><h2>Top players</h2></div></div>
          <div className="mt-4"><LeaderboardPreview rankings={rankings.data?.items || []} /></div>
        </Card>
      </section>
      <section className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Your circle</p><h2>Friends online</h2></div><Link to={routes.friends}>View friends →</Link></div>
          <div className="mt-4"><FriendsPreview friends={friends.data || []} /></div>
        </Card>
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>This session</p><h2>Live notifications</h2></div></div>
          <div className="mt-2"><NotificationList compact /></div>
        </Card>
      </section>
    </main>
  )
}
