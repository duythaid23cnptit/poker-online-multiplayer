import type { ReactNode } from 'react'

type Tone = 'accent' | 'muted' | 'success' | 'warning' | 'danger'

const tones: Record<Tone, string> = {
  accent: 'border-accent/25 bg-accent/10 text-accent',
  muted: 'border-border bg-surface-elevated text-secondary',
  success: 'border-success/25 bg-success/10 text-success',
  warning: 'border-warning/25 bg-warning/10 text-warning',
  danger: 'border-danger/25 bg-danger/10 text-danger',
}

export function StatusBadge({ tone = 'muted', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-flex items-center rounded-full border px-2.5 py-1 text-[0.68rem] font-bold uppercase tracking-[0.08em] ${tones[tone]}`}>{children}</span>
}
