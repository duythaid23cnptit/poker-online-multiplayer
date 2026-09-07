import { forwardRef, type ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
  loadingLabel?: string
}

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-slate-950 hover:bg-accent-strong active:bg-accent-strong',
  secondary: 'border border-border bg-surface-elevated text-text hover:border-border-strong hover:bg-surface-hover',
  ghost: 'text-secondary hover:bg-surface-hover hover:text-text',
  danger: 'bg-danger text-slate-950 hover:bg-danger/90',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className = '', variant = 'primary', loading = false, loadingLabel = 'Working…', disabled, children, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-4 py-2.5 text-sm font-bold transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2 focus-visible:ring-offset-canvas disabled:cursor-not-allowed disabled:opacity-55 ${variants[variant]} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <span className="size-4 animate-spin rounded-full border-2 border-current border-r-transparent motion-reduce:animate-none" aria-hidden="true" />}
      <span>{loading ? loadingLabel : children}</span>
    </button>
  )
})
