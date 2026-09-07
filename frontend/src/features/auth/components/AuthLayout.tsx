import { Outlet } from 'react-router-dom'
import { AppLogo } from '../../../shared/ui/AppLogo'

export function AuthLayout() {
  return (
    <main className="auth-backdrop min-h-screen px-4 py-5 sm:px-6 sm:py-7 lg:px-10 lg:py-8">
      <div className="auth-layout mx-auto grid min-h-[calc(100vh-2.5rem)] max-w-[1500px] items-center gap-8 md:min-h-[calc(100vh-3.5rem)] lg:min-h-[calc(100vh-4rem)] lg:grid-cols-[minmax(0,1fr)_minmax(26rem,31rem)] lg:gap-12">
      <section className="auth-brand-panel relative hidden min-h-[36rem] overflow-hidden px-1 py-2 lg:flex lg:flex-col lg:justify-between lg:min-h-[44rem] lg:px-4" aria-label="Poker Online introduction">
        <AppLogo />
        <div className="auth-brand-copy relative z-10 max-w-xl pb-8 pt-16 lg:pb-12 lg:pt-20">
          <p className="mb-5 text-xs font-bold uppercase tracking-[0.24em] text-accent">Texas Hold’em · Live multiplayer</p>
          <h1 className="auth-brand-title text-balance text-5xl font-black uppercase leading-[0.98] tracking-[-0.035em] text-text lg:text-6xl">
            Real players.<br />Real time.<br /><span>Real poker.</span>
          </h1>
          <div className="auth-brand-rule" aria-hidden="true" />
          <p className="mt-6 max-w-lg text-base leading-7 text-secondary lg:text-lg lg:leading-8">
            A focused Texas Hold&apos;em experience built around fair play, clear decisions, and live competition.
          </p>
          <div className="mt-9 grid max-w-lg gap-5 sm:grid-cols-3 sm:gap-4">
            <div className="auth-value-item">
              <span className="auth-value-icon" aria-hidden="true">◆</span>
              <span><strong>Fair &amp; secure</strong><small>Protected account sessions</small></span>
            </div>
            <div className="auth-value-item">
              <span className="auth-value-icon" aria-hidden="true">↯</span>
              <span><strong>Real-time play</strong><small>Built for live tables</small></span>
            </div>
            <div className="auth-value-item">
              <span className="auth-value-icon" aria-hidden="true">♠</span>
              <span><strong>Made to compete</strong><small>Decisions that stay yours</small></span>
            </div>
          </div>
        </div>
        <div className="auth-scene" aria-hidden="true">
          <div className="auth-table-surface" />
          <div className="auth-card-mark auth-card-one"><span>A</span><b>♠</b></div>
          <div className="auth-card-mark auth-card-two"><span>K</span><b>♥</b></div>
          <div className="auth-chip-stack auth-chip-stack-one"><i /><i /><i /></div>
          <div className="auth-chip-stack auth-chip-stack-two"><i /><i /></div>
        </div>
      </section>
      <section className="mx-auto flex w-full max-w-[31rem] items-center justify-center">
        <div className="w-full">
          <div className="mb-7 flex justify-center lg:hidden"><AppLogo /></div>
          <Outlet />
        </div>
      </section>
      </div>
    </main>
  )
}
