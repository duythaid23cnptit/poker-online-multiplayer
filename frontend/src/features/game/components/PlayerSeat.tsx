import type { CSSProperties } from 'react'
import { Avatar } from '../../../shared/ui/Avatar'
import { formatNumber } from '../../../shared/lib/format'
import type { PublicGamePlayer } from '../types/game'
import { seatPosition } from '../utils/seatLayout'

export function PlayerSeat({player,capacity,ownUserId,ownSeat,name,dealer,currentActor,live=true,layoutSeat,layoutOwnSeat}:{player:PublicGamePlayer;capacity:number;ownUserId:number;ownSeat?:number;name:string;dealer:boolean;currentActor:boolean;live?:boolean;layoutSeat?:number;layoutOwnSeat?:number}){
  const position=seatPosition(layoutSeat??player.seat,capacity,layoutOwnSeat??ownSeat); const style={ '--seat-x':`${position.x}%`,'--seat-y':`${position.y}%`} as CSSProperties
  return <div role="group" aria-label={`${name}, seat ${player.seat}${player.userId===ownUserId?', your seat':''}`} className={`game-seat ${player.userId===ownUserId?'game-seat-own':''} ${currentActor?'game-seat-turn':''} ${live&&!player.connected?'game-seat-disconnected':''} ${player.participation==='FOLDED'?'game-seat-folded':''}`} style={style}>
    <Avatar displayName={name} size="sm"/><div className="game-seat-copy"><strong title={name}>{name}</strong><span>{formatNumber(player.tableChips)} chips</span><small>{!live?'Final stack':player.leaving?'Leaving':!player.connected?'Disconnected':currentActor?(player.userId===ownUserId?'Your turn':'Current turn'):player.participation.replace('_',' ')}</small></div>
    {live&&player.currentBet>0&&<span className="seat-bet">Bet {formatNumber(player.currentBet)}</span>}{dealer&&<span className="dealer-button" aria-label="Dealer">D</span>}
  </div>
}
