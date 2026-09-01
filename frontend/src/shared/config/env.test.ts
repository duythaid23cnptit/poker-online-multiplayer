import { describe, expect, it } from 'vitest'
import {
  createAppConfig,
  normalizeApiBaseUrl,
  normalizeWebSocketUrl,
} from './env'

function environment(values: Partial<ImportMetaEnv>): ImportMetaEnv {
  return values as ImportMetaEnv
}

describe('frontend environment configuration', () => {
  it('normalizes absolute and root-relative API base URLs', () => {
    expect(normalizeApiBaseUrl(' https://api.poker.test/api/v1/ ')).toBe(
      'https://api.poker.test/api/v1',
    )
    expect(normalizeApiBaseUrl('/api/v1/')).toBe('/api/v1')
  })

  it('rejects API base URLs that are neither HTTP URLs nor root-relative paths', () => {
    expect(() => normalizeApiBaseUrl('api/v1')).toThrow(/VITE_API_BASE_URL/)
    expect(() => normalizeApiBaseUrl('javascript:alert(1)')).toThrow(/VITE_API_BASE_URL/)
  })

  it('normalizes WebSocket URLs without putting credentials in the URL', () => {
    const secureLocation = { protocol: 'https:', host: 'poker.test' } as Location

    expect(normalizeWebSocketUrl('/ws', secureLocation)).toBe('wss://poker.test/ws')
    expect(normalizeWebSocketUrl('https://api.poker.test/ws')).toBe('wss://api.poker.test/ws')
    expect(normalizeWebSocketUrl('ws://localhost:8080/ws')).toBe('ws://localhost:8080/ws')
  })

  it('creates one typed config from the two supported environment variables', () => {
    const config = createAppConfig(
      environment({
        VITE_API_BASE_URL: 'https://api.poker.test/api/v1/',
        VITE_WS_URL: 'https://api.poker.test/ws',
      }),
    )

    expect(config).toEqual({
      apiBaseUrl: 'https://api.poker.test/api/v1',
      websocketUrl: 'wss://api.poker.test/ws',
    })
  })
})
