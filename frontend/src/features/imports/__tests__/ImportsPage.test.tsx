import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ImportsPage } from '../ImportsPage'

vi.mock('@/shared/api', () => ({
  listImports: vi.fn(),
  uploadQrp: vi.fn(),
}))

import { listImports, uploadQrp } from '@/shared/api'

const jobRow = {
  id: 'job-12345678-abcd',
  status: 'SUCCEEDED' as const,
  createdAt: '2026-07-01T12:00:00',
  completedAt: '2026-07-01T12:05:00',
  files: [{ id: 'f1', originalFilename: 'a.qrp', status: 'IMPORTED' as const, deduplicated: false, duplicateOfImportFileId: null, parseAttemptId: null, createdAt: 't', completedAt: null }],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/imports']}>
      <ImportsPage />
    </MemoryRouter>
  )
}

describe('ImportsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listImports).mockResolvedValue({
      content: [jobRow],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('renders history and supports file selection upload flow', async () => {
    vi.mocked(uploadQrp).mockResolvedValue({
      id: 'j-new',
      status: 'PENDING',
      createdAt: 't',
      completedAt: null,
      files: [{ id: 'f', originalFilename: 'x.qrp', status: 'PENDING', deduplicated: false, duplicateOfImportFileId: null, parseAttemptId: null, createdAt: 't', completedAt: null }],
    })
    renderPage()
    expect(await screen.findByText('Histórico')).toBeInTheDocument()
    expect(screen.getByText('1 job(s)')).toBeInTheDocument()
    expect(screen.getByText('Concluído')).toBeInTheDocument()

    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })
    expect(screen.getByText(/test\.qrp/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect(await screen.findByText(/importação pending/i)).toBeInTheDocument()
  })

  it('handles drag-and-drop qrp filter and upload errors', async () => {
    vi.mocked(uploadQrp).mockRejectedValue(new Error('upload failed'))
    renderPage()
    await screen.findByText('Histórico')

    const dropZone = screen.getByText('Arraste os .QRP aqui').closest('section')!
    fireEvent.dragOver(dropZone, { preventDefault: vi.fn() })
    expect(dropZone.className).toContain('drag')
    fireEvent.dragLeave(dropZone)
    expect(dropZone.className).not.toContain('drag')

    const qrp = new File(['x'], 'a.qrp', { type: 'application/octet-stream' })
    const txt = new File(['y'], 'b.txt', { type: 'text/plain' })
    fireEvent.drop(dropZone, { dataTransfer: { files: [qrp, txt] } })
    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect(await screen.findByText('upload failed')).toBeInTheDocument()
  })

  it('shows loading skeleton, error and empty states', async () => {
    vi.mocked(listImports).mockImplementation(() => new Promise(() => {}))
    const { unmount } = renderPage()
    expect(await screen.findByRole('table')).toBeInTheDocument()
    unmount()

    vi.mocked(listImports).mockRejectedValue(new Error('list fail'))
    renderPage()
    expect(await screen.findByText('list fail')).toBeInTheDocument()

    vi.mocked(listImports).mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
    renderPage()
    expect(await screen.findByText('Nenhuma importação ainda.')).toBeInTheDocument()
  })

  it('opens file picker and navigates job row', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    renderPage()
    await screen.findByRole('link', { name: /job-1234/ })
    fireEvent.click(screen.getByRole('button', { name: 'Selecionar arquivos' }))
    const row = screen.getByRole('link', { name: /job-1234/ }).closest('tr')!
    fireEvent.click(row)
    expect(assign).toHaveBeenCalledWith('/imports/job-12345678-abcd')
    vi.unstubAllGlobals()
  })

  it('keeps import disabled when no files selected', async () => {
    renderPage()
    await screen.findByText('Histórico')
    const importBtn = screen.getByRole('button', { name: /^Importar$/ }) as HTMLButtonElement
    expect(importBtn.disabled).toBe(true)
    expect(uploadQrp).not.toHaveBeenCalled()
  })

  it('clears selection when file input change has no files', async () => {
    renderPage()
    await screen.findByText('Histórico')
    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })
    expect(screen.getByText(/test\.qrp/)).toBeInTheDocument()
    fireEvent.change(input, { target: { files: null } })
    expect(screen.queryByText(/test\.qrp/)).not.toBeInTheDocument()
  })

  it('handles non-Error upload rejection', async () => {
    vi.mocked(uploadQrp).mockRejectedValue('nope')
    renderPage()
    await screen.findByText('Histórico')
    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    await waitFor(() => expect(screen.getByText('Falha ao importar')).toBeInTheDocument())
  })
})
