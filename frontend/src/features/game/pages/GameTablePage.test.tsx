import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '../../../app/router/routePaths'
import { ApiClientError } from '../../../shared/api/apiError'
import { createTestQueryClient, currentUserFixture, deferred, renderWithAppContext } from '../../../test/testUtils'
import type { RoomDetail } from '../../rooms/types/room'
import { hydrateGameSnapshot, initialGameState } from '../realtime/gameEventReducer'
import type { GameResultPayload, GameViewState } from '../types/game'
import { GameTablePage } from './GameTablePage'
import { gameApi } from '../api/gameApi'
import { roomKeys } from '../../rooms/api/roomQueries'
import { chatKeys } from '../api/chatQueries'
import { gameKeys } from '../api/gameQueries'
import { useGameDiscoveryStore } from '../hooks/gameDiscoveryStore'

const mocks = vi.hoisted(() => ({
  useCurrentProfile: vi.fn(),
  useGameRealtime: vi.fn(),
  useRoomDetail: vi.fn(),
}))

vi.mock('../../profile/hooks/useCurrentProfile', () => ({ useCurrentProfile: mocks.useCurrentProfile }))
vi.mock('../../rooms/hooks/useRooms', () => ({ useRoomDetail: mocks.useRoomDetail }))
vi.mock('../hooks/useGameRealtime', () => ({ useGameRealtime: mocks.useGameRealtime }))
vi.mock('../components/GameSidePanel', () => ({ GameSidePanel: () => <aside>Game side panel</aside> }))

const gameId = '11111111-1111-4111-8111-111111111111'
const participantRoom: RoomDetail = {
  room: { id: 7, name: 'Final Table', ownerUserId: 42, roomType: 'PUBLIC', passwordRequired: false, status: 'FINISHED', seatedPlayers: 2, maxPlayers: 6, smallBlind: 5, bigBlind: 10, buyIn: 500 },
  members: [
    { userId: 42, username: 'Hero', seatNumber: 1, state: 'PLAYING', tableChips: 1_000 },
    { userId: 99, username: 'Opponent', seatNumber: 2, state: 'PLAYING', tableChips: 0 },
  ],
}

function finishedState(result: GameResultPayload | null = null): GameViewState {
  return {
    ...initialGameState(7, gameId),
    version: 12,
    publicState: {
      handId: 3,
      handNumber: 3,
      phase: 'FINISHED',
      dealerSeat: 1,
      smallBlindSeat: 1,
      bigBlindSeat: 2,
      currentTurnUserId: null,
      currentBet: 0,
      minimumRaise: 10,
      communityCards: [
        { rank: 'ACE', suit: 'SPADES' },
        { rank: 'KING', suit: 'HEARTS' },
        { rank: 'QUEEN', suit: 'CLUBS' },
        { rank: 'TEN', suit: 'DIAMONDS' },
        { rank: 'TWO', suit: 'SPADES' },
      ],
      players: [
        { userId: 42, seat: 1, tableChips: 1_000, currentBet: 0, participation: 'ACTIVE', connected: true, leaving: false },
        { userId: 99, seat: 2, tableChips: 0, currentBet: 0, participation: 'ALL_IN', connected: true, leaving: false },
      ],
      handCompleted: true,
      sessionFinished: true,
    },
    result,
  }
}

const finalResult: GameResultPayload = {
  handId: 3,
  awards: [{ potIndex: 0, potType: 'MAIN', potAmount: 1_000, winnerUserIds: [42], winnerPayouts: { '42': 1_000 } }],
  uncalledReturns: [],
  finalPlayers: finishedState().publicState!.players,
  endReason: 'SHOWDOWN',
}

interface RenderPageOptions {
  room?: RoomDetail | null
  roomError?: unknown
  snapshotError?: unknown
  queryClient?: ReturnType<typeof createTestQueryClient>
}

