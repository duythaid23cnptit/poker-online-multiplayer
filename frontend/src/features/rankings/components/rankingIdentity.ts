import type { CurrentRanking } from '../types/ranking'

export function rankingPlayerIdentity(player: Pick<CurrentRanking, 'userId' | 'username' | 'displayName'>) {
  const username = player.username?.trim() || null
  const displayName = player.displayName?.trim() || null
  return {
    name: displayName || username || `Player #${player.userId}`,
    username: displayName && username ? username : null,
  }
}
