import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import { currentUserFixture, renderWithAppContext } from '../../../test/testUtils'
import { gameApi } from '../../game/api/gameApi'
import { profileKeys } from '../../profile/api/profileQueries'
import { roomApi } from '../api/roomApi'
import { roomKeys } from '../api/roomQueries'
import { stompSession } from '../../../shared/realtime/stompSession'
import type { RoomDetail, RoomPlayerState, RoomSummary } from '../types/room'
import { RoomsPage } from './RoomsPage'

const room: RoomSummary = {
  id: 17,
  name: 'River Room',
  ownerUserId: 7,
  roomType: 'PUBLIC',
  passwordRequired: false,
  status: 'WAITING',
  seatedPlayers: 2,
  maxPlayers: 6,
  smallBlind: 10,
  bigBlind: 20,
  buyIn: 500,
}

function detailForMembership(
  seatNumber: number | null,
  state: RoomPlayerState = seatNumber === null ? 'SPECTATING' : 'READY',
): RoomDetail {
  return {
    room,
    members: [{
      userId: currentUserFixture.id,
      username: currentUserFixture.username,
      seatNumber,
      state,
      tableChips: seatNumber === null ? 0 : 500,
    }],
  }
}

function renderRoomsPage(initialEntry = '/app/rooms') {
  const result = renderWithAppContext(<RoomsPage />, { initialEntries: [initialEntry] })
  result.queryClient.setQueryData(profileKeys.current(), currentUserFixture)
  return result
}

afterEach(() => vi.restoreAllMocks())

