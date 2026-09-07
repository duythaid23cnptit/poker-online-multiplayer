import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { getErrorMessage } from '../../../shared/api/apiError'
import { gameApi } from '../api/gameApi'
import type { GameDeparture } from '../types/game'

interface LeaveGameControlProps {
  gameId: string
  participant: boolean
  terminal: boolean
  authoritativeLeaving: boolean
  onDeparture: (departure: GameDeparture) => void
}

export function LeaveGameControl({
  gameId,
  participant,
  terminal,
  authoritativeLeaving,
  onDeparture,
}: LeaveGameControlProps) {
  const [confirming, setConfirming] = useState(false)
  const [departureRequested, setDepartureRequested] = useState(authoritativeLeaving)
  const dialogRef = useRef<HTMLElement>(null)
  useEffect(() => {
    if (!confirming) return
    const previous = document.activeElement as HTMLElement | null
    const dialog = dialogRef.current
    dialog?.querySelector<HTMLButtonElement>('button')?.focus()
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); setConfirming(false) }
      if (event.key !== 'Tab') return
      const controls = Array.from(dialog?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])
      if (!controls.length) { event.preventDefault(); return }
      const first = controls[0], last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', keydown)
    return () => { document.removeEventListener('keydown', keydown); previous?.focus() }
  }, [confirming])

  const leave = useMutation({
    mutationFn: () => gameApi.leave(gameId),
    onSuccess: (departure) => {
      setConfirming(false)
      setDepartureRequested(true)
      onDeparture(departure)
    },
  })

  const departurePending = departureRequested || authoritativeLeaving

  if (!participant || terminal) return null

  if (departurePending) {
    return <span className="rounded-lg border border-amber-300/25 bg-amber-300/10 px-3 py-2 text-xs font-extrabold text-amber-200">
      Leaving after this hand
    </span>
  }

  return <>
    <button
      type="button"
      className="rounded-lg border border-red-300/25 bg-red-300/8 px-3 py-2 text-xs font-extrabold text-red-200 transition hover:border-red-300/45 hover:bg-red-300/12 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-200 disabled:cursor-wait disabled:opacity-60"
      onClick={() => { leave.reset(); setConfirming(true) }}
      disabled={leave.isPending}
    >
      {leave.isPending ? 'Leaving...' : 'Leave game'}
    </button>
    {leave.isError && <span role="alert" className="basis-full text-right text-xs text-red-300">{getErrorMessage(leave.error)}</span>}
    {confirming && <div className="fixed inset-0 z-50 grid place-items-center bg-black/70 p-4" role="presentation">
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="leave-game-title"
        className="w-full max-w-md rounded-2xl border border-slate-600 bg-slate-950 p-6 shadow-2xl"
      >
        <h2 id="leave-game-title" className="text-xl font-black text-slate-50">Leave this game?</h2>
        <p className="mt-3 text-sm leading-6 text-slate-300">
          You will remain at the table until the current hand is resolved. Your remaining table chips will then return to your account, and you will leave the table.
        </p>
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" className="rounded-lg border border-slate-600 px-4 py-2 text-sm font-bold text-slate-200 hover:bg-slate-800" onClick={() => setConfirming(false)} disabled={leave.isPending}>Stay in game</button>
          <button type="button" className="rounded-lg bg-red-400 px-4 py-2 text-sm font-black text-slate-950 hover:bg-red-300 disabled:cursor-wait disabled:opacity-60" onClick={() => leave.mutate()} disabled={leave.isPending}>
            {leave.isPending ? 'Leaving...' : 'Confirm leave'}
          </button>
        </div>
      </section>
    </div>}
  </>
}
