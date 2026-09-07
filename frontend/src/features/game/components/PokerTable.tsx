import { formatNumber } from '../../../shared/lib/format'
import type { RoomDetail } from '../../rooms/types/room'
import type { GameViewState } from '../types/game'
import { PlayingCard } from './PlayingCard'
import { PlayerSeat } from './PlayerSeat'
import { TurnTimer } from './TurnTimer'
import { playerNameProjection, resolvedPlayerName } from '../utils/playerIdentity'

export function PokerTable({state,room,ownUserId}:{state:GameViewState;room?:RoomDetail;ownUserId:number}){
  const game=state.publicState; if(!game)return <div className="game-awaiting"><span className="empty-motif empty-motif-cards" aria-hidden="true"/><h2>Awaiting authoritative game state</h2><p>This table will render when the server delivers a game snapshot event.</p></div>
  const own=game.players.find((player)=>player.userId===ownUserId)
  const names=playerNameProjection(state.roomId,room)
  const occupiedSeats = game.players.map((player) => player.seat).sort((a, b) => a - b)
  const capacity = occupiedSeats.length
  const committed = game.players.every((player) => player.totalCommitted != null)
    ? game.players.reduce((total, player) => total + player.totalCommitted!, 0) : null
  const live = !game.sessionFinished
  return <><div className={`poker-stage ${game.players.length <= 3 ? 'poker-stage-small' : ''} ${game.players.length >= 7 ? 'poker-stage-dense' : ''}`}><div className="poker-felt">
    <div className="table-center">{live&&!game.handCompleted&&committed!==null&&<div className="table-pot" aria-label="Current pot"><span>Pot · Including bets</span><b>{formatNumber(committed)}</b></div>}<div className="community-cards" aria-label="Community cards">{game.communityCards.map((card,index)=><PlayingCard card={card} key={`${card.rank}-${card.suit}-${index}`}/>)}{Array.from({length:5-game.communityCards.length},(_,index)=><span className="card-slot" key={index}/>)}</div><div className="table-meta"><strong>{game.phase.replace('_',' ')}</strong><span>Hand #{game.handNumber}</span>{live&&!game.handCompleted&&<TurnTimer timer={state.timer}/>}</div></div>
    {game.players.map((player)=><PlayerSeat key={player.userId} player={player} capacity={capacity} layoutSeat={occupiedSeats.indexOf(player.seat)+1} layoutOwnSeat={own?occupiedSeats.indexOf(own.seat)+1:undefined} ownUserId={ownUserId} ownSeat={own?.seat} name={resolvedPlayerName(names,player.userId)} dealer={player.seat===game.dealerSeat} live={live} currentActor={live && player.userId===game.currentTurnUserId}/>) }
  </div></div>
  {state.holeCards.length>0&&<div className="own-hole-cards"><span>Your cards</span><div>{state.holeCards.map((card,index)=><PlayingCard card={card} key={index}/>)}</div></div>}
  {state.result&&<div className="game-result" role="status"><strong>{state.result.endReason==='SHOWDOWN'?'Showdown complete':'Hand complete'}</strong>{state.result.awards.map((award)=><div className="hand-award" key={award.potIndex}>
    <span>{award.potType==='MAIN'?'Main pot':`Side pot ${award.potIndex}`} · {formatNumber(award.potAmount)} chips</span>
    <b>{award.winnerUserIds.map((id)=>`${resolvedPlayerName(names,id)}${award.winnerPayouts[String(id)]!==undefined?` +${formatNumber(award.winnerPayouts[String(id)])}`:''}`).join(' · ')}</b>
  </div>)}</div>}
  </>
}