function renderPage(state: GameViewState, options: RenderPageOptions = {}) {
  const selectedRoom = options.room === undefined ? participantRoom : options.room
  mocks.useRoomDetail.mockReturnValue({
    data: selectedRoom ?? undefined,
    isPending: !selectedRoom && !options.roomError,
    isFetchedAfterMount: true,
    isError: Boolean(options.roomError),
    error: options.roomError ?? null,
  })
  mocks.useCurrentProfile.mockReturnValue({ data: currentUserFixture, isPending: false, isError: false, error: null })
  mocks.useGameRealtime.mockReturnValue({ state, connected: true, snapshotPending: false, snapshotError: options.snapshotError ?? null })
  let forceRender = () => {}
  function PageHarness() {
    const [, setVersion] = useState(0)
    forceRender = () => setVersion((version) => version + 1)
    return <Routes><Route path="/app/rooms/:roomId/games/:gameId" element={<GameTablePage />} /><Route path={routes.rooms} element={<p>Rooms destination</p>} /></Routes>
  }
  const result = renderWithAppContext(
    <PageHarness />,
    { initialEntries: [routes.game(7, gameId)], queryClient: options.queryClient },
  )
  return { ...result, refreshPage: () => act(() => forceRender()) }
}

describe('GameTablePage terminal session state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(gameApi, 'activeMine').mockResolvedValue({roomId:7,gameId,gameSessionId:1,handId:3,handNumber:3})
  })

  it('replaces player action controls with authoritative final stacks and awards', () => {
    renderPage(finishedState(finalResult))

    const terminal = screen.getByRole('region', { name: 'Session finished' })
    expect(within(terminal).getByText('Game complete')).toBeVisible()
    expect(within(terminal).getByText('Hero')).toBeVisible()
    expect(within(terminal).getByText('1,000 chips')).toBeVisible()
    expect(within(terminal).getByText('Opponent')).toBeVisible()
    expect(within(terminal).getByText('0 chips')).toBeVisible()
    expect(within(terminal).getByText('Hero — 1,000 chips')).toBeVisible()
    expect(within(terminal).getByRole('link', { name: 'Back to Rooms' })).toHaveAttribute('href', routes.rooms)
    expect(screen.getByLabelText('ace of spades')).toBeVisible()
    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7, false)
    expect(screen.getByText('Game side panel')).toBeVisible()
    expect(screen.queryByText('Waiting for your turn')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^(Fold|Check|Call|Bet|Raise|All in)/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Leave game' })).not.toBeInTheDocument()
  })

  it('renders snapshot-only terminal state without inventing award or winner text', () => {
    const terminalPublicState = finishedState().publicState!
    const snapshotState = hydrateGameSnapshot(initialGameState(7, gameId), {
      roomId: 7,
      gameId,
      gameSessionId: 23,
      version: 12,
      publicState: terminalPublicState,
      holeCards: [],
      turn: null,
      timer: null,
      participant: true,
    })
    renderPage(snapshotState)

    const terminal = screen.getByRole('region', { name: 'Session finished' })
    expect(within(terminal).getByText('Final stacks')).toBeVisible()
    expect(within(terminal).queryByText('Final awards')).not.toBeInTheDocument()
    expect(within(terminal).queryByText(/winner/i)).not.toBeInTheDocument()
    expect(screen.queryByText('Waiting for your turn')).not.toBeInTheDocument()
  })

  it('shows the same terminal state to a spectator', () => {
    const spectatorRoom: RoomDetail = {
      ...participantRoom,
      members: [
        participantRoom.members[1],
        { userId: 42, username: 'Observer', seatNumber: null, state: 'SPECTATING', tableChips: 0 },
      ],
    }

    renderPage(finishedState(), { room: spectatorRoom })

    expect(screen.getByRole('region', { name: 'Session finished' })).toBeVisible()
    expect(screen.queryByText('Public table state only. Player actions and private cards are unavailable.')).not.toBeInTheDocument()
  })

  it('preserves the normal waiting state while the session remains active', () => {
    const state = finishedState()
    state.publicState = { ...state.publicState!, phase: 'FLOP', handCompleted: false, sessionFinished: false }
    renderPage(state)

    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7, true)
    expect(screen.getByText('Final Table')).toBeVisible()
    expect(screen.getByText('Hero')).toBeVisible()
    expect(screen.getByText('Game side panel')).toBeVisible()
    expect(screen.getByText('Waiting for your turn')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Leave game' })).toBeVisible()
    expect(screen.queryByText('Game complete')).not.toBeInTheDocument()
  })

  it('does not render cached player fallbacks before live room identities refresh', () => {
    const state = finishedState()
    state.publicState = { ...state.publicState!, phase: 'FLOP', handCompleted: false, sessionFinished: false }
    mocks.useRoomDetail.mockReturnValue({ data: undefined, isPending: false, isFetchedAfterMount: false,
      isError: false, error: null })
    mocks.useCurrentProfile.mockReturnValue({ data: currentUserFixture, isPending: false, isError: false, error: null })
    mocks.useGameRealtime.mockReturnValue({ state, connected: true, snapshotPending: false, snapshotError: null })

    renderWithAppContext(<Routes><Route path="/app/rooms/:roomId/games/:gameId" element={<GameTablePage />} /></Routes>,
      { initialEntries: [routes.game(7, gameId)] })

    expect(screen.getByText('Preparing the poker table...')).toBeVisible()
    expect(screen.queryByText('Player #99')).not.toBeInTheDocument()
  })

  it('dwells on the completed board, awards, and final stacks until the next hand starts', () => {
    const state = finishedState(finalResult)
    state.publicState = { ...state.publicState!, sessionFinished: false }
    const playingRoom = { ...participantRoom, room: { ...participantRoom.room, status: 'PLAYING' as const } }

    renderPage(state, { room: playingRoom })

    expect(screen.getByLabelText('ace of spades')).toBeVisible()
    expect(screen.getByText('Showdown complete')).toBeVisible()
    expect(screen.getByText(/Main pot/)).toHaveTextContent('1,000 chips')
    expect(screen.getAllByText('1,000 chips').length).toBeGreaterThan(0)
    expect(screen.getByText('Next hand starting soon…')).toBeVisible()
    expect(screen.queryByText('Game complete')).not.toBeInTheDocument()
  })

  it('shows authoritative deferred departure without offering another leave command', () => {
    const state = finishedState()
    state.publicState = {
      ...state.publicState!,
      phase: 'FLOP',
      handCompleted: false,
      sessionFinished: false,
      players: state.publicState!.players.map((player) => player.userId === 42 ? { ...player, leaving: true } : player),
    }
    renderPage(state)

    expect(screen.getAllByText('Leaving after this hand')).toHaveLength(2)
    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7, false)
    expect(screen.queryByRole('button', { name: 'Leave game' })).not.toBeInTheDocument()
  })

  it('renders a cold historical finish without requesting active room presentation', () => {
    const state = finishedState()
    state.holeCards = [
      { rank: 'QUEEN', suit: 'HEARTS' },
      { rank: 'QUEEN', suit: 'SPADES' },
    ]

    state.publicState!.players.forEach((player) => {player.connected = false})
    renderPage(state, { room: null })

    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7, false)
    expect(screen.getByRole('heading', { name: 'Finished game' })).toBeVisible()
    expect(screen.getByText('Game 11111111')).toBeVisible()
    expect(screen.getByRole('region', { name: 'Session finished' })).toBeVisible()
    expect(screen.getAllByText('Player #42').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Player #99').length).toBeGreaterThan(0)
    expect(screen.getAllByText('1,000 chips').length).toBeGreaterThan(0)
    expect(screen.getByLabelText('ace of spades')).toBeVisible()
    expect(screen.getByLabelText('queen of hearts')).toBeVisible()
    expect(screen.getByLabelText('queen of spades')).toBeVisible()
    expect(screen.queryByLabelText('ace of hearts')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByText(/Disconnected/i)).not.toBeInTheDocument()
    expect(screen.queryByText('Game side panel')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^(Fold|Check|Call|Bet|Raise|All in)/i })).not.toBeInTheDocument()
  })

  it('ignores a stale room authorization error after a terminal snapshot succeeds', () => {
    renderPage(finishedState(), {
      room: null,
      roomError: new ApiClientError(403, 'FORBIDDEN', 'You do not have permission to perform this action.'),
    })

    expect(screen.getByRole('region', { name: 'Session finished' })).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('clears stale active-game recovery state when the session becomes terminal', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(gameKeys.activeMine(), { roomId: 7, gameId, gameSessionId: 23, handId: 3, handNumber: 3 })
    useGameDiscoveryStore.getState().discover(7, gameId)

    renderPage(finishedState(), { queryClient })

    await waitFor(() => expect(queryClient.getQueryData(gameKeys.activeMine())).toBeNull())
    expect(useGameDiscoveryStore.getState()).toMatchObject({ roomId: null, gameId: null })
  })

  it('confirms final departure after a room 403 and clears recovery without a full-page error', async () => {
    vi.spyOn(gameApi, 'activeMine').mockRejectedValue(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'Not active'))
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(gameKeys.activeMine(), {roomId:7,gameId})
    queryClient.setQueryData(roomKeys.detail(7), participantRoom)
    queryClient.setQueryData(chatKeys.history(7), [])
    useGameDiscoveryStore.getState().discover(7, gameId)
    const state = finishedState()
    state.publicState = { ...state.publicState!, phase: 'FLOP', handCompleted: false, sessionFinished: false }

    renderPage(state, {
      room: null,
      roomError: new ApiClientError(403, 'FORBIDDEN', 'You do not have permission to perform this action.'),
      queryClient,
    })

    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7, true)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(await screen.findByText('Rooms destination')).toBeVisible()
    expect(queryClient.getQueryData(gameKeys.activeMine())).toBeNull()
    expect(queryClient.getQueryData(roomKeys.detail(7))).toBeUndefined()
    expect(queryClient.getQueryData(chatKeys.history(7))).toBeUndefined()
    expect(useGameDiscoveryStore.getState()).toMatchObject({roomId:null,gameId:null})
    expect(screen.queryByText(/Disconnected/i)).not.toBeInTheDocument()
    expect(screen.queryByText('Game side panel')).not.toBeInTheDocument()
  })

  it('keeps the leaving table stable and skips terminal rendering until one Rooms transition', async () => {
    const user = userEvent.setup()
    const settlement = deferred<Awaited<ReturnType<typeof gameApi.activeMine>>>()
    vi.spyOn(gameApi, 'activeMine').mockReturnValue(settlement.promise)
    vi.spyOn(gameApi, 'leave').mockResolvedValue({ roomId: 7, gameId, changed: true, deferred: true })
    const activeState = finishedState()
    activeState.publicState = { ...activeState.publicState!, phase: 'FLOP', handCompleted: false, sessionFinished: false }
    const page = renderPage(activeState)

    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    await user.click(screen.getByRole('button', { name: 'Confirm leave' }))
    expect(await screen.findAllByText('Leaving after this hand')).not.toHaveLength(0)

    mocks.useGameRealtime.mockReturnValue({ state: finishedState(finalResult), connected: true, snapshotPending: false, snapshotError: null })
    page.refreshPage()

    expect(screen.getByText('Final Table')).toBeVisible()
    expect(screen.queryByRole('region', { name: 'Session finished' })).not.toBeInTheDocument()
    expect(screen.queryByText('Finished game')).not.toBeInTheDocument()
    expect(screen.queryByText('Rooms destination')).not.toBeInTheDocument()
    expect(screen.getAllByText('Leaving after this hand').length).toBeGreaterThan(0)

    settlement.reject(new ApiClientError(404, 'GAME_NOT_ACTIVE', 'Not active'))
    expect(await screen.findByText('Rooms destination')).toBeVisible()
  })

  it('keeps a cold LEAVING snapshot visible while finalization is still pending', () => {
    const state = finishedState()
    state.publicState = {...state.publicState!,sessionFinished:false,handCompleted:false,
      players:state.publicState!.players.map((player)=>({...player,leaving:player.userId===42}))}
    renderPage(state, {room:null})
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('Leaving after this hand')).toBeVisible()
    expect(screen.getByText('Poker table')).toBeVisible()
    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7,false)
  })

  it('retains a genuine room access error when fresh activeMine still confirms membership', async () => {
    const state = finishedState()
    state.publicState = {...state.publicState!,sessionFinished:false,handCompleted:false}
    renderPage(state,{room:null,roomError:new ApiClientError(403,'FORBIDDEN','Access denied')})
    expect(await screen.findByRole('alert')).toHaveTextContent('Access denied')
    expect(screen.queryByText('Rooms destination')).not.toBeInTheDocument()
  })

  it('keeps a snapshot failure fatal before room presentation is enabled', () => {
    renderPage(initialGameState(7, gameId), {
      room: null,
      snapshotError: new ApiClientError(403, 'FORBIDDEN', 'Snapshot access denied.'),
    })

    expect(mocks.useRoomDetail).toHaveBeenCalledWith(7, false)
    expect(screen.getByRole('alert')).toHaveTextContent('Snapshot access denied.')
  })
})
