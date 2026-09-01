import { forwardRef, type InputHTMLAttributes } from 'react'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className = '', invalid = false, ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={`min-h-12 w-full rounded-control border bg-surface-elevated px-3.5 py-2.5 text-sm text-text outline-none transition duration-200 placeholder:text-muted/75 focus:border-accent focus:ring-2 focus:ring-accent/20 disabled:cursor-not-allowed disabled:opacity-60 ${invalid ? 'border-danger focus:border-danger focus:ring-danger/20' : 'border-border hover:border-border-strong'} ${className}`}
      {...props}
    />
  )
})
