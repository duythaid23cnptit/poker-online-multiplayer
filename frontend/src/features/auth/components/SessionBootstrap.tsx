import { useEffect, type PropsWithChildren } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { restoreSession } from '../session/sessionCoordinator'

export function SessionBootstrap({ children }: PropsWithChildren) {
  const queryClient = useQueryClient()
  useEffect(() => { void restoreSession(queryClient) }, [queryClient])
  return children
}
