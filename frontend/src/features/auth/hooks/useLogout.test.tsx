import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestQueryClient } from '../../../test/testUtils'
import { stompSession } from '../../../shared/realtime/stompSession'
import { authApi } from '../api/authApi'
import { useSessionStore } from '../session/sessionStore'
import { tokenVault } from '../session/tokenVault'
import { useLogout } from './useLogout'

vi.mock('../api/authApi', () => ({
  authApi: { logout: vi.fn() },
}))

vi.mock('../../../shared/realtime/stompSession', () => ({
  stompSession: { disconnect: vi.fn() },
}))

function LogoutHarness() {
  const logout = useLogout()
  return <button type="button" onClick={() => logout.mutate()}>Log out now</button>
}

function renderLogout() {
  const queryClient = createTestQueryClient()
  queryClient.setQueryData(['profile', 'me'], { displayName: 'Sensitive profile' })
  queryClient.setQueryData(['private', 'history'], [{ handId: 10 }])
  const result = renderWithRouter(queryClient)
  return { ...result, queryClient }
}

function renderWithRouter(queryClient: ReturnType<typeof createTestQueryClient>) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/app']}>
        <Routes>
          <Route path="/app" element={<LogoutHarness />} />
          <Route path="/login" element={<h1>Signed out</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('useLogout', () => {
  beforeEach(() => {
    vi.mocked(authApi.logout).mockReset()
    vi.mocked(stompSession.disconnect).mockReset().mockResolvedValue()
    sessionStorage.clear()
    tokenVault.storeRefreshToken('refresh-token')
    useSessionStore.setState({ status: 'AUTHENTICATED', accessToken: 'access-token', epoch: 7 })
  })

  it('revokes the refresh token then clears tokens, session, realtime, and query data', async () => {
    vi.mocked(authApi.logout).mockResolvedValue()
    const user = userEvent.setup()
    const { queryClient } = renderLogout()

    await user.click(screen.getByRole('button', { name: 'Log out now' }))

    expect(await screen.findByRole('heading', { name: 'Signed out' })).toBeVisible()
    expect(authApi.logout).toHaveBeenCalledWith('refresh-token')
    expect(stompSession.disconnect).toHaveBeenCalledOnce()
    expect(tokenVault.readRefreshToken()).toBeNull()
    expect(useSessionStore.getState().status).toBe('UNAUTHENTICATED')
    expect(useSessionStore.getState().accessToken).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('performs the same local teardown when backend logout fails', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network unavailable'))
    const user = userEvent.setup()
    const { queryClient } = renderLogout()

    await user.click(screen.getByRole('button', { name: 'Log out now' }))

    expect(await screen.findByRole('heading', { name: 'Signed out' })).toBeVisible()
    await waitFor(() => expect(useSessionStore.getState().status).toBe('UNAUTHENTICATED'))
    expect(stompSession.disconnect).toHaveBeenCalledOnce()
    expect(tokenVault.readRefreshToken()).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })
})
