import type { QueryClient } from '@tanstack/react-query'
import { authApi } from '../api/authApi'
import type { AuthResponse } from '../types/auth'
import { currentProfileQueryOptions } from '../../profile/api/profileQueries'
import { tokenVault } from './tokenVault'
import { useSessionStore } from './sessionStore'
import type { CurrentUser } from '../../profile/types/profile'

let refreshInFlight: Promise<string> | null = null
let restorationInFlight: Promise<void> | null = null

export function clearAuthSession(queryClient: QueryClient): void {
  tokenVault.clear()
  useSessionStore.getState().clearSession()
  queryClient.clear()
}

export async function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) return refreshInFlight
  const refreshToken = tokenVault.readRefreshToken()
  if (!refreshToken) throw new Error('No refresh token')
  const epoch = useSessionStore.getState().epoch
  refreshInFlight = authApi.refresh(refreshToken)
    .then((response) => {
      if (useSessionStore.getState().epoch !== epoch) throw new Error('Session changed during refresh')
      useSessionStore.getState().setAccessToken(response.accessToken)
      return response.accessToken
    })
    .finally(() => { refreshInFlight = null })
  return refreshInFlight
}

export async function establishAuthenticatedSession(response: AuthResponse, queryClient: QueryClient): Promise<CurrentUser> {
  const epoch = useSessionStore.getState().epoch
  tokenVault.storeRefreshToken(response.refreshToken)
  useSessionStore.getState().setAccessToken(response.accessToken)
  try {
    const user = await queryClient.fetchQuery(currentProfileQueryOptions())
    if (useSessionStore.getState().epoch !== epoch) throw new Error('Session changed during sign in')
    useSessionStore.getState().markAuthenticated()
    return user
  } catch (error) {
    clearAuthSession(queryClient)
    throw error
  }
}

export function restoreSession(queryClient: QueryClient): Promise<void> {
  if (restorationInFlight) return restorationInFlight
  restorationInFlight = (async () => {
    useSessionStore.getState().beginSessionCheck()
    const epoch = useSessionStore.getState().epoch
    if (!tokenVault.readRefreshToken()) {
      clearAuthSession(queryClient)
      return
    }
    try {
      await refreshAccessToken()
      await queryClient.fetchQuery(currentProfileQueryOptions())
      if (useSessionStore.getState().epoch !== epoch) {
        throw new Error('Session changed during restoration')
      }
      useSessionStore.getState().markAuthenticated()
    } catch {
      clearAuthSession(queryClient)
    }
  })().finally(() => { restorationInFlight = null })
  return restorationInFlight
}
