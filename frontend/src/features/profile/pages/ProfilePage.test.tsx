import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import { currentUserFixture, renderWithAppContext } from '../../../test/testUtils'
import { profileApi } from '../api/profileApi'
import { profileKeys } from '../api/profileQueries'
import { ProfilePage } from './ProfilePage'

vi.mock('../api/profileApi', () => ({
  profileApi: { current: vi.fn(), update: vi.fn() },
}))

function renderProfile() {
  return renderWithAppContext(<ProfilePage />)
}

describe('ProfilePage', () => {
  beforeEach(() => {
    vi.mocked(profileApi.current).mockReset().mockResolvedValue(currentUserFixture)
    vi.mocked(profileApi.update).mockReset()
  })

  it('renders only the safe current-user identity subset', async () => {
    renderProfile()

    expect(await screen.findByRole('heading', { name: 'Your profile' })).toBeVisible()
    expect(screen.getByText('River Reader')).toBeVisible()
    expect(screen.getByText('@river_reader')).toBeVisible()
    expect(screen.getByText('river@example.com')).toBeVisible()
    expect(screen.getByRole('img', { name: "River Reader's avatar" })).toHaveAttribute('referrerpolicy', 'no-referrer')
    expect(screen.queryByText('PLAYER')).not.toBeInTheDocument()
    expect(screen.queryByText('ACTIVE')).not.toBeInTheDocument()
    expect(screen.queryByText('ONLINE')).not.toBeInTheDocument()
    expect(screen.queryByText('12500')).not.toBeInTheDocument()
    expect(screen.queryByText('42')).not.toBeInTheDocument()
  })

  it('sends only editable fields and replaces the cached profile after success', async () => {
    const updated = {
      ...currentUserFixture,
      displayName: 'Turn Artist',
      avatarUrl: 'https://images.example.com/turn.png',
    }
    vi.mocked(profileApi.update).mockResolvedValue(updated)
    const user = userEvent.setup()
    const { queryClient } = renderProfile()
    const displayName = await screen.findByLabelText('Display name')
    const avatarUrl = screen.getByLabelText('Avatar URL')

    await user.clear(displayName)
    await user.type(displayName, '  Turn Artist  ')
    await user.clear(avatarUrl)
    await user.type(avatarUrl, '  https://images.example.com/turn.png  ')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Profile updated.')
    expect(profileApi.update).toHaveBeenCalledWith({
      displayName: 'Turn Artist',
      avatarUrl: 'https://images.example.com/turn.png',
    })
    expect(queryClient.getQueryData(profileKeys.current())).toEqual(updated)
  })

  it('rejects invalid edits before sending a PATCH request', async () => {
    const user = userEvent.setup()
    renderProfile()
    const displayName = await screen.findByLabelText('Display name')
    const avatarUrl = screen.getByLabelText('Avatar URL')

    await user.clear(displayName)
    await user.type(displayName, 'x')
    await user.clear(avatarUrl)
    await user.type(avatarUrl, 'javascript:alert(1)')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Display name must be at least 2 characters')).toBeVisible()
    expect(screen.getByText('Enter a complete http:// or https:// URL')).toBeVisible()
    expect(profileApi.update).not.toHaveBeenCalled()
  })

  it('preserves the edit and displays a safe message when the backend rejects it', async () => {
    vi.mocked(profileApi.update).mockRejectedValue(new ApiClientError(
      500,
      'INTERNAL_ERROR',
      'The server is temporarily unavailable. Please try again.',
    ))
    const user = userEvent.setup()
    renderProfile()
    const displayName = await screen.findByLabelText('Display name')

    await user.clear(displayName)
    await user.type(displayName, 'Still Here')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The server is temporarily unavailable.')
    expect(displayName).toHaveValue('Still Here')
    expect(screen.queryByText(/sql|constraint|exception/i)).not.toBeInTheDocument()
  })
})
