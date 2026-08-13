import { afterEach, describe, expect, it, vi } from 'vitest'
import { readSessionFilter, writeSessionFilter } from '../sessionFilters'

describe('sessionFilters', () => {
  afterEach(() => {
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('returns fallback when missing or invalid and writes values', () => {
    expect(readSessionFilter('k', { from: '', to: '' })).toEqual({ from: '', to: '' })
    sessionStorage.setItem('k', 'not-json')
    expect(readSessionFilter('k', { from: 'a', to: 'b' })).toEqual({ from: 'a', to: 'b' })
    sessionStorage.setItem('k', 'null')
    expect(readSessionFilter('k', { from: 'a' })).toEqual({ from: 'a' })
    sessionStorage.setItem('k', '[]')
    expect(readSessionFilter('k', { q: '' })).toEqual({ q: '' })
    writeSessionFilter('k', { from: '2026-01-01', to: '' })
    expect(readSessionFilter('k', { from: '', to: '' })).toEqual({ from: '2026-01-01', to: '' })
  })

  it('tolerates sessionStorage failures', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readSessionFilter('k', { q: 'x' })).toEqual({ q: 'x' })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(() => writeSessionFilter('k', { q: 'y' })).not.toThrow()
  })
})
