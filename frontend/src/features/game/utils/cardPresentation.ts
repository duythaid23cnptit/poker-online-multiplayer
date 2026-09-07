import type { CardRank, CardSuit } from '../types/game'
export const rankLabels: Record<CardRank,string> = { TWO:'2',THREE:'3',FOUR:'4',FIVE:'5',SIX:'6',SEVEN:'7',EIGHT:'8',NINE:'9',TEN:'10',JACK:'J',QUEEN:'Q',KING:'K',ACE:'A' }
export const suitLabels: Record<CardSuit,string> = { CLUBS:'♣',DIAMONDS:'♦',HEARTS:'♥',SPADES:'♠' }
export function isRedSuit(suit: CardSuit) { return suit === 'DIAMONDS' || suit === 'HEARTS' }
