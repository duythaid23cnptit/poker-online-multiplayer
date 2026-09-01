import { Link } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { Card } from '../../../shared/ui/Card'
import { useCurrentProfile } from '../hooks/useCurrentProfile'

export function AccountHomePage() {
  const { data: user } = useCurrentProfile()
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-10 sm:px-6 sm:py-14 lg:px-8">
      <div className="max-w-3xl">
        <p className="text-xs font-bold uppercase tracking-[0.22em] text-accent">Account ready</p>
        <h1 className="mt-3 text-4xl font-black tracking-tight text-text sm:text-5xl">
          Welcome, {user?.displayName || 'player'}.
        </h1>
        <p className="mt-5 max-w-2xl text-base leading-7 text-secondary">
          Your secure player identity is set up. Review the details other players will see when you take a seat.
        </p>
      </div>
      <Card className="mt-10 max-w-2xl p-6 sm:p-8">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-lg font-bold text-text">Player profile</h2>
            <p className="mt-2 text-sm leading-6 text-secondary">Keep your display name and avatar ready for future tables.</p>
          </div>
          <Link className="inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-4 py-2.5 text-sm font-bold text-slate-950 transition hover:bg-accent-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas" to={routes.profile}>
            View profile
          </Link>
        </div>
      </Card>
    </main>
  )
}
