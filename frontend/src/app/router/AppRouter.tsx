import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router-dom'
import { AppShell } from '../layouts/AppShell'
import { ProfilePage } from '../../features/profile/pages/ProfilePage'
import { FriendsPage } from '../../features/friends/pages/FriendsPage'
import { LobbyPage } from '../../features/lobby/pages/LobbyPage'
import { RankingsPage } from '../../features/rankings/pages/RankingsPage'
import { RoomsPage } from '../../features/rooms/pages/RoomsPage'
import { AuthLayout } from '../../features/auth/components/AuthLayout'
import { PublicOnlyRoute } from '../../features/auth/components/PublicOnlyRoute'
import { RequireAuth } from '../../features/auth/components/RequireAuth'
import { RootRedirect } from '../../features/auth/components/RootRedirect'
import { LoginPage } from '../../features/auth/pages/LoginPage'
import { RegisterPage } from '../../features/auth/pages/RegisterPage'
import { NotFoundPage } from '../../shared/ui/NotFoundPage'
import { routes } from './routePaths'

const appRoutes: RouteObject[] = [
  { path: routes.root, element: <RootRedirect /> },
  {
    element: <PublicOnlyRoute />, children: [{
      element: <AuthLayout />, children: [
        { path: routes.login, element: <LoginPage /> },
        { path: routes.register, element: <RegisterPage /> },
      ],
    }],
  },
  {
    element: <RequireAuth />, children: [{
      path: routes.app, element: <AppShell />, children: [
        { index: true, element: <LobbyPage /> },
        { path: 'rooms', element: <RoomsPage /> },
        { path: 'friends', element: <FriendsPage /> },
        { path: 'rankings', element: <RankingsPage /> },
        { path: 'profile', element: <ProfilePage /> },
        { path: '*', element: <NotFoundPage /> },
      ],
    }],
  },
  { path: '*', element: <NotFoundPage /> },
]

const router = createBrowserRouter(appRoutes)

export function AppRouter() {
  return <RouterProvider router={router} />
}
