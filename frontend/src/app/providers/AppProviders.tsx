import { QueryClientProvider } from '@tanstack/react-query'
import type { PropsWithChildren } from 'react'
import { SessionBootstrap } from '../../features/auth/components/SessionBootstrap'
import { RealtimeBootstrap } from '../../features/notifications/realtime/RealtimeBootstrap'
import { clearAuthSession, refreshAccessToken } from '../../features/auth/session/sessionCoordinator'
import { useSessionStore } from '../../features/auth/session/sessionStore'
import { apiClient } from '../../shared/api/httpClient'
import { appQueryClient } from '../../shared/api/queryClient'

apiClient.configureAuthentication({
  getAccessToken: () => useSessionStore.getState().accessToken,
  refreshAccessToken,
  onAuthenticationFailure: () => clearAuthSession(appQueryClient),
})

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <QueryClientProvider client={appQueryClient}>
      <SessionBootstrap>
        <RealtimeBootstrap />
        {children}
      </SessionBootstrap>
    </QueryClientProvider>
  )
}
