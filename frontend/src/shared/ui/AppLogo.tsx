interface AppLogoProps {
  compact?: boolean
}

export function AppLogo({ compact = false }: AppLogoProps) {
  return (
    <div className="flex items-center gap-3" role="img" aria-label="Poker Online">
      <span className="auth-logo-mark grid size-11 place-items-center rounded-xl border border-accent/30 bg-accent/10 text-2xl font-black leading-none text-accent shadow-soft">
        ♠
      </span>
      {!compact && (
        <span className="leading-tight">
          <span className="block text-[1.08rem] font-black uppercase tracking-[0.08em] text-text">Poker <em className="not-italic text-accent">Online</em></span>
          <span className="block text-[0.65rem] uppercase tracking-[0.2em] text-muted">Multiplayer Texas Hold&apos;em</span>
        </span>
      )}
    </div>
  )
}
