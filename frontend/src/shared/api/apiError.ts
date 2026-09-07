export interface FieldValidationError {
  field: string
  code: string
  message: string
}

export interface BackendErrorBody {
  code: string
  message: string
  fieldErrors: FieldValidationError[]
}

const codeMessages: Record<string, string> = {
  INSUFFICIENT_CHIPS: "You don't have enough chips to join this table.",
  SEAT_OCCUPIED: 'Seat is no longer available. Choose another seat.',
  SEAT_OR_MEMBERSHIP_CONFLICT: 'Seat is no longer available. Choose another seat.',
  ACCOUNT_LOCKED: 'This account is currently locked. Contact an administrator for help.',
  AUTHENTICATION_FAILED: 'The username or password is incorrect.',
  DUPLICATE_ACCOUNT: 'An account already uses that username or email address.',
  INVALID_REFRESH_TOKEN: 'Your session has expired. Please sign in again.',
  PROFILE_NOT_FOUND: 'We could not find your player profile.',
  VALIDATION_FAILED: 'Please review the highlighted fields and try again.',
}

export class ApiClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly fieldErrors: FieldValidationError[] = [],
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseFieldErrors(value: unknown): FieldValidationError[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!isRecord(item)) return []
    const { field, code, message } = item
    return typeof field === 'string' && typeof code === 'string' && typeof message === 'string'
      ? [{ field, code, message }]
      : []
  })
}

export function parseBackendError(value: unknown): BackendErrorBody | null {
  if (!isRecord(value) || typeof value.code !== 'string' || typeof value.message !== 'string') {
    return null
  }
  return {
    code: value.code,
    message: value.message,
    fieldErrors: parseFieldErrors(value.fieldErrors),
  }
}

export function safeErrorMessage(status: number, code?: string): string {
  if (code && codeMessages[code]) return codeMessages[code]
  if (status === 0) return 'We cannot reach the server. Check your connection and try again.'
  if (status === 400) return 'The request could not be completed. Please check your information.'
  if (status === 401) return 'Your session has expired. Please sign in again.'
  if (status === 403) return 'You do not have permission to perform this action.'
  if (status === 404) return 'The requested information could not be found.'
  if (status === 409) return 'That change conflicts with existing information.'
  return 'The server is temporarily unavailable. Please try again.'
}

export function getErrorMessage(error: unknown): string {
  return error instanceof ApiClientError
    ? error.message
    : 'Something went wrong. Please try again.'
}
