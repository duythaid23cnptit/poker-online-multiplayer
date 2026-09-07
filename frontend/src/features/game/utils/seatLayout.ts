export function seatPosition(seat: number, capacity: number, ownSeat?: number): { x:number; y:number } {
  const count = Math.max(2, capacity); const normalized = ownSeat ? ((seat - ownSeat + count) % count) : seat - 1
  const angle = Math.PI / 2 + (normalized / count) * Math.PI * 2
  return { x: 50 + Math.cos(angle) * 43, y: 50 + Math.sin(angle) * 44 }
}

export function historicalSeatCapacity(seats: number[]): number {
  return Math.max(2, ...seats)
}
