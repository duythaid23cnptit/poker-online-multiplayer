import { useState } from 'react'

interface AvatarProps {
  displayName: string
  src?: string | null
  size?: 'sm' | 'md' | 'lg'
}

const sizes = { sm: 'size-9 text-xs', md: 'size-12 text-sm', lg: 'size-20 text-xl' }

function safeImageSource(src?: string | null): string | null {
  if (!src) return null
  try {
    const url = new URL(src)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null
  } catch {
    return null
  }
}

export function Avatar({ displayName, src, size = 'md' }: AvatarProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const safeSrc = safeImageSource(src)
  const failed = Boolean(safeSrc && failedSrc === safeSrc)
  const initial = displayName.trim().charAt(0).toUpperCase() || 'P'
  return (
    <span className={`grid shrink-0 place-items-center overflow-hidden rounded-full border border-border bg-accent/12 font-bold text-accent ${sizes[size]}`}>
      {safeSrc && !failed ? (
        <img src={safeSrc} alt={`${displayName}'s avatar`} className="size-full object-cover" referrerPolicy="no-referrer" onError={() => setFailedSrc(safeSrc)} />
      ) : (
        <span aria-hidden="true">{initial}</span>
      )}
    </span>
  )
}
