import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ImportsPage } from '../ImportsPage'

vi.mock('@/shared/api', () => ({
  listImports: vi.fn(),
  uploadQrp: vi.fn(),
  getDebugStatus: vi.fn(),
  resetDataset: vi.fn(),
}))

import { listImports, uploadQrp, getDebugStatus, resetDataset } from '@/shared/api'
import type { ImportJob } from '@/shared/api'

const jobRow = {
  id: 'job-12345678-abcd',
  status: 'SUCCEEDED' as const,
  createdAt: '2026-07-01T12:00:00',
  completedAt: '2026-07-01T12:05:00',
  files: [{
    id: 'f1',
    originalFilename: 'a.qrp',
    status: 'IMPORTED' as const,
    deduplicated: false,
    duplicateOfImportFileId: null,
    parseAttemptId: null,
    createdAt: 't',
    completedAt: null,
    productName: 'MOLHO POMODORO',
    recordsFound: 134,
    parsedQuantity: '52.986',
    parsedRevenue: '3013.07',
    sourceQuantity: '52.986',
    quantityValidationStatus: 'VALID',
  }],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/imports']}>
      <Routes>
        <Route path="/imports" element={<ImportsPage />} />
        <Route path="/imports/:jobId" element={<div>job detail</div>} />
        <Route path="/imports/:jobId/files/:fileId" element={<div>file detail</div>} />
      </Routes>
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
    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: false })
  })

  it('renders history and supports file selection upload flow', async () => {
    vi.mocked(uploadQrp).mockImplementation(async (files, onProgress) => {
      onProgress?.(files[0]!, 50, 100)
      return {
      id: 'j-new',
      status: 'SUCCEEDED',
      createdAt: 't',
      completedAt: null,
      files: [{
        id: 'f',
        originalFilename: 'x.qrp',
        status: 'IMPORTED',
        deduplicated: false,
        duplicateOfImportFileId: null,
        parseAttemptId: null,
        createdAt: 't',
        completedAt: null,
        productName: 'MOLHO POMODORO',
        recordsFound: 134,
        parsedQuantity: '52.986',
        parsedRevenue: '3013.07',
        quantityValidationStatus: 'VALID',
      }],
    }
    })
    renderPage()
    expect(await screen.findByText('Histórico')).toBeInTheDocument()
    expect(screen.getByText('1 envio(s)')).toBeInTheDocument()
    expect(screen.getByText('Concluído')).toBeInTheDocument()

    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [file] } })
    expect(screen.getByText(/test\.qrp/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect(await screen.findByText('MOLHO POMODORO')).toBeInTheDocument()
    expect(screen.getByText(/Arquivo validado/)).toBeInTheDocument()
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
    renderPage()
    const link = await screen.findByRole('link', { name: 'a.qrp' })
    fireEvent.click(screen.getByRole('button', { name: 'Selecionar arquivos' }))
    fireEvent.click(link.closest('tr')!)
    expect(await screen.findByText('job detail')).toBeInTheDocument()
  })

  it('ignores non-activation keys on a job row', async () => {
    renderPage()
    const row = (await screen.findByRole('link', { name: 'a.qrp' })).closest('tr')!
    fireEvent.keyDown(row, { key: 'Escape' })
    expect(screen.queryByText('job detail')).not.toBeInTheDocument()
  })

  it('navigates job row with keyboard', async () => {
    const { unmount } = renderPage()
    const enterRow = (await screen.findByRole('link', { name: 'a.qrp' })).closest('tr')!
    fireEvent.keyDown(enterRow, { key: 'Enter' })
    expect(await screen.findByText('job detail')).toBeInTheDocument()
    unmount()
    renderPage()
    const spaceRow = (await screen.findByRole('link', { name: 'a.qrp' })).closest('tr')!
    fireEvent.keyDown(spaceRow, { key: ' ' })
    expect(await screen.findByText('job detail')).toBeInTheDocument()
  })

  it('falls back to Importação when the job has no filename', async () => {
    vi.mocked(listImports).mockResolvedValue({
      content: [{
        ...jobRow,
        files: [],
      }],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    })
    renderPage()
    expect(await screen.findByRole('link', { name: 'Importação' })).toBeInTheDocument()
  })

  it('shows upload percent while the request is in flight', async () => {
    let finish: ((job: ImportJob) => void) | undefined
    vi.mocked(uploadQrp).mockImplementation(async (files, onProgress) => {
      onProgress?.(files[0]!, 50, 100)
      return new Promise((resolve) => {
        finish = resolve
      })
    })
    renderPage()
    await screen.findByText('Histórico')
    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect(await screen.findByRole('button', { name: 'Enviando… 50%' })).toBeInTheDocument()
    finish!({
      id: 'j-new',
      status: 'SUCCEEDED',
      createdAt: 't',
      completedAt: null,
      files: jobRow.files,
    })
    expect(await screen.findByText('MOLHO POMODORO')).toBeInTheDocument()
  })

  it('shows sending without percent when total is zero', async () => {
    let finish: ((job: ImportJob) => void) | undefined
    vi.mocked(uploadQrp).mockImplementation(async (files, onProgress) => {
      onProgress?.(files[0]!, 1, 0)
      return new Promise((resolve) => {
        finish = resolve
      })
    })
    renderPage()
    await screen.findByText('Histórico')
    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect(await screen.findByRole('button', { name: 'Enviando…' })).toBeInTheDocument()
    finish!({
      id: 'j-new',
      status: 'SUCCEEDED',
      createdAt: 't',
      completedAt: null,
      files: jobRow.files,
    })
    expect(await screen.findByText('MOLHO POMODORO')).toBeInTheDocument()
  })

  it('keeps import disabled when no files are selected', async () => {
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

  it('shows mismatch and duplicate result cards and paginates', async () => {
    vi.mocked(listImports).mockResolvedValue({
      content: [jobRow],
      page: 0,
      size: 10,
      totalElements: 20,
      totalPages: 2,
    })
    vi.mocked(uploadQrp).mockResolvedValue({
      id: 'j-new',
      status: 'PARTIAL_SUCCESS',
      createdAt: 't',
      completedAt: null,
      files: [
        {
          id: 'f-bad',
          originalFilename: 'bad.qrp',
          status: 'WARNING',
          deduplicated: true,
          duplicateOfImportFileId: 'x',
          parseAttemptId: null,
          createdAt: 't',
          completedAt: null,
          quantityValidationStatus: 'INVALID',
          sourceQuantity: '52.986',
          parsedQuantity: '51.420',
        },
        {
          id: 'f-empty',
          originalFilename: '',
          status: 'FAILED',
          deduplicated: false,
          duplicateOfImportFileId: null,
          parseAttemptId: null,
          createdAt: 't',
          completedAt: null,
        },
        {
          id: 'f-mismatch-bare',
          originalFilename: 'bare.qrp',
          status: 'WARNING',
          deduplicated: false,
          duplicateOfImportFileId: null,
          parseAttemptId: null,
          createdAt: 't',
          completedAt: null,
          quantityValidationStatus: 'INVALID',
        },
      ],
    })
    renderPage()
    await screen.findByText('Histórico')
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    expect(listImports).toHaveBeenCalledWith({ page: 1, size: 10 })
    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect((await screen.findAllByText(/Arquivo importado com divergências/))).toHaveLength(2)
    expect(screen.getByText(/Já importado/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Ver detalhes' }))
    expect(await screen.findByText('job detail')).toBeInTheDocument()
  })

  it('refreshes history after a debug dataset reset', async () => {
    vi.mocked(getDebugStatus).mockResolvedValue({ enabled: true })
    vi.mocked(resetDataset).mockResolvedValue({ reset: true, artifactCount: 1, filesDeleted: 1 })
    vi.mocked(uploadQrp).mockResolvedValue({
      id: 'j-new',
      status: 'SUCCEEDED',
      createdAt: 't',
      completedAt: null,
      files: jobRow.files,
    })
    renderPage()
    const file = new File(['data'], 'test.qrp', { type: 'application/octet-stream' })
    fireEvent.change(document.querySelector('input[type="file"]')!, { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: /Importar \(1\)/ }))
    expect(await screen.findByText('MOLHO POMODORO')).toBeInTheDocument()
    vi.mocked(listImports).mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    })
    fireEvent.click(await screen.findByRole('button', { name: 'Apagar dados' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar exclusão' }))
    expect(await screen.findByText('Nenhuma importação ainda.')).toBeInTheDocument()
    expect(screen.queryByText('MOLHO POMODORO')).not.toBeInTheDocument()
  })
})
