import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../shared/api/httpClient'
import { rankingApi } from './rankingApi'

describe('rankingApi contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uses all three authenticated ranking routes with exact pagination', async () => {
    const request = vi.spyOn(apiClient, 'request').mockResolvedValue(undefined)
    const signal = new AbortController().signal
    await rankingApi.current(signal)
    await rankingApi.leaderboard(2, 25, signal)
    await rankingApi.history(1, 12, signal)
    expect(request.mock.calls).toEqual([
      ['/rankings/me', { authenticated: true, signal }],
      ['/rankings/leaderboard?page=2&size=25', { authenticated: true, signal }],
      ['/rankings/me/history?page=1&size=12', { authenticated: true, signal }],
    ])
  })

  it('loads a complete leaderboard with one request rather than per-player identity calls', async () => {
    const page = {
      items: [
        { rank: 1, userId: 4, username: 'vuthai', displayName: 'Thai', rating: 1_500, gamesRated: 12, peakRating: 1_520 },
        { rank: 2, userId: 3, username: 'river', displayName: null, rating: 1_400, gamesRated: 10, peakRating: 1_450 },
      ], page: 0, size: 5, total: 2,
    }
    const request = vi.spyOn(apiClient, 'request').mockResolvedValue(page)

    await expect(rankingApi.leaderboard(0, 5)).resolves.toEqual(page)
    expect(request).toHaveBeenCalledOnce()
    expect(request).toHaveBeenCalledWith('/rankings/leaderboard?page=0&size=5', {
      authenticated: true, signal: undefined,
    })
  })
})
