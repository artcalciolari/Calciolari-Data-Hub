import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi, afterEach } from 'vitest'

const updateServiceWorker = vi.fn()
const setNeedRefresh = vi.fn()
const registrationUpdate = vi.fn()

vi.mock('virtual:pwa-register/react', () => ({
  useRegisterSW: (options?: {
    onRegisteredSW?: (swUrl: string, registration?: ServiceWorkerRegistration) => void
  }) => {
    options?.onRegisteredSW?.('/sw.js', {
      update: registrationUpdate,
    } as unknown as ServiceWorkerRegistration)
    options?.onRegisteredSW?.('/sw.js', undefined)
    return {
      needRefresh: [true, setNeedRefresh],
      offlineReady: [false, vi.fn()],
      updateServiceWorker,
    }
  },
}))

import { PwaUpdateBanner } from '../PwaUpdateBanner'

describe('PwaUpdateBanner', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders nothing when refresh is not needed', async () => {
    vi.resetModules()
    vi.doMock('virtual:pwa-register/react', () => ({
      useRegisterSW: () => ({
        needRefresh: [false, vi.fn()],
        offlineReady: [false, vi.fn()],
        updateServiceWorker: vi.fn(),
      }),
    }))
    const { PwaUpdateBanner: Banner } = await import('../PwaUpdateBanner')
    const { container } = render(<Banner />)
    expect(container.firstChild).toBeNull()
  })

  it('shows update actions when refresh is needed', () => {
    render(<PwaUpdateBanner />)
    expect(screen.getByRole('status')).toHaveTextContent('Nova versão disponível.')
    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }))
    expect(updateServiceWorker).toHaveBeenCalledWith(true)
    fireEvent.click(screen.getByRole('button', { name: 'Depois' }))
    expect(setNeedRefresh).toHaveBeenCalledWith(false)
  })

  it('schedules periodic service worker update checks', () => {
    vi.useFakeTimers()
    registrationUpdate.mockClear()
    const { unmount } = render(<PwaUpdateBanner />)
    vi.advanceTimersByTime(60 * 60 * 1000)
    expect(registrationUpdate).toHaveBeenCalled()
    unmount()
  })
})
