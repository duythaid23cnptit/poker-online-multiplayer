import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { CurrentRanking } from '../../rankings/types/ranking'
import { LeaderboardPreview } from './LeaderboardPreview'

const rankings: CurrentRanking[] = [
  { rank: 1, userId: 4, username: 'vuthai', displayName: 'Thai', rating: 1_500, gamesRated: 12, peakRating: 1_520 },
  { rank: 2, userId: 3, username: 'river', displayName: '  ', rating: 1_400, gamesRated: 10, peakRating: 1_450 },
  { rank: 3, userId: 2, username: null, displayName: null, rating: 1_300, gamesRated: 8, peakRating: 1_350 },
]

describe('LeaderboardPreview', () => {
  it('renders authoritative identities with username and legacy fallbacks in ranking order', () => {
    render(<MemoryRouter><LeaderboardPreview rankings={rankings} /></MemoryRouter>)

    const rows = screen.getAllByRole('listitem').slice(0, 3)
    expect(within(rows[0]).getByText('Thai')).toBeVisible()
    expect(within(rows[0]).getByText('@vuthai')).toBeVisible()
    expect(within(rows[0]).getByText('1,500')).toBeVisible()
    expect(within(rows[1]).getByText('river')).toBeVisible()
    expect(within(rows[1]).queryByText('@river')).not.toBeInTheDocument()
    expect(within(rows[2]).getByText('Player #2')).toBeVisible()
    expect(rows.map((row) => row.textContent)).toEqual([
      '1Thai@vuthai1,500',
      '2river1,400',
      '3Player #21,300',
    ])
  })
})
