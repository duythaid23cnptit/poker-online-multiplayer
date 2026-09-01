import { useMutation } from '@tanstack/react-query'
import { authApi } from '../api/authApi'
import type { RegisterRequest } from '../types/auth'

export function useRegister() {
  return useMutation({ mutationFn: (request: RegisterRequest) => authApi.register(request) })
}
