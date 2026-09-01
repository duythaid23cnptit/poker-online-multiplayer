import { describe, expect, it } from 'vitest'
import { loginSchema, registerSchema } from './authSchemas'

const validRegistration = {
  username: 'abc',
  displayName: 'AB',
  email: '',
  password: '12345678',
}

describe('auth schemas', () => {
  it('matches the backend username bounds and character set', () => {
    expect(registerSchema.safeParse(validRegistration).success).toBe(true)
    expect(registerSchema.safeParse({ ...validRegistration, username: 'ab' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...validRegistration, username: 'a'.repeat(50) }).success).toBe(true)
    expect(registerSchema.safeParse({ ...validRegistration, username: 'a'.repeat(51) }).success).toBe(false)
    expect(registerSchema.safeParse({ ...validRegistration, username: 'bad-name' }).success).toBe(false)
  })

  it('matches display-name, email, and password bounds', () => {
    expect(registerSchema.safeParse({ ...validRegistration, displayName: 'x' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...validRegistration, displayName: 'x'.repeat(100) }).success).toBe(true)
    expect(registerSchema.safeParse({ ...validRegistration, displayName: 'x'.repeat(101) }).success).toBe(false)
    expect(registerSchema.safeParse({ ...validRegistration, email: 'player@example.com' }).success).toBe(true)
    expect(registerSchema.safeParse({ ...validRegistration, email: 'invalid' }).success).toBe(false)
    expect(registerSchema.safeParse({ ...validRegistration, password: 'x'.repeat(72) }).success).toBe(true)
    expect(registerSchema.safeParse({ ...validRegistration, password: 'x'.repeat(73) }).success).toBe(false)
  })

  it('matches login required and maximum lengths', () => {
    expect(loginSchema.safeParse({ username: 'player', password: 'password' }).success).toBe(true)
    expect(loginSchema.safeParse({ username: '', password: '' }).success).toBe(false)
    expect(loginSchema.safeParse({ username: 'x'.repeat(51), password: 'password' }).success).toBe(false)
    expect(loginSchema.safeParse({ username: 'player', password: 'x'.repeat(73) }).success).toBe(false)
  })
})
