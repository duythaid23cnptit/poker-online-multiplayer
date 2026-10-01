import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUserFixture } from '../../../test/testUtils'
import { RequirePlayer } from './RequirePlayer'

const useCurrentProfile = vi.fn()
vi.mock('../../profile/hooks/useCurrentProfile', () => ({ useCurrentProfile: () => useCurrentProfile() }))

function renderGuard(role: 'PLAYER' | 'ADMIN') {
  useCurrentProfile.mockReturnValue({ data: { ...currentUserFixture, role }, isPending: false, isError: false })
  render(<MemoryRouter initialEntries={['/app/rooms']}><Routes>
    <Route path="/admin" element={<p>Admin area</p>} />
    <Route element={<RequirePlayer />}><Route path="/app/rooms" element={<p>Player area</p>} /></Route>
  </Routes></MemoryRouter>)
}

describe('RequirePlayer', () => {
  beforeEach(() => useCurrentProfile.mockReset())

  it('allows a player into player routes', () => {
    renderGuard('PLAYER')
    expect(screen.getByText('Player area')).toBeVisible()
  })

  it('redirects an administrator to administration without a loop', () => {
    renderGuard('ADMIN')
    expect(screen.getByText('Admin area')).toBeVisible()
    expect(screen.queryByText('Player area')).not.toBeInTheDocument()
  })
})
