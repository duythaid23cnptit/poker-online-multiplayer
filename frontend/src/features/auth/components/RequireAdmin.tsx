import { Navigate, Outlet } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { getErrorMessage } from '../../../shared/api/apiError'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'

export function RequireAdmin() {
  const profile = useCurrentProfile()
  if (profile.isPending) return <LoadingState label="Checking administrator access..." />
  if (profile.isError) return <ErrorState message={getErrorMessage(profile.error)} onRetry={() => void profile.refetch()} />
  if (profile.data?.role !== 'ADMIN') return <Navigate to={routes.app} replace />
  return <Outlet />
}
