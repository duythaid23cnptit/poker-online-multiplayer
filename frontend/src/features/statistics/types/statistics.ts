export interface PlayerStatistics {
  userId: number
  totalGames: number
  totalHands: number
  totalWins: number
  totalLosses: number
  winRate: number
  totalChipsWon: number
  totalChipsLost: number
  netChip: number
  largestPotWon: number
  averagePlayingSeconds: number
}

export interface AnalyticsBucket {
  handsPlayed: number
  handsWon: number
  handsLost: number
  handsTied: number
  chipsWon: number
  chipsLost: number
  netChips: number
  largestPotWon: number
  playingTimeSeconds: number
  sessionsParticipated: number
}

export interface DailyAnalytics extends AnalyticsBucket {
  date: string
}

export interface WeeklyAnalytics extends AnalyticsBucket {
  weekStartDate: string
  weekEndDate: string
}

export interface AnalyticsSummary {
  today: DailyAnalytics
  currentWeek: WeeklyAnalytics
}
