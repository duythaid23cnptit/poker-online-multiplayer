import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber, formatTimestamp } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { useCurrentRanking, useLeaderboard, useRankingHistory } from '../hooks/useRankings'
import { RatingChart } from '../components/RatingChart'
import { RankingPlayerIdentity } from '../components/RankingPlayerIdentity'

export function RankingsPage() {
  const [page, setPage] = useState(0)
  const current = useCurrentRanking()
  const leaderboard = useLeaderboard(page, 20)
  const history = useRankingHistory(0, 12)
  const queries = [current, leaderboard, history]
  const error = queries.find((query) => query.isError)?.error
  return <main className="app-page">
    <PageHeader title="The global field" description="Your rating, the global field, and your completed-game history." />
    {queries.some((query) => query.isPending) && <LoadingState variant="table" label="Loading rankings…" />}
    {error && <ErrorState message={getErrorMessage(error)} onRetry={() => queries.forEach((query) => void query.refetch())} />}
    {!queries.some((query) => query.isPending) && !error && <div className="mt-7 grid items-start gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(19rem,0.65fr)]">
      <Card className="dashboard-card min-w-0 p-5 sm:p-6"><div className="section-heading"><div><p>Global</p><h2>Leaderboard</h2></div><span>{formatNumber(leaderboard.data?.total || 0)} rated players</span></div>
        <div className="mt-4 overflow-x-auto" role="region" aria-label="Leaderboard" tabIndex={0}><table className="data-table"><thead><tr><th>Rank</th><th>Player</th><th>Rating</th><th>Games rated</th><th>Peak</th></tr></thead><tbody>{leaderboard.data?.items.map((item) => <tr key={item.userId} className={item.userId === current.data?.userId ? 'current-player-row' : ''}><td>#{item.rank ?? '—'}</td><td><RankingPlayerIdentity player={item} />{item.userId === current.data?.userId && <small>You</small>}</td><td>{formatNumber(item.rating)}</td><td>{formatNumber(item.gamesRated)}</td><td>{formatNumber(item.peakRating)}</td></tr>)}</tbody></table></div>
        {!leaderboard.data?.items.length && <EmptyState variant="compact" motif="chip" title="No rated players yet" description="The leaderboard will populate after completed rated games." />}
        {Boolean(leaderboard.data?.total) && <div className="mt-5 flex items-center justify-between"><Button variant="secondary" type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</Button><span className="text-sm text-muted">Page {page + 1}</span><Button variant="secondary" type="button" disabled={(page + 1) * 20 >= (leaderboard.data?.total || 0)} onClick={() => setPage((value) => value + 1)}>Next</Button></div>}
      </Card>
      <aside className="space-y-5"><Card className="dashboard-card p-5 sm:p-6"><p className="text-xs font-black uppercase tracking-[0.18em] text-accent">Your rating</p><div className="mt-3 flex items-end justify-between gap-4"><strong className="text-4xl font-black text-text">{formatNumber(current.data?.rating || 0)}</strong><span className="text-sm font-bold text-accent">{current.data?.rank ? `#${current.data.rank} global` : 'Unrated'}</span></div><dl className="mt-5 grid grid-cols-2 gap-3"><div className="metric-tile"><dt>Peak rating</dt><dd>{formatNumber(current.data?.peakRating || 0)}</dd></div><div className="metric-tile"><dt>Games rated</dt><dd>{formatNumber(current.data?.gamesRated || 0)}</dd></div></dl></Card>
        <Card className="dashboard-card p-5 sm:p-6"><div className="section-heading"><div><p>Last 12 games</p><h2>Rating history</h2></div></div>{history.data?.length ? <><div className="mt-4"><RatingChart history={history.data} /></div><ol className="mt-3 divide-y divide-border/70">{history.data.slice(0, 5).map((item) => <li key={item.gameSessionId} className="flex items-center justify-between gap-4 py-3 text-sm"><span><strong className="block text-text">Game #{item.gameSessionId}</strong><time className="text-xs text-muted" dateTime={item.createdAt}>{formatTimestamp(item.createdAt)}</time></span><span className={item.ratingDelta >= 0 ? 'font-bold text-success' : 'font-bold text-danger'}>{item.ratingDelta >= 0 ? '+' : ''}{item.ratingDelta}</span></li>)}</ol></> : <div className="mt-4"><EmptyState variant="inline" motif="cards" title="No rating history" description="Completed rated games will appear here." /></div>}</Card>
      </aside>
    </div>}
  </main>
}
