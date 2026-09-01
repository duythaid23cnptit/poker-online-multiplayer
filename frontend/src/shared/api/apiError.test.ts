import { describe, expect, it } from 'vitest'
import {
  ApiClientError,
  getErrorMessage,
  parseBackendError,
  safeErrorMessage,
} from './apiError'

describe('API error normalization', () => {
  it('preserves stable backend codes and only structurally valid field errors', () => {
    expect(
      parseBackendError({
        code: 'VALIDATION_FAILED',
        message: 'Backend detail',
        fieldErrors: [
          { field: 'username', code: 'SIZE', message: 'Must be longer' },
          { field: 'password', message: 'Missing error code' },
          'unexpected',
        ],
      }),
    ).toEqual({
      code: 'VALIDATION_FAILED',
      message: 'Backend detail',
      fieldErrors: [{ field: 'username', code: 'SIZE', message: 'Must be longer' }],
    })
  })

  it('rejects malformed backend error envelopes', () => {
    expect(parseBackendError(null)).toBeNull()
    expect(parseBackendError({ code: 500, message: 'Internal detail' })).toBeNull()
    expect(parseBackendError({ code: 'BROKEN' })).toBeNull()
  })

  it('maps known codes and HTTP categories to human-readable safe messages', () => {
    expect(safeErrorMessage(401, 'AUTHENTICATION_FAILED')).toBe(
      'The username or password is incorrect.',
    )
    expect(safeErrorMessage(403)).toBe('You do not have permission to perform this action.')
    expect(safeErrorMessage(500)).toBe(
      'The server is temporarily unavailable. Please try again.',
    )
  })

  it('does not expose arbitrary exception messages to the UI', () => {
    const apiError = new ApiClientError(500, 'HTTP_500', 'A safe message')

    expect(getErrorMessage(apiError)).toBe('A safe message')
    expect(getErrorMessage(new Error('SQL constraint chat_messages leaked'))).toBe(
      'Something went wrong. Please try again.',
    )
  })
})
