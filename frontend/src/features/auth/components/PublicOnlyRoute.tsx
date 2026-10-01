import { Outlet } from 'react-router-dom'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useSessionStore } from '../session/sessionStore'
import { RoleHomeRedirect } from './RoleHomeRedirect'

export function PublicOnlyRoute() {
  const status = useSessionStore((state) => state.status)
  if (status === 'CHECKING_SESSION') {
    return <main className="grid min-h-screen place-items-center bg-canvas"><LoadingState label="Checking your session…" /></main>
  }
  return status === 'AUTHENTICATED' ? <RoleHomeRedirect /> : <Outlet />
}
