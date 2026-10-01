import { NavLink, Outlet } from 'react-router-dom'
import { routes } from '../router/routePaths'
import { useLogout } from '../../features/auth/hooks/useLogout'
import { useCurrentProfile } from '../../features/profile/hooks/useCurrentProfile'
import { getErrorMessage } from '../../shared/api/apiError'
import { AppLogo } from '../../shared/ui/AppLogo'
import { Avatar } from '../../shared/ui/Avatar'
import { Button } from '../../shared/ui/Button'
import { ErrorState } from '../../shared/ui/ErrorState'
import { LoadingState } from '../../shared/ui/LoadingState'
import { ActiveGameRecovery } from '../../features/game/components/ActiveGameRecovery'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `app-nav-link ${isActive ? 'app-nav-link-active' : ''}`

const navItems = [
  ['Lobby', routes.app, true], ['Rooms', routes.rooms, false], ['Friends', routes.friends, false],
  ['Rankings', routes.rankings, false], ['Performance', routes.statistics, false], ['Profile', routes.profile, false],
] as const

export function AppShell() {
  const profile = useCurrentProfile()
  const logout = useLogout()
  if (profile.isPending) return <main className="grid min-h-screen place-items-center bg-canvas"><LoadingState label="Loading your account…" /></main>
  if (profile.isError || !profile.data) {
    return <main className="grid min-h-screen place-items-center bg-canvas p-6"><div className="w-full max-w-lg"><ErrorState message={getErrorMessage(profile.error)} onRetry={() => void profile.refetch()} /></div></main>
  }
  const user = profile.data
  return (
    <div className="min-h-screen bg-canvas text-text">
      <header className="app-header">
        <div className="mx-auto flex min-h-16 max-w-[90rem] flex-wrap items-center gap-3 px-4 py-3 sm:flex-nowrap sm:gap-4 sm:px-6 sm:py-0 lg:px-8">
          <AppLogo />
          <nav className="app-nav" aria-label="Primary navigation">
            {navItems.map(([label, to, end]) => <NavLink key={to} end={end} to={to} className={navClass}>{label}</NavLink>)}
          </nav>
          <ActiveGameRecovery />
          <div className="hidden items-center gap-3 border-l border-border pl-4 xl:flex">
            <Avatar displayName={user.displayName} src={user.avatarUrl} size="sm" />
            <div className="max-w-36 leading-tight">
              <p className="truncate text-sm font-bold text-text">{user.displayName}</p>
              <p className="truncate text-xs text-muted">@{user.username}</p>
            </div>
          </div>
          <Button type="button" className="ml-auto sm:ml-0" variant="ghost" loading={logout.isPending} loadingLabel="Leaving…" onClick={() => logout.mutate()}>
            Log out
          </Button>
        </div>
      </header>
      <Outlet />
      <footer className="app-footer"><div><AppLogo /><p>Real-time tables. Server-authoritative play.</p></div><p>Play responsibly and enjoy the game.</p></footer>
    </div>
  )
}
