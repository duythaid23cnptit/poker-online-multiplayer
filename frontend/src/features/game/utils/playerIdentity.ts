import type { RoomDetail } from '../../rooms/types/room'

export function playerNameProjection(roomId: number, room?: RoomDetail) {
  if (!room || room.room.id !== roomId) return new Map<number, string>()
  return new Map(room.members.flatMap((member) => {
    const username = member.username.trim()
    return username ? [[member.userId, username] as const] : []
  }))
}

export function resolvedPlayerName(names: ReadonlyMap<number, string>, userId: number) {
  return names.get(userId) ?? `Player #${userId}`
}
