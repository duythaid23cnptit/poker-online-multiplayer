import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiClientError, getErrorMessage } from '../../../shared/api/apiError'
import { formatNumber } from '../../../shared/lib/format'
import { Button } from '../../../shared/ui/Button'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { useJoinRoom, useRoomDetail } from '../hooks/useRooms'
import { joinRoomSchema, type JoinRoomFormValues } from '../schemas/roomSchemas'
import type { RoomDetail, RoomSummary } from '../types/room'
import { SeatPicker } from './SeatPicker'

export function JoinRoomPanel({ room, onJoined }: { room: RoomSummary; onJoined: (detail: RoomDetail) => void }) {
  const mutation = useJoinRoom()
  const seating = useRoomDetail(room.id)
  const { control, register, reset, setValue, handleSubmit, formState: { errors } } = useForm<JoinRoomFormValues>({
    resolver: zodResolver(joinRoomSchema), mode: 'onTouched',
    defaultValues: { spectator: false, seatNumber: null, password: '' },
  })
  const spectator = useWatch({ control, name: 'spectator' })
  const selectedSeat = useWatch({ control, name: 'seatNumber' })
  useEffect(() => reset({ spectator: false, seatNumber: null, password: '' }), [reset, room.id])
  const selectedAvailable = selectedSeat != null
    && seating.data?.members.every((member) => member.seatNumber !== selectedSeat)

  const submit = handleSubmit(async (values) => {
    if (mutation.isPending || (!values.spectator && !selectedAvailable)) return
    try {
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
    } catch (error) {
      if (error instanceof ApiClientError && (error.code === 'SEAT_OCCUPIED' || error.code === 'SEAT_OR_MEMBERSHIP_CONFLICT')) {
        setValue('seatNumber', null)
        await seating.refetch()
      }
    }
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
        {!spectator && (seating.isPending || !seating.isFetchedAfterMount) && <p className="text-sm text-secondary" role="status">Refreshing seat availability...</p>}
        {!spectator && seating.isError && <div className="form-alert" role="alert">Seat availability could not be refreshed. Try again.</div>}
        {!spectator && seating.data && seating.isFetchedAfterMount && <SeatPicker
          maxPlayers={seating.data.room.maxPlayers}
          members={seating.data.members}
          selectedSeat={selectedSeat}
          onSelect={(seatNumber) => setValue('seatNumber', seatNumber, { shouldValidate: true })}
          disabled={mutation.isPending}
        />}
        {errors.seatNumber && <p className="text-xs text-danger" role="alert">{errors.seatNumber.message}</p>}
        {room.passwordRequired && <FormField id="joinPassword" label="Room password" error={errors.password?.message}>
          <Input id="joinPassword" type="password" autoComplete="current-password" disabled={mutation.isPending} invalid={Boolean(errors.password)} aria-describedby={errors.password ? 'joinPassword-error' : undefined} {...register('password')} />
        </FormField>}
        <Button className="w-full" type="submit" disabled={!spectator && !selectedAvailable} loading={mutation.isPending} loadingLabel="Joining room...">{spectator ? 'Watch room' : `Join · ${formatNumber(room.buyIn)} chips`}</Button>
      </form>
    </div>
  )
}
