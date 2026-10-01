export const routes = {
  root: '/',
  login: '/login',
  register: '/register',
  app: '/app',
  rooms: '/app/rooms',
  friends: '/app/friends',
  rankings: '/app/rankings',
  statistics: '/app/statistics',
  profile: '/app/profile',
  admin: '/admin',
  adminUsers: '/admin/users',
  adminRooms: '/admin/rooms',
  adminGames: '/admin/games',
  adminAudit: '/admin/audit',
  game: (roomId: number, gameId: string) => `/app/rooms/${roomId}/games/${gameId}`,
} as const

export type ApplicationRole = 'PLAYER' | 'ADMIN'

export function applicationHome(role: ApplicationRole): string {
  return role === 'ADMIN' ? routes.admin : routes.app
}

export function safeRoleReturnPath(value: unknown, role: ApplicationRole): string {
  const home = applicationHome(role)
  if (typeof value !== 'string') return home
  return value === home || value.startsWith(`${home}/`) ? value : home
}
