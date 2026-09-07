import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { roomApi } from '../api/roomApi'
import { cacheRoomDetail } from '../api/roomCache'
import { existingRoomMembershipsQueryOptions, roomDetailQueryOptions, roomKeys, roomListQueryOptions } from '../api/roomQueries'
import type { RoomDetail } from '../types/room'

export function useRooms() {
  return useQuery(roomListQueryOptions())
}
export function useRoomDetail(roomId: number, enabled = true) { return useQuery(roomDetailQueryOptions(roomId, enabled)) }
export function useExistingRoomMemberships(roomIds: number[]) { return useQuery(existingRoomMembershipsQueryOptions(roomIds)) }

export function useCreateRoom() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: roomApi.create,
    onSuccess: (detail) => {
      cacheRoomDetail(queryClient, detail)
      void queryClient.invalidateQueries({ queryKey: roomKeys.memberships() })
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
      cacheRoomDetail(queryClient, detail)
      void queryClient.invalidateQueries({ queryKey: roomKeys.memberships() })
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
      queryClient.setQueriesData<RoomDetail[]>({ queryKey: roomKeys.memberships() }, (memberships) =>
        memberships?.filter((membership) => membership.room.id !== detail.room.id))
      void queryClient.invalidateQueries({ queryKey: roomKeys.memberships() })
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
    },
  })
}
