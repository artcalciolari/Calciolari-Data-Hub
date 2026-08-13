import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearBasicAuth, readBasicAuth, setBasicAuth } from '../auth'

describe('auth', () => {
  afterEach(() => {
    sessionStorage.clear()
  })

  it('stores and reads basic credentials', () => {
    expect(readBasicAuth()).toBeNull()
    setBasicAuth('admin', 'secret')
    expect(readBasicAuth()).toBe(btoa('admin:secret'))
    clearBasicAuth()
    expect(readBasicAuth()).toBeNull()
  })

  it('tolerates sessionStorage failures', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readBasicAuth()).toBeNull()
    getItem.mockRestore()
    const remove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => clearBasicAuth()).not.toThrow()
    remove.mockRestore()
  })
})
