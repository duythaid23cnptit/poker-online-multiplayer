import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { RoomPlayer } from '../types/room'
import { SeatPicker } from './SeatPicker'

const members: RoomPlayer[] = [
  { userId: 11, username: 'thai_20260831170753', seatNumber: 1, state: 'READY', tableChips: 500 },
  { userId: 42, username: 'vuthai', seatNumber: 3, state: 'NOT_READY', tableChips: 500 },
]

describe('SeatPicker', () => {
  it('renders occupied usernames as disabled and available seats as selectable', async () => {
    const user = userEvent.setup()
    const select = vi.fn()
    render(<SeatPicker maxPlayers={6} members={members} selectedSeat={null} currentUserId={42} onSelect={select} />)

    expect(screen.getByRole('button', { name: /Seat 1.*thai_20260831170753.*Occupied/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Seat 3.*vuthai.*You.*Occupied/ })).toBeDisabled()
    const available = screen.getByRole('button', { name: /Seat 2.*Available.*Select/ })
    expect(available).toBeEnabled()
    await user.click(available)
    expect(select).toHaveBeenCalledWith(2)
  })

  it('renders updated occupancy directly from the latest membership snapshot', () => {
    const { rerender } = render(<SeatPicker maxPlayers={3} members={members.slice(0, 1)} selectedSeat={2} onSelect={() => {}} />)
    expect(screen.getByRole('button', { name: /Seat 2.*Available.*Selected/ })).toBeEnabled()

    rerender(<SeatPicker maxPlayers={3} members={[...members, {
      userId: 77, username: 'new_arrival', seatNumber: 2, state: 'NOT_READY', tableChips: 500,
    }]} selectedSeat={2} onSelect={() => {}} />)

    expect(screen.getByRole('button', { name: /Seat 2.*new_arrival.*Occupied/ })).toBeDisabled()
  })
})
