import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  getDashboard,
  getImportFile,
  getImportJob,
  getProduct,
  getSale,
  getDebugStatus,
  listImports,
  listProducts,
  listSales,
  NAV_ITEMS,
  resetDataset,
  uploadQrp,
  waitForImportJob,
} from '../api'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type ProgressEvt = { lengthComputable: boolean; loaded: number; total: number }

function mockXhr(options: {
  status?: number
  body?: unknown
  text?: string
  error?: boolean
  statusText?: string
}) {
  const xhr = {
    upload: { onprogress: null as ((event: ProgressEvt) => void) | null },
    status: options.status ?? 202,
    responseText: options.text ?? JSON.stringify(options.body ?? {}),
    statusText: options.statusText ?? 'Error',
    onload: null as (() => void) | null,
    onerror: null as (() => void) | null,
    headers: {} as Record<string, string>,
    open: vi.fn(),
    setRequestHeader: vi.fn((key: string, value: string) => {
      xhr.headers[key] = value
    }),
    send: vi.fn(() => {
      xhr.upload.onprogress?.({ lengthComputable: true, loaded: 4, total: 8 })
      xhr.upload.onprogress?.({ lengthComputable: false, loaded: 0, total: 0 })
      queueMicrotask(() => {
        if (options.error) xhr.onerror?.()
        else xhr.onload?.()
      })
    }),
  }
  vi.stubGlobal(
    'XMLHttpRequest',
    vi.fn(function XMLHttpRequest(this: unknown) {
      return xhr
    }),
  )
  return xhr
}

