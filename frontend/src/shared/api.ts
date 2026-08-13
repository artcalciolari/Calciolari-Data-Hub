import type { IconName } from '@/shared/icons'
import { clearBasicAuth, readBasicAuth } from '@/shared/auth'

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
  recordsFound?: number | null
  parseStatus?: string | null
  productName?: string | null
  productExternalId?: string | null
  parsedQuantity?: string | null
  parsedRevenue?: string | null
  sourceQuantity?: string | null
  quantityValidationStatus?: string | null
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
  previousStock: string | null
  resultingStock: string | null
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
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  headers.set('Content-Type', 'application/json')
  const auth = readBasicAuth()
  if (auth) {
    headers.set('Authorization', `Basic ${auth}`)
  }
  const response = await fetch(`${base}${path}`, { ...init, headers })
  if (!response.ok) {
    if (response.status === 401) {
      clearBasicAuth()
    }
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

function delay(ms: number) {
  return new Promise<void>((resolve) => {
    setTimeout(resolve, ms)
  })
}

function problemDetail(status: number, raw: string, fallback: string): ApiError {
  try {
    const problem: unknown = JSON.parse(raw)
    const detail =
      problem !== null &&
      typeof problem === 'object' &&
      'detail' in problem &&
      typeof (problem as { detail: unknown }).detail === 'string'
        ? (problem as { detail: string }).detail
        : undefined
    return new ApiError(status, detail)
  } catch {
    return new ApiError(status, fallback)
  }
}

export async function waitForImportJob(
  id: string,
  options: { intervalMs?: number; timeoutMs?: number; initial?: ImportJob } = {},
): Promise<ImportJob> {
  const intervalMs = options.intervalMs ?? 250
  const timeoutMs = options.timeoutMs ?? 60_000
  const started = Date.now()
  let job = options.initial ?? (await getImportJob(id))
  while (job.status === 'PENDING' || job.status === 'PROCESSING') {
    if (Date.now() - started > timeoutMs) {
      throw new Error('Importação excedeu o tempo de espera')
    }
    await delay(intervalMs)
    job = await getImportJob(id)
  }
  return job
}

export function uploadQrp(files: File[], onProgress?: (file: File, done: number, total: number) => void) {
  const body = new FormData()
  files.forEach((file) => body.append('files', file, file.name))
  const fallback = files[0] ?? new File([], '')
  return new Promise<ImportJob>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${base}/api/imports/qrp`)
    xhr.setRequestHeader('Accept', 'application/json')
    const auth = readBasicAuth()
    if (auth) {
      xhr.setRequestHeader('Authorization', `Basic ${auth}`)
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.(fallback, event.loaded, event.total)
      }
    }
    xhr.onload = () => {
      if (xhr.status === 401) {
        clearBasicAuth()
      }
      if (xhr.status === 202 || xhr.status === 200) {
        try {
          const job = JSON.parse(xhr.responseText) as ImportJob
          waitForImportJob(job.id, { initial: job }).then(resolve, reject)
        } catch (error) {
          reject(error)
        }
        return
      }
      reject(problemDetail(xhr.status, xhr.responseText, xhr.statusText || `HTTP ${xhr.status}`))
    }
    xhr.onerror = () => {
      reject(new ApiError(0, 'Falha de rede'))
    }
    xhr.send(body)
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
  { to: '/sales', label: 'Vendas', icon: 'cart' },
  { to: '/products', label: 'Produtos', icon: 'tag' },
  { to: '/imports', label: 'Importar', icon: 'upload' },
]
