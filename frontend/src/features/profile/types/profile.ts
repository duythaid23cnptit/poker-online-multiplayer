export interface CurrentUser {
  id: number
  username: string
  email: string | null
  role: 'PLAYER' | 'ADMIN'
  accountStatus: 'ACTIVE' | 'LOCKED'
  accountChips: number
  displayName: string
  avatarUrl: string | null
  onlineStatus: 'ONLINE' | 'IN_GAME' | 'OFFLINE'
}

export interface UpdateProfileRequest {
  displayName: string
  avatarUrl: string | null
}
