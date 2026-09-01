import { Client, type StompConfig, type StompHeaders } from '@stomp/stompjs'
import { appConfig } from '../config/env'

export interface StompClientPort {
  active: boolean
  connectHeaders: StompHeaders
  activate(): void
  deactivate(): Promise<void>
}

export type StompClientFactory = (config: StompConfig) => StompClientPort

export class StompSessionManager {
  private client: StompClientPort | null = null

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
    })
    this.client.activate()
  }

  async disconnect(): Promise<void> {
    const client = this.client
    this.client = null
    if (client?.active) await client.deactivate()
  }

  isActive(): boolean {
    return Boolean(this.client?.active)
  }
}

export const stompSession = new StompSessionManager(appConfig.websocketUrl)
