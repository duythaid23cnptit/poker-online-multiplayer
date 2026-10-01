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
  INVALID_ROOM_PASSWORD: 'The room password is incorrect.',
  ROOM_FULL: 'This room has no available seats.',
  ROOM_NOT_JOINABLE: 'This room is no longer accepting players.',
  ALREADY_JOINED: 'You already have an active membership in this room.',
  ROOM_HAS_ACTIVE_GAME: 'Terminate the active game before closing this room.',
  ADMIN_SELF_SUSPEND: 'You cannot suspend your own administrator account.',
  GAME_ALREADY_FINISHED: 'This game session has already finished.',
  GAME_RUNTIME_NOT_ACTIVE: 'The active game runtime is no longer available.',
  INVALID_REASON: 'The moderation reason must not exceed 500 characters.',
  USER_NOT_FOUND: 'The requested user could not be found.',
  FRIEND_REQUEST_ALREADY_EXISTS: 'A pending friend request already exists.',
  FRIENDSHIP_ALREADY_EXISTS: 'You are already friends with this player.',
  SELF_FRIEND_REQUEST: 'You cannot send a friend request to yourself.',
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
