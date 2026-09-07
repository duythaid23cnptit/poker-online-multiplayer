import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../layouts/AppShell', async () => {
  const { Outlet } = await import('react-router-dom')
  return { AppShell: () => <main aria-label="Application shell"><Outlet /></main> }
})

vi.mock('../../features/friends/pages/FriendsPage', () => ({
  FriendsPage: () => <h1>Friends route loaded</h1>,
}))

describe('AppRouter route loading', () => {
  afterEach(() => {
    window.history.replaceState({}, '', '/')
  })

  it('loads a protected feature route through the shell and lazy boundary', async () => {
    window.history.replaceState({}, '', '/app/friends')
    const { useSessionStore } = await import('../../features/auth/session/sessionStore')
    useSessionStore.setState({ status: 'AUTHENTICATED', accessToken: 'test-access-token' })
    const { AppRouter } = await import('./AppRouter')

    render(<AppRouter />)

    expect(await screen.findByRole('heading', { name: 'Friends route loaded' })).toBeVisible()
    expect(screen.getByRole('main', { name: 'Application shell' })).toBeVisible()
  })
})
