import { ApiClientError, getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { routes } from '../../../app/router/routePaths'
import { useCurrentProfile } from '../../profile/hooks/useCurrentProfile'
import { gameApi } from '../../game/api/gameApi'
import { useGameDiscoveryStore } from '../../game/hooks/gameDiscoveryStore'
import { stompSession } from '../../../shared/realtime/stompSession'
import { parseRoomEvent, parseRoomGameStartedEvent } from '../realtime/roomEvent'
import { roomKeys } from '../api/roomQueries'
import { cacheRoomDetail } from '../api/roomCache'
import { useJoinRoom, useLeaveRoom, useRoomDetail } from '../hooks/useRooms'
import { joinRoomSchema, type JoinRoomFormValues } from '../schemas/roomSchemas'
import type { RoomDetail } from '../types/room'
import { SeatPicker } from './SeatPicker'

export function JoinedRoomPanel({ detail, onLeft }: { detail: RoomDetail; onLeft: () => void }) {
  const leave = useLeaveRoom()
  const join = useJoinRoom()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const profile = useCurrentProfile()
  const { data: currentRoomDetail, refetch: refetchRoomDetail } = useRoomDetail(detail.room.id, false)
  const snapshot = currentRoomDetail ?? detail
  const [takingSeat, setTakingSeat] = useState(false)
  const start = useMutation({
    mutationFn: () => gameApi.start(snapshot.room.id),
    onSuccess: (game) => {
      cacheRoomDetail(queryClient, snapshot)
      useGameDiscoveryStore.getState().discover(game.roomId, game.gameId)
      navigate(routes.game(game.roomId, game.gameId))
    },
  })
  const { control, reset, setValue, handleSubmit, formState: { errors } } = useForm<JoinRoomFormValues>({
    resolver: zodResolver(joinRoomSchema),
    mode: 'onTouched',
    defaultValues: { spectator: false, seatNumber: 1, password: '' },
  })
  const selectedSeat = useWatch({ control, name: 'seatNumber' })
  const discoveredRoomId = useGameDiscoveryStore((state) => state.roomId)
  const gameId = useGameDiscoveryStore((state) => state.gameId)
  useEffect(() => {
    cacheRoomDetail(queryClient, detail)
  }, [detail, queryClient])
  useEffect(() => {
    let active = true
    const enterGame = (roomId: number, authoritativeGameId: string) => {
      useGameDiscoveryStore.getState().discover(roomId, authoritativeGameId)
      navigate(routes.game(roomId, authoritativeGameId))
    }
    const stop = stompSession.listen(`/topic/room/${detail.room.id}`, (body) => {
      const discovery = parseRoomGameStartedEvent(body)
      if (discovery && discovery.scope.roomId === detail.room.id) {
        enterGame(detail.room.id, discovery.payload.gameId)
        return
      }
      const event = parseRoomEvent(body)
      if (!event || event.scope.roomId !== detail.room.id) return
      const payload = event.payload as Partial<RoomDetail> | null
      if (payload?.room && Array.isArray(payload.members)) cacheRoomDetail(queryClient, payload as RoomDetail)
      else {
        void queryClient.invalidateQueries({ queryKey: roomKeys.detail(detail.room.id) })
        void queryClient.invalidateQueries({ queryKey: roomKeys.list() })
      }
    })
    const controller = new AbortController()
    const reconcileActiveGame = () => void gameApi.activeByRoom(detail.room.id, controller.signal).then((game) => {
      if (active && game.roomId === detail.room.id) enterGame(game.roomId, game.gameId)
    }).catch(() => { /* A waiting room normally has no active game. */ })
    reconcileActiveGame()
    let hasConnected = false
    let disconnectedAfterConnect = false
    const stopConnection = stompSession.listenConnection((connected) => {
      if (!connected) {
        if (hasConnected) disconnectedAfterConnect = true
        return
      }
      if (!hasConnected) {
        hasConnected = true
        return
      }
      if (!disconnectedAfterConnect) return
      disconnectedAfterConnect = false
      void refetchRoomDetail()
      reconcileActiveGame()
    })
    return () => { active = false; controller.abort(); stop(); stopConnection() }
  }, [detail.room.id, navigate, queryClient, refetchRoomDetail])
  const ownMember = snapshot.members.find((member) => member.userId === profile.data?.id)
  const canReady = ownMember?.seatNumber != null && (ownMember.state === 'NOT_READY' || ownMember.state === 'READY')
  const occupiedSeats = new Set(snapshot.members.flatMap((member) => member.seatNumber == null ? [] : [member.seatNumber]))
  const availableSeats = Array.from({ length: snapshot.room.maxPlayers }, (_, index) => index + 1)
    .filter((seatNumber) => !occupiedSeats.has(seatNumber))
  const canTakeSeat = snapshot.room.status === 'WAITING' && ownMember?.state === 'SPECTATING'
    && ownMember.seatNumber == null && availableSeats.length > 0
  const isHost = profile.data?.id === snapshot.room.ownerUserId
  const seatedMembers = snapshot.members.filter((member) => member.seatNumber != null)
  const startDisabledReason = seatedMembers.length < 2
    ? 'At least 2 seated players are required.'
    : seatedMembers.some((member) => member.state !== 'READY')
      ? 'Every seated player must be ready.'
      : null
  const toggleReady = () => stompSession.send(`/app/room/${snapshot.room.id}/ready`, { clientCommandId: crypto.randomUUID(), ready: ownMember?.state !== 'READY' })
  const beginTakingSeat = async () => {
    join.reset()
    const refreshed = await refetchRoomDetail()
    if (!refreshed.data) return
    const occupied = new Set(refreshed.data.members.flatMap((member) => member.seatNumber == null ? [] : [member.seatNumber]))
    const firstAvailable = Array.from({ length: refreshed.data.room.maxPlayers }, (_, index) => index + 1)
      .find((seatNumber) => !occupied.has(seatNumber)) ?? null
    reset({ spectator: false, seatNumber: firstAvailable, password: '' })
    setTakingSeat(true)
  }
  const takeSeat = handleSubmit(async (values) => {
    if (join.isPending || values.seatNumber == null) return
    try {
      await join.mutateAsync({
        roomId: snapshot.room.id,
        request: {
          spectator: false,
          seatNumber: values.seatNumber,
          buyInAmount: snapshot.room.buyIn,
          password: null,
        },
      })
      setTakingSeat(false)
    } catch (error) {
      if (error instanceof ApiClientError && (error.code === 'SEAT_OCCUPIED' || error.code === 'SEAT_OR_MEMBERSHIP_CONFLICT')) {
        setValue('seatNumber', null)
        await refetchRoomDetail()
      }
    }
  })
  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><p className="text-xs font-black uppercase tracking-[0.18em] text-success">Active membership</p><h2 className="mt-2 text-xl font-black text-text">{snapshot.room.name}</h2></div>
        <StatusBadge tone="success">{ownMember?.seatNumber == null ? 'Spectating' : ownMember.state === 'READY' ? 'Ready' : 'Joined'}</StatusBadge>
      </div>
      <p className="mt-3 text-sm leading-6 text-secondary">{ownMember?.state === 'SPECTATING' ? 'Keep watching, or take an available seat while the room is waiting.' : 'Seated players can mark themselves ready. The host starts the game when every seated player is ready.'}</p>
      <ul className="mt-5 space-y-2">
        {snapshot.members.map((member) => <li key={member.userId} className="flex items-center justify-between gap-3 rounded-control border border-border/70 bg-surface-elevated/45 px-3 py-2.5">
          <span className="min-w-0"><strong className="flex items-center gap-2 truncate text-sm text-text">{member.username}{member.userId === snapshot.room.ownerUserId && <span className="rounded-full border border-accent/25 bg-accent/10 px-2 py-0.5 text-[0.62rem] uppercase tracking-[0.08em] text-accent">Host</span>}</strong><small className="text-xs text-muted">{member.seatNumber ? `Seat ${member.seatNumber}` : 'Spectator'} · {member.state}</small></span>
          <span className="text-xs font-bold text-accent">{formatNumber(member.tableChips)} chips</span>
        </li>)}
      </ul>
      {leave.isError && <div className="form-alert mt-4" role="alert">{getErrorMessage(leave.error)}</div>}
      {join.isError && <div className="form-alert mt-4" role="alert">{getErrorMessage(join.error)}</div>}
      {start.isError && <div className="form-alert mt-4" role="alert">{getErrorMessage(start.error)}</div>}
      {canTakeSeat && !takingSeat && <Button type="button" className="mt-5 w-full" onClick={beginTakingSeat}>Take a seat</Button>}
      {canTakeSeat && takingSeat && <form className="mt-5 space-y-4 rounded-control border border-border bg-surface-elevated/45 p-4" onSubmit={takeSeat} noValidate>
        <div><p className="text-sm font-black text-text">Choose your seat</p><p className="mt-1 text-xs text-secondary">The configured buy-in of <strong className="text-accent">{formatNumber(snapshot.room.buyIn)} chips</strong> will be transferred from your account.</p></div>
        <SeatPicker maxPlayers={snapshot.room.maxPlayers} members={snapshot.members} selectedSeat={selectedSeat}
          currentUserId={profile.data?.id} disabled={join.isPending}
          onSelect={(seatNumber) => setValue('seatNumber', seatNumber, { shouldValidate: true })} />
        {errors.seatNumber && <p className="text-xs text-danger" role="alert">{errors.seatNumber.message}</p>}
        <div className="grid gap-2 sm:grid-cols-2">
          <Button type="button" variant="secondary" disabled={join.isPending} onClick={() => setTakingSeat(false)}>Keep watching</Button>
          <Button type="submit" disabled={selectedSeat == null || !availableSeats.includes(selectedSeat)} loading={join.isPending} loadingLabel="Taking seat…">Confirm · {formatNumber(snapshot.room.buyIn)} chips</Button>
        </div>
      </form>}
      {canReady && <Button type="button" className="mt-5 w-full" onClick={toggleReady}>{ownMember?.state === 'READY' ? 'Not ready' : 'Ready to play'}</Button>}
      {isHost && snapshot.room.status === 'WAITING' && <div className="mt-3">
        <Button type="button" className="w-full" disabled={startDisabledReason !== null} loading={start.isPending} loadingLabel="Starting game…" onClick={() => start.mutate()}>Start game</Button>
        {startDisabledReason && <p className="mt-2 text-center text-xs text-muted">{startDisabledReason}</p>}
      </div>}
      {gameId && discoveredRoomId === snapshot.room.id && <Link className="action-link-primary mt-3 w-full" to={routes.game(snapshot.room.id, gameId)}>Enter poker table</Link>}
      <Button type="button" variant="secondary" className="mt-3 w-full" disabled={join.isPending} loading={leave.isPending} loadingLabel="Leaving room…" onClick={() => leave.mutate(snapshot.room.id, { onSuccess: onLeft })}>Leave waiting room</Button>
    </div>
  )
}
