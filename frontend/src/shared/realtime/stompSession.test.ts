import type { IMessage, StompConfig } from '@stomp/stompjs'
import { describe, expect, it, vi } from 'vitest'
import {
  StompSessionManager,
  type StompClientFactory,
  type StompClientPort,
} from './stompSession'

function clientPort(active = true) {
  const activate = vi.fn()
  const deactivate = vi.fn(async () => undefined)
  const unsubscribe = vi.fn()
  const subscribe = vi.fn((_destination: string, _callback: (message: IMessage) => void) => ({ unsubscribe }))
  const client: StompClientPort & { subscribe: typeof subscribe } = {
    active,
    connected: false,
    connectHeaders: {},
    activate,
    deactivate,
    subscribe,
  }
  return { client, activate, deactivate, subscribe, unsubscribe }
}

describe('StompSessionManager', () => {
  it('authenticates through a native STOMP CONNECT header without invented subscriptions', () => {
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
    expect(config.onConnect).toEqual(expect.any(Function))
    expect(activate).toHaveBeenCalledTimes(1)
    expect(subscribe).not.toHaveBeenCalled()
  })

  it('subscribes registered listeners after connect and forwards message bodies', () => {
    const { client, subscribe, unsubscribe } = clientPort()
    const factory = vi.fn<StompClientFactory>(() => client)
    const manager = new StompSessionManager('wss://poker.test/ws', factory)
    const listener = vi.fn()
    const stopListening = manager.listen('/topic/lobby', listener)

    manager.connect('access-token')
    const config: StompConfig = factory.mock.calls[0][0]
    config.onConnect?.({} as never)

    expect(subscribe).toHaveBeenCalledWith('/topic/lobby', expect.any(Function))
    const callback = subscribe.mock.calls[0][1]
    callback({ body: '{"protocolVersion":1}' } as never)
    expect(listener).toHaveBeenCalledWith('{"protocolVersion":1}')

    stopListening()
    expect(unsubscribe).toHaveBeenCalledTimes(1)
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
