import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getImportFile } from '@/shared/api'
import { ImportFilePage } from '../ImportFilePage'

vi.mock('@/shared/api', () => ({
  getImportFile: vi.fn(),
}))

const fileDetail = {
  id: 'file-1',
  jobId: 'job-1',
  originalFilename: 'export.qrp',
  status: 'IMPORTED' as const,
  deduplicated: false,
  duplicateOfImportFileId: null,
  parseAttemptId: 'p1',
  createdAt: '2026-07-01T12:00:00',
  completedAt: '2026-07-01T12:05:00',
  rawArtifactId: 'r1',
  source: 'upload',
  sha256: 'abc123def456',
  byteSize: 2048,
  parseStatus: 'OK',
  recordsFound: 10,
  parserName: 'qrp',
  parserVersion: '1.0',
  filenameHints: { month: 7 },
  validations: [
    {
      code: 'TOTAL',
      status: 'VALID' as const,
      sourceValue: '1',
      calculatedValue: '1',
      difference: null,
      tolerance: '0.01',
      ruleVersion: 'v1',
      sourceLocator: 'row:1',
    },
    {
      code: 'EMPTY',
      status: 'WARNING' as const,
      sourceValue: null,
      calculatedValue: null,
      difference: null,
      tolerance: null,
      ruleVersion: 'v2',
      sourceLocator: null,
    },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/imports/job-1/files/file-1']}>
      <Routes>
        <Route path="/imports/:jobId/files/:fileId" element={<ImportFilePage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ImportFilePage', () => {
  beforeEach(() => {
    vi.mocked(getImportFile).mockResolvedValue(fileDetail)
  })

  it('renders file metadata and validations', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'export.qrp' })).toBeInTheDocument()
    expect(screen.getByText(/Pistas do nome do arquivo/)).toBeInTheDocument()
    expect(screen.getByText('qrp')).toBeInTheDocument()
    expect(screen.getByText('TOTAL')).toBeInTheDocument()
    expect(screen.getByText('Válido')).toBeInTheDocument()
  })

  it('shows loading, error and not-found states', async () => {
    vi.mocked(getImportFile).mockImplementation(() => new Promise(() => {}))
    const pending = renderPage()
    expect(await screen.findByLabelText('Carregando arquivo')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Importação' })).toBeInTheDocument()
    expect(screen.getByText('Parser')).toBeInTheDocument()
    expect(screen.getByText('Pistas do nome do arquivo')).toBeInTheDocument()
    expect(screen.getByText('Validações')).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
    pending.unmount()

    vi.mocked(getImportFile).mockRejectedValue(new Error('file err'))
    const failed = renderPage()
    expect(await screen.findByText('file err')).toBeInTheDocument()
    failed.unmount()

    vi.mocked(getImportFile).mockResolvedValue(null as never)
    renderPage()
    expect(await screen.findByText('Arquivo não encontrado')).toBeInTheDocument()
  })

  it('renders empty validations and missing filename fallbacks', async () => {
    vi.mocked(getImportFile).mockResolvedValue({
      ...fileDetail,
      originalFilename: '',
      parserName: null,
      parserVersion: null,
      recordsFound: null,
      parseStatus: null,
      validations: [],
    })
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Arquivo sem nome' })).toBeInTheDocument()
    expect(screen.getByText('Sem validações registradas.')).toBeInTheDocument()
  })
})
