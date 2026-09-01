import { zodResolver } from '@hookform/resolvers/zod'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { getErrorMessage } from '../../../shared/api/apiError'
import { Avatar } from '../../../shared/ui/Avatar'
import { Button } from '../../../shared/ui/Button'
import { Card } from '../../../shared/ui/Card'
import { EmptyState } from '../../../shared/ui/EmptyState'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { FormField } from '../../../shared/ui/FormField'
import { Input } from '../../../shared/ui/Input'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { StatusBadge } from '../../../shared/ui/StatusBadge'
import { NotificationList } from '../../notifications/components/NotificationList'
import { useAcceptFriendRequest, useFriendRequests, useFriends, useRejectFriendRequest, useRemoveFriend, useSendFriendRequest } from '../hooks/useFriends'
import { sendFriendRequestSchema, type SendFriendRequestFormValues } from '../schemas/friendSchema'
import type { FriendshipView } from '../types/friend'

function Person({ friendship, actions }: { friendship: FriendshipView; actions?: ReactNode }) {
  const person = friendship.otherPlayer
  const tone = friendship.presenceStatus === 'ONLINE' ? 'success' : friendship.presenceStatus === 'IN_GAME' ? 'warning' : 'muted'
  return <li className="friend-row">
    <Avatar displayName={person.displayName} src={person.avatarUrl} size="sm" />
    <div className="min-w-0 flex-1"><strong className="block truncate text-sm text-text">{person.displayName}</strong><span className="text-xs text-muted">Player #{person.userId}</span></div>
    {friendship.presenceStatus && <StatusBadge tone={tone}>{friendship.presenceStatus.replace('_', ' ')}</StatusBadge>}
    {actions && <div className="friend-actions flex shrink-0 gap-2">{actions}</div>}
  </li>
}

export function FriendsPage() {
  const friends = useFriends()
  const incoming = useFriendRequests('incoming')
  const outgoing = useFriendRequests('outgoing')
  const send = useSendFriendRequest()
  const accept = useAcceptFriendRequest()
  const reject = useRejectFriendRequest()
  const remove = useRemoveFriend()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<SendFriendRequestFormValues>({ resolver: zodResolver(sendFriendRequestSchema) })
  const queries = [friends, incoming, outgoing]
  const error = queries.find((query) => query.isError)?.error
  const submit = handleSubmit(async ({ recipientUserId }) => { await send.mutateAsync(recipientUserId); reset() })
  return <main className="app-page">
    <PageHeader title="Your poker circle" description="Manage requests and see the latest server-authoritative presence for accepted friends." />
    {queries.some((query) => query.isPending) && <LoadingState variant="avatars" label="Loading your social circle…" />}
    {error && <ErrorState message={getErrorMessage(error)} onRetry={() => queries.forEach((query) => void query.refetch())} />}
    {!queries.some((query) => query.isPending) && !error && <div className="mt-8 grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(20rem,0.65fr)]">
      <div className="space-y-5">
        <Card className="dashboard-card p-5 sm:p-6">
          <div className="section-heading"><div><p>Connections</p><h2>Friends ({friends.data?.length || 0})</h2></div></div>
          {!friends.data?.length ? <div className="mt-3"><EmptyState variant="compact" motif="suit" title="No accepted friends yet" description="Use the player ID panel to send your first request." /></div> : <ul className="mt-3 divide-y divide-border/70">{friends.data.map((item) => <Person key={item.requestId} friendship={item} actions={<Button variant="ghost" type="button" disabled={remove.isPending} onClick={() => remove.mutate(item.otherPlayer.userId)}>Remove</Button>} />)}</ul>}
        </Card>
        <div className="grid gap-5 lg:grid-cols-2">
          <Card className="dashboard-card p-5 sm:p-6"><div className="section-heading"><div><p>Action needed</p><h2>Incoming requests</h2></div></div>
            {!incoming.data?.length ? <div className="mt-3"><EmptyState variant="inline" motif="chip" title="No incoming requests" description="New requests will appear here." /></div> : <ul className="mt-3 divide-y divide-border/70">{incoming.data.map((item) => <Person key={item.requestId} friendship={item} actions={<><Button type="button" disabled={accept.isPending} onClick={() => accept.mutate(item.requestId)}>Accept</Button><Button variant="secondary" type="button" disabled={reject.isPending} onClick={() => reject.mutate(item.requestId)}>Reject</Button></>} />)}</ul>}
          </Card>
          <Card className="dashboard-card p-5 sm:p-6"><div className="section-heading"><div><p>Awaiting response</p><h2>Outgoing requests</h2></div></div>
            {!outgoing.data?.length ? <div className="mt-3"><EmptyState variant="inline" motif="chip" title="No outgoing requests" description="Requests you send will appear here." /></div> : <ul className="mt-3 divide-y divide-border/70">{outgoing.data.map((item) => <Person key={item.requestId} friendship={item} />)}</ul>}
          </Card>
        </div>
      </div>
      <aside className="space-y-5">
        <Card className="dashboard-card p-5 sm:p-6"><p className="text-xs font-black uppercase tracking-[0.18em] text-accent">Add a player</p><h2 className="mt-2 text-xl font-black text-text">Send a request</h2><p className="mt-2 text-sm leading-6 text-secondary">Player search is not available, so enter a player ID you already know.</p>
          {send.isError && <div className="form-alert mt-4" role="alert">{getErrorMessage(send.error)}</div>}
          {send.isSuccess && <div className="form-success mt-4" role="status">Friend request updated.</div>}
          <form className="mt-5 space-y-4" onSubmit={submit}><FormField id="recipientUserId" label="Player ID" error={errors.recipientUserId?.message}><Input id="recipientUserId" type="number" min={1} placeholder="e.g. 42" invalid={Boolean(errors.recipientUserId)} {...register('recipientUserId', { valueAsNumber: true })} /></FormField><Button className="w-full" type="submit" loading={send.isPending} loadingLabel="Sending…">Send friend request</Button></form>
        </Card>
        <Card className="dashboard-card p-5 sm:p-6"><div className="section-heading"><div><p>This session</p><h2>Live notifications</h2></div></div><div className="mt-2"><NotificationList /></div></Card>
      </aside>
    </div>}
  </main>
}
