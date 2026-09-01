export interface AppConfig {
  apiBaseUrl: string
  websocketUrl: string
}

export function normalizeApiBaseUrl(value: string): string {
  const normalized = value.trim().replace(/\/$/, '')
  if (!normalized || (!normalized.startsWith('/') && !/^https?:\/\//i.test(normalized))) {
    throw new Error('VITE_API_BASE_URL must be an absolute HTTP URL or a root-relative path')
  }
  return normalized
}

export function normalizeWebSocketUrl(value: string, location = globalThis.location): string {
  const normalized = value.trim()
  if (/^wss?:\/\//i.test(normalized)) {
    return normalized
  }
  if (/^https?:\/\//i.test(normalized)) {
    return normalized.replace(/^http/i, 'ws')
  }
  if (normalized.startsWith('/')) {
    const protocol = location?.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = location?.host || 'localhost:5173'
    return `${protocol}//${host}${normalized}`
  }
  throw new Error('VITE_WS_URL must be an absolute WebSocket URL or a root-relative path')
}

export function createAppConfig(environment: ImportMetaEnv = import.meta.env): AppConfig {
  return {
    apiBaseUrl: normalizeApiBaseUrl(environment.VITE_API_BASE_URL || '/api/v1'),
    websocketUrl: normalizeWebSocketUrl(environment.VITE_WS_URL || '/ws'),
  }
}

export const appConfig = Object.freeze(createAppConfig())
