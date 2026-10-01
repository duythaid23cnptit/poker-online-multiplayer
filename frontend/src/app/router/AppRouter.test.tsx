import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../layouts/AppShell', async () => {
  const { Outlet } = await import('react-router-dom')
  return { AppShell: () => <main aria-label="Application shell"><Outlet /></main> }
})

vi.mock('../layouts/AdminShell', async () => {
  const { Outlet } = await import('react-router-dom')
  return { AdminShell: () => <main aria-label="Administration shell"><Outlet /></main> }
})

vi.mock('../../features/friends/pages/FriendsPage', () => ({
  FriendsPage: () => <h1>Friends route loaded</h1>,
}))

vi.mock('../../features/statistics/pages/StatisticsPage', () => ({
  StatisticsPage: () => <h1>Statistics route loaded</h1>,
}))

vi.mock('../../features/admin/pages/AdminPage', () => ({
  AdminPage: () => <h1>Admin route loaded</h1>,
}))

vi.mock('../../features/auth/components/RequireAdmin', async () => {
  const { Outlet } = await import('react-router-dom')
  return { RequireAdmin: () => <Outlet /> }
})

vi.mock('../../features/auth/components/RequirePlayer', async () => {
  const { Outlet } = await import('react-router-dom')
  return { RequirePlayer: () => <Outlet /> }
})

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

  it.each([
    ['/app/statistics', 'Statistics route loaded'],
    ['/admin', 'Admin route loaded'],
  ])('loads the aligned %s feature route', async (path, heading) => {
    window.history.replaceState({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
    const { useSessionStore } = await import('../../features/auth/session/sessionStore')
    useSessionStore.setState({ status: 'AUTHENTICATED', accessToken: 'test-access-token' })
    const { AppRouter } = await import('./AppRouter')
    render(<AppRouter />)
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
  })
})
