import type { PlayingCardValue } from '../types/game'
import { isRedSuit, rankLabels, suitLabels } from '../utils/cardPresentation'

export function PlayingCard({ card, hidden=false, compact=false }: { card?:PlayingCardValue; hidden?:boolean; compact?:boolean }) {
  if(hidden||!card)return <span className={`playing-card playing-card-back ${compact?'playing-card-compact':''}`} aria-label="Hidden card"><i>♠</i></span>
  const red=isRedSuit(card.suit); return <span className={`playing-card ${compact?'playing-card-compact':''} ${red?'playing-card-red':''}`} aria-label={`${card.rank.toLowerCase()} of ${card.suit.toLowerCase()}`}><b>{rankLabels[card.rank]}</b><i>{suitLabels[card.suit]}</i></span>
}
