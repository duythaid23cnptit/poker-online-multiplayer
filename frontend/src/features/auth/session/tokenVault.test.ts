import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenVault } from './tokenVault'

describe('tokenVault', () => {
  afterEach(() => vi.restoreAllMocks())

  it('fails closed when browser privacy policy denies storage operations', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })

    expect(tokenVault.readRefreshToken()).toBeNull()
    expect(() => tokenVault.storeRefreshToken('refresh-token')).not.toThrow()
    expect(() => tokenVault.clear()).not.toThrow()
  })
})
