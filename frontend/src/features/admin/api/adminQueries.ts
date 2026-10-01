import { queryOptions } from '@tanstack/react-query'
import { adminApi } from './adminApi'
import type { AdminAuditFilters, AdminGameFilters, AdminRoomFilters, AdminUserFilters } from '../types/admin'

export const adminKeys = {
  all: ['admin'] as const,
  overview: () => [...adminKeys.all, 'overview'] as const,
  users: (filters: AdminUserFilters) => [...adminKeys.all, 'users', filters] as const,
  user: (id: number) => [...adminKeys.all, 'user', id] as const,
  rooms: (filters: AdminRoomFilters) => [...adminKeys.all, 'rooms', filters] as const,
  room: (id: number) => [...adminKeys.all, 'room', id] as const,
  games: (filters: AdminGameFilters) => [...adminKeys.all, 'games', filters] as const,
  game: (id: number) => [...adminKeys.all, 'game', id] as const,
  hands: (id: number, page: number, size: number) => [...adminKeys.all, 'hands', id, page, size] as const,
  audit: (filters: AdminAuditFilters) => [...adminKeys.all, 'audit', filters] as const,
}

export const adminOverviewQueryOptions = () => queryOptions({
  queryKey: adminKeys.overview(), queryFn: ({ signal }) => adminApi.overview(signal), staleTime: 15_000,
})
export const adminUsersQueryOptions = (filters: AdminUserFilters) => queryOptions({
  queryKey: adminKeys.users(filters), queryFn: ({ signal }) => adminApi.users(filters, signal),
})
export const adminUserQueryOptions = (id: number | null) => queryOptions({
  queryKey: adminKeys.user(id ?? 0), queryFn: ({ signal }) => adminApi.user(id!, signal), enabled: id !== null,
})
export const adminRoomsQueryOptions = (filters: AdminRoomFilters) => queryOptions({
  queryKey: adminKeys.rooms(filters), queryFn: ({ signal }) => adminApi.rooms(filters, signal),
})
export const adminRoomQueryOptions = (id: number | null) => queryOptions({
  queryKey: adminKeys.room(id ?? 0), queryFn: ({ signal }) => adminApi.room(id!, signal), enabled: id !== null,
})
export const adminGamesQueryOptions = (filters: AdminGameFilters) => queryOptions({
  queryKey: adminKeys.games(filters), queryFn: ({ signal }) => adminApi.games(filters, signal),
})
export const adminGameQueryOptions = (id: number | null) => queryOptions({
  queryKey: adminKeys.game(id ?? 0), queryFn: ({ signal }) => adminApi.game(id!, signal), enabled: id !== null,
})
export const adminHandsQueryOptions = (id: number | null, page = 0, size = 20) => queryOptions({
  queryKey: adminKeys.hands(id ?? 0, page, size), queryFn: ({ signal }) => adminApi.hands(id!, page, size, signal), enabled: id !== null,
})
export const adminAuditQueryOptions = (filters: AdminAuditFilters) => queryOptions({
  queryKey: adminKeys.audit(filters), queryFn: ({ signal }) => adminApi.audit(filters, signal),
})
