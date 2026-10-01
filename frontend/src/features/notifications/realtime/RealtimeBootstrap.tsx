import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useSessionStore } from '../../auth/session/sessionStore'
import { friendKeys } from '../../friends/api/friendQueries'
import type { FriendshipView } from '../../friends/types/friend'
import { stompSession } from '../../../shared/realtime/stompSession'
import { useNotificationStore } from '../hooks/notificationStore'
import { parseNotificationEvent } from './notificationEvent'
import { parseGameEvent } from '../../game/realtime/gameEvent'
import { useGameDiscoveryStore } from '../../game/hooks/gameDiscoveryStore'
import { gameKeys } from '../../game/api/gameQueries'
import { roomKeys } from '../../rooms/api/roomQueries'
import { profileKeys } from '../../profile/api/profileQueries'
import type { CurrentUser } from '../../profile/types/profile'

export function RealtimeBootstrap() {
  const queryClient = useQueryClient()
  const status = useSessionStore((state) => state.status)
  const accessToken = useSessionStore((state) => state.accessToken)
  const role = queryClient.getQueryData<CurrentUser>(profileKeys.current())?.role

  useEffect(() => {
    if (status !== 'AUTHENTICATED' || !accessToken || role !== 'PLAYER') {
      useNotificationStore.getState().clear()
      void stompSession.disconnect()
      return
    }
    const stopListening = stompSession.listen('/user/queue/notifications', (body) => {
      const event = parseNotificationEvent(body)
      if (!event) return
      useNotificationStore.getState().add(event)
      if (event.type === 'FRIEND_STATUS_CHANGED') {
        queryClient.setQueryData<FriendshipView[]>(friendKeys.list(), (friends) => friends?.map((friend) =>
          friend.otherPlayer.userId === event.payload.userId
            ? { ...friend, presenceStatus: event.payload.presenceStatus }
            : friend,
        ))
      } else {
        void queryClient.invalidateQueries({ queryKey: friendKeys.all })
      }
    })
    const stopPrivate = stompSession.listen('/user/queue/private', (body) => {
      const event = parseGameEvent(body)
      if (event) useGameDiscoveryStore.getState().discover(event.roomId, event.gameId)
    })
    const stopConnection = stompSession.listenConnection((connected) => {
      if (connected) {
        void queryClient.invalidateQueries({ queryKey: gameKeys.activeMine() })
        void queryClient.invalidateQueries({ queryKey: friendKeys.all })
        void queryClient.invalidateQueries({ queryKey: roomKeys.all })
      }
    })
    stompSession.connect(accessToken)
    return () => { stopListening(); stopPrivate(); stopConnection() }
  }, [accessToken, queryClient, role, status])

  return null
}
