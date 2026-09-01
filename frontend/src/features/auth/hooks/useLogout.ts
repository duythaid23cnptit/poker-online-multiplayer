import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { stompSession } from '../../../shared/realtime/stompSession'
import { authApi } from '../api/authApi'
import { clearAuthSession } from '../session/sessionCoordinator'
import { tokenVault } from '../session/tokenVault'

export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: async () => {
      const refreshToken = tokenVault.readRefreshToken()
      try {
        if (refreshToken) await authApi.logout(refreshToken)
      } finally {
        try {
          await stompSession.disconnect()
        } finally {
          clearAuthSession(queryClient)
          navigate(routes.login, { replace: true })
        }
      }
    },
  })
}
