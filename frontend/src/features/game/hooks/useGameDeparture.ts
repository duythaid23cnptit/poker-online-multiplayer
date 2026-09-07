import { useCallback, useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { roomKeys } from '../../rooms/api/roomQueries'
import { profileKeys } from '../../profile/api/profileQueries'
import { activeMineQueryOptions, gameKeys } from '../api/gameQueries'
import { chatKeys } from '../api/chatQueries'
import type { GameDeparture } from '../types/game'
import { useGameDiscoveryStore } from './gameDiscoveryStore'

// This stays mounted above the table's loading/error branches. A room 403 can
// race settlement; only a fresh activeMine response confirms final departure.
export function useGameDeparture(roomId: number, gameId: string, leaving: boolean, accessLost = false) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [acknowledged, setAcknowledged] = useState(false)
  const [immediateFinalized, setImmediateFinalized] = useState(false)
  const finishingRef = useRef(false)
  const pending = acknowledged || leaving
  const checking = pending || accessLost
  const active = useQuery({
    ...activeMineQueryOptions(),
    enabled: checking && !immediateFinalized,
    staleTime: 0,
    refetchInterval: pending && !immediateFinalized ? 1_000 : false,
  })

  const finish = useCallback(() => {
    if (finishingRef.current) return
    finishingRef.current = true
    void queryClient.cancelQueries({ queryKey: gameKeys.activeMine() })
    queryClient.setQueryData(gameKeys.activeMine(), null)
    useGameDiscoveryStore.getState().clear()
    void queryClient.cancelQueries({ queryKey: roomKeys.detail(roomId) })
    void queryClient.cancelQueries({ queryKey: chatKeys.history(roomId) })
    void queryClient.cancelQueries({ queryKey: roomKeys.memberships() })
    queryClient.removeQueries({ queryKey: roomKeys.detail(roomId) })
    queryClient.removeQueries({ queryKey: chatKeys.history(roomId) })
    queryClient.removeQueries({ queryKey: roomKeys.memberships() })
    void queryClient.invalidateQueries({ queryKey: roomKeys.memberships() })
    void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
    void queryClient.invalidateQueries({ queryKey: profileKeys.all })
    navigate(routes.rooms, { replace: true })
  }, [navigate, queryClient, roomId])

  const confirmed = checking && active.isFetchedAfterMount && active.isSuccess && !active.isFetching
    && (!active.data || active.data.gameId !== gameId)
  useEffect(() => { if (confirmed) finish() }, [confirmed, finish])

  const acknowledge = useCallback((departure: GameDeparture) => {
    setAcknowledged(true)
    if (!departure.deferred) { setImmediateFinalized(true); finish() }
  }, [finish])

  const accessDenied = accessLost && !pending && active.isFetchedAfterMount && !active.isFetching
    && (active.isError || (active.isSuccess && active.data?.gameId === gameId))

  return { pending, finalized: immediateFinalized || confirmed, acknowledge, accessDenied }
}
