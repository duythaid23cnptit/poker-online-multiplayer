import { create } from 'zustand'

export type SessionStatus = 'CHECKING_SESSION' | 'AUTHENTICATED' | 'UNAUTHENTICATED'

interface SessionState {
  status: SessionStatus
  accessToken: string | null
  epoch: number
  beginSessionCheck: () => void
  setAccessToken: (accessToken: string) => void
  markAuthenticated: () => void
  clearSession: () => void
}

export const useSessionStore = create<SessionState>((set) => ({
  status: 'CHECKING_SESSION',
  accessToken: null,
  epoch: 0,
  beginSessionCheck: () => set({ status: 'CHECKING_SESSION' }),
  setAccessToken: (accessToken) => set({ accessToken }),
  markAuthenticated: () => set({ status: 'AUTHENTICATED' }),
  clearSession: () => set((state) => ({
    status: 'UNAUTHENTICATED', accessToken: null, epoch: state.epoch + 1,
  })),
}))
