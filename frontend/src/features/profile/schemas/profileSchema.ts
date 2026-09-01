import { z } from 'zod'

function isSafeHttpUrl(value: string): boolean {
  if (!value) return true
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

export const profileSchema = z.object({
  displayName: z.string().trim()
    .min(2, 'Display name must be at least 2 characters')
    .max(100, 'Display name must be 100 characters or fewer'),
  avatarUrl: z.string().trim()
    .max(2048, 'Avatar URL must be 2048 characters or fewer')
    .refine(isSafeHttpUrl, 'Enter a complete http:// or https:// URL'),
})

export type ProfileFormValues = z.infer<typeof profileSchema>
