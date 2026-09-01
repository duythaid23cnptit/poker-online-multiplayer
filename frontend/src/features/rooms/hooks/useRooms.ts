import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { roomApi } from '../api/roomApi'
import { roomKeys, roomListQueryOptions } from '../api/roomQueries'

export function useRooms() {
  return useQuery(roomListQueryOptions())
}

export function useCreateRoom() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: roomApi.create,
    onSuccess: (detail) => {
      queryClient.setQueryData(roomKeys.detail(detail.room.id), detail)
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
    },
  })
}

export function useJoinRoom() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ roomId, request }: { roomId: number; request: Parameters<typeof roomApi.join>[1] }) =>
      roomApi.join(roomId, request),
    onSuccess: (detail) => {
      queryClient.setQueryData(roomKeys.detail(detail.room.id), detail)
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
    },
  })
}

export function useLeaveRoom() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: roomApi.leave,
    onSuccess: (detail) => {
      queryClient.removeQueries({ queryKey: roomKeys.detail(detail.room.id) })
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
    },
  })
}
