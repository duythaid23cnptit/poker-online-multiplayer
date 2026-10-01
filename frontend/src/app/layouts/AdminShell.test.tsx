import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUserFixture } from '../../test/testUtils'
import { AdminShell } from './AdminShell'

const mocks = vi.hoisted(() => ({ profile: vi.fn(), logout: vi.fn() }))
vi.mock('../../features/profile/hooks/useCurrentProfile', () => ({ useCurrentProfile: mocks.profile }))
vi.mock('../../features/auth/hooks/useLogout', () => ({ useLogout: mocks.logout }))

describe('AdminShell', () => {
  beforeEach(() => {
    mocks.profile.mockReturnValue({ data: { ...currentUserFixture, role: 'ADMIN' }, isPending: false, isError: false })
    mocks.logout.mockReturnValue({ isPending: false, mutate: vi.fn() })
  })

  it('shows only administration navigation and account controls', () => {
    render(<MemoryRouter initialEntries={['/admin']}><Routes><Route path="/admin" element={<AdminShell />}><Route index element={<p>Admin content</p>} /></Route></Routes></MemoryRouter>)

    const navigation = screen.getByRole('navigation', { name: 'Administration navigation' })
    expect(navigation).toHaveTextContent('OverviewUsersRoomsGamesAudit log')
    expect(screen.getByText('Administrator')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Lobby' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Friends' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Rankings' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Performance' })).not.toBeInTheDocument()
  })
})
