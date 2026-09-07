import type { RoomPlayer } from '../types/room'

interface SeatPickerProps {
  maxPlayers: number
  members: RoomPlayer[]
  selectedSeat: number | null
  currentUserId?: number
  disabled?: boolean
  onSelect: (seatNumber: number) => void
}

export function SeatPicker({ maxPlayers, members, selectedSeat, currentUserId, disabled, onSelect }: SeatPickerProps) {
  const occupants = new Map(members.flatMap((member) =>
    member.seatNumber == null ? [] : [[member.seatNumber, member] as const]))

  return (
    <fieldset>
      <legend className="text-sm font-bold text-text">Choose a seat</legend>
      <p className="mt-1 text-xs text-muted">Live room occupancy. Select an available seat to continue.</p>
      <div className="mt-3 grid gap-2 sm:grid-cols-2" aria-label="Room seats">
        {Array.from({ length: maxPlayers }, (_, index) => index + 1).map((seatNumber) => {
          const occupant = occupants.get(seatNumber)
          const selected = !occupant && selectedSeat === seatNumber
          const isCurrentUser = occupant?.userId === currentUserId
          return (
            <button
              key={seatNumber}
              type="button"
              disabled={disabled || Boolean(occupant)}
              aria-pressed={selected}
              onClick={() => onSelect(seatNumber)}
              className={`flex min-h-16 items-center justify-between gap-3 rounded-control border px-3 py-2.5 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
                occupant
                  ? 'cursor-not-allowed border-border/60 bg-surface-elevated/35 opacity-65'
                  : selected
                    ? 'border-accent bg-accent/10 shadow-[0_0_0_1px_rgba(45,212,191,0.2)]'
                    : 'border-border bg-surface-elevated/60 hover:border-accent/55 hover:bg-surface-hover'
              }`}
            >
              <span className="min-w-0">
                <strong className="block text-sm text-text">Seat {seatNumber}</strong>
                <span className="block truncate text-xs text-secondary">
                  {occupant ? occupant.username || `Player #${occupant.userId}` : 'Available'}
                  {isCurrentUser ? ' · You' : ''}
                </span>
              </span>
              <span className={`text-[0.65rem] font-black uppercase tracking-[0.1em] ${occupant ? 'text-muted' : 'text-accent'}`}>
                {occupant ? 'Occupied' : selected ? 'Selected' : 'Select'}
              </span>
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}
