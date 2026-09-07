import { Link } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { formatNumber } from '../../../shared/lib/format'
import type { RoomDetail } from '../../rooms/types/room'
import type { GameViewState } from '../types/game'
import { playerNameProjection, resolvedPlayerName } from '../utils/playerIdentity'

export function GameSessionComplete({ state, room }: { state: GameViewState; room?: RoomDetail }) {
  const game = state.publicState
  if (!game?.sessionFinished) return null
  const names = playerNameProjection(state.roomId, room)
  const playerName = (userId: number) => resolvedPlayerName(names, userId)

  return <section className="game-session-complete" aria-labelledby="game-complete-title">
    <div className="game-complete-heading">
      <div><p>Game complete</p><h2 id="game-complete-title">Session finished</h2></div>
      <Link className="action-link-primary" to={routes.rooms}>Back to Rooms</Link>
    </div>
    <div className="game-complete-grid">
      <div><h3>Final stacks</h3><ul className="final-stack-list">
        {[...game.players].sort((left, right) => left.seat - right.seat).map((player) => <li key={player.userId}>
          <span><small>Seat {player.seat}</small>{playerName(player.userId)}</span>
          <strong>{formatNumber(player.tableChips)} chips</strong>
        </li>)}
      </ul></div>
      {state.result?.awards.length ? <div><h3>Final awards</h3><ul className="final-award-list">
        {state.result.awards.map((award) => <li key={award.potIndex}>
          <span>Pot {award.potIndex + 1} · {formatNumber(award.potAmount)} chips</span>
          <strong>{award.winnerUserIds.map((userId) => {
            const payout = award.winnerPayouts[String(userId)]
            return payout === undefined ? playerName(userId) : `${playerName(userId)} — ${formatNumber(payout)} chips`
          }).join(', ')}</strong>
        </li>)}
      </ul></div> : null}
    </div>
  </section>
}
