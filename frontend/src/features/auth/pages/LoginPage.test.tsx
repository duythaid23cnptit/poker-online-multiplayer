import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import { deferred, renderWithAppContext } from '../../../test/testUtils'
import { authApi } from '../api/authApi'
import { establishAuthenticatedSession } from '../session/sessionCoordinator'
import type { AuthResponse } from '../types/auth'
import { LoginPage } from './LoginPage'

vi.mock('../api/authApi', () => ({
  authApi: { login: vi.fn() },
}))

vi.mock('../session/sessionCoordinator', () => ({
  establishAuthenticatedSession: vi.fn(),
}))

const authResponse: AuthResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  expiresInSeconds: 900,
}

function renderLogin(initialEntry: string | { pathname: string; state?: unknown } = '/login') {
  return renderWithAppContext(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/app" element={<h1>Authenticated home</h1>} />
      <Route path="/app/profile" element={<h1>Profile destination</h1>} />
    </Routes>,
    { initialEntries: [initialEntry] },
  )
}

async function enterCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Username'), '  river_reader  ')
  await user.type(screen.getByLabelText('Password'), 'correct horse battery staple')
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.login).mockReset()
    vi.mocked(establishAuthenticatedSession).mockReset()
  })

  afterEach(() => vi.restoreAllMocks())

  it('validates required credentials before calling the backend', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your username')).toBeVisible()
    expect(screen.getByText('Enter your password')).toBeVisible()
    expect(authApi.login).not.toHaveBeenCalled()
  })

  it('establishes the session and honors a safe protected return path', async () => {
    vi.mocked(authApi.login).mockResolvedValue(authResponse)
    vi.mocked(establishAuthenticatedSession).mockResolvedValue()
    const user = userEvent.setup()
    const { queryClient } = renderLogin({ pathname: '/login', state: { from: '/app/profile' } })

    await enterCredentials(user)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Profile destination' })).toBeVisible()
    expect(authApi.login).toHaveBeenCalledWith({
      username: 'river_reader',
      password: 'correct horse battery staple',
    })
    expect(establishAuthenticatedSession).toHaveBeenCalledWith(authResponse, queryClient)
  })

  it('ignores an external return target after successful login', async () => {
    vi.mocked(authApi.login).mockResolvedValue(authResponse)
    vi.mocked(establishAuthenticatedSession).mockResolvedValue()
    const user = userEvent.setup()
    renderLogin({ pathname: '/login', state: { from: 'https://evil.example/steal' } })

    await enterCredentials(user)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Authenticated home' })).toBeVisible()
  })

  it('shows a safe backend authentication error without leaking internals', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new ApiClientError(
      401,
      'AUTHENTICATION_FAILED',
      'The username or password is incorrect.',
    ))
    const user = userEvent.setup()
    renderLogin()

    await enterCredentials(user)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The username or password is incorrect.')
    expect(alert).not.toHaveTextContent(/sql|exception|constraint/i)
  })

  it('disables submission while login is pending and sends only one request', async () => {
    const pending = deferred<AuthResponse>()
    vi.mocked(authApi.login).mockReturnValue(pending.promise)
    vi.mocked(establishAuthenticatedSession).mockResolvedValue()
    const user = userEvent.setup()
    renderLogin()

    await enterCredentials(user)
    const submit = screen.getByRole('button', { name: 'Sign in' })
    await user.click(submit)
    await waitFor(() => expect(screen.getByRole('button', { name: /Signing in/ })).toBeDisabled())
    await user.click(screen.getByRole('button', { name: /Signing in/ }))

    expect(authApi.login).toHaveBeenCalledTimes(1)
    pending.resolve(authResponse)
    expect(await screen.findByRole('heading', { name: 'Authenticated home' })).toBeVisible()
  })

  it('provides an accessible password visibility control', async () => {
    const user = userEvent.setup()
    renderLogin()
    const password = screen.getByLabelText('Password')

    expect(password).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Hide password' })).toHaveAttribute('aria-pressed', 'true')
  })
})
