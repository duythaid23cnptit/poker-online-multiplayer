import { create } from 'zustand'
import type { NotificationEvent } from '../types/notification'

interface NotificationState {
  events: NotificationEvent[]
  add: (event: NotificationEvent) => void
  clear: () => void
}

export const useNotificationStore = create<NotificationState>((set) => ({
  events: [],
  add: (event) => set((state) => ({
    events: [event, ...state.events.filter((item) => item.eventId !== event.eventId)].slice(0, 20),
  })),
  clear: () => set({ events: [] }),
}))
