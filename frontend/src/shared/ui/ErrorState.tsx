import { Button } from './Button'

interface ErrorStateProps {
  title?: string
  message: string
  onRetry?: () => void
}

export function ErrorState({ title = 'We hit a snag', message, onRetry }: ErrorStateProps) {
  return (
    <div className="rounded-panel border border-danger/25 bg-danger/5 p-6 text-center" role="alert">
      <div className="mx-auto mb-4 grid size-10 place-items-center rounded-full bg-danger/10 font-bold text-danger" aria-hidden="true">!</div>
      <h2 className="text-lg font-bold text-text">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-secondary">{message}</p>
      {onRetry && <Button type="button" variant="secondary" className="mt-5" onClick={onRetry}>Try again</Button>}
    </div>
  )
}
