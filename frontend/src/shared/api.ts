import type { IconName } from '@/shared/icons'

const base = ''

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ImportFileSummary {
  id: string
  originalFilename: string
  status: 'PENDING' | 'PROCESSING' | 'IMPORTED' | 'WARNING' | 'INVALID' | 'FAILED'
  deduplicated: boolean
  duplicateOfImportFileId: string | null
  parseAttemptId: string | null
  createdAt: string
  completedAt: string | null
}

export interface ImportJob {
  id: string
  status: 'PENDING' | 'PROCESSING' | 'SUCCEEDED' | 'PARTIAL_SUCCESS' | 'FAILED'
  createdAt: string
  completedAt: string | null
  files: ImportFileSummary[]
}

export interface ValidationItem {
  code: string
  status: 'VALID' | 'WARNING' | 'INVALID'
  sourceValue: string | null
  calculatedValue: string | null
  difference: string | null
  tolerance: string | null
  ruleVersion: string
  sourceLocator: string | null
}

export interface ImportFileDetail extends ImportFileSummary {
  jobId: string
  rawArtifactId: string
  source: string
  sha256: string
  byteSize: number
  parseStatus: string | null
  recordsFound: number | null
  parserName: string | null
  parserVersion: string | null
  filenameHints: unknown
  validations: ValidationItem[]
}

export interface ProductSummary {
  id: string
  externalSource: string
  externalId: string
  name: string
  unit: string | null
}

export interface ProductDetail extends ProductSummary {
  firstSeenParseAttemptId: string
}

export interface SaleSummary {
  id: string
  externalSource: string
  externalSaleId: string
  occurredAt: string | null
  total: string | null
}

export interface SaleItem {
  id: string
  productId: string
  productName: string
  productExternalId: string
  sourceRecordIndex: number
  quantity: string
  unitPrice: string
  discountPercentage: string | null
  total: string
}

export interface SaleDetail {
  id: string
  externalSource: string
  externalSaleId: string
  occurredAt: string | null
  items: SaleItem[]
}

export interface DailyPoint {
  date: string
  quantity: string
  revenue: string
}

export interface TopProduct {
  productId: string
  name: string
  externalId: string
  quantity: string
  revenue: string
}

export interface DashboardResponse {
  revenueTotal: string
  quantityTotal: string
  salesCount: number
  itemCount: number
  averageTicket: string | null
  firstMovementAt: string | null
  lastMovementAt: string | null
  daily: DailyPoint[]
  topProducts: TopProduct[]
}

export class ApiError extends Error {
  readonly status: number
  readonly detail: string | undefined

  constructor(status: number, detail: string | undefined) {
    super(detail ?? `HTTP ${status}`)
    this.status = status
    this.detail = detail
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    headers: { Accept: 'application/json', ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }) },
    ...init,
  })
  if (!response.ok) {
    let detail: string | undefined
    try {
      const problem = await response.json()
      detail = typeof problem?.detail === 'string' ? problem.detail : undefined
    } catch {
      detail = response.statusText
    }
    throw new ApiError(response.status, detail)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function uploadQrp(files: File[], onProgress?: (file: File, done: number, total: number) => void) {
  const body = new FormData()
  files.forEach((file) => body.append('files', file, file.name))
  onProgress?.(files[0] ?? new File([], ''), 0, files.length)
  return request<ImportJob>('/api/imports/qrp', { method: 'POST', body })
    .then((job) => {
      onProgress?.(files[files.length - 1] ?? new File([], ''), files.length, files.length)
      return job
    })
}

export function listImports(params: { page?: number; size?: number } = {}) {
  const q = new URLSearchParams()
  if (params.page != null) q.set('page', String(params.page))
  if (params.size != null) q.set('size', String(params.size))
  return request<Page<ImportJob>>(`/api/imports?${q.toString()}`)
}

export function getImportJob(id: string) {
  return request<ImportJob>(`/api/imports/${id}`)
}

export function getImportFile(jobId: string, fileId: string) {
  return request<ImportFileDetail>(`/api/imports/${jobId}/files/${fileId}`)
}

export function listProducts(params: { q?: string; page?: number; size?: number } = {}) {
  const q = new URLSearchParams()
  if (params.q) q.set('q', params.q)
  if (params.page != null) q.set('page', String(params.page))
  if (params.size != null) q.set('size', String(params.size))
  return request<Page<ProductSummary>>(`/api/products?${q.toString()}`)
}

export function getProduct(id: string) {
  return request<ProductDetail>(`/api/products/${id}`)
}

export function listSales(params: {
  productId?: string
  from?: string
  to?: string
  page?: number
  size?: number
} = {}) {
  const q = new URLSearchParams()
  if (params.productId) q.set('productId', params.productId)
  if (params.from) q.set('from', params.from)
  if (params.to) q.set('to', params.to)
  if (params.page != null) q.set('page', String(params.page))
  if (params.size != null) q.set('size', String(params.size))
  return request<Page<SaleSummary>>(`/api/sales?${q.toString()}`)
}

export function getSale(id: string) {
  return request<SaleDetail>(`/api/sales/${id}`)
}

export function getDashboard(params: { from?: string; to?: string; productId?: string } = {}) {
  const q = new URLSearchParams()
  if (params.from) q.set('from', params.from)
  if (params.to) q.set('to', params.to)
  if (params.productId) q.set('productId', params.productId)
  return request<DashboardResponse>(`/api/dashboard?${q.toString()}`)
}

export interface NavItem {
  to: string
  label: string
  icon: IconName
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Resumo', icon: 'chart' },
  { to: '/sales', label: 'Vendas', icon: 'receipt' },
  { to: '/products', label: 'Produtos', icon: 'box' },
  { to: '/imports', label: 'Importar', icon: 'upload' },
]
