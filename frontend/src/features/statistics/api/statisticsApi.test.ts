import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../shared/api/httpClient'
import { statisticsApi } from './statisticsApi'

describe('statisticsApi contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('uses the authenticated player statistics and summary routes', async () => {
    const request = vi.spyOn(apiClient, 'request').mockResolvedValue(undefined)
    const signal = new AbortController().signal

    await statisticsApi.current(signal)
    await statisticsApi.summary(signal)

    expect(request).toHaveBeenNthCalledWith(1, '/players/me/statistics', { authenticated: true, signal })
    expect(request).toHaveBeenNthCalledWith(2, '/analytics/me/summary', { authenticated: true, signal })
  })

  it('encodes both authoritative analytics range parameters', async () => {
    const request = vi.spyOn(apiClient, 'request').mockResolvedValue(undefined)
    const signal = new AbortController().signal

    await statisticsApi.daily('2026-08-01', '2026-08-31', signal)
    await statisticsApi.weekly('2026-06-01', '2026-08-31', signal)

    expect(request).toHaveBeenNthCalledWith(1, '/analytics/me/daily?from=2026-08-01&to=2026-08-31', { authenticated: true, signal })
    expect(request).toHaveBeenNthCalledWith(2, '/analytics/me/weekly?from=2026-06-01&to=2026-08-31', { authenticated: true, signal })
  })
})
