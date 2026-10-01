import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import { StatisticsPage } from './StatisticsPage'

const mocks = vi.hoisted(() => ({ current: vi.fn(), summary: vi.fn(), daily: vi.fn(), weekly: vi.fn() }))
vi.mock('../hooks/useStatistics', () => ({
  useCurrentStatistics: mocks.current,
  useAnalyticsSummary: mocks.summary,
  useDailyAnalytics: mocks.daily,
  useWeeklyAnalytics: mocks.weekly,
}))

const bucket = { handsPlayed: 12, handsWon: 5, handsLost: 6, handsTied: 1, chipsWon: 800, chipsLost: 300, netChips: 500, largestPotWon: 420, playingTimeSeconds: 3_600, sessionsParticipated: 2 }
const current = { userId: 42, totalGames: 4, totalHands: 40, totalWins: 3, totalLosses: 1, winRate: 75, totalChipsWon: 2_000, totalChipsLost: 900, netChip: 1_100, largestPotWon: 700, averagePlayingSeconds: 900 }
const summary = { today: { ...bucket, date: '2026-09-07' }, currentWeek: { ...bucket, weekStartDate: '2026-09-07', weekEndDate: '2026-09-13' } }
const query = (data: unknown) => ({ data, isPending: false, isError: false, error: null, refetch: vi.fn() })

describe('StatisticsPage', () => {
  beforeEach(() => {
    mocks.current.mockReturnValue(query(current))
    mocks.summary.mockReturnValue(query(summary))
    mocks.daily.mockReturnValue(query([{ ...bucket, date: '2026-09-06' }]))
    mocks.weekly.mockReturnValue(query([{ ...bucket, weekStartDate: '2026-09-01', weekEndDate: '2026-09-07' }]))
  })

  it('renders lifetime, summary, daily, and weekly authoritative data', () => {
    render(<StatisticsPage />)
    expect(screen.getByRole('heading', { name: 'Performance' })).toBeVisible()
    expect(screen.getByRole('region', { name: 'Daily performance' })).toHaveTextContent('2026-09-06')
    expect(screen.getByRole('region', { name: 'Weekly performance' })).toHaveTextContent('2026-09-01')
    expect(screen.getByText('75.00%')).toBeVisible()
  })

  it('shows a loading state while any contract is pending', () => {
    mocks.daily.mockReturnValue({ ...query(undefined), isPending: true })
    render(<StatisticsPage />)
    expect(screen.getByText('Loading your performance...')).toBeVisible()
  })

  it('shows a safe error and retry state', () => {
    mocks.summary.mockReturnValue({ ...query(undefined), isError: true, error: new ApiClientError(500, 'INTERNAL_ERROR', 'The server is temporarily unavailable. Please try again.') })
    render(<StatisticsPage />)
    expect(screen.getByText('The server is temporarily unavailable. Please try again.')).toBeVisible()
    expect(screen.getByRole('button', { name: /try again/i })).toBeEnabled()
  })

  it('shows independent empty states for ranges without data', () => {
    mocks.daily.mockReturnValue(query([]))
    mocks.weekly.mockReturnValue(query([]))
    render(<StatisticsPage />)
    expect(screen.getByText('No daily results')).toBeVisible()
    expect(screen.getByText('No weekly results')).toBeVisible()
  })
})
