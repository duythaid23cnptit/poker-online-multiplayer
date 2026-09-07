import { useEffect } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { useActiveGame } from '../hooks/useActiveGame'
import { useGameDiscoveryStore } from '../hooks/gameDiscoveryStore'

export function ActiveGameRecovery() {
  const activeGame = useActiveGame()
  const location = useLocation()
  const navigate = useNavigate()
  const game = activeGame.data
  const refetch = activeGame.refetch
  const target = game ? routes.game(game.roomId, game.gameId) : null
  const autoRecoveryRoute = location.pathname === routes.app
    || location.pathname === `${routes.app}/`
    || location.pathname === routes.rooms

  useEffect(() => {
    if (!activeGame.isSuccess) return
    if (!game) {
      useGameDiscoveryStore.getState().clear()
      return
    }
    useGameDiscoveryStore.getState().discover(game.roomId, game.gameId)
  }, [activeGame.isSuccess, game])

  useEffect(() => {
    if (!autoRecoveryRoute) return
    let active = true
    void refetch({ cancelRefetch: false }).then((result) => {
      if (!active) return
      if (!result.data) {
        useGameDiscoveryStore.getState().clear()
        return
      }
      useGameDiscoveryStore.getState().discover(result.data.roomId, result.data.gameId)
      const freshTarget = routes.game(result.data.roomId, result.data.gameId)
      if (location.pathname !== freshTarget) navigate(freshTarget, { replace: true })
    })
    return () => { active = false }
  }, [autoRecoveryRoute, location.pathname, navigate, refetch])

  if (!game || !target || autoRecoveryRoute || location.pathname === target) return null
  return <Link className="action-link-secondary" to={target}>Active game · Return to table</Link>
}
