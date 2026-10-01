import type { CurrentRanking } from '../types/ranking'
import { rankingPlayerIdentity } from './rankingIdentity'

export function RankingPlayerIdentity({ player }: { player: CurrentRanking }) {
  const identity = rankingPlayerIdentity(player)
  return <span className="min-w-0">
    <strong className="block truncate text-text">{identity.name}</strong>
    {identity.username && <small className="block truncate text-muted">@{identity.username}</small>}
  </span>
}
