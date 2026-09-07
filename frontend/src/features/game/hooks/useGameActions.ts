import { useCallback, useEffect, useState } from 'react'
import { stompSession } from '../../../shared/realtime/stompSession'
import type { GameViewState, PokerActionType } from '../types/game'

export function useGameActions(gameId: string, state: GameViewState) {
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [localError, setLocalError] = useState<string | null>(null)
  const acknowledgedActionId = state.lastAction?.clientActionId
  const acknowledgedErrorId = state.commandError?.clientActionId
  useEffect(() => {
    if (!pendingId || (acknowledgedActionId !== pendingId && acknowledgedErrorId !== pendingId)) return
    // Authoritative props acknowledge this local command; this transition must persist beyond the current render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPendingId((current) => current === pendingId ? null : current)
  }, [acknowledgedActionId, acknowledgedErrorId, pendingId])
  const act = useCallback((actionType: PokerActionType, amount?: number) => {
    if (!state.turn || pendingId) return
    const clientActionId=crypto.randomUUID(); setLocalError(null)
    const sent=stompSession.send(`/app/game/${gameId}/action`, { actionType, ...(amount === undefined ? {} : { amount }), turnId:state.turn.turnId, clientActionId })
    if(sent)setPendingId(clientActionId); else setLocalError('Connection unavailable. Your action was not sent.')
  },[gameId,pendingId,state.turn])
  return { act, pending: Boolean(pendingId), error: localError || (state.commandError ? (state.commandError.code === 'STALE_TURN' ? 'That turn has already advanced.' : 'The server rejected this action.') : null) }
}
