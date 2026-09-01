import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useSessionStore } from '../session/sessionStore'

export function RequireAuth() {
  const status = useSessionStore((state) => state.status)
  const location = useLocation()
  if (status === 'CHECKING_SESSION') {
    return <main className="grid min-h-screen place-items-center bg-canvas"><LoadingState label="Checking your session…" /></main>
  }
  if (status === 'UNAUTHENTICATED') {
    return <Navigate to={routes.login} replace state={{ from: `${location.pathname}${location.search}` }} />
  }
  return <Outlet />
}
