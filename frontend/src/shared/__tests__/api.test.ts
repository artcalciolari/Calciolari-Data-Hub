import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  getDashboard,
  getImportFile,
  getImportJob,
  getProduct,
  getSale,
  listImports,
  listProducts,
  listSales,
  NAV_ITEMS,
  uploadQrp,
} from '../api'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('api', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exports navigation items', () => {
    expect(NAV_ITEMS.map((item) => item.to)).toEqual(['/', '/sales', '/products', '/imports'])
  })

  it('uploadQrp posts FormData and reports progress', async () => {
    const job = { id: 'j1', status: 'PENDING', createdAt: 't', completedAt: null, files: [] }
    vi.mocked(fetch).mockResolvedValue(jsonResponse(job))
    const file = new File(['x'], 'a.qrp', { type: 'application/octet-stream' })
    const onProgress = vi.fn()
    const result = await uploadQrp([file], onProgress)
    expect(result).toEqual(job)
    expect(onProgress).toHaveBeenCalledTimes(2)
    const [, init] = vi.mocked(fetch).mock.calls[0]!
    expect(init?.method).toBe('POST')
    expect(init?.body).toBeInstanceOf(FormData)
  })

  it('uploadQrp works with empty file list progress fallback', async () => {
    const job = { id: 'j1', status: 'PENDING', createdAt: 't', completedAt: null, files: [] }
    vi.mocked(fetch).mockResolvedValue(jsonResponse(job))
    const onProgress = vi.fn()
    await uploadQrp([], onProgress)
    expect(onProgress).toHaveBeenCalled()
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
})