describe('RoomsPage membership restoration', () => {
  it('hydrates the latest room identities before GAME_STARTED navigation', async () => {
    let roomFrame: ((body: string) => void) | undefined
    vi.spyOn(stompSession, 'listen').mockImplementation((destination, handler) => {
      if (destination === `/topic/room/${room.id}`) roomFrame = handler
      return () => {}
    })
    const waiting = detailForMembership(2, 'READY')
    const live = { ...waiting, members: [
      ...waiting.members,
      { userId: 4, username: 'vuthai', seatNumber: 3, state: 'READY' as const, tableChips: 500 },
    ] }
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(waiting)
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))
    const { queryClient } = renderRoomsPage()
    await screen.findByText('Active membership')

    act(() => roomFrame?.(JSON.stringify({ type: 'PLAYER_JOINED', payload: live })))
    act(() => roomFrame?.(JSON.stringify({ protocolVersion: 1,
      eventId: '123e4567-e89b-12d3-a456-426614174000', type: 'GAME_STARTED', occurredAt: '2026-09-01T00:00:00Z',
      scope: { roomId: room.id }, payload: { gameId: '123e4567-e89b-42d3-a456-426614174000',
        gameSessionId: 22, handId: 31, handNumber: 1 } })))

    expect(queryClient.getQueryData<RoomDetail>(roomKeys.detail(room.id))?.members)
      .toEqual(expect.arrayContaining([expect.objectContaining({ userId: 4, username: 'vuthai' })]))
  })

  it('shows the host badge and lets a spectator host start two ready seated players', async () => {
    const user = userEvent.setup()
    const hostRoom = { ...room, ownerUserId: currentUserFixture.id }
    const detail: RoomDetail = { room: hostRoom, members: [
      { userId: currentUserFixture.id, username: currentUserFixture.username, seatNumber: null, state: 'SPECTATING', tableChips: 0 },
      { userId: 91, username: 'First', seatNumber: 1, state: 'READY', tableChips: 500 },
      { userId: 92, username: 'Second', seatNumber: 2, state: 'READY', tableChips: 500 },
    ] }
    vi.spyOn(roomApi, 'list').mockResolvedValue([hostRoom])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detail)
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))
    const start = vi.spyOn(gameApi, 'start').mockResolvedValue({ roomId: room.id,
      gameId: '11111111-1111-4111-8111-111111111111', gameSessionId: 3, handId: 4, handNumber: 1 })

    renderRoomsPage()

    expect(await screen.findByText('Host')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Start game' }))
    await waitFor(() => expect(start).toHaveBeenCalledWith(room.id))
  })

  it('keeps the host in the waiting room and shows an authoritative start rejection', async () => {
    const user = userEvent.setup()
    const hostRoom = { ...room, ownerUserId: currentUserFixture.id }
    const detail: RoomDetail = { room: hostRoom, members: [
      { userId: currentUserFixture.id, username: currentUserFixture.username, seatNumber: 1, state: 'READY', tableChips: 500 },
      { userId: 91, username: 'First', seatNumber: 2, state: 'READY', tableChips: 500 },
    ] }
    vi.spyOn(roomApi, 'list').mockResolvedValue([hostRoom])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detail)
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))
    vi.spyOn(gameApi, 'start').mockRejectedValue(new ApiClientError(
      409, 'INVALID_REQUEST', 'Every seated player must be ready.',
    ))

    renderRoomsPage()
    await user.click(await screen.findByRole('button', { name: 'Start game' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Every seated player must be ready.')
    expect(screen.getByText('Active membership')).toBeVisible()
  })

  it('hides Start game from a non-host', async () => {
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detailForMembership(2, 'READY'))

    renderRoomsPage()

    expect(await screen.findByText('Active membership')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Start game' })).not.toBeInTheDocument()
  })

  it('explains why Start game is disabled when fewer than two players are seated', async () => {
    const hostRoom = { ...room, ownerUserId: currentUserFixture.id, seatedPlayers: 0 }
    vi.spyOn(roomApi, 'list').mockResolvedValue([hostRoom])
    vi.spyOn(roomApi, 'detail').mockResolvedValue({ ...detailForMembership(null), room: hostRoom })

    renderRoomsPage()

    expect(await screen.findByRole('button', { name: 'Start game' })).toBeDisabled()
    expect(screen.getByText('At least 2 seated players are required.')).toBeVisible()
  })

  it('explains why Start game is disabled while a seated player is unready', async () => {
    const hostRoom = { ...room, ownerUserId: currentUserFixture.id }
    const detail: RoomDetail = { room: hostRoom, members: [
      { userId: currentUserFixture.id, username: currentUserFixture.username, seatNumber: null, state: 'SPECTATING', tableChips: 0 },
      { userId: 91, username: 'First', seatNumber: 1, state: 'READY', tableChips: 500 },
      { userId: 92, username: 'Second', seatNumber: 2, state: 'NOT_READY', tableChips: 500 },
    ] }
    vi.spyOn(roomApi, 'list').mockResolvedValue([hostRoom])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detail)

    renderRoomsPage()

    expect(await screen.findByRole('button', { name: 'Start game' })).toBeDisabled()
    expect(screen.getByText('Every seated player must be ready.')).toBeVisible()
  })

  it('restores an existing player membership from authoritative room detail on a fresh render', async () => {
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detailForMembership(2))

    renderRoomsPage()

    expect(await screen.findByText('Active membership')).toBeInTheDocument()
    expect(screen.getByText(/Seat 2.*READY/)).toBeInTheDocument()
    expect(screen.getByText('500 chips')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Join · 500 chips' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create room' })).not.toBeInTheDocument()
  })

  it('restores an existing spectator membership without player controls', async () => {
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detailForMembership(null))

    renderRoomsPage('/app/rooms?create=1')

    expect(await screen.findByText('Active membership')).toBeInTheDocument()
    expect(screen.getByText(/Spectator.*SPECTATING/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ready to play' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Join · 500 chips' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create room' })).not.toBeInTheDocument()
  })

  it('keeps the normal join panel for a non-member', async () => {
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockRejectedValue(new ApiClientError(403, 'ROOM_DETAIL_FORBIDDEN', 'Room membership is required.'))

    renderRoomsPage('/app/rooms?join=17')

    expect(await screen.findByText('Join table')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Join · 500 chips' })).toBeInTheDocument()
    expect(screen.queryByText('Active membership')).not.toBeInTheDocument()
  })

  it('does not issue a duplicate join request while restoring membership', async () => {
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    const detail = vi.spyOn(roomApi, 'detail').mockResolvedValue(detailForMembership(4))
    const join = vi.spyOn(roomApi, 'join')

    renderRoomsPage()

    await waitFor(() => expect(detail).toHaveBeenCalledWith(room.id, expect.any(AbortSignal)))
    expect(await screen.findByText('Active membership')).toBeInTheDocument()
    expect(join).not.toHaveBeenCalled()
  })

  it('converts an active spectator into a seated player through the existing join contract', async () => {
    const user = userEvent.setup()
    const spectatorDetail = detailForMembership(null)
    const seatedDetail = detailForMembership(3, 'NOT_READY')
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail')
      .mockResolvedValueOnce(spectatorDetail)
      .mockResolvedValueOnce(spectatorDetail)
      .mockResolvedValue(seatedDetail)
    const join = vi.spyOn(roomApi, 'join').mockResolvedValue(seatedDetail)
    const leave = vi.spyOn(roomApi, 'leave')
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))

    const { queryClient } = renderRoomsPage()

    await user.click(await screen.findByRole('button', { name: 'Take a seat' }))
    await user.click(await screen.findByRole('button', { name: /Seat 3.*Available.*Select/ }))
    expect(screen.getByText(/configured buy-in of/)).toHaveTextContent('500 chips')
    await user.click(screen.getByRole('button', { name: /Confirm/ }))

    await waitFor(() => expect(join).toHaveBeenCalledWith(room.id, {
      spectator: false,
      seatNumber: 3,
      buyInAmount: room.buyIn,
      password: null,
    }))
    expect(leave).not.toHaveBeenCalled()
    expect(await screen.findByText(/Seat 3.*NOT_READY/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ready to play' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Leave waiting room' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Take a seat' })).not.toBeInTheDocument()

    await waitFor(() => {
      const memberships = queryClient.getQueryData<RoomDetail[]>(roomKeys.membershipLookup([room.id]))
      expect(memberships).toEqual([seatedDetail])
    })
  })

  it.each([
    ['creator host', currentUserFixture.id],
    ['authorized spectator', 7],
  ])('does not request a private-room password when the %s takes a seat', async (_scenario, ownerUserId) => {
    const user = userEvent.setup()
    const privateRoom: RoomSummary = { ...room, roomType: 'PRIVATE', passwordRequired: true, ownerUserId }
    const spectatorDetail: RoomDetail = { room: privateRoom, members: [{
      userId: currentUserFixture.id,
      username: currentUserFixture.username,
      seatNumber: null,
      state: 'SPECTATING',
      tableChips: 0,
    }] }
    const seatedDetail: RoomDetail = { room: privateRoom, members: [{
      ...spectatorDetail.members[0], seatNumber: 2, state: 'NOT_READY', tableChips: privateRoom.buyIn,
    }] }
    vi.spyOn(roomApi, 'list').mockResolvedValue([privateRoom])
    vi.spyOn(roomApi, 'detail')
      .mockResolvedValueOnce(spectatorDetail)
      .mockResolvedValueOnce(spectatorDetail)
      .mockResolvedValue(seatedDetail)
    const join = vi.spyOn(roomApi, 'join').mockResolvedValue(seatedDetail)
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))

    renderRoomsPage()
    await user.click(await screen.findByRole('button', { name: 'Take a seat' }))

    expect(screen.queryByLabelText('Room password')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Seat 2.*Available.*Select/ }))
    await user.click(screen.getByRole('button', { name: /Confirm/ }))

    await waitFor(() => expect(join).toHaveBeenCalledWith(privateRoom.id, {
      spectator: false,
      seatNumber: 2,
      buyInAmount: privateRoom.buyIn,
      password: null,
    }))
    expect(join).toHaveBeenCalledOnce()
  })

  it('keeps the spectator membership and reports an insufficient-chip rejection', async () => {
    const user = userEvent.setup()
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(detailForMembership(null))
    const join = vi.spyOn(roomApi, 'join').mockRejectedValue(new ApiClientError(
      409,
      'INSUFFICIENT_CHIPS',
      "You don't have enough chips to join this table.",
    ))
    const leave = vi.spyOn(roomApi, 'leave')
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))

    renderRoomsPage()

    await user.click(await screen.findByRole('button', { name: 'Take a seat' }))
    await user.click(screen.getByRole('button', { name: /Confirm/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent("You don't have enough chips to join this table.")
    expect(join).toHaveBeenCalledOnce()
    expect(leave).not.toHaveBeenCalled()
    expect(screen.getByText(/Spectator.*SPECTATING/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Keep watching' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Leave waiting room' })).toBeInTheDocument()
  })

  it('refreshes authoritative occupancy and keeps the spectator chooser open after a stale-seat conflict', async () => {
    const user = userEvent.setup()
    const spectatorDetail: RoomDetail = { ...detailForMembership(null), members: [
      ...detailForMembership(null).members,
      { userId: 91, username: 'thai_20260831170753', seatNumber: 1, state: 'READY', tableChips: 500 },
    ] }
    const refreshedDetail: RoomDetail = { ...spectatorDetail, members: [
      ...spectatorDetail.members,
      { userId: 92, username: 'vuthai', seatNumber: 2, state: 'NOT_READY', tableChips: 500 },
    ] }
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    const detail = vi.spyOn(roomApi, 'detail')
      .mockResolvedValueOnce(spectatorDetail)
      .mockResolvedValueOnce(spectatorDetail)
      .mockResolvedValue(refreshedDetail)
    const join = vi.spyOn(roomApi, 'join').mockRejectedValue(new ApiClientError(
      409, 'SEAT_OCCUPIED', 'Seat is no longer available. Choose another seat.',
    ))
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))

    renderRoomsPage()
    await user.click(await screen.findByRole('button', { name: 'Take a seat' }))
    expect(screen.getByRole('button', { name: /Seat 1.*thai_20260831170753.*Occupied/ })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /Seat 2.*Available.*Selected/ }))
    await user.click(screen.getByRole('button', { name: /Confirm/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Seat is no longer available. Choose another seat.')
    await waitFor(() => expect(detail).toHaveBeenCalledTimes(3))
    expect(screen.getByRole('button', { name: /Seat 2.*vuthai.*Occupied/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Confirm/ })).toBeDisabled()
    expect(join).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Keep watching' })).toBeVisible()
  })

  it('uses authoritative occupancy in normal Join options and submits one available seat', async () => {
    const user = userEvent.setup()
    const occupancy: RoomDetail = { room, members: [
      { userId: 91, username: 'thai_20260831170753', seatNumber: 1, state: 'READY', tableChips: 500 },
      { userId: 92, username: 'vuthai', seatNumber: 3, state: 'NOT_READY', tableChips: 500 },
    ] }
    const joined = detailForMembership(2, 'NOT_READY')
    vi.spyOn(roomApi, 'list').mockResolvedValue([room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(occupancy)
    const join = vi.spyOn(roomApi, 'join').mockResolvedValue(joined)

    renderRoomsPage('/app/rooms?join=17')

    expect(await screen.findByRole('button', { name: /Seat 1.*thai_20260831170753.*Occupied/ })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /Seat 2.*Available.*Select/ }))
    await user.click(screen.getByRole('button', { name: /Join.*500 chips/ }))

    await waitFor(() => expect(join).toHaveBeenCalledWith(room.id, {
      spectator: false,
      seatNumber: 2,
      buyInAmount: room.buyIn,
      password: null,
    }))
    expect(join).toHaveBeenCalledOnce()
  })

  it('keeps the password field for a private-room non-member initial join', async () => {
    const user = userEvent.setup()
    const privateRoom: RoomSummary = { ...room, roomType: 'PRIVATE', passwordRequired: true }
    const occupancy: RoomDetail = { room: privateRoom, members: [
      { userId: 91, username: 'existing_player', seatNumber: 1, state: 'READY', tableChips: 500 },
    ] }
    const joined = { ...detailForMembership(2, 'NOT_READY'), room: privateRoom }
    vi.spyOn(roomApi, 'list').mockResolvedValue([privateRoom])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(occupancy)
    const join = vi.spyOn(roomApi, 'join').mockResolvedValue(joined)

    renderRoomsPage('/app/rooms?join=17')
    await user.click(await screen.findByRole('button', { name: /Seat 2.*Available.*Select/ }))
    await user.type(screen.getByLabelText('Room password'), 'room-password')
    await user.click(screen.getByRole('button', { name: /Join.*500 chips/ }))

    await waitFor(() => expect(join).toHaveBeenCalledWith(privateRoom.id, expect.objectContaining({
      seatNumber: 2,
      password: 'room-password',
    })))
  })

  it('does not offer a seat when every seat is occupied', async () => {
    const fullRoomDetail = detailForMembership(null)
    fullRoomDetail.room = { ...room, seatedPlayers: room.maxPlayers }
    fullRoomDetail.members = [
      ...fullRoomDetail.members,
      ...Array.from({ length: room.maxPlayers }, (_, index) => ({
        userId: currentUserFixture.id + index + 1,
        username: `Player ${index + 1}`,
        seatNumber: index + 1,
        state: 'NOT_READY' as const,
        tableChips: room.buyIn,
      })),
    ]
    vi.spyOn(roomApi, 'list').mockResolvedValue([fullRoomDetail.room])
    vi.spyOn(roomApi, 'detail').mockResolvedValue(fullRoomDetail)
    const join = vi.spyOn(roomApi, 'join')
    vi.spyOn(gameApi, 'activeByRoom').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'No active game.'))

    renderRoomsPage()

    expect(await screen.findByText('Active membership')).toBeInTheDocument()
    expect(screen.getByText(/Spectator.*SPECTATING/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Take a seat' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Leave waiting room' })).toBeInTheDocument()
    expect(join).not.toHaveBeenCalled()
  })
})
