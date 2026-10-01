import { useEffect, useReducer, useState } from 'react'
import { stompSession } from '../../../shared/realtime/stompSession'
import { gameApi } from '../api/gameApi'
import type { GameRealtimeEvent, GameSnapshot, GameViewState } from '../types/game'
import { parseGameEvent } from '../realtime/gameEvent'
import { hydrateGameSnapshot, initialGameState, reduceGameEvent } from '../realtime/gameEventReducer'

type ReconciliationAction = { type: 'event'; event: GameRealtimeEvent } | { type: 'snapshot'; snapshot: GameSnapshot } | { type: 'reset'; roomId: number; gameId: string }
function reconcile(state: GameViewState, action: ReconciliationAction) {
  if (action.type === 'reset') return initialGameState(action.roomId, action.gameId)
  return action.type === 'snapshot' ? hydrateGameSnapshot(state, action.snapshot) : reduceGameEvent(state, action.event)
}

export function useGameRealtime(roomId: number, gameId: string) {
  const [state, dispatch] = useReducer(reconcile, initialGameState(roomId, gameId))
  const [connected, setConnected] = useState(false)
  const [snapshotPending, setSnapshotPending] = useState(true)
  const [snapshotError, setSnapshotError] = useState<unknown>(null)
  useEffect(() => {
    let active = true
    let hydrating = true
    let hydrated = false
    let hydrationInFlight = false
    const buffered: GameRealtimeEvent[] = []
    const receive = (body:string) => {
      const event=parseGameEvent(body)
      if (!event) return
      if (hydrating) buffered.push(event)
      else dispatch({ type: 'event', event })
    }
    const stopPublic=stompSession.listen(`/topic/game/${gameId}`,receive)
    const stopPrivate=stompSession.listen('/user/queue/private',receive)
    const controller = new AbortController()
    queueMicrotask(() => {
      if (!active) return
      dispatch({ type: 'reset', roomId, gameId }); setSnapshotPending(true); setSnapshotError(null)
    })
    const hydrate = (initial: boolean) => {
      if (hydrationInFlight) return
      hydrationInFlight = true
      hydrating = true
      setSnapshotError(null)
      void gameApi.snapshot(gameId, controller.signal).then((snapshot) => {
        if (active) dispatch({ type: 'snapshot', snapshot })
      }).catch((error) => { if (active && !controller.signal.aborted) setSnapshotError(error) }).finally(() => {
        if (!active) return
        hydrationInFlight = false
        hydrated = true
        hydrating = false
        buffered.splice(0).forEach((event) => dispatch({ type: 'event', event }))
        if (initial) setSnapshotPending(false)
      })
    }
    const stopConnection=stompSession.listenConnection((isConnected) => {
      setConnected(isConnected)
      if (isConnected && hydrated) hydrate(false)
    })
    hydrate(true)
    return ()=>{active=false;controller.abort();stopPublic();stopPrivate();stopConnection()}
  }, [gameId, roomId])
  return { state, connected, snapshotPending, snapshotError }
}
