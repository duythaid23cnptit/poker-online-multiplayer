import { screen } from '@testing-library/react'
import { Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { routes, safeAppReturnPath } from '../../../app/router/routePaths'
import { renderWithAppContext } from '../../../test/testUtils'
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

function renderPublic(initialEntry = '/login') {
  return renderWithAppContext(
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<h1>Login form</h1>} />
        <Route path="/register" element={<h1>Register form</h1>} />
      </Route>
      <Route path="/app" element={<h1>Authenticated home</h1>} />
    </Routes>,
    { initialEntries: [initialEntry] },
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

  it('lets an unauthenticated user reach the public login route', () => {
    setStatus('UNAUTHENTICATED')
    renderPublic()
    expect(screen.getByRole('heading', { name: 'Login form' })).toBeVisible()
  })

  it('accepts only internal app return paths', () => {
    expect(safeAppReturnPath('/app/profile')).toBe('/app/profile')
    expect(safeAppReturnPath('/app/profile?tab=identity')).toBe('/app/profile?tab=identity')
    expect(safeAppReturnPath('https://evil.example/steal')).toBe(routes.app)
    expect(safeAppReturnPath('//evil.example/steal')).toBe(routes.app)
    expect(safeAppReturnPath('/application')).toBe(routes.app)
    expect(safeAppReturnPath(null)).toBe(routes.app)
  })
})