describe('api', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exports navigation items', () => {
    expect(NAV_ITEMS.map((item) => item.to)).toEqual(['/', '/sales', '/products', '/imports'])
  })

  it('uploadQrp posts FormData, reports progress and waits for a terminal job', async () => {
    const job = { id: 'j1', status: 'SUCCEEDED', createdAt: 't', completedAt: null, files: [] }
    mockXhr({ status: 202, body: job })
    const file = new File(['x'], 'a.qrp', { type: 'application/octet-stream' })
    const onProgress = vi.fn()
    const result = await uploadQrp([file], onProgress)
    expect(result).toEqual(job)
    expect(onProgress).toHaveBeenCalledWith(file, 4, 8)
  })

  it('uploadQrp works with empty file list progress fallback and HTTP 200', async () => {
    const job = { id: 'j1', status: 'SUCCEEDED', createdAt: 't', completedAt: null, files: [] }
    mockXhr({ status: 200, body: job })
    const onProgress = vi.fn()
    await uploadQrp([], onProgress)
    expect(onProgress).toHaveBeenCalled()
  })

  it('uploadQrp sends basic auth and polls PROCESSING jobs', async () => {
    sessionStorage.setItem('datahub.basic', btoa('admin:secret'))
    const pending = { id: 'j1', status: 'PROCESSING', createdAt: 't', completedAt: null, files: [] }
    const done = { ...pending, status: 'SUCCEEDED' }
    const xhr = mockXhr({ status: 202, body: pending })
    vi.mocked(fetch).mockResolvedValue(jsonResponse(done))
    const result = await uploadQrp([new File(['x'], 'a.qrp')])
    expect(result.status).toBe('SUCCEEDED')
    expect(xhr.headers.Authorization).toBe(`Basic ${btoa('admin:secret')}`)
    expect(fetch).toHaveBeenCalledWith('/api/imports/j1', expect.any(Object))
  })

  it('uploadQrp clears auth on 401 and parses problem details', async () => {
    sessionStorage.setItem('datahub.basic', btoa('admin:secret'))
    mockXhr({ status: 401, body: { detail: 'nope' } })
    await expect(uploadQrp([new File(['x'], 'a.qrp')])).rejects.toMatchObject({ status: 401, detail: 'nope' })
    expect(sessionStorage.getItem('datahub.basic')).toBeNull()
  })

  it('uploadQrp rejects invalid JSON success bodies and network errors', async () => {
    mockXhr({ status: 202, text: '{not-json' })
    await expect(uploadQrp([new File(['x'], 'a.qrp')])).rejects.toBeInstanceOf(SyntaxError)
    mockXhr({ error: true })
    await expect(uploadQrp([new File(['x'], 'a.qrp')])).rejects.toMatchObject({ status: 0, detail: 'Falha de rede' })
  })

  it('uploadQrp maps non-JSON and detail-less error bodies', async () => {
    mockXhr({ status: 400, text: 'plain', statusText: 'Bad' })
    await expect(uploadQrp([new File(['x'], 'a.qrp')])).rejects.toMatchObject({ status: 400, detail: 'Bad' })
    mockXhr({ status: 400, body: { title: 'nope' }, statusText: '' })
    await expect(uploadQrp([new File(['x'], 'a.qrp')])).rejects.toMatchObject({ status: 400, message: 'HTTP 400' })
  })

  it('waitForImportJob returns terminal jobs and times out while processing', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ id: 'j1', status: 'SUCCEEDED', createdAt: 't', completedAt: null, files: [] }),
    )
    await expect(waitForImportJob('j1')).resolves.toMatchObject({ status: 'SUCCEEDED' })
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ id: 'j1', status: 'SUCCEEDED', createdAt: 't', completedAt: null, files: [] }),
    )
    await expect(
      waitForImportJob('j1', {
        intervalMs: 5,
        initial: { id: 'j1', status: 'PENDING', createdAt: 't', completedAt: null, files: [] },
      }),
    ).resolves.toMatchObject({ status: 'SUCCEEDED' })
    vi.mocked(fetch).mockImplementation(() =>
      Promise.resolve(
        jsonResponse({ id: 'j1', status: 'PROCESSING', createdAt: 't', completedAt: null, files: [] }),
      ),
    )
    await expect(waitForImportJob('j1', { intervalMs: 5, timeoutMs: 20 })).rejects.toThrow('tempo de espera')
  })

  it('listImports builds query params', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ content: [], page: 1, size: 5, totalElements: 0, totalPages: 0 })
    )
    await listImports({ page: 1, size: 5 })
    expect(fetch).toHaveBeenCalledWith('/api/imports?page=1&size=5', expect.any(Object))
  })

  it('getImportJob fetches by id', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ id: 'j1' }))
    await getImportJob('j1')
    expect(fetch).toHaveBeenCalledWith('/api/imports/j1', expect.any(Object))
  })

  it('getImportFile fetches nested resource', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ id: 'f1' }))
    await getImportFile('j1', 'f1')
    expect(fetch).toHaveBeenCalledWith('/api/imports/j1/files/f1', expect.any(Object))
  })

  it('listProducts builds optional query', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    )
    await listProducts({ q: 'molho', page: 0, size: 50 })
    expect(fetch).toHaveBeenCalledWith('/api/products?q=molho&page=0&size=50', expect.any(Object))
  })

  it('getProduct fetches by id', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ id: 'p1' }))
    await getProduct('p1')
    expect(fetch).toHaveBeenCalledWith('/api/products/p1', expect.any(Object))
  })

  it('listSales builds filter query', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    )
    await listSales({ productId: 'p1', from: '2026-01-01', to: '2026-02-01', page: 0, size: 50 })
    expect(fetch).toHaveBeenCalledWith(
      '/api/sales?productId=p1&from=2026-01-01&to=2026-02-01&page=0&size=50',
      expect.any(Object)
    )
  })

  it('getSale fetches by id', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ id: 's1' }))
    await getSale('s1')
    expect(fetch).toHaveBeenCalledWith('/api/sales/s1', expect.any(Object))
  })

  it('getDashboard builds query params', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ daily: [], topProducts: [] }))
    await getDashboard({ from: 'a', to: 'b', productId: 'p1' })
    expect(fetch).toHaveBeenCalledWith('/api/dashboard?from=a&to=b&productId=p1', expect.any(Object))
  })

  it('listProducts without optional filters', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    )
    await listProducts({})
    expect(fetch).toHaveBeenCalledWith('/api/products?', expect.any(Object))
  })

  it('listSales without optional filters', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    )
    await listSales({})
    expect(fetch).toHaveBeenCalledWith('/api/sales?', expect.any(Object))
  })

  it('getDashboard without optional filters', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ daily: [], topProducts: [] }))
    await getDashboard({})
    expect(fetch).toHaveBeenCalledWith('/api/dashboard?', expect.any(Object))
  })

  it('getDebugStatus and resetDataset call debug endpoints', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ enabled: true }))
    await expect(getDebugStatus()).resolves.toEqual({ enabled: true })
    expect(fetch).toHaveBeenCalledWith('/api/debug', expect.any(Object))
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse({ reset: true, artifactCount: 1, filesDeleted: 2 }))
    await expect(resetDataset()).resolves.toEqual({ reset: true, artifactCount: 1, filesDeleted: 2 })
    expect(fetch).toHaveBeenCalledWith('/api/debug/reset-dataset', expect.objectContaining({ method: 'POST' }))
  })

  it('throws ApiError with problem detail', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ detail: 'Não encontrado' }, 404))
    await expect(getSale('missing')).rejects.toMatchObject({
      status: 404,
      detail: 'Não encontrado',
      message: 'Não encontrado',
    })
  })

  it('throws ApiError with status text when JSON parse fails', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('plain', { status: 500, statusText: 'Server Error' }))
    await expect(getSale('x')).rejects.toBeInstanceOf(ApiError)
    await expect(getSale('x')).rejects.toMatchObject({ status: 500, detail: 'Server Error' })
  })

  it('throws ApiError without detail when problem has no string detail', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ title: 'Bad' }, 400))
    await expect(getSale('x')).rejects.toMatchObject({ status: 400, message: 'HTTP 400' })
  })

  it('returns undefined for 204 responses', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }))
    await expect(listImports()).resolves.toBeUndefined()
  })

  it('sends stored basic auth and clears it on 401', async () => {
    sessionStorage.setItem('datahub.basic', btoa('admin:secret'))
    vi.mocked(fetch).mockResolvedValue(jsonResponse({ detail: 'nope' }, 401))
    await expect(getSale('x')).rejects.toMatchObject({ status: 401 })
    expect(sessionStorage.getItem('datahub.basic')).toBeNull()
    const [, init] = vi.mocked(fetch).mock.calls[0]!
    const headers = new Headers(init?.headers)
    expect(headers.get('Authorization')).toBe(`Basic ${btoa('admin:secret')}`)
  })
})
