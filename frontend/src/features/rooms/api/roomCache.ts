import type { QueryClient } from '@tanstack/react-query'
import type { RoomDetail } from '../types/room'
import { roomKeys } from './roomQueries'

export function cacheRoomDetail(queryClient: QueryClient, detail: RoomDetail) {
  queryClient.setQueryData(roomKeys.detail(detail.room.id), detail)
  queryClient.setQueriesData<RoomDetail[]>({ queryKey: roomKeys.memberships() }, (memberships) =>
    memberships?.map((membership) => membership.room.id === detail.room.id ? detail : membership))
}
