import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description: string
  action?: ReactNode
  variant?: 'compact' | 'panel' | 'inline'
  motif?: 'chip' | 'cards' | 'suit'
}

export function EmptyState({ title, description, action, variant = 'panel', motif = 'chip' }: EmptyStateProps) {
  return (
    <div className={`empty-state empty-state-${variant}`}>
      <div className={`empty-motif empty-motif-${motif}`} aria-hidden="true">{motif === 'cards' ? '' : '♠'}</div>
      <div className="empty-copy"><h2>{title}</h2><p>{description}</p></div>
      {action && <div className="empty-action">{action}</div>}
    </div>
  )
}
