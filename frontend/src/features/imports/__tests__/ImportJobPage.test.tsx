import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getImportJob } from '@/shared/api'
import { ImportJobPage } from '../ImportJobPage'

vi.mock('@/shared/api', () => ({
  getImportJob: vi.fn(),
}))

const job = {
  id: 'job-abcdef12-zzzz',
  status: 'PARTIAL_SUCCESS' as const,
  createdAt: '2026-07-01T12:00:00',
  completedAt: null,
  files: [
    {
      id: 'file-1',
      originalFilename: '',
      status: 'WARNING' as const,
      deduplicated: true,
      duplicateOfImportFileId: 'dup',
      parseAttemptId: null,
      createdAt: 't',
      completedAt: '2026-07-01T13:00:00',
    },
    {
      id: 'file-2',
      originalFilename: 'b.qrp',
      status: 'IMPORTED' as const,
      deduplicated: false,
      duplicateOfImportFileId: null,
      parseAttemptId: null,
      createdAt: 't',
      completedAt: null,
    },
  ],
}

function renderPage(jobId = 'job-abcdef12-zzzz') {
  return render(
    <MemoryRouter initialEntries={[`/imports/${jobId}`]}>
      <Routes>
        <Route path="/imports/:jobId" element={<ImportJobPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ImportJobPage', () => {
  beforeEach(() => {
    vi.mocked(getImportJob).mockResolvedValue(job)
  })

  it('renders job files table', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: /Job job-abcd/ })).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
    expect(screen.getByText('sim')).toBeInTheDocument()
    expect(screen.getByText('não')).toBeInTheDocument()
    expect(screen.getByText('Parcial')).toBeInTheDocument()
  })

  it('shows loading, error and not-found states', async () => {
    vi.mocked(getImportJob).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(await screen.findByText('Carregando job…')).toBeInTheDocument()

    vi.mocked(getImportJob).mockRejectedValue(new Error('job err'))
    renderPage()
    expect(await screen.findByText('job err')).toBeInTheDocument()

    vi.mocked(getImportJob).mockResolvedValue(null as never)
    renderPage()
    expect(await screen.findByText('Job não encontrado')).toBeInTheDocument()
  })

  it('navigates to file detail on row click', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    renderPage()
    const link = await screen.findByRole('link', { name: 'b.qrp' })
    fireEvent.click(link.closest('tr')!)
    expect(assign).toHaveBeenCalled()
    expect(assign.mock.calls[0][0]).toMatch(/\/imports\/job-abcdef12-zzzz\/files\/file-[12]$/)
    vi.unstubAllGlobals()
  })
})
