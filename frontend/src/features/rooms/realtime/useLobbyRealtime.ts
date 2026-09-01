import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { stompSession } from '../../../shared/realtime/stompSession'
import { roomKeys } from '../api/roomQueries'
import type { RoomSummary } from '../types/room'

interface LobbyEvent {
  protocolVersion: number
  type: 'ROOM_CREATED' | 'ROOM_CLOSED' | 'PLAYER_COUNT_CHANGED'
  payload: RoomSummary
}

function parseLobbyEvent(body: string): LobbyEvent | null {
  try {
    const value = JSON.parse(body) as Partial<LobbyEvent>
    if (value.protocolVersion !== 1 || !value.payload || typeof value.payload.id !== 'number') return null
    if (value.type !== 'ROOM_CREATED' && value.type !== 'ROOM_CLOSED' && value.type !== 'PLAYER_COUNT_CHANGED') return null
    return value as LobbyEvent
  } catch { return null }
}

export function useLobbyRealtime(): void {
  const queryClient = useQueryClient()
  useEffect(() => stompSession.listen('/topic/lobby', (body) => {
    const event = parseLobbyEvent(body)
    if (!event) return
    queryClient.setQueryData<RoomSummary[]>(roomKeys.list(), (rooms = []) => {
      const withoutCurrent = rooms.filter((room) => room.id !== event.payload.id)
      if (event.type === 'ROOM_CLOSED' || event.payload.status !== 'WAITING') return withoutCurrent
      return [event.payload, ...withoutCurrent]
    })
  }), [queryClient])
}
