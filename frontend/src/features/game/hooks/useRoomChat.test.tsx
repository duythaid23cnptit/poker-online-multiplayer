import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren, ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { stompSession } from '../../../shared/realtime/stompSession'
import { chatApi } from '../api/chatApi'
import type { ChatMessage } from '../types/chat'
import { useRoomChat } from './useRoomChat'

function message(messageId: number, roomId = 7, clientMessageId = `command-${messageId}`): ChatMessage {
  return { messageId, roomId, clientMessageId, content: `message ${messageId}`,
    createdAt: `2026-09-04T00:00:${String(messageId).padStart(2, '0')}Z`,
    sender: { userId: 3, displayName: 'Dealer', avatarUrl: null } }
}
function event(value: ChatMessage) { return JSON.stringify({ protocolVersion: 1, type: 'CHAT_MESSAGE', scope: { roomId: value.roomId }, payload: value }) }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done }); return { promise, resolve } }

describe('useRoomChat', () => {
  const listeners = new Map<string, (body: string) => void>()
  let queryClient: QueryClient
  let wrapper: ({ children }: PropsWithChildren) => ReactNode

  beforeEach(() => {
    listeners.clear()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    wrapper = ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    vi.spyOn(stompSession, 'listen').mockImplementation((destination, listener) => { listeners.set(destination, listener); return () => listeners.delete(destination) })
    vi.spyOn(stompSession, 'send').mockReturnValue(true)
    vi.spyOn(chatApi, 'history').mockResolvedValue([])
  })

  it('hydrates persisted history for a fresh player or spectator render and restores it on remount', async () => {
    vi.mocked(chatApi.history).mockResolvedValue([message(1), message(2)])
    const first = renderHook(() => useRoomChat(7), { wrapper })
    await waitFor(() => expect(first.result.current.messages).toHaveLength(2))
    expect(chatApi.history).toHaveBeenCalledWith(7, 50, expect.any(AbortSignal))
    first.unmount()

    const restored = renderHook(() => useRoomChat(7), { wrapper })
    expect(restored.result.current.messages.map((item) => item.messageId)).toEqual([1, 2])
  })

  it('merges a new STOMP message and renders an own broadcast exactly once', async () => {
    vi.mocked(chatApi.history).mockResolvedValue([message(1)])
    const { result } = renderHook(() => useRoomChat(7), { wrapper })
    await waitFor(() => expect(result.current.messages).toHaveLength(1))
    let commandId = ''
    act(() => { commandId = result.current.send('mine').clientMessageId })
    expect(result.current.pendingId).toBe(commandId)
    const own = message(2, 7, commandId)
    act(() => { listeners.get('/topic/room/7')?.(event(own)); listeners.get('/topic/room/7')?.(event(own)) })
    expect(result.current.messages.map((item) => item.messageId)).toEqual([1, 2])
    expect(result.current.pendingId).toBeNull()
  })

  it('does not lose or duplicate realtime data when STOMP arrives before history', async () => {
    const history = deferred<ChatMessage[]>()
    vi.mocked(chatApi.history).mockReturnValue(history.promise)
    const { result } = renderHook(() => useRoomChat(7), { wrapper })
    await waitFor(() => expect(listeners.has('/topic/room/7')).toBe(true))
    act(() => listeners.get('/topic/room/7')?.(event(message(2))))
    await waitFor(() => expect(result.current.messages.map((item) => item.messageId)).toEqual([2]))
    act(() => history.resolve([message(1), message(2)]))
    await waitFor(() => expect(result.current.messages.map((item) => item.messageId)).toEqual([1, 2]))
  })

  it('isolates room switches and subscribes only to the selected room', async () => {
    vi.mocked(chatApi.history).mockImplementation(async (roomId) => [message(roomId, roomId)])
    const { result, rerender } = renderHook(({ roomId }) => useRoomChat(roomId), { initialProps: { roomId: 7 }, wrapper })
    await waitFor(() => expect(result.current.messages[0]?.roomId).toBe(7))
    rerender({ roomId: 8 })
    await waitFor(() => expect(result.current.messages[0]?.roomId).toBe(8))
    expect(listeners.has('/topic/room/7')).toBe(false)
    expect(listeners.has('/topic/room/8')).toBe(true)
  })

  it('preserves retry identity after local send failure', async () => {
    vi.mocked(stompSession.send).mockReturnValue(false)
    const { result } = renderHook(() => useRoomChat(7), { wrapper })
    await waitFor(() => expect(result.current.historyPending).toBe(false))
    let first!: { clientMessageId: string; sent: boolean }
    act(() => { first = result.current.send('hello') })
    expect(result.current.failed).toBe(true)
    act(() => result.current.send('hello', first.clientMessageId))
    expect(vi.mocked(stompSession.send).mock.calls[1][1]).toMatchObject({ clientMessageId: first.clientMessageId })
  })
})
