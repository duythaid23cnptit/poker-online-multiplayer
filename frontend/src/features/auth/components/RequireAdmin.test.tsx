import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUserFixture } from '../../../test/testUtils'
import { RequireAdmin } from './RequireAdmin'

const useCurrentProfile = vi.fn()
vi.mock('../../profile/hooks/useCurrentProfile', () => ({ useCurrentProfile: () => useCurrentProfile() }))

function renderGuard() {
  render(<MemoryRouter initialEntries={['/admin']}><Routes>
    <Route path="/app" element={<p>Lobby</p>} />
    <Route element={<RequireAdmin />}><Route path="/admin" element={<p>Admin area</p>} /></Route>
  </Routes></MemoryRouter>)
}

describe('RequireAdmin', () => {
  beforeEach(() => useCurrentProfile.mockReset())

  it('allows an administrator', () => {
    useCurrentProfile.mockReturnValue({ data: { ...currentUserFixture, role: 'ADMIN' }, isPending: false, isError: false })
    renderGuard()
    expect(screen.getByText('Admin area')).toBeVisible()
  })

  it('redirects a normal player away from the admin route', () => {
    useCurrentProfile.mockReturnValue({ data: currentUserFixture, isPending: false, isError: false })
    renderGuard()
    expect(screen.getByText('Lobby')).toBeVisible()
    expect(screen.queryByText('Admin area')).not.toBeInTheDocument()
  })

  it('shows loading and safe error states', () => {
    useCurrentProfile.mockReturnValue({ isPending: true, isError: false })
    const { unmount } = render(<MemoryRouter><RequireAdmin /></MemoryRouter>)
    expect(screen.getByText('Checking administrator access...')).toBeVisible()
    unmount()
    useCurrentProfile.mockReturnValue({ isPending: false, isError: true, error: new Error('private'), refetch: vi.fn() })
    render(<MemoryRouter><RequireAdmin /></MemoryRouter>)
    expect(screen.getByText('Something went wrong. Please try again.')).toBeVisible()
  })
})
