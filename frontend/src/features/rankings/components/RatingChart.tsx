import type { RankingHistory } from '../types/ranking'

export function RatingChart({ history }: { history: RankingHistory[] }) {
  const points = [...history].reverse()
  if (points.length < 2) return <div className="ranking-chart-empty">Complete more rated games to build your rating trend.</div>
  const ratings = points.map((point) => point.newRating)
  const min = Math.min(...ratings)
  const max = Math.max(...ratings)
  const range = Math.max(max - min, 1)
  const path = ratings.map((rating, index) => `${(index / (ratings.length - 1)) * 100},${92 - ((rating - min) / range) * 76}`).join(' ')
  return <div className="ranking-chart" aria-label={`Rating history from ${ratings[0]} to ${ratings.at(-1)}`} role="img">
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="rating-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#58d6a3" stopOpacity=".32"/><stop offset="1" stopColor="#58d6a3" stopOpacity="0"/></linearGradient></defs><polygon points={`0,100 ${path} 100,100`} fill="url(#rating-fill)"/><polyline points={path} fill="none" stroke="#58d6a3" strokeWidth="2" vectorEffect="non-scaling-stroke"/></svg>
    <span className="ranking-chart-max">{max}</span><span className="ranking-chart-min">{min}</span>
  </div>
}
