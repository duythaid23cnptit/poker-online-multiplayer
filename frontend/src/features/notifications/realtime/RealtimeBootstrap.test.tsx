import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { friendKeys } from '../../friends/api/friendQueries'
import { gameKeys } from '../../game/api/gameQueries'
import { roomKeys } from '../../rooms/api/roomQueries'
import { useSessionStore } from '../../auth/session/sessionStore'
import { stompSession } from '../../../shared/realtime/stompSession'
import { RealtimeBootstrap } from './RealtimeBootstrap'
import { profileKeys } from '../../profile/api/profileQueries'
import { currentUserFixture } from '../../../test/testUtils'

describe('RealtimeBootstrap reconciliation', () => {
  let connection: (connected: boolean) => void

  beforeEach(() => {
    vi.restoreAllMocks()
    useSessionStore.setState({ status: 'AUTHENTICATED', accessToken: 'token' })
    vi.spyOn(stompSession, 'listen').mockReturnValue(vi.fn())
    vi.spyOn(stompSession, 'listenConnection').mockImplementation((listener) => { connection = listener; listener(false); return vi.fn() })
    vi.spyOn(stompSession, 'connect').mockImplementation(() => undefined)
    vi.spyOn(stompSession, 'disconnect').mockResolvedValue(undefined)
  })

  it('invalidates active-game, friendship, and room authority after reconnect', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(profileKeys.current(), currentUserFixture)
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    render(<QueryClientProvider client={client}><RealtimeBootstrap /></QueryClientProvider>)
    act(() => connection(true))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: gameKeys.activeMine() })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: friendKeys.all })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: roomKeys.all })
  })

  it('does not connect or subscribe for an administrator session', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(profileKeys.current(), { ...currentUserFixture, role: 'ADMIN' })

    render(<QueryClientProvider client={client}><RealtimeBootstrap /></QueryClientProvider>)

    expect(stompSession.connect).not.toHaveBeenCalled()
    expect(stompSession.listen).not.toHaveBeenCalled()
    expect(stompSession.listenConnection).not.toHaveBeenCalled()
    expect(stompSession.disconnect).toHaveBeenCalled()
  })
})
