import { useState } from 'react'
import { getErrorMessage } from '../../../shared/api/apiError'
import { Button } from '../../../shared/ui/Button'
import { Input } from '../../../shared/ui/Input'

export function ModerationPrompt({ title, description, pending, error, onCancel, onConfirm }: {
  title: string; description: string; pending: boolean; error: unknown
  onCancel: () => void; onConfirm: (reason?: string) => void
}) {
  const [reason, setReason] = useState('')
  return <section className="admin-moderation" aria-label={title}>
    <div><h3>{title}</h3><p>{description}</p></div>
    {Boolean(error) && <div className="form-alert" role="alert">{getErrorMessage(error)}</div>}
    <label htmlFor="admin-moderation-reason">Reason <span>(optional)</span></label>
    <Input id="admin-moderation-reason" maxLength={500} value={reason} disabled={pending} onChange={(event) => setReason(event.target.value)} />
    <div className="flex flex-wrap justify-end gap-2">
      <Button type="button" variant="secondary" disabled={pending} onClick={onCancel}>Cancel</Button>
      <Button type="button" variant="danger" loading={pending} loadingLabel="Applying..." onClick={() => onConfirm(reason.trim() || undefined)}>Confirm action</Button>
    </div>
  </section>
}
