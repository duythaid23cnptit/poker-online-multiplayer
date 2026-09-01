import type { ReactNode } from 'react'

interface FormFieldProps {
  id: string
  label: string
  hint?: string
  error?: string
  children: ReactNode
}

export function FormField({ id, label, hint, error, children }: FormFieldProps) {
  return (
    <div className="space-y-2">
      <div className="flex items-baseline justify-between gap-4">
        <label htmlFor={id} className="text-sm font-semibold text-text">{label}</label>
        {hint && <span id={`${id}-hint`} className="text-xs text-muted">{hint}</span>}
      </div>
      {children}
      {error && <p id={`${id}-error`} className="text-sm text-danger" role="alert">{error}</p>}
    </div>
  )
}
