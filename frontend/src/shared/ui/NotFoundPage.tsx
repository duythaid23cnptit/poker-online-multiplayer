import { Link } from 'react-router-dom'
import { routes } from '../../app/router/routePaths'
import { Card } from './Card'

export function NotFoundPage() {
  return (
    <main className="grid min-h-screen place-items-center bg-canvas p-6">
      <Card className="w-full max-w-lg p-8 text-center">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-accent">404</p>
        <h1 className="mt-3 text-3xl font-black text-text">That page is not at this table</h1>
        <p className="mt-3 text-sm leading-6 text-secondary">The address may be incorrect or the page may have moved.</p>
        <Link
          to={routes.root}
          className="mt-6 inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-4 py-2.5 text-sm font-bold text-slate-950 transition hover:bg-accent-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas"
        >
          Return home
        </Link>
      </Card>
    </main>
  )
}
