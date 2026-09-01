export interface RegisterRequest {
  username: string
  password: string
  email?: string | null
  displayName: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: 'Bearer' | string
  expiresInSeconds: number
}

export interface AccessTokenResponse {
  accessToken: string
  tokenType: 'Bearer' | string
  expiresInSeconds: number
}
