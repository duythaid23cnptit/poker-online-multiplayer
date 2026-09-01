import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/authApi'
import { establishAuthenticatedSession } from '../session/sessionCoordinator'
import type { LoginRequest } from '../types/auth'

export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (request: LoginRequest) => {
      const response = await authApi.login(request)
      await establishAuthenticatedSession(response, queryClient)
    },
  })
}
