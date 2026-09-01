import { Navigate, Outlet } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useSessionStore } from '../session/sessionStore'

export function PublicOnlyRoute() {
  const status = useSessionStore((state) => state.status)
  if (status === 'CHECKING_SESSION') {
    return <main className="grid min-h-screen place-items-center bg-canvas"><LoadingState label="Checking your session…" /></main>
  }
  return status === 'AUTHENTICATED' ? <Navigate to={routes.app} replace /> : <Outlet />
}
