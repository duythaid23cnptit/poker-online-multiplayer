const refreshTokenKey = 'poker-online.refresh-token'

function storage(): Storage | null {
  try {
    return globalThis.sessionStorage || null
  } catch {
    return null
  }
}

export const tokenVault = {
  readRefreshToken(): string | null {
    try {
      return storage()?.getItem(refreshTokenKey) || null
    } catch {
      return null
    }
  },
  storeRefreshToken(token: string): void {
    try {
      storage()?.setItem(refreshTokenKey, token)
    } catch {
      // Storage may be disabled by browser privacy policy; the access token remains memory-only.
    }
  },
  clear(): void {
    try {
      storage()?.removeItem(refreshTokenKey)
    } catch {
      // Local session state is still cleared when browser storage is unavailable.
    }
  },
}
