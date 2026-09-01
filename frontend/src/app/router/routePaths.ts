export const routes = {
  root: '/',
  login: '/login',
  register: '/register',
  app: '/app',
  profile: '/app/profile',
} as const

export function safeAppReturnPath(value: unknown): string {
  if (typeof value !== 'string') return routes.app
  return value === routes.app || value.startsWith(`${routes.app}/`) ? value : routes.app
}
