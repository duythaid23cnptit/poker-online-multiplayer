export type AdminAccountStatus = 'ACTIVE' | 'LOCKED'
export type AdminRole = 'PLAYER' | 'ADMIN'
export type AdminRoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'
export type AdminRoomType = 'PUBLIC' | 'PRIVATE'
export type AdminGameStatus = 'ACTIVE' | 'FINISHED' | 'ABORTED'
export type AdminRoomPlayerState = 'NOT_READY' | 'READY' | 'PLAYING' | 'SPECTATING' | 'DISCONNECTED' | 'LEAVING'
export type AdminGamePhase = 'PRE_FLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN' | 'FINISHED'
export type AdminHandEndReason = 'SHOWDOWN' | 'ALL_OTHERS_FOLDED' | 'ABORTED'
export type AdminActionType = 'USER_SUSPENDED' | 'USER_REACTIVATED' | 'PLAYER_REMOVED_FROM_ROOM' | 'ROOM_CLOSED' | 'GAME_TERMINATED'
export type AdminTargetType = 'USER' | 'ROOM' | 'GAME_SESSION'

export interface AdminPage<T> { items: T[]; page: number; size: number; total: number }

export interface AdminOverview {
  totalUsers: number
  activeUsers: number
  totalRooms: number
  openRooms: number
  activeGameSessions: number
  completedGameSessions: number
  handsPlayed: number
  totalChipsInAccounts: number
  generatedAt: string
}

export interface AdminUserItem {
  userId: number; username: string; email: string | null; role: AdminRole
  accountStatus: AdminAccountStatus; accountChips: number; displayName: string
  createdAt: string; rating: number; gamesRated: number; totalGames: number; totalHands: number
}

export interface AdminRankingHistory {
  gameSessionId: number; oldRating: number; newRating: number; ratingDelta: number
  sessionNet: number; placement: number; participantCount: number; createdAt: string
}

export interface AdminUserDetail extends AdminUserItem {
  avatarUrl: string | null; onlineStatus: 'ONLINE' | 'IN_GAME' | 'OFFLINE'; peakRating: number
  totalWins: number; totalLosses: number; winRate: number; totalChipsWon: number
  totalChipsLost: number; netChip: number; largestPotWon: number; averagePlayingSeconds: number
  recentRankingHistory: AdminRankingHistory[]
}

export interface AdminRoomItem {
  roomId: number; name: string; roomType: AdminRoomType; status: AdminRoomStatus
  ownerUserId: number; seatedPlayers: number; spectators: number; capacity: number
  createdAt: string; currentGameSessionId: number | null
}

export interface AdminRoomParticipant {
  userId: number; username: string; seatNumber: number | null; playerState: AdminRoomPlayerState
  tableChips: number; joinedAt: string; leftAt: string | null
}

export interface AdminRoomDetail {
  roomId: number; name: string; roomType: AdminRoomType; status: AdminRoomStatus
  ownerUserId: number; ownerUsername: string; capacity: number; smallBlind: number
  bigBlind: number; buyIn: number; createdAt: string; activeGameSessionId: number | null
  participants: AdminRoomParticipant[]
}

export interface AdminGameItem {
  gameSessionId: number; roomId: number; status: AdminGameStatus; startedAt: string
  finishedAt: string | null; participantCount: number; handCount: number
}

export interface AdminGameParticipant {
  userId: number; username: string; netChips: number; handsPlayed: number
}

export interface AdminGameDetail {
  gameSessionId: number; roomId: number; roomName: string; status: AdminGameStatus
  startedAt: string; finishedAt: string | null; handCount: number; participants: AdminGameParticipant[]
}

export interface AdminHandItem {
  handId: number; handNumber: number; startedAt: string; endedAt: string | null
  participantCount: number; totalPotAwarded: number; finalPhase: AdminGamePhase | null
  endReason: AdminHandEndReason | null; boardCards: string | null
}

export interface AdminMutationResponse {
  status: string; targetId: number; changed: boolean; deferred: boolean
}

export interface AdminAuditItem {
  id: number; adminUserId: number; actionType: AdminActionType; targetType: AdminTargetType
  targetId: number | null; reason: string | null; metadata: Record<string, unknown>; createdAt: string
}

export interface AdminUserFilters { page: number; size: number; search?: string; status?: AdminAccountStatus; role?: AdminRole }
export interface AdminRoomFilters { page: number; size: number; search?: string; status?: AdminRoomStatus; roomType?: AdminRoomType }
export interface AdminGameFilters { page: number; size: number; status?: AdminGameStatus; roomId?: number; userId?: number; from?: string; to?: string }
export interface AdminAuditFilters { page: number; size: number; adminUserId?: number; actionType?: AdminActionType; targetType?: AdminTargetType; targetId?: number; from?: string; to?: string }
