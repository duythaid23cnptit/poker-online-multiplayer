import { Link } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import type { CurrentRanking } from '../../rankings/types/ranking'
import { formatNumber } from '../../../shared/lib/format'
import { EmptyState } from '../../../shared/ui/EmptyState'

export function LeaderboardPreview({ rankings }: { rankings: CurrentRanking[] }) {
  if (!rankings.length) return <EmptyState variant="compact" motif="chip" title="Leaderboard is taking shape" description="Rated players appear after completed game sessions." action={<Link className="empty-action-link" to={routes.rankings}>View rankings</Link>} />
  return (
    <ol className="space-y-2">
      {rankings.slice(0, 5).map((ranking) => (
        <li key={ranking.userId} className="flex items-center gap-3 rounded-control border border-border/70 bg-surface-elevated/45 px-3 py-2.5">
          <span className="grid size-7 place-items-center rounded-full bg-accent/10 text-xs font-black text-accent">{ranking.rank}</span>
          <span className="min-w-0 flex-1 truncate text-sm font-semibold text-text">Player #{ranking.userId}</span>
          <strong className="text-sm text-accent">{formatNumber(ranking.rating)}</strong>
        </li>
      ))}
      <li><Link className="mt-2 inline-flex text-sm font-bold text-accent hover:text-accent-strong" to={routes.rankings}>View full leaderboard →</Link></li>
    </ol>
  )
}
