import { zodResolver } from '@hookform/resolvers/zod'
import { useForm, useWatch } from 'react-hook-form'
import { getErrorMessage } from '../../../shared/api/apiError'
import { Button } from '../../../shared/ui/Button'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { useCreateRoom } from '../hooks/useRooms'
import { createRoomSchema, type CreateRoomFormValues } from '../schemas/roomSchemas'
import type { RoomDetail } from '../types/room'

export function CreateRoomPanel({ onCreated }: { onCreated: (detail: RoomDetail) => void }) {
  const mutation = useCreateRoom()
  const { control, register, handleSubmit, formState: { errors } } = useForm<CreateRoomFormValues>({
    resolver: zodResolver(createRoomSchema),
    mode: 'onTouched',
    defaultValues: { name: '', roomType: 'PUBLIC', maxPlayers: 6, smallBlind: 5, bigBlind: 10, buyIn: 500, password: '' },
  })
  const roomType = useWatch({ control, name: 'roomType' })
  const submit = handleSubmit(async (values) => {
    if (mutation.isPending) return
    const detail = await mutation.mutateAsync({
      ...values,
      name: values.name.trim(),
      password: values.roomType === 'PRIVATE' ? values.password : null,
    })
    onCreated(detail)
  })
  return (
    <div>
      <p className="text-xs font-black uppercase tracking-[0.18em] text-accent">Host a table</p>
      <h2 className="mt-2 text-xl font-black text-text">Create a room</h2>
      <p className="mt-2 text-sm leading-6 text-secondary">Configure a waiting room using the server&apos;s supported table settings.</p>
      {mutation.isError && <div className="form-alert mt-5" role="alert">{getErrorMessage(mutation.error)}</div>}
      <form className="mt-6 space-y-4" onSubmit={submit} noValidate>
        <FormField id="roomName" label="Room name" error={errors.name?.message}>
          <Input id="roomName" placeholder="Friday night table" disabled={mutation.isPending} invalid={Boolean(errors.name)} aria-describedby={errors.name ? 'roomName-error' : undefined} {...register('name')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="roomType" label="Access" error={errors.roomType?.message}>
            <select id="roomType" className="select-control" disabled={mutation.isPending} {...register('roomType')}>
              <option value="PUBLIC">Public</option><option value="PRIVATE">Private</option>
            </select>
          </FormField>
          <FormField id="maxPlayers" label="Seats" hint="6–9" error={errors.maxPlayers?.message}>
            <Input id="maxPlayers" type="number" min={6} max={9} disabled={mutation.isPending} invalid={Boolean(errors.maxPlayers)} aria-describedby={`maxPlayers-hint${errors.maxPlayers ? ' maxPlayers-error' : ''}`} {...register('maxPlayers', { valueAsNumber: true })} />
          </FormField>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="smallBlind" label="Small blind" error={errors.smallBlind?.message}>
            <Input id="smallBlind" type="number" min={1} disabled={mutation.isPending} invalid={Boolean(errors.smallBlind)} aria-describedby={errors.smallBlind ? 'smallBlind-error' : undefined} {...register('smallBlind', { valueAsNumber: true })} />
          </FormField>
          <FormField id="bigBlind" label="Big blind" error={errors.bigBlind?.message}>
            <Input id="bigBlind" type="number" min={2} disabled={mutation.isPending} invalid={Boolean(errors.bigBlind)} aria-describedby={errors.bigBlind ? 'bigBlind-error' : undefined} {...register('bigBlind', { valueAsNumber: true })} />
          </FormField>
          <FormField id="buyIn" label="Buy-in" error={errors.buyIn?.message}>
            <Input id="buyIn" type="number" min={1} disabled={mutation.isPending} invalid={Boolean(errors.buyIn)} aria-describedby={errors.buyIn ? 'buyIn-error' : undefined} {...register('buyIn', { valueAsNumber: true })} />
          </FormField>
        </div>
        {roomType === 'PRIVATE' && <FormField id="roomPassword" label="Room password" hint="8–72 characters" error={errors.password?.message}>
          <Input id="roomPassword" type="password" autoComplete="new-password" disabled={mutation.isPending} invalid={Boolean(errors.password)} aria-describedby={`roomPassword-hint${errors.password ? ' roomPassword-error' : ''}`} {...register('password')} />
        </FormField>}
        <Button className="w-full" type="submit" loading={mutation.isPending} loadingLabel="Creating room…">Create room</Button>
      </form>
    </div>
  )
}
