import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useMediaQuery } from '../useMediaQuery'

function mockMatchMedia(initialMatches: boolean) {
  const listeners = new Set<(event: MediaQueryListEvent) => void>()
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: initialMatches,
    media: query,
    addEventListener: (_: 'change', cb: (event: MediaQueryListEvent) => void) => listeners.add(cb),
    removeEventListener: (_: 'change', cb: (event: MediaQueryListEvent) => void) => listeners.delete(cb),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    onchange: null,
    dispatchEvent: vi.fn(),
  }))
  return listeners
}

describe('useMediaQuery', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns false when matchMedia is unavailable', () => {
    const original = window.matchMedia
    // @ts-expect-error testing environments without matchMedia
    delete window.matchMedia
    const { result } = renderHook(() => useMediaQuery('(max-width: 760px)'))
    expect(result.current).toBe(false)
    window.matchMedia = original
  })

  it('returns the initial match and updates on change', async () => {
    const listeners = mockMatchMedia(false)
    const { result } = renderHook(() => useMediaQuery('(max-width: 760px)'))
    expect(result.current).toBe(false)

    const listener = Array.from(listeners)[0]
    listener({ matches: true } as MediaQueryListEvent)
    await waitFor(() => expect(result.current).toBe(true))
  })
})
