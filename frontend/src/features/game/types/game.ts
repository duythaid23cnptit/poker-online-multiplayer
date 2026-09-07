export type GamePhase = 'PRE_FLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN' | 'FINISHED'
export type PokerPlayerState = 'ACTIVE' | 'FOLDED' | 'ALL_IN'
export type PokerActionType = 'FOLD' | 'CHECK' | 'CALL' | 'BET' | 'RAISE' | 'ALL_IN'
export type CardRank = 'TWO' | 'THREE' | 'FOUR' | 'FIVE' | 'SIX' | 'SEVEN' | 'EIGHT' | 'NINE' | 'TEN' | 'JACK' | 'QUEEN' | 'KING' | 'ACE'
export type CardSuit = 'CLUBS' | 'DIAMONDS' | 'HEARTS' | 'SPADES'
export interface PlayingCardValue { rank: CardRank; suit: CardSuit }
export interface PublicGamePlayer { userId: number; seat: number; tableChips: number; currentBet: number; totalCommitted?: number | null; participation: PokerPlayerState; connected: boolean; leaving: boolean }
export interface GameStatePayload { handId: number; handNumber: number; phase: GamePhase; dealerSeat: number; smallBlindSeat: number; bigBlindSeat: number; currentTurnUserId: number | null; currentBet: number; minimumRaise: number; communityCards: PlayingCardValue[]; players: PublicGamePlayer[]; handCompleted: boolean; sessionFinished: boolean }
export interface TurnPayload { handId: number; turnId: string; legalActions: PokerActionType[]; callAmount: number; minimumTarget: number; maximumTarget: number; tableChips: number }
export interface TimerPayload { handId: number; turnId: string; currentTurnSeat: number; remainingSeconds: number; deadline: string }
export interface PlayerActionPayload { userId: number; seat: number; actionType: PokerActionType; amount: number; resultingCurrentBet: number; resultingTableChips: number; clientActionId: string | null; automatic: boolean }
export interface GameAward { potIndex: number; potType: string; potAmount: number; winnerUserIds: number[]; winnerPayouts: Record<string, number> }
export interface GameResultPayload { handId: number; awards: GameAward[]; uncalledReturns: { userId: number; amount: number }[]; finalPlayers: PublicGamePlayer[]; endReason: string }
export type GameEventType = 'GAME_STARTED' | 'HAND_STARTED' | 'HOLE_CARDS' | 'COMMUNITY_CARDS' | 'YOUR_TURN' | 'PLAYER_ACTION' | 'TIMER_UPDATE' | 'GAME_STATE_UPDATE' | 'SHOWDOWN' | 'GAME_RESULT' | 'HAND_FINISHED' | 'COMMAND_ERROR'
export interface GameRealtimeEvent { eventId: string; type: GameEventType; timestamp: string; roomId: number; gameId: string; version: number; payload: unknown }
export interface GameViewState { roomId: number; gameId: string; version: number; seenEventIds: string[]; publicState: GameStatePayload | null; holeCards: PlayingCardValue[]; turn: TurnPayload | null; timer: TimerPayload | null; result: GameResultPayload | null; lastAction: PlayerActionPayload | null; commandError: { code: string; clientActionId: string | null } | null }
export interface GameActionCommand { actionType: PokerActionType; amount?: number; turnId: string; clientActionId: string }
export interface ActiveGame { roomId: number; gameId: string; gameSessionId: number; handId: number; handNumber: number }
export interface GameDeparture { roomId: number; gameId: string; changed: boolean; deferred: boolean }
export interface GameSnapshot { roomId: number; gameId: string; gameSessionId: number; version: number; publicState: GameStatePayload; holeCards: PlayingCardValue[]; turn: TurnPayload | null; timer: TimerPayload | null; participant: boolean }
