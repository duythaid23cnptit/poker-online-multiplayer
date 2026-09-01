import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useJoinRoom } from '../hooks/useRooms'
import { joinRoomSchema, type JoinRoomFormValues } from '../schemas/roomSchemas'
import type { RoomDetail, RoomSummary } from '../types/room'

export function JoinRoomPanel({ room, onJoined }: { room: RoomSummary; onJoined: (detail: RoomDetail) => void }) {
  const mutation = useJoinRoom()
  const { control, register, reset, handleSubmit, formState: { errors } } = useForm<JoinRoomFormValues>({
    resolver: zodResolver(joinRoomSchema), mode: 'onTouched',
    defaultValues: { spectator: false, seatNumber: 1, password: '' },
  })
  const spectator = useWatch({ control, name: 'spectator' })
  useEffect(() => reset({ spectator: false, seatNumber: 1, password: '' }), [reset, room.id])
  const submit = handleSubmit(async (values) => {
    if (mutation.isPending) return
    const detail = await mutation.mutateAsync({
      roomId: room.id,
      request: {
        spectator: values.spectator,
        seatNumber: values.spectator ? null : values.seatNumber,
        buyInAmount: values.spectator ? null : room.buyIn,
        password: room.passwordRequired ? values.password : null,
      },
    })
    onJoined(detail)
  })
  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><p className="text-xs font-black uppercase tracking-[0.18em] text-accent">Join table</p><h2 className="mt-2 text-xl font-black text-text">{room.name}</h2></div>
        <StatusBadge tone={room.passwordRequired ? 'warning' : 'success'}>{room.roomType}</StatusBadge>
      </div>
      <dl className="mt-5 grid grid-cols-3 gap-3">
        <div className="metric-tile"><dt>Blinds</dt><dd>{formatNumber(room.smallBlind)} / {formatNumber(room.bigBlind)}</dd></div>
        <div className="metric-tile"><dt>Players</dt><dd>{room.seatedPlayers} / {room.maxPlayers}</dd></div>
        <div className="metric-tile"><dt>Buy-in</dt><dd>{formatNumber(room.buyIn)}</dd></div>
      </dl>
      {mutation.isError && <div className="form-alert mt-5" role="alert">{getErrorMessage(mutation.error)}</div>}
      <form className="mt-6 space-y-4" onSubmit={submit} noValidate>
        <label className="flex cursor-pointer items-center gap-3 rounded-control border border-border bg-surface-elevated/60 p-3 text-sm text-secondary">
          <input type="checkbox" className="size-4 accent-accent" disabled={mutation.isPending} {...register('spectator')} />
          Join as a spectator without taking a seat or transferring chips
        </label>
        {!spectator && <FormField id="seatNumber" label="Seat number" hint={`1–${room.maxPlayers}`} error={errors.seatNumber?.message}>
          <Input id="seatNumber" type="number" min={1} max={room.maxPlayers} disabled={mutation.isPending} invalid={Boolean(errors.seatNumber)} aria-describedby={`seatNumber-hint${errors.seatNumber ? ' seatNumber-error' : ''}`} {...register('seatNumber', { valueAsNumber: true })} />
        </FormField>}
        {room.passwordRequired && <FormField id="joinPassword" label="Room password" error={errors.password?.message}>
          <Input id="joinPassword" type="password" autoComplete="current-password" disabled={mutation.isPending} invalid={Boolean(errors.password)} aria-describedby={errors.password ? 'joinPassword-error' : undefined} {...register('password')} />
        </FormField>}
        <Button className="w-full" type="submit" loading={mutation.isPending} loadingLabel="Joining room…">{spectator ? 'Watch room' : 'Join room'}</Button>
      </form>
    </div>
  )
}
