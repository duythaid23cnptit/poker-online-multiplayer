import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import { currentUserFixture, renderWithAppContext } from '../../../test/testUtils'
import { authApi } from '../api/authApi'
import { useSessionStore } from '../session/sessionStore'
import { RegisterPage } from './RegisterPage'

vi.mock('../api/authApi', () => ({
  authApi: { register: vi.fn() },
}))

function LoginDestination() {
  const location = useLocation()
  const registered = (location.state as { registered?: boolean } | null)?.registered
  return <h1>{registered ? 'Registration complete' : 'Login destination'}</h1>
}

function renderRegister() {
  return renderWithAppContext(
    <Routes>
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginDestination />} />
    </Routes>,
    { initialEntries: ['/register'] },
  )
}

async function enterRegistration(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Username'), '  new_player  ')
  await user.type(screen.getByLabelText('Display name'), '  New Player  ')
  await user.type(screen.getByLabelText('Password'), 'secure-password')
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.register).mockReset()
    useSessionStore.setState({ status: 'UNAUTHENTICATED', accessToken: null, epoch: 0 })
  })

  it('shows contract-aligned client validation and does not submit invalid values', async () => {
    const user = userEvent.setup()
    renderRegister()

    await user.type(screen.getByLabelText('Username'), 'bad name')
    await user.type(screen.getByLabelText('Display name'), 'x')
    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.type(screen.getByLabelText('Password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Use only letters, numbers, and underscores')).toBeVisible()
    expect(screen.getByText('Display name must be at least 2 characters')).toBeVisible()
    expect(screen.getByText('Enter a valid email address')).toBeVisible()
    expect(screen.getByText('Password must be at least 8 characters')).toBeVisible()
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('sends the exact DTO, remains unauthenticated, and redirects to login after 201', async () => {
    vi.mocked(authApi.register).mockResolvedValue(currentUserFixture)
    const user = userEvent.setup()
    renderRegister()

    await enterRegistration(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('heading', { name: 'Registration complete' })).toBeVisible()
    expect(authApi.register).toHaveBeenCalledWith({
      username: 'new_player',
      displayName: 'New Player',
      email: null,
      password: 'secure-password',
    })
    expect(useSessionStore.getState().status).toBe('UNAUTHENTICATED')
    expect(useSessionStore.getState().accessToken).toBeNull()
  })

  it('renders a safe duplicate-account error and preserves entered values', async () => {
    vi.mocked(authApi.register).mockRejectedValue(new ApiClientError(
      409,
      'DUPLICATE_ACCOUNT',
      'An account already uses that username or email address.',
      [{ field: 'username', code: 'Duplicate', message: 'Choose another username' }],
    ))
    const user = userEvent.setup()
    renderRegister()

    await enterRegistration(user)
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Choose another username')).toBeVisible()
    expect(screen.getByText('An account already uses that username or email address.')).toBeVisible()
    expect(screen.getByLabelText('Username')).toHaveValue('  new_player  ')
    expect(screen.queryByText(/sql|constraint|exception/i)).not.toBeInTheDocument()
  })
})
