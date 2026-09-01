export type RoomType = 'PUBLIC' | 'PRIVATE'
export type RoomStatus = 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'
export type RoomPlayerState = 'NOT_READY' | 'READY' | 'PLAYING' | 'SPECTATING' | 'DISCONNECTED' | 'LEAVING'

export interface RoomSummary {
  id: number
  name: string
  ownerUserId: number
  roomType: RoomType
  passwordRequired: boolean
  status: RoomStatus
  seatedPlayers: number
  maxPlayers: number
  smallBlind: number
  bigBlind: number
  buyIn: number
}

export interface RoomPlayer {
  userId: number
  username: string
  seatNumber: number | null
  state: RoomPlayerState
  tableChips: number
}

export interface RoomDetail {
  room: RoomSummary
  members: RoomPlayer[]
}

export interface CreateRoomRequest {
  name: string
  roomType: RoomType
  maxPlayers: number
  smallBlind: number
  bigBlind: number
  buyIn: number
  password: string | null
}

export interface JoinRoomRequest {
  spectator: boolean
  seatNumber: number | null
  buyInAmount: number | null
  password: string | null
}
