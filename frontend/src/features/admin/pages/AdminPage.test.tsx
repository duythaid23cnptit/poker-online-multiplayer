import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError } from '../../../shared/api/apiError'
import { AdminPage } from './AdminPage'

const mocks = vi.hoisted(() => ({
  overview: vi.fn(), users: vi.fn(), user: vi.fn(), rooms: vi.fn(), room: vi.fn(),
  games: vi.fn(), game: vi.fn(), hands: vi.fn(), audit: vi.fn(), mutation: vi.fn(),
}))
vi.mock('../hooks/useAdmin', () => ({
  useAdminOverview: mocks.overview,
  useAdminUsers: mocks.users,
  useAdminUser: mocks.user,
  useAdminRooms: mocks.rooms,
  useAdminRoom: mocks.room,
  useAdminGames: mocks.games,
  useAdminGame: mocks.game,
  useAdminHands: mocks.hands,
  useAdminAudit: mocks.audit,
  useAdminMutation: mocks.mutation,
  adminMutations: { suspend: vi.fn(), reactivate: vi.fn(), removePlayer: vi.fn(), closeRoom: vi.fn(), terminateGame: vi.fn() },
}))

const query = (data: unknown) => ({ data, isPending: false, isError: false, error: null, refetch: vi.fn() })
const page = (items: unknown[]) => ({ items, page: 0, size: 20, total: items.length })
const overview = { totalUsers: 12, activeUsers: 10, totalRooms: 5, openRooms: 2, activeGameSessions: 1, completedGameSessions: 9, handsPlayed: 80, totalChipsInAccounts: 120_000, generatedAt: '2026-09-07T12:00:00Z' }
const userItem = { userId: 7, username: 'river', email: 'r@example.test', role: 'PLAYER', accountStatus: 'ACTIVE', accountChips: 5_000, displayName: 'River', createdAt: '2026-09-01T00:00:00Z', rating: 1_050, gamesRated: 2, totalGames: 3, totalHands: 20 }
const userDetail = { ...userItem, avatarUrl: null, onlineStatus: 'ONLINE', peakRating: 1_100, totalWins: 2, totalLosses: 1, winRate: 66.67, totalChipsWon: 900, totalChipsLost: 400, netChip: 500, largestPotWon: 300, averagePlayingSeconds: 600, recentRankingHistory: [] }
const roomItem = { roomId: 9, name: 'Final Table', roomType: 'PRIVATE', status: 'WAITING', ownerUserId: 2, seatedPlayers: 1, spectators: 1, capacity: 6, createdAt: '2026-09-01T00:00:00Z', currentGameSessionId: null }
const roomDetail = { ...roomItem, ownerUsername: 'host', smallBlind: 5, bigBlind: 10, buyIn: 500, activeGameSessionId: null, participants: [{ userId: 7, username: 'river', seatNumber: 1, playerState: 'READY', tableChips: 500, joinedAt: '2026-09-01T00:00:00Z', leftAt: null }] }
const gameItem = { gameSessionId: 11, roomId: 9, status: 'ACTIVE', startedAt: '2026-09-07T10:00:00Z', finishedAt: null, participantCount: 2, handCount: 1 }
const gameDetail = { ...gameItem, roomName: 'Final Table', participants: [{ userId: 7, username: 'river', netChips: 100, handsPlayed: 1 }] }
const hand = { handId: 21, handNumber: 1, startedAt: '2026-09-07T10:00:00Z', endedAt: '2026-09-07T10:01:00Z', participantCount: 2, totalPotAwarded: 100, finalPhase: 'FINISHED', endReason: 'SHOWDOWN', boardCards: 'AS,KD' }

