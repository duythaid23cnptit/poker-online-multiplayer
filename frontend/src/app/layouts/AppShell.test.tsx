import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUserFixture } from '../../test/testUtils'
import { AppShell } from './AppShell'

const mocks = vi.hoisted(() => ({ profile: vi.fn(), logout: vi.fn() }))
vi.mock('../../features/profile/hooks/useCurrentProfile', () => ({ useCurrentProfile: mocks.profile }))
vi.mock('../../features/auth/hooks/useLogout', () => ({ useLogout: mocks.logout }))
vi.mock('../../features/game/components/ActiveGameRecovery', () => ({ ActiveGameRecovery: () => null }))

function renderShell() {
  render(<MemoryRouter initialEntries={['/app']}><Routes><Route path="/app" element={<AppShell />}><Route index element={<p>Content</p>} /></Route></Routes></MemoryRouter>)
}

describe('AppShell role navigation', () => {
  beforeEach(() => {
    mocks.logout.mockReturnValue({ isPending: false, mutate: vi.fn() })
  })

  it('does not show admin navigation to a player', () => {
    mocks.profile.mockReturnValue({ data: currentUserFixture, isPending: false, isError: false })
    renderShell()
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Performance' })).toBeVisible()
  })

  it('shows the complete player navigation without administration', () => {
    mocks.profile.mockReturnValue({ data: currentUserFixture, isPending: false, isError: false })
    renderShell()
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toHaveTextContent('LobbyRoomsFriendsRankingsPerformanceProfile')
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })
})
