import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  description: string
  actions?: ReactNode
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="page-heading">
      <div className="max-w-3xl">
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  )
}
