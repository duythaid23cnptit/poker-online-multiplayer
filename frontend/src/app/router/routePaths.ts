export const routes = {
  root: '/',
  login: '/login',
  register: '/register',
  app: '/app',
  rooms: '/app/rooms',
  friends: '/app/friends',
  rankings: '/app/rankings',
  profile: '/app/profile',
  game: (roomId: number, gameId: string) => `/app/rooms/${roomId}/games/${gameId}`,
} as const

export function safeAppReturnPath(value: unknown): string {
  if (typeof value !== 'string') return routes.app
  return value === routes.app || value.startsWith(`${routes.app}/`) ? value : routes.app
}
