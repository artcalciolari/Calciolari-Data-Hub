import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { DebugResetPanel } from '../DebugResetPanel'

vi.mock('@/shared/api', () => ({
  getDebugStatus: vi.fn(),
  resetDataset: vi.fn(),
}))

import { getDebugStatus, resetDataset } from '@/shared/api'

describe('DebugResetPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing while loading or when debug is off', async () => {
    vi.mocked(getDebugStatus).mockImplementation(() => new Promise(() => {}))
    const { unmount } = render(<DebugResetPanel onCleared={() => {}} />)
    expect(screen.queryByRole('heading', { name: 'Modo debug' })).not.toBeInTheDocument()
    unmount()

    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: false })
    render(<DebugResetPanel onCleared={() => {}} />)
    await waitFor(() => expect(getDebugStatus).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Modo debug' })).not.toBeInTheDocument()
  })

  it('hides when status lookup fails', async () => {
    vi.mocked(getDebugStatus).mockRejectedValue(new Error('nope'))
    render(<DebugResetPanel onCleared={() => {}} />)
    await waitFor(() => expect(getDebugStatus).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Modo debug' })).not.toBeInTheDocument()
  })

  it('confirms, resets, and can cancel', async () => {
    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: true })
    vi.mocked(resetDataset).mockResolvedValue({ reset: true, artifactCount: 2, filesDeleted: 2 })
    const onCleared = vi.fn()
    render(<DebugResetPanel onCleared={onCleared} />)
    expect(await screen.findByRole('heading', { name: 'Modo debug' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Apagar dados' }))
    expect(screen.getByText(/Isso não tem volta/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByText(/Isso não tem volta/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Apagar dados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByText(/Dados apagados/)).toBeInTheDocument()
    expect(onCleared).toHaveBeenCalled()
    expect(resetDataset).toHaveBeenCalled()
  })

  it('shows Error message when reset fails', async () => {
    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: true })
    vi.mocked(resetDataset).mockRejectedValue(new Error('boom'))
    render(<DebugResetPanel onCleared={() => {}} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Apagar dados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByText('boom')).toBeInTheDocument()
  })

  it('shows fallback when reset rejects a non-Error', async () => {
    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: true })
    vi.mocked(resetDataset).mockRejectedValue('nope')
    render(<DebugResetPanel onCleared={() => {}} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Apagar dados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByText('Falha ao apagar dados')).toBeInTheDocument()
  })

  it('shows working label while reset is in flight', async () => {
    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: true })
    let finish!: () => void
    vi.mocked(resetDataset).mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = () => resolve({ reset: true, artifactCount: 0, filesDeleted: 0 })
        }),
    )
    render(<DebugResetPanel onCleared={() => {}} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Apagar dados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByRole('button', { name: 'Apagando…' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
    finish()
    expect(await screen.findByText(/Dados apagados/)).toBeInTheDocument()
  })
})
