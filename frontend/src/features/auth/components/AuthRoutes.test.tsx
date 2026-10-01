import { screen } from '@testing-library/react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { routes, safeRoleReturnPath } from '../../../app/router/routePaths'
import { profileKeys } from '../../profile/api/profileQueries'
import { createTestQueryClient, currentUserFixture, renderWithAppContext } from '../../../test/testUtils'
import { useSessionStore, type SessionStatus } from '../session/sessionStore'
import { PublicOnlyRoute } from './PublicOnlyRoute'
import { RequireAuth } from './RequireAuth'

function setStatus(status: SessionStatus) {
  useSessionStore.setState({
    status,
    accessToken: status === 'AUTHENTICATED' ? 'access-token' : null,
    epoch: 0,
  })
}

function LoginTarget() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return <><h1>Login page</h1>{from && <output aria-label="Return path">{from}</output>}</>
}

function renderProtected(initialEntry = '/app/profile?tab=identity') {
  return renderWithAppContext(
    <Routes>
      <Route element={<RequireAuth />}>
        <Route path="/app/profile" element={<h1>Protected profile</h1>} />
      </Route>
      <Route path="/login" element={<LoginTarget />} />
    </Routes>,
    { initialEntries: [initialEntry] },
  )
}

function renderPublic(initialEntry = '/login', role: 'PLAYER' | 'ADMIN' = 'PLAYER') {
  const queryClient = createTestQueryClient()
  queryClient.setQueryData(profileKeys.current(), { ...currentUserFixture, role })
  return renderWithAppContext(
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<h1>Login form</h1>} />
        <Route path="/register" element={<h1>Register form</h1>} />
      </Route>
      <Route path="/app" element={<h1>Authenticated home</h1>} />
      <Route path="/admin" element={<h1>Administration home</h1>} />
    </Routes>,
    { initialEntries: [initialEntry], queryClient },
  )
}

describe('authentication route guards', () => {
  afterEach(() => setStatus('CHECKING_SESSION'))

  it('redirects unauthenticated protected navigation and preserves its internal return path', async () => {
    setStatus('UNAUTHENTICATED')
    renderProtected()

    expect(await screen.findByRole('heading', { name: 'Login page' })).toBeVisible()
    expect(screen.getByLabelText('Return path')).toHaveTextContent('/app/profile?tab=identity')
    expect(screen.queryByRole('heading', { name: 'Protected profile' })).not.toBeInTheDocument()
  })

  it('allows authenticated users through the protected guard', () => {
    setStatus('AUTHENTICATED')
    renderProtected()

    expect(screen.getByRole('heading', { name: 'Protected profile' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Login page' })).not.toBeInTheDocument()
  })

  it('shows only session checking UI while identity is unresolved', () => {
    setStatus('CHECKING_SESSION')
    renderProtected()

    expect(screen.getByText(/Checking your session/)).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Protected profile' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Login page' })).not.toBeInTheDocument()
  })

  it.each(['/login', '/register'])('redirects an authenticated user away from %s', async (path) => {
    setStatus('AUTHENTICATED')
    renderPublic(path)

    expect(await screen.findByRole('heading', { name: 'Authenticated home' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: /form/i })).not.toBeInTheDocument()
  })

  it('restores an administrator session into administration', async () => {
    setStatus('AUTHENTICATED')
    renderPublic('/login', 'ADMIN')

    expect(await screen.findByRole('heading', { name: 'Administration home' })).toBeVisible()
  })

  it('lets an unauthenticated user reach the public login route', () => {
    setStatus('UNAUTHENTICATED')
    renderPublic()
    expect(screen.getByRole('heading', { name: 'Login form' })).toBeVisible()
  })

  it('accepts return paths only within the authenticated role application', () => {
    expect(safeRoleReturnPath('/app/profile', 'PLAYER')).toBe('/app/profile')
    expect(safeRoleReturnPath('/app/profile?tab=identity', 'PLAYER')).toBe('/app/profile?tab=identity')
    expect(safeRoleReturnPath('/admin/users', 'ADMIN')).toBe('/admin/users')
    expect(safeRoleReturnPath('/app/rooms', 'ADMIN')).toBe(routes.admin)
    expect(safeRoleReturnPath('/admin', 'PLAYER')).toBe(routes.app)
    expect(safeRoleReturnPath('https://evil.example/steal', 'PLAYER')).toBe(routes.app)
    expect(safeRoleReturnPath('//evil.example/steal', 'ADMIN')).toBe(routes.admin)
    expect(safeRoleReturnPath(null, 'PLAYER')).toBe(routes.app)
  })
})
