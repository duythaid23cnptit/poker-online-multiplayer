import { apiClient } from '../../../shared/api/httpClient'
import type {
  AdminAuditFilters, AdminAuditItem, AdminGameDetail, AdminGameFilters, AdminGameItem,
  AdminHandItem, AdminMutationResponse, AdminOverview, AdminPage, AdminRoomDetail,
  AdminRoomFilters, AdminRoomItem, AdminUserDetail, AdminUserFilters, AdminUserItem,
} from '../types/admin'

type QueryValue = string | number | undefined

function queryPath(path: string, values: object) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(values as Record<string, QueryValue>)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  const suffix = query.toString()
  return suffix ? `${path}?${suffix}` : path
}

function mutation(path: string, reason?: string) {
  const normalized = reason?.trim()
  return apiClient.request<AdminMutationResponse>(path, {
    method: 'POST', body: normalized ? { reason: normalized } : undefined, authenticated: true,
  })
}

export const adminApi = {
  overview(signal?: AbortSignal) {
    return apiClient.request<AdminOverview>('/admin/overview', { authenticated: true, signal })
  },
  users(filters: AdminUserFilters, signal?: AbortSignal) {
    return apiClient.request<AdminPage<AdminUserItem>>(queryPath('/admin/users', filters), { authenticated: true, signal })
  },
  user(id: number, signal?: AbortSignal) {
    return apiClient.request<AdminUserDetail>(`/admin/users/${id}`, { authenticated: true, signal })
  },
  suspendUser(id: number, reason?: string) {
    return mutation(`/admin/users/${id}/suspend`, reason)
  },
  reactivateUser(id: number, reason?: string) {
    return mutation(`/admin/users/${id}/reactivate`, reason)
  },
  rooms(filters: AdminRoomFilters, signal?: AbortSignal) {
    return apiClient.request<AdminPage<AdminRoomItem>>(queryPath('/admin/rooms', filters), { authenticated: true, signal })
  },
  room(id: number, signal?: AbortSignal) {
    return apiClient.request<AdminRoomDetail>(`/admin/rooms/${id}`, { authenticated: true, signal })
  },
  removePlayer(roomId: number, userId: number, reason?: string) {
    return mutation(`/admin/rooms/${roomId}/players/${userId}/remove`, reason)
  },
  closeRoom(id: number, reason?: string) {
    return mutation(`/admin/rooms/${id}/close`, reason)
  },
  games(filters: AdminGameFilters, signal?: AbortSignal) {
    return apiClient.request<AdminPage<AdminGameItem>>(queryPath('/admin/games', filters), { authenticated: true, signal })
  },
  game(id: number, signal?: AbortSignal) {
    return apiClient.request<AdminGameDetail>(`/admin/games/${id}`, { authenticated: true, signal })
  },
  hands(id: number, page: number, size: number, signal?: AbortSignal) {
    return apiClient.request<AdminPage<AdminHandItem>>(queryPath(`/admin/games/${id}/hands`, { page, size }), { authenticated: true, signal })
  },
  terminateGame(id: number, reason?: string) {
    return mutation(`/admin/games/${id}/terminate`, reason)
  },
  audit(filters: AdminAuditFilters, signal?: AbortSignal) {
    return apiClient.request<AdminPage<AdminAuditItem>>(queryPath('/admin/audit-log', filters), { authenticated: true, signal })
  },
}
