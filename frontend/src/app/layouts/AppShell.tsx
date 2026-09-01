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

const navClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-control px-3 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${isActive ? 'bg-accent/12 text-accent' : 'text-secondary hover:bg-surface-hover hover:text-text'}`

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
      <header className="sticky top-0 z-20 border-b border-border/80 bg-canvas/92 backdrop-blur-xl">
        <div className="mx-auto flex min-h-16 max-w-7xl flex-wrap items-center gap-3 px-4 py-3 sm:flex-nowrap sm:gap-4 sm:px-6 sm:py-0 lg:px-8">
          <AppLogo />
          <nav className="order-3 flex w-full items-center gap-1 border-t border-border/70 pt-2 sm:order-none sm:ml-auto sm:w-auto sm:border-0 sm:pt-0" aria-label="Primary navigation">
            <NavLink end to={routes.app} className={navClass}>Home</NavLink>
            <NavLink to={routes.profile} className={navClass}>Profile</NavLink>
          </nav>
          <div className="hidden items-center gap-3 border-l border-border pl-4 sm:flex">
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
    </div>
  )
}
