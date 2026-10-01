import { Navigate } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useSessionStore } from '../session/sessionStore'
import { RoleHomeRedirect } from './RoleHomeRedirect'

export function RootRedirect() {
  const status = useSessionStore((state) => state.status)
  if (status === 'CHECKING_SESSION') {
    return <main className="grid min-h-screen place-items-center bg-canvas"><LoadingState label="Checking your session…" /></main>
  }
  return status === 'AUTHENTICATED' ? <RoleHomeRedirect /> : <Navigate to={routes.login} replace />
}
