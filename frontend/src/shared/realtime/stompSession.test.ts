import type { StompConfig } from '@stomp/stompjs'
import { describe, expect, it, vi } from 'vitest'
import {
  StompSessionManager,
  type StompClientFactory,
  type StompClientPort,
} from './stompSession'

function clientPort(active = true) {
  const activate = vi.fn()
  const deactivate = vi.fn(async () => undefined)
  const subscribe = vi.fn()
  const client: StompClientPort & { subscribe: typeof subscribe } = {
    active,
    connectHeaders: {},
    activate,
    deactivate,
    subscribe,
  }
  return { client, activate, deactivate, subscribe }
}

describe('StompSessionManager', () => {
  it('authenticates through a native STOMP CONNECT header without subscriptions', () => {
    const { client, activate, subscribe } = clientPort()
    const factory = vi.fn<StompClientFactory>(() => client)
    const manager = new StompSessionManager('wss://poker.test/ws', factory)

    manager.connect('signed-access-token')

    expect(factory).toHaveBeenCalledTimes(1)
    const config: StompConfig = factory.mock.calls[0][0]
    expect(config.brokerURL).toBe('wss://poker.test/ws')
    expect(config.connectHeaders).toEqual({ Authorization: 'Bearer signed-access-token' })
    expect(config.reconnectDelay).toBe(5_000)
    expect(config.heartbeatIncoming).toBe(10_000)
    expect(config.heartbeatOutgoing).toBe(10_000)
    expect(config.onConnect).toBeUndefined()
    expect(activate).toHaveBeenCalledTimes(1)
    expect(subscribe).not.toHaveBeenCalled()
  })

  it('reuses an active client instead of creating a duplicate connection', () => {
    const { client, activate } = clientPort(true)
    const factory = vi.fn<StompClientFactory>(() => client)
    const manager = new StompSessionManager('wss://poker.test/ws', factory)

    manager.connect('first-token')
    manager.connect('replacement-token')

    expect(factory).toHaveBeenCalledTimes(1)
    expect(activate).toHaveBeenCalledTimes(1)
    expect(client.connectHeaders).toEqual({ Authorization: 'Bearer replacement-token' })
    expect(manager.isActive()).toBe(true)
  })

  it('disconnects once and makes repeated cleanup idempotent', async () => {
    const { client, deactivate } = clientPort(true)
    const factory = vi.fn<StompClientFactory>(() => client)
    const manager = new StompSessionManager('wss://poker.test/ws', factory)
    manager.connect('access-token')

    await manager.disconnect()
    await manager.disconnect()

    expect(deactivate).toHaveBeenCalledTimes(1)
    expect(manager.isActive()).toBe(false)
  })
})
