export interface CurrentRanking {
  rank: number | null
  userId: number
  rating: number
  gamesRated: number
  peakRating: number
}

export interface RankingPage {
  items: CurrentRanking[]
  page: number
  size: number
  total: number
}

export interface RankingHistory {
  gameSessionId: number
  oldRating: number
  newRating: number
  ratingDelta: number
  sessionNet: number
  placement: number
  participantCount: number
  createdAt: string
}
