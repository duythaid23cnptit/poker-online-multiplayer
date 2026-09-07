import { fireEvent, screen, waitFor } from '@testing-library/react'
import { useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '../../../app/router/routePaths'
import { renderWithAppContext } from '../../../test/testUtils'
import type { ActiveGame } from '../types/game'
import { useGameDiscoveryStore } from '../hooks/gameDiscoveryStore'
import { ActiveGameRecovery } from './ActiveGameRecovery'

const mocks = vi.hoisted(() => ({ useActiveGame: vi.fn() }))

vi.mock('../hooks/useActiveGame', () => ({ useActiveGame: mocks.useActiveGame }))

const activeGame: ActiveGame = {
  roomId: 12,
  gameId: '11111111-1111-4111-8111-111111111111',
  gameSessionId: 10,
  handId: 20,
  handNumber: 3,
}

function activeQuery(data: ActiveGame | null, refreshed: ActiveGame | null = data) {
  return {
    data,
    isSuccess: true,
    refetch: vi.fn().mockResolvedValue({ data: refreshed }),
  }
}

function Harness() {
  const location = useLocation()
  return <><ActiveGameRecovery /><span data-testid="pathname">{location.pathname}</span></>
}

describe('ActiveGameRecovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useGameDiscoveryStore.getState().clear()
  })

  it.each([routes.app, routes.rooms])('redirects %s to the exact active table with replace semantics', async (pathname) => {
    mocks.useActiveGame.mockReturnValue(activeQuery(activeGame))
    renderWithAppContext(<Harness />, { initialEntries: [pathname] })

    await waitFor(() => expect(screen.getByTestId('pathname')).toHaveTextContent(routes.game(12, activeGame.gameId)))
    expect(useGameDiscoveryStore.getState()).toMatchObject({ roomId: 12, gameId: activeGame.gameId })
  })

  it.each([routes.app, routes.rooms])('keeps %s in place when 404 was normalized to no active game', (pathname) => {
    mocks.useActiveGame.mockReturnValue(activeQuery(null))
    renderWithAppContext(<Harness />, { initialEntries: [pathname] })

    expect(screen.getByTestId('pathname')).toHaveTextContent(pathname)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('does nothing when already on the discovered game route', () => {
    mocks.useActiveGame.mockReturnValue(activeQuery(activeGame))
    const target = routes.game(activeGame.roomId, activeGame.gameId)
    renderWithAppContext(<Harness />, { initialEntries: [target] })

    expect(screen.getByTestId('pathname')).toHaveTextContent(target)
    expect(screen.queryByRole('link', { name: /Return to table/i })).not.toBeInTheDocument()
  })

  it.each([routes.profile, routes.friends, routes.rankings])('does not force navigation from %s and exposes a return CTA', (pathname) => {
    mocks.useActiveGame.mockReturnValue(activeQuery(activeGame))
    renderWithAppContext(<Harness />, { initialEntries: [pathname] })

    expect(screen.getByTestId('pathname')).toHaveTextContent(pathname)
    const link = screen.getByRole('link', { name: 'Active game · Return to table' })
    expect(link).toHaveAttribute('href', routes.game(activeGame.roomId, activeGame.gameId))
  })

  it('routes the return CTA to the exact discovered game', () => {
    mocks.useActiveGame.mockReturnValue(activeQuery(activeGame))
    renderWithAppContext(<Harness />, { initialEntries: [routes.profile] })

    fireEvent.click(screen.getByRole('link', { name: 'Active game · Return to table' }))

    expect(screen.getByTestId('pathname')).toHaveTextContent(routes.game(activeGame.roomId, activeGame.gameId))
  })

  it.each([routes.app, routes.rooms])('revalidates stale recovery data before redirecting from %s', async (pathname) => {
    useGameDiscoveryStore.getState().discover(activeGame.roomId, activeGame.gameId)
    mocks.useActiveGame.mockReturnValue(activeQuery(activeGame, null))
    renderWithAppContext(<Harness />, { initialEntries: [pathname] })

    await waitFor(() => expect(useGameDiscoveryStore.getState()).toMatchObject({ roomId: null, gameId: null }))
    expect(screen.getByTestId('pathname')).toHaveTextContent(pathname)
  })
})
