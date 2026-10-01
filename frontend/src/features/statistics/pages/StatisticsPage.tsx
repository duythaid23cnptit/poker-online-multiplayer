import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber } from '../../../shared/lib/format'
import { Card } from '../../../shared/ui/Card'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { useAnalyticsSummary, useCurrentStatistics, useDailyAnalytics, useWeeklyAnalytics } from '../hooks/useStatistics'
import type { AnalyticsBucket } from '../types/statistics'

function isoDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function range(days: number) {
  const to = new Date()
  const from = new Date(to)
  from.setUTCDate(from.getUTCDate() - days)
  return { from: isoDate(from), to: isoDate(to) }
}

function duration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return hours ? `${hours}h ${minutes}m` : `${minutes}m`
}

function netTone(value: number) {
  return value > 0 ? 'text-success' : value < 0 ? 'text-danger' : 'text-secondary'
}

function BucketMetrics({ bucket }: { bucket: AnalyticsBucket }) {
  return <dl className="statistics-bucket-grid">
    <div><dt>Hands</dt><dd>{formatNumber(bucket.handsPlayed)}</dd></div>
    <div><dt>Won</dt><dd>{formatNumber(bucket.handsWon)}</dd></div>
    <div><dt>Net chips</dt><dd className={netTone(bucket.netChips)}>{bucket.netChips > 0 ? '+' : ''}{formatNumber(bucket.netChips)}</dd></div>
    <div><dt>Table time</dt><dd>{duration(bucket.playingTimeSeconds)}</dd></div>
  </dl>
}

export function StatisticsPage() {
  const dailyRange = range(29)
  const weeklyRange = range(83)
  const statistics = useCurrentStatistics()
  const summary = useAnalyticsSummary()
  const daily = useDailyAnalytics(dailyRange.from, dailyRange.to)
  const weekly = useWeeklyAnalytics(weeklyRange.from, weeklyRange.to)
  const queries = [statistics, summary, daily, weekly]
  const error = queries.find((query) => query.isError)?.error

  return <main className="app-page statistics-page">
    <PageHeader title="Performance" description="Authoritative results calculated from your completed game sessions and poker hands." />
    {queries.some((query) => query.isPending) && <LoadingState variant="dashboard" label="Loading your performance..." />}
    {error && <ErrorState message={getErrorMessage(error)} onRetry={() => queries.forEach((query) => void query.refetch())} />}
    {!queries.some((query) => query.isPending) && !error && statistics.data && summary.data && <>
      <section className="statistics-overview mt-7" aria-label="Lifetime statistics">
        <Card className="dashboard-card statistics-hero p-5 sm:p-6">
          <p className="eyebrow">Lifetime record</p>
          <strong>{formatNumber(statistics.data.totalGames)}</strong>
          <span>completed sessions</span>
        </Card>
        {[
          ['Hands played', statistics.data.totalHands],
          ['Sessions won', statistics.data.totalWins],
          ['Win rate', `${statistics.data.winRate.toFixed(2)}%`],
          ['Largest pot', statistics.data.largestPotWon],
          ['Net chips', statistics.data.netChip],
        ].map(([label, value]) => <Card className="dashboard-card statistics-metric" key={label}><p>{label}</p><strong className={label === 'Net chips' ? netTone(Number(value)) : ''}>{typeof value === 'number' ? formatNumber(value) : value}</strong></Card>)}
      </section>

      <section className="mt-5 grid gap-5 lg:grid-cols-2">
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Today</p><h2>{summary.data.today.date}</h2></div><span>{formatNumber(summary.data.today.sessionsParticipated)} sessions</span></div>
          <BucketMetrics bucket={summary.data.today} />
        </Card>
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Current week</p><h2>{summary.data.currentWeek.weekStartDate} to {summary.data.currentWeek.weekEndDate}</h2></div><span>{formatNumber(summary.data.currentWeek.sessionsParticipated)} sessions</span></div>
          <BucketMetrics bucket={summary.data.currentWeek} />
        </Card>
      </section>

      <section className="mt-5 grid items-start gap-5 xl:grid-cols-2">
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Last 30 days</p><h2>Daily results</h2></div></div>
          {!daily.data?.length ? <EmptyState variant="inline" motif="cards" title="No daily results" description="Completed hands in this period will appear here." /> : <div className="mt-4 overflow-x-auto" role="region" aria-label="Daily performance" tabIndex={0}><table className="data-table"><thead><tr><th>Date</th><th>Hands</th><th>Won</th><th>Net chips</th><th>Sessions</th></tr></thead><tbody>{daily.data.map((item) => <tr key={item.date}><td>{item.date}</td><td>{formatNumber(item.handsPlayed)}</td><td>{formatNumber(item.handsWon)}</td><td className={netTone(item.netChips)}>{item.netChips > 0 ? '+' : ''}{formatNumber(item.netChips)}</td><td>{formatNumber(item.sessionsParticipated)}</td></tr>)}</tbody></table></div>}
        </Card>
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Last 12 weeks</p><h2>Weekly results</h2></div></div>
          {!weekly.data?.length ? <EmptyState variant="inline" motif="chip" title="No weekly results" description="Completed hands in this period will appear here." /> : <div className="mt-4 overflow-x-auto" role="region" aria-label="Weekly performance" tabIndex={0}><table className="data-table"><thead><tr><th>Week</th><th>Hands</th><th>Won</th><th>Net chips</th><th>Table time</th></tr></thead><tbody>{weekly.data.map((item) => <tr key={item.weekStartDate}><td>{item.weekStartDate}</td><td>{formatNumber(item.handsPlayed)}</td><td>{formatNumber(item.handsWon)}</td><td className={netTone(item.netChips)}>{item.netChips > 0 ? '+' : ''}{formatNumber(item.netChips)}</td><td>{duration(item.playingTimeSeconds)}</td></tr>)}</tbody></table></div>}
        </Card>
      </section>
    </>}
  </main>
}
