import { z } from 'zod'

export const loginSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username').max(50, 'Username must be 50 characters or fewer'),
  password: z.string().min(1, 'Enter your password').max(72, 'Password must be 72 characters or fewer'),
})

export type LoginFormValues = z.infer<typeof loginSchema>

export const registerSchema = z.object({
  username: z.string().trim()
    .min(3, 'Username must be at least 3 characters')
    .max(50, 'Username must be 50 characters or fewer')
    .regex(/^[A-Za-z0-9_]+$/, 'Use only letters, numbers, and underscores'),
  displayName: z.string().trim()
    .min(2, 'Display name must be at least 2 characters')
    .max(100, 'Display name must be 100 characters or fewer'),
  email: z.string().trim()
    .max(255, 'Email must be 255 characters or fewer')
    .refine((value) => value === '' || z.email().safeParse(value).success, 'Enter a valid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters').max(72, 'Password must be 72 characters or fewer'),
})

export type RegisterFormValues = z.infer<typeof registerSchema>
