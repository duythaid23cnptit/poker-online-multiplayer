interface LoadingStateProps {
  label?: string
  variant?: 'centered' | 'rows' | 'table' | 'avatars' | 'dashboard'
}

export function LoadingState({ label = 'Preparing your table…', variant = 'centered' }: LoadingStateProps) {
  if (variant !== 'centered') {
    const count = variant === 'dashboard' ? 4 : 3
    return <div className={`skeleton-group skeleton-${variant}`} role="status" aria-live="polite" aria-label={label}>
      {Array.from({ length: count }, (_, index) => <div className="skeleton-row" key={index}><span /><div><i /><i /></div><b /></div>)}
      <span className="sr-only">{label}</span>
    </div>
  }
  return (
    <div className="grid min-h-36 place-items-center" role="status" aria-live="polite">
      <div className="flex flex-col items-center gap-3 text-center">
        <span className="loading-chip" aria-hidden="true" />
        <p className="text-sm text-secondary">{label}</p>
      </div>
    </div>
  )
}
