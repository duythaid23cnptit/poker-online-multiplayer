import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCurrentRanking, useLeaderboard, useRankingHistory } from '../hooks/useRankings'
import { RankingsPage } from './RankingsPage'

vi.mock('../hooks/useRankings', () => ({
  useCurrentRanking: vi.fn(),
  useLeaderboard: vi.fn(),
  useRankingHistory: vi.fn(),
}))

const ready = (data: unknown) => ({ data, isPending: false, isError: false, error: null, refetch: vi.fn() })

describe('RankingsPage', () => {
  beforeEach(() => {
    vi.mocked(useCurrentRanking).mockReturnValue(ready({ rank: 2, userId: 4, username: 'vuthai', displayName: 'Thai', rating: 1_400, gamesRated: 10, peakRating: 1_450 }) as never)
    vi.mocked(useLeaderboard).mockReturnValue(ready({
      items: [
        { rank: 1, userId: 8, username: 'ace', displayName: 'Ace High', rating: 1_500, gamesRated: 12, peakRating: 1_520 },
        { rank: 2, userId: 4, username: 'vuthai', displayName: 'Thai', rating: 1_400, gamesRated: 10, peakRating: 1_450 },
      ], page: 0, size: 20, total: 2,
    }) as never)
    vi.mocked(useRankingHistory).mockReturnValue(ready([]) as never)
  })

  it('renders the same authoritative player identity in the full leaderboard', () => {
    render(<RankingsPage />)

    const table = within(screen.getByRole('region', { name: 'Leaderboard' }))
    expect(table.getByText('Ace High')).toBeVisible()
    expect(table.getByText('@ace')).toBeVisible()
    expect(table.getByText('Thai')).toBeVisible()
    expect(table.getByText('@vuthai')).toBeVisible()
    expect(table.queryByText('Player #8')).not.toBeInTheDocument()
    expect(table.getAllByRole('row').slice(1).map((row) => row.textContent)).toEqual([
      '#1Ace High@ace1,500121,520',
      '#2Thai@vuthaiYou1,400101,450',
    ])
  })
})
