import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { roomDetailQueryOptions, roomKeys } from './roomQueries'

const mocks = vi.hoisted(() => ({ detail: vi.fn() }))

vi.mock('./roomApi', () => ({
  roomApi: {
    detail: mocks.detail,
  },
}))

function RoomDetailProbe({ enabled }: { enabled: boolean }) {
  useQuery(roomDetailQueryOptions(7, enabled))
  return null
}

describe('roomDetailQueryOptions', () => {
  beforeEach(() => mocks.detail.mockReset())

  it('keeps the stable detail key and does not request a disabled historical room', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    mocks.detail.mockResolvedValue({ room: { id: 7 }, members: [] })

    const view = render(<RoomDetailProbe enabled={false} />, { wrapper })

    expect(roomDetailQueryOptions(7, false).queryKey).toEqual(roomKeys.detail(7))
    expect(mocks.detail).not.toHaveBeenCalled()

    view.rerender(<RoomDetailProbe enabled />)
    await waitFor(() => expect(mocks.detail).toHaveBeenCalledTimes(1))
  })

  it('replaces a globally fresh but incomplete cache on live mount', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    queryClient.setQueryData(roomKeys.detail(7), { room: { id: 7 }, members: [
      { userId: 42, username: 'hero' },
    ] })
    mocks.detail.mockResolvedValue({ room: { id: 7 }, members: [
      { userId: 42, username: 'hero' }, { userId: 4, username: 'vuthai' },
    ] })

    render(<RoomDetailProbe enabled />, { wrapper })

    await waitFor(() => expect(queryClient.getQueryData<{ members: { username: string }[] }>(roomKeys.detail(7))
      ?.members.map((member) => member.username)).toEqual(['hero', 'vuthai']))
    expect(mocks.detail).toHaveBeenCalledTimes(1)
  })

  it('uses the same authoritative fetch for cold hydration and reconnect remounts', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 30_000 } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
    mocks.detail.mockResolvedValueOnce({ room: { id: 7 }, members: [{ userId: 4, username: 'vuthai' }] })
      .mockResolvedValueOnce({ room: { id: 7 }, members: [{ userId: 4, username: 'vuthai' }] })

    const cold = render(<RoomDetailProbe enabled />, { wrapper })
    await waitFor(() => expect(mocks.detail).toHaveBeenCalledTimes(1))
    cold.unmount()
    render(<RoomDetailProbe enabled />, { wrapper })

    await waitFor(() => expect(mocks.detail).toHaveBeenCalledTimes(2))
    expect(roomDetailQueryOptions(7).refetchOnReconnect).toBe('always')
    expect(queryClient.getQueryData<{ members: { username: string }[] }>(roomKeys.detail(7))
      ?.members[0].username).toBe('vuthai')
  })
})
