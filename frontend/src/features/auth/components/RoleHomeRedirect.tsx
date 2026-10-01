import { Navigate } from 'react-router-dom'
import { applicationHome } from '../../../app/router/routePaths'
import { getErrorMessage } from '../../../shared/api/apiError'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'

export function RoleHomeRedirect() {
  const profile = useCurrentProfile()
  if (profile.isPending) return <LoadingState label="Opening your workspace..." />
  if (profile.isError || !profile.data) {
    return <ErrorState message={getErrorMessage(profile.error)} onRetry={() => void profile.refetch()} />
  }
  return <Navigate to={applicationHome(profile.data.role)} replace />
}
