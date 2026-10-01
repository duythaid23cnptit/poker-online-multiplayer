import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../shared/api/httpClient'
import { adminApi } from './adminApi'

describe('adminApi contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('maps every read route and supported query parameter', async () => {
    const request = vi.spyOn(apiClient, 'request').mockResolvedValue(undefined)
    const signal = new AbortController().signal
    await adminApi.overview(signal)
    await adminApi.users({ page: 1, size: 20, search: 'river & ace', status: 'ACTIVE', role: 'PLAYER' }, signal)
    await adminApi.user(7, signal)
    await adminApi.rooms({ page: 2, size: 10, search: 'final', status: 'WAITING', roomType: 'PRIVATE' }, signal)
    await adminApi.room(9, signal)
    await adminApi.games({ page: 0, size: 20, status: 'FINISHED', roomId: 9, userId: 7, from: '2026-01-01T00:00:00.000Z', to: '2026-09-01T00:00:00.000Z' }, signal)
    await adminApi.game(11, signal)
    await adminApi.hands(11, 3, 20, signal)
    await adminApi.audit({ page: 0, size: 20, adminUserId: 2, actionType: 'ROOM_CLOSED', targetType: 'ROOM', targetId: 9, from: '2026-01-01T00:00:00.000Z', to: '2026-09-01T00:00:00.000Z' }, signal)

    expect(request.mock.calls.map(([path]) => path)).toEqual([
      '/admin/overview',
      '/admin/users?page=1&size=20&search=river+%26+ace&status=ACTIVE&role=PLAYER',
      '/admin/users/7',
      '/admin/rooms?page=2&size=10&search=final&status=WAITING&roomType=PRIVATE',
      '/admin/rooms/9',
      '/admin/games?page=0&size=20&status=FINISHED&roomId=9&userId=7&from=2026-01-01T00%3A00%3A00.000Z&to=2026-09-01T00%3A00%3A00.000Z',
      '/admin/games/11',
      '/admin/games/11/hands?page=3&size=20',
      '/admin/audit-log?page=0&size=20&adminUserId=2&actionType=ROOM_CLOSED&targetType=ROOM&targetId=9&from=2026-01-01T00%3A00%3A00.000Z&to=2026-09-01T00%3A00%3A00.000Z',
    ])
    for (const [, options] of request.mock.calls) expect(options).toMatchObject({ authenticated: true, signal })
  })

  it('maps all five moderation routes and trims optional reasons', async () => {
    const request = vi.spyOn(apiClient, 'request').mockResolvedValue(undefined)
    await adminApi.suspendUser(7, '  abuse  ')
    await adminApi.reactivateUser(7)
    await adminApi.removePlayer(9, 7, 'seat policy')
    await adminApi.closeRoom(9, 'closed')
    await adminApi.terminateGame(11, 'incident')

    expect(request.mock.calls).toEqual([
      ['/admin/users/7/suspend', { method: 'POST', body: { reason: 'abuse' }, authenticated: true }],
      ['/admin/users/7/reactivate', { method: 'POST', body: undefined, authenticated: true }],
      ['/admin/rooms/9/players/7/remove', { method: 'POST', body: { reason: 'seat policy' }, authenticated: true }],
      ['/admin/rooms/9/close', { method: 'POST', body: { reason: 'closed' }, authenticated: true }],
      ['/admin/games/11/terminate', { method: 'POST', body: { reason: 'incident' }, authenticated: true }],
    ])
  })
})