describe('AdminPage', () => {
  const mutation = { mutate: vi.fn(), reset: vi.fn(), isPending: false, isSuccess: false, error: null }

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.overview.mockReturnValue(query(overview))
    mocks.users.mockReturnValue(query(page([userItem])))
    mocks.user.mockImplementation((id) => query(id ? userDetail : undefined))
    mocks.rooms.mockReturnValue(query(page([roomItem])))
    mocks.room.mockImplementation((id) => query(id ? roomDetail : undefined))
    mocks.games.mockReturnValue(query(page([gameItem])))
    mocks.game.mockImplementation((id) => query(id ? gameDetail : undefined))
    mocks.hands.mockImplementation((id) => query(id ? page([hand]) : undefined))
    mocks.audit.mockReturnValue(query(page([{ id: 1, adminUserId: 2, actionType: 'ROOM_CLOSED', targetType: 'ROOM', targetId: 9, reason: 'complete', metadata: {}, createdAt: '2026-09-07T10:00:00Z' }])))
    mocks.mutation.mockReturnValue(mutation)
  })

  it('renders the overview, user list, detail, and guarded moderation confirmation', async () => {
    const user = userEvent.setup()
    render(<AdminPage section="users" />)
    expect(screen.getByRole('heading', { name: 'Administration' })).toBeVisible()
    expect(screen.queryByRole('region', { name: 'Platform overview' })).not.toBeInTheDocument()
    expect(screen.getByText('Select a user')).toBeVisible()
    expect(screen.queryByText('Loading user detail...')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Inspect' }))
    expect(screen.getByRole('row', { selected: true })).toHaveTextContent('River')
    expect(screen.getByRole('heading', { name: 'River' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Suspend account' }))
    expect(screen.getByRole('region', { name: 'Suspend River' })).toBeVisible()
    await user.type(screen.getByLabelText(/Reason/), 'policy violation')
    await user.click(screen.getByRole('button', { name: 'Confirm action' }))
    expect(mutation.mutate).toHaveBeenCalledWith(expect.objectContaining({ id: 7, kind: 'suspend', reason: 'policy violation' }), expect.any(Object))
  })

  it('keeps the overview dashboard on the overview route only', () => {
    render(<AdminPage />)
    expect(screen.getByRole('region', { name: 'Platform overview' })).toHaveTextContent('120,000 account chips')
    expect(screen.getByRole('region', { name: 'Operational snapshot' })).toHaveTextContent('Platform activity at a glance')
  })

  it('shows user detail loading only after a user is selected', async () => {
    const user = userEvent.setup()
    mocks.user.mockReturnValue({ ...query(undefined), isPending: true })
    render(<AdminPage section="users" />)
    expect(screen.getByText('Select a user')).toBeVisible()
    expect(screen.queryByText('Loading user detail...')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Inspect' }))
    expect(screen.queryByText('Select a user')).not.toBeInTheDocument()
    expect(screen.getByText('Loading user detail...')).toBeVisible()
  })

  it('keeps room and game detail states empty until selected', () => {
    const { rerender } = render(<AdminPage section="rooms" />)
    expect(screen.getByText('Select a room')).toBeVisible()
    expect(screen.queryByText('Loading room detail...')).not.toBeInTheDocument()
    rerender(<AdminPage section="games" />)
    expect(screen.getByText('Select a game session')).toBeVisible()
    expect(screen.queryByText('Loading game detail...')).not.toBeInTheDocument()
  })

  it('exposes room detail, member removal, and close controls', async () => {
    const user = userEvent.setup()
    render(<AdminPage section="rooms" />)
    await user.click(screen.getByRole('button', { name: 'Inspect' }))
    expect(screen.getByRole('heading', { name: 'Final Table' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Close room' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Remove' })).toBeEnabled()
  })

  it('exposes game detail, hands, filters, and termination', async () => {
    const user = userEvent.setup()
    render(<AdminPage section="games" />)
    expect(screen.getByLabelText('Room ID')).toBeVisible()
    expect(screen.getByLabelText('Player ID')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Inspect' }))
    expect(screen.getByRole('region', { name: 'Session hands' })).toHaveTextContent('SHOWDOWN')
    expect(screen.getByRole('button', { name: 'Terminate game' })).toBeEnabled()
  })

  it('renders the audited moderation history and its supported filters', async () => {
    const user = userEvent.setup()
    render(<AdminPage section="audit" />)
    expect(screen.getByRole('region', { name: 'Admin audit log' })).toHaveTextContent('ROOM CLOSED')
    expect(screen.getByLabelText('Admin user ID')).toBeVisible()
    expect(screen.getByLabelText('Target ID')).toBeVisible()
    expect(screen.getByLabelText('From')).toBeVisible()
    expect(screen.getByLabelText('To')).toBeVisible()
  })

  it('renders loading, error, and empty states without raw backend text', () => {
    mocks.overview.mockReturnValue({ ...query(undefined), isPending: true })
    const { rerender } = render(<AdminPage />)
    expect(screen.getByText('Loading platform overview...')).toBeVisible()

    mocks.overview.mockReturnValue({ ...query(undefined), isError: true, error: new ApiClientError(500, 'INTERNAL_ERROR', 'The server is temporarily unavailable. Please try again.') })
    rerender(<AdminPage />)
    expect(screen.getByText('The server is temporarily unavailable. Please try again.')).toBeVisible()

    mocks.overview.mockReturnValue(query(overview))
    mocks.users.mockReturnValue(query(page([])))
    rerender(<AdminPage section="users" />)
    expect(screen.getByText('No users found')).toBeVisible()
  })

  it('updates filter state using accessible controls', () => {
    render(<AdminPage section="users" />)
    fireEvent.change(screen.getByLabelText('Search users'), { target: { value: 'river' } })
    expect(mocks.users).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'river', page: 0 }))
  })
})
