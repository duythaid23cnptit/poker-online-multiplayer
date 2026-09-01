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
}

export type StompClientFactory = (config: StompConfig) => StompClientPort

export class StompSessionManager {
  private client: StompClientPort | null = null
  private readonly handlers = new Map<string, Set<(body: string) => void>>()
  private readonly subscriptions = new Map<string, StompSubscriptionPort>()

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
      onConnect: () => this.subscribeAll(),
    })
    this.client.activate()
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
}

export const stompSession = new StompSessionManager(appConfig.websocketUrl)
