import type { ReactNode } from 'react'

interface FormFieldProps {
  id: string
  label: string
  hint?: string
  error?: string
  reserveErrorSpace?: boolean
  children: ReactNode
}

export function FormField({ id, label, hint, error, children, reserveErrorSpace = false }: FormFieldProps) {
  return (
    <div className="space-y-2">
      <div className="flex items-baseline justify-between gap-4">
        <label htmlFor={id} className="text-sm font-semibold text-text">{label}</label>
        {hint && <span id={`${id}-hint`} className="text-xs text-muted">{hint}</span>}
      </div>
      {children}
      {(error || reserveErrorSpace) && <p id={`${id}-error`} className="min-h-5 text-sm text-danger" role={error ? 'alert' : undefined} aria-hidden={!error || undefined}>{error}</p>}
    </div>
  )
}
