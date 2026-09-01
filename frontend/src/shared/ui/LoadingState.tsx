interface LoadingStateProps {
  label?: string
}

export function LoadingState({ label = 'Preparing your table…' }: LoadingStateProps) {
  return (
    <div className="grid min-h-52 place-items-center" role="status" aria-live="polite">
      <div className="flex flex-col items-center gap-4 text-center">
        <span className="size-9 animate-spin rounded-full border-2 border-accent/25 border-r-accent motion-reduce:animate-none" aria-hidden="true" />
        <p className="text-sm text-secondary">{label}</p>
      </div>
    </div>
  )
}
