import type { HTMLAttributes } from 'react'

export function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`rounded-panel border border-border bg-surface/95 shadow-panel ${className}`} {...props} />
}
