import { Navigate, Outlet } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { getErrorMessage } from '../../../shared/api/apiError'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'

export function RequirePlayer() {
  const profile = useCurrentProfile()
  if (profile.isPending) return <LoadingState label="Checking player access..." />
  if (profile.isError) return <ErrorState message={getErrorMessage(profile.error)} onRetry={() => void profile.refetch()} />
  if (profile.data?.role !== 'PLAYER') return <Navigate to={routes.admin} replace />
  return <Outlet />
}
