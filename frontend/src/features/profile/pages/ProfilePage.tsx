import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiClientError, getErrorMessage } from '../../../shared/api/apiError'
import { Avatar } from '../../../shared/ui/Avatar'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { useCurrentProfile } from '../hooks/useCurrentProfile'
import { useUpdateProfile } from '../hooks/useUpdateProfile'
import { profileSchema, type ProfileFormValues } from '../schemas/profileSchema'

export function ProfilePage() {
  const { data: profile } = useCurrentProfile()
  const update = useUpdateProfile()
  const [saved, setSaved] = useState(false)
  const { control, register, handleSubmit, reset, setError, formState: { errors, isDirty } } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema), mode: 'onTouched',
    defaultValues: { displayName: profile?.displayName || '', avatarUrl: profile?.avatarUrl || '' },
  })
  const watchedDisplayName = useWatch({ control, name: 'displayName' })
  const watchedAvatarUrl = useWatch({ control, name: 'avatarUrl' })

  useEffect(() => {
    if (profile) reset({ displayName: profile.displayName, avatarUrl: profile.avatarUrl || '' })
  }, [profile, reset])

  const submit = handleSubmit(async (values) => {
    if (update.isPending) return
    setSaved(false)
    try {
      await update.mutateAsync({ displayName: values.displayName.trim(), avatarUrl: values.avatarUrl.trim() || null })
      setSaved(true)
    } catch (error) {
      if (error instanceof ApiClientError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'displayName' || fieldError.field === 'avatarUrl') {
            setError(fieldError.field, { message: fieldError.message })
          }
        }
      }
    }
  })

  if (!profile) return null
  const previewName = watchedDisplayName || profile.displayName
  const previewAvatar = watchedAvatarUrl || null
  return (
    <main className="app-page">
      <PageHeader title="Your profile" description="Manage the safe identity other players see at the tables." />
      <div className="mt-9 grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
        <Card className="dashboard-card h-fit p-6 text-center">
          <div className="flex justify-center"><Avatar displayName={previewName} src={previewAvatar} size="lg" /></div>
          <h2 className="mt-4 truncate text-lg font-bold text-text">{previewName}</h2>
          <p className="mt-1 truncate text-sm text-muted">@{profile.username}</p>
          {profile.email && <p className="mt-4 break-all border-t border-border pt-4 text-xs text-secondary">{profile.email}</p>}
        </Card>
        <Card className="dashboard-card p-6 sm:p-8">
          <h2 className="text-lg font-bold text-text">Profile details</h2>
          <p className="mt-2 text-sm leading-6 text-secondary">Only your display name and avatar can be changed here.</p>
          {saved && <div className="mt-5 rounded-control border border-success/30 bg-success/10 px-4 py-3 text-sm text-success" role="status">Profile updated.</div>}
          {update.isError && <div className="mt-5 rounded-control border border-danger/25 bg-danger/8 px-4 py-3 text-sm text-danger" role="alert">{getErrorMessage(update.error)}</div>}
          <form className="mt-6 space-y-5" onSubmit={submit} noValidate>
            <FormField id="displayName" label="Display name" error={errors.displayName?.message}>
              <Input id="displayName" autoComplete="nickname" invalid={Boolean(errors.displayName)} aria-describedby={errors.displayName ? 'displayName-error' : undefined} disabled={update.isPending} {...register('displayName')} />
            </FormField>
            <FormField id="avatarUrl" label="Avatar URL" hint="Optional · http(s)" error={errors.avatarUrl?.message}>
              <Input id="avatarUrl" type="url" inputMode="url" autoComplete="url" placeholder="https://example.com/avatar.jpg" invalid={Boolean(errors.avatarUrl)} aria-describedby={`avatarUrl-hint${errors.avatarUrl ? ' avatarUrl-error' : ''}`} disabled={update.isPending} {...register('avatarUrl')} />
            </FormField>
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <Button type="button" variant="secondary" disabled={!isDirty || update.isPending} onClick={() => { reset({ displayName: profile.displayName, avatarUrl: profile.avatarUrl || '' }); setSaved(false) }}>Reset</Button>
              <Button type="submit" disabled={!isDirty} loading={update.isPending} loadingLabel="Saving…">Save changes</Button>
            </div>
          </form>
        </Card>
      </div>
    </main>
  )
}
