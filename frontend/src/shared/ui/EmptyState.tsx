import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description: string
  action?: ReactNode
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="rounded-panel border border-dashed border-border bg-surface/55 p-8 text-center">
      <div className="mx-auto mb-4 size-10 rounded-full border-4 border-accent/20 border-t-accent" aria-hidden="true" />
      <h2 className="font-bold text-text">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-secondary">{description}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}
