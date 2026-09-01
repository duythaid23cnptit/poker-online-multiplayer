import { useMutation, useQueryClient } from '@tanstack/react-query'
import { profileApi } from '../api/profileApi'
import { profileKeys } from '../api/profileQueries'
import type { UpdateProfileRequest } from '../types/profile'

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateProfileRequest) => profileApi.update(request),
    onSuccess: (profile) => queryClient.setQueryData(profileKeys.current(), profile),
  })
}
