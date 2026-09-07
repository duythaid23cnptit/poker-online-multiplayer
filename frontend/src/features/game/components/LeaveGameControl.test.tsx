import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '../../../app/router/routePaths'
import { ApiClientError } from '../../../shared/api/apiError'
import { createTestQueryClient, deferred, renderWithAppContext } from '../../../test/testUtils'
import { roomKeys } from '../../rooms/api/roomQueries'
import { gameKeys } from '../api/gameQueries'
import { useGameDiscoveryStore } from '../hooks/gameDiscoveryStore'
import { useGameDeparture } from '../hooks/useGameDeparture'
import { LeaveGameControl } from './LeaveGameControl'

const mocks = vi.hoisted(() => ({ leave: vi.fn(), activeMine: vi.fn() }))
vi.mock('../api/gameApi', () => ({ gameApi: { leave: mocks.leave, activeMine: mocks.activeMine } }))

const gameId = '11111111-1111-4111-8111-111111111111'
const defaultProps = {
  roomId: 7,
  gameId,
  participant: true,
  terminal: false,
  authoritativeLeaving: false,
  handResolved: false,
}

function DepartureControl(props: typeof defaultProps) {
  const departure = useGameDeparture(props.roomId, props.gameId, props.authoritativeLeaving)
  return <LeaveGameControl {...props} authoritativeLeaving={departure.pending} onDeparture={departure.acknowledge} />
}

function renderControl(overrides: Partial<typeof defaultProps> = {}, queryClient = createTestQueryClient()) {
  return renderWithAppContext(
    <Routes>
      <Route path="/game" element={<DepartureControl {...defaultProps} {...overrides} />} />
      <Route path={routes.rooms} element={<p>Rooms destination</p>} />
    </Routes>,
    { initialEntries: ['/game'], queryClient },
  )
}

function RerenderHarness() {
  const [, setVersion] = useState(0)
  return <>
    <button type="button" onClick={() => setVersion((version) => version + 1)}>Unrelated game event</button>
    <DepartureControl {...defaultProps} />
  </>
}

describe('LeaveGameControl', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.activeMine.mockResolvedValue({roomId:7, gameId})
    useGameDiscoveryStore.getState().clear()
  })

  it('is available only to an active participant', () => {
    const spectator = renderControl({ participant: false })
    expect(screen.queryByRole('button', { name: 'Leave game' })).not.toBeInTheDocument()
    spectator.unmount()
    renderControl({ terminal: true })
    expect(screen.queryByRole('button', { name: 'Leave game' })).not.toBeInTheDocument()
  })

  it('requires confirmation and cancel does not send a command', async () => {
    const user = userEvent.setup()
    renderControl()
    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    expect(screen.getByRole('dialog', { name: 'Leave this game?' })).toBeVisible()
    expect(screen.getByText(/remain at the table until the current hand is resolved/i)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Stay in game' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(screen.getByRole('button', { name: 'Confirm leave' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Stay in game' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Stay in game' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(mocks.leave).not.toHaveBeenCalled()
  })

  it('sends once, disables confirmation while pending, and shows deferred status', async () => {
    const user = userEvent.setup()
    const request = deferred<{ roomId: number; gameId: string; changed: boolean; deferred: boolean }>()
    mocks.leave.mockReturnValue(request.promise)
    renderControl()
    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    await user.click(screen.getByRole('button', { name: 'Confirm leave' }))
    const pendingButtons = screen.getAllByRole('button', { name: 'Leaving...' })
    expect(pendingButtons).toHaveLength(2)
    pendingButtons.forEach((button) => expect(button).toBeDisabled())
    expect(mocks.leave).toHaveBeenCalledTimes(1)
    request.resolve({ roomId: 7, gameId, changed: true, deferred: true })
    expect(await screen.findByText('Leaving after this hand')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Leave game' })).not.toBeInTheDocument()
  })

  it('keeps acknowledged departure latched when unrelated game state follows', async () => {
    const user = userEvent.setup()
    mocks.leave.mockResolvedValue({ roomId: 7, gameId, changed: true, deferred: true })
    renderWithAppContext(
      <Routes><Route path="/game" element={<RerenderHarness />} /></Routes>,
      { initialEntries: ['/game'] },
    )
    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    await user.click(screen.getByRole('button', { name: 'Confirm leave' }))
    await screen.findByText('Leaving after this hand')
    await user.click(screen.getByRole('button', { name: 'Unrelated game event' }))
    expect(screen.getByText('Leaving after this hand')).toBeVisible()
    expect(mocks.leave).toHaveBeenCalledTimes(1)
  })

  it('restores authoritative leaving state without another POST', () => {
    renderControl({ authoritativeLeaving: true })
    expect(screen.getByText('Leaving after this hand')).toBeVisible()
    expect(mocks.leave).not.toHaveBeenCalled()
  })

  it('cleans active discovery and room queries after deferred settlement', async () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(gameKeys.activeMine(), { roomId: 7, gameId, gameSessionId: 2, handId: 3, handNumber: 1 })
    queryClient.setQueryData(roomKeys.detail(7), { room: { id: 7 } })
    useGameDiscoveryStore.getState().discover(7, gameId)
    mocks.activeMine.mockResolvedValue(null)
    renderControl({ authoritativeLeaving: true, handResolved: true }, queryClient)
    expect(await screen.findByText('Rooms destination')).toBeVisible()
    expect(queryClient.getQueryData(gameKeys.activeMine())).toBeNull()
    expect(queryClient.getQueryData(roomKeys.detail(7))).toBeUndefined()
    expect(useGameDiscoveryStore.getState()).toMatchObject({ roomId: null, gameId: null })
    expect(mocks.leave).not.toHaveBeenCalled()
  })

  it('cleans and navigates immediately when the server reports no deferral', async () => {
    const user = userEvent.setup()
    mocks.leave.mockResolvedValue({ roomId: 7, gameId, changed: true, deferred: false })
    renderControl()
    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    await user.click(screen.getByRole('button', { name: 'Confirm leave' }))
    expect(await screen.findByText('Rooms destination')).toBeVisible()
    expect(mocks.leave).toHaveBeenCalledTimes(1)
  })

  it('closes with Escape and restores focus to the leave control', async () => {
    const user = userEvent.setup()
    renderControl()
    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Leave game' })).toHaveFocus()
    expect(mocks.leave).not.toHaveBeenCalled()
  })

  it('rechecks after an early active response without needing another game event', async () => {
    mocks.activeMine.mockResolvedValueOnce({roomId:7, gameId}).mockResolvedValue(null)
    renderControl({ authoritativeLeaving: true, handResolved: true })
    expect(screen.getByText('Leaving after this hand')).toBeVisible()
    expect(await screen.findByText('Rooms destination', {}, {timeout:3000})).toBeVisible()
    expect(mocks.activeMine).toHaveBeenCalledTimes(2)
  })

  it('keeps a failed request recoverable and does not claim departure', async () => {
    const user = userEvent.setup()
    mocks.leave.mockRejectedValue(new ApiClientError(409, 'GAME_NOT_ACTIVE', 'That change conflicts with existing information.'))
    renderControl()
    await user.click(screen.getByRole('button', { name: 'Leave game' }))
    await user.click(screen.getByRole('button', { name: 'Confirm leave' }))
    await waitFor(() => expect(screen.getByRole('alert')).toBeVisible())
    expect(screen.getByRole('button', { name: 'Confirm leave' })).toBeEnabled()
    expect(screen.queryByText('Leaving after this hand')).not.toBeInTheDocument()
  })
})
