import { Client, type IMessage, type StompConfig, type StompHeaders } from '@stomp/stompjs'
import { appConfig } from '../config/env'

interface StompSubscriptionPort { unsubscribe(): void }

export interface StompClientPort {
  active: boolean
  connected: boolean
  connectHeaders: StompHeaders
  activate(): void
  deactivate(): Promise<void>
  subscribe(destination: string, callback: (message: IMessage) => void): StompSubscriptionPort
  publish(parameters: { destination: string; body: string }): void
}

export type StompClientFactory = (config: StompConfig) => StompClientPort

export class StompSessionManager {
  private client: StompClientPort | null = null
  private readonly handlers = new Map<string, Set<(body: string) => void>>()
  private readonly subscriptions = new Map<string, StompSubscriptionPort>()
  private readonly connectionHandlers = new Set<(connected: boolean) => void>()

  constructor(
    private readonly brokerUrl: string,
    private readonly factory: StompClientFactory = (config) => new Client(config),
  ) {}

  connect(accessToken: string): void {
    const connectHeaders = { Authorization: `Bearer ${accessToken}` }
    if (this.client) {
      this.client.connectHeaders = connectHeaders
      if (!this.client.active) this.client.activate()
      return
    }
    this.client = this.factory({
      brokerURL: this.brokerUrl,
      connectHeaders,
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => undefined,
      onConnect: () => { this.subscribeAll(); this.notifyConnection(true) },
      onWebSocketClose: () => { this.subscriptions.clear(); this.notifyConnection(false) },
    })
    this.client.activate()
  }

  send(destination: string, body: unknown): boolean {
    if (!this.client?.connected) return false
    this.client.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  listenConnection(handler: (connected: boolean) => void): () => void {
    this.connectionHandlers.add(handler)
    handler(Boolean(this.client?.connected))
    return () => { this.connectionHandlers.delete(handler) }
  }

  listen(destination: string, handler: (body: string) => void): () => void {
    const destinationHandlers = this.handlers.get(destination) || new Set<(body: string) => void>()
    destinationHandlers.add(handler)
    this.handlers.set(destination, destinationHandlers)
    if (this.client?.connected) this.ensureSubscription(destination)
    return () => {
      const handlers = this.handlers.get(destination)
      handlers?.delete(handler)
      if (handlers?.size) return
      this.handlers.delete(destination)
      this.subscriptions.get(destination)?.unsubscribe()
      this.subscriptions.delete(destination)
    }
  }

  async disconnect(): Promise<void> {
    const client = this.client
    this.client = null
    for (const subscription of this.subscriptions.values()) subscription.unsubscribe()
    this.subscriptions.clear()
    if (client?.active) await client.deactivate()
    this.notifyConnection(false)
  }

  isActive(): boolean {
    return Boolean(this.client?.active)
  }

  private subscribeAll(): void {
    for (const destination of this.handlers.keys()) this.ensureSubscription(destination)
  }

  private ensureSubscription(destination: string): void {
    if (!this.client || this.subscriptions.has(destination)) return
    const subscription = this.client.subscribe(destination, (message) => {
      for (const handler of this.handlers.get(destination) || []) handler(message.body)
    })
    this.subscriptions.set(destination, subscription)
  }

  private notifyConnection(connected: boolean): void {
    for (const handler of this.connectionHandlers) handler(connected)
  }
}

export const stompSession = new StompSessionManager(appConfig.websocketUrl)
