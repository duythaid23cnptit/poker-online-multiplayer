import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../api/adminApi'
import {
  adminAuditQueryOptions, adminGameQueryOptions, adminGamesQueryOptions, adminHandsQueryOptions,
  adminKeys, adminOverviewQueryOptions, adminRoomQueryOptions, adminRoomsQueryOptions,
  adminUserQueryOptions, adminUsersQueryOptions,
} from '../api/adminQueries'
import type { AdminAuditFilters, AdminGameFilters, AdminRoomFilters, AdminUserFilters } from '../types/admin'

export const useAdminOverview = () => useQuery(adminOverviewQueryOptions())
export const useAdminUsers = (filters: AdminUserFilters) => useQuery(adminUsersQueryOptions(filters))
export const useAdminUser = (id: number | null) => useQuery(adminUserQueryOptions(id))
export const useAdminRooms = (filters: AdminRoomFilters) => useQuery(adminRoomsQueryOptions(filters))
export const useAdminRoom = (id: number | null) => useQuery(adminRoomQueryOptions(id))
export const useAdminGames = (filters: AdminGameFilters) => useQuery(adminGamesQueryOptions(filters))
export const useAdminGame = (id: number | null) => useQuery(adminGameQueryOptions(id))
export const useAdminHands = (id: number | null, page = 0) => useQuery(adminHandsQueryOptions(id, page, 20))
export const useAdminAudit = (filters: AdminAuditFilters) => useQuery(adminAuditQueryOptions(filters))

export function useAdminMutation<TVariables>(mutationFn: (variables: TVariables) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: adminKeys.all }),
  })
}

export const adminMutations = {
  suspend: (value: { id: number; reason?: string }) => adminApi.suspendUser(value.id, value.reason),
  reactivate: (value: { id: number; reason?: string }) => adminApi.reactivateUser(value.id, value.reason),
  removePlayer: (value: { roomId: number; userId: number; reason?: string }) => adminApi.removePlayer(value.roomId, value.userId, value.reason),
  closeRoom: (value: { id: number; reason?: string }) => adminApi.closeRoom(value.id, value.reason),
  terminateGame: (value: { id: number; reason?: string }) => adminApi.terminateGame(value.id, value.reason),
}
