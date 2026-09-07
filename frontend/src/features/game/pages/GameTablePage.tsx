import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { ApiClientError, getErrorMessage } from '../../../shared/api/apiError'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'
import { useRoomDetail } from '../../rooms/hooks/useRooms'
import { ActionBar } from '../components/ActionBar'
import { GameSessionComplete } from '../components/GameSessionComplete'
import { GameSidePanel } from '../components/GameSidePanel'
import { LeaveGameControl } from '../components/LeaveGameControl'
import { PokerTable } from '../components/PokerTable'
import { useGameRealtime } from '../hooks/useGameRealtime'
import { gameKeys } from '../api/gameQueries'
import { useGameDeparture } from '../hooks/useGameDeparture'
import { roomKeys } from '../../rooms/api/roomQueries'
import { chatKeys } from '../api/chatQueries'
import { useGameDiscoveryStore } from '../hooks/gameDiscoveryStore'

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function GameTablePage() {
  const params = useParams()
  const roomId = Number(params.roomId)
  const gameId = params.gameId || ''
  const valid = Number.isSafeInteger(roomId) && roomId > 0 && uuidPattern.test(gameId)
  if (!valid) return <main className="app-page"><ErrorState message="This game link is invalid." /></main>
  return <ValidGamePage key={gameId} roomId={roomId} gameId={gameId} />
}

function ValidGamePage({ roomId, gameId }: { roomId: number; gameId: string }) {
  const queryClient = useQueryClient()
  const profile = useCurrentProfile()
  const { state, connected, snapshotPending, snapshotError } = useGameRealtime(roomId, gameId)
  const stateMatchesRoute = state.roomId === roomId && state.gameId === gameId
  const publicState = stateMatchesRoute ? state.publicState : null
  const terminal = publicState?.sessionFinished === true
  const ownGamePlayer = publicState?.players.find((player) => player.userId === profile.data?.id)
  const gameLeaving = !terminal && ownGamePlayer?.leaving === true
  const roomRequired = publicState !== null && !terminal && !gameLeaving
  const room = useRoomDetail(roomId, roomRequired)
  const roomIdentityPending = roomRequired && (room.isPending || !room.isFetchedAfterMount)
  const accessLost = !terminal && room.error instanceof ApiClientError && (room.error.status === 403 || room.error.status === 404)
  const own = room.data?.members.find((member) => member.userId === profile.data?.id)
  const departure = useGameDeparture(roomId, gameId, gameLeaving || (!terminal && own?.state === 'LEAVING'), accessLost)
  const departingTerminal = terminal && departure.pending
  const presentationState = departingTerminal && publicState
    ? { ...state, publicState: { ...publicState, sessionFinished: false } }
    : state
  const presentationPublicState = presentationState.publicState
  const presentationRoom = room.data
  const renderTerminal = terminal && !departure.pending

  useEffect(() => {
    if (!renderTerminal) return
    void queryClient.cancelQueries({ queryKey: gameKeys.activeMine() })
    queryClient.setQueryData(gameKeys.activeMine(), null)
    useGameDiscoveryStore.getState().clear()
    void queryClient.cancelQueries({ queryKey: roomKeys.detail(roomId) })
    void queryClient.cancelQueries({ queryKey: chatKeys.history(roomId) })
    void queryClient.invalidateQueries({ queryKey: roomKeys.memberships() })
    void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
  }, [queryClient, renderTerminal, roomId])

  if (profile.isPending || snapshotPending || !stateMatchesRoute || roomIdentityPending) {
    return <main className="game-page"><LoadingState variant="dashboard" label="Preparing the poker table..." /></main>
  }
  if (snapshotError || !publicState) {
    return <main className="app-page"><ErrorState message={getErrorMessage(snapshotError)} onRetry={() => window.location.reload()} /></main>
  }
  if (profile.isError || !profile.data) {
    return <main className="app-page"><ErrorState message={getErrorMessage(profile.error)} onRetry={() => window.location.reload()} /></main>
  }
  if (!renderTerminal && !departure.pending && (room.isError || !room.data) && (!accessLost || departure.accessDenied)) {
    return <main className="app-page"><ErrorState message={getErrorMessage(room.error)} onRetry={() => window.location.reload()} /></main>
  }
  const presentationOwn = presentationRoom?.members.find((member) => member.userId === profile.data.id)
  const presentationGamePlayer = presentationPublicState?.players.find((player) => player.userId === profile.data.id)
  const spectator = presentationOwn?.seatNumber == null && presentationGamePlayer === undefined
  const participant = presentationOwn?.seatNumber != null && presentationGamePlayer !== undefined
  const authoritativeLeaving = departure.pending
  return <main className="game-page">
    {presentationRoom
      ? <header className="game-room-header"><div><Link to={routes.rooms}>Back to rooms</Link><h1>{presentationRoom.room.name}</h1><p>Game {gameId.slice(0, 8)} · Blinds {presentationRoom.room.smallBlind} / {presentationRoom.room.bigBlind}</p></div><div>{!renderTerminal && <StatusBadge tone={connected ? 'success' : 'warning'}>{connected ? 'Connected' : 'Reconnecting'}</StatusBadge>}{!renderTerminal && spectator && <StatusBadge tone="accent">Spectator</StatusBadge>}<LeaveGameControl gameId={gameId} participant={participant} terminal={renderTerminal} authoritativeLeaving={authoritativeLeaving} onDeparture={departure.acknowledge} /></div></header>
      : <header className="game-room-header"><div><Link to={routes.rooms}>Back to rooms</Link><h1>{renderTerminal ? 'Finished game' : 'Poker table'}</h1><p>Game {gameId.slice(0, 8)}</p></div></header>}
    <div className="game-layout"><section className="game-main"><PokerTable state={presentationState} room={presentationRoom} ownUserId={profile.data.id} />{renderTerminal
      ? <GameSessionComplete state={state} room={presentationRoom} />
      : <ActionBar gameId={gameId} state={presentationState} spectator={spectator} connected={connected} leaving={authoritativeLeaving} />}</section>{presentationRoom && <GameSidePanel room={presentationRoom} connected={connected} live={!terminal && !departure.finalized} game={presentationPublicState ?? undefined} />}</div>
  </main>
}
