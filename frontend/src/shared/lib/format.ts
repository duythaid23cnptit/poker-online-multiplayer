export function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US').format(value)
}

export function formatTimestamp(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Recently' : new Intl.DateTimeFormat('en-US', {
    month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  }).format(date)
}
