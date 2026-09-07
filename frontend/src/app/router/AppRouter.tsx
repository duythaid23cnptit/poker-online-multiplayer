import { lazy, Suspense } from 'react'
import { LoadingState } from '../../shared/ui/LoadingState'
import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router-dom'
import { AppShell } from '../layouts/AppShell'
import { AuthLayout } from '../../features/auth/components/AuthLayout'
import { PublicOnlyRoute } from '../../features/auth/components/PublicOnlyRoute'
import { RequireAuth } from '../../features/auth/components/RequireAuth'
import { RootRedirect } from '../../features/auth/components/RootRedirect'
import { LoginPage } from '../../features/auth/pages/LoginPage'
import { RegisterPage } from '../../features/auth/pages/RegisterPage'
import { RoomsPage } from '../../features/rooms/pages/RoomsPage'
import { NotFoundPage } from '../../shared/ui/NotFoundPage'
import { routes } from './routePaths'

const ProfilePage = lazy(() => import('../../features/profile/pages/ProfilePage').then((module) => ({ default: module.ProfilePage })))
const FriendsPage = lazy(() => import('../../features/friends/pages/FriendsPage').then((module) => ({ default: module.FriendsPage })))
const LobbyPage = lazy(() => import('../../features/lobby/pages/LobbyPage').then((module) => ({ default: module.LobbyPage })))
const RankingsPage = lazy(() => import('../../features/rankings/pages/RankingsPage').then((module) => ({ default: module.RankingsPage })))
const GameTablePage = lazy(() => import('../../features/game/pages/GameTablePage').then((module) => ({ default: module.GameTablePage })))

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
        { path: 'rooms/:roomId/games/:gameId', element: <GameTablePage /> },
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
  return <Suspense fallback={<main className="app-page"><LoadingState variant="dashboard" label="Loading page…" /></main>}><RouterProvider router={router} /></Suspense>
}
