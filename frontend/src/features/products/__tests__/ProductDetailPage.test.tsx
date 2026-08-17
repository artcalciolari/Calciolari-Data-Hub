import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getDashboard, getProduct } from '@/shared/api'
import { ProductDetailPage } from '../ProductDetailPage'

vi.mock('@/shared/api', () => ({
  getProduct: vi.fn(),
  getDashboard: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/products/p1']}>
      <Routes>
        <Route path="/products/:id" element={<ProductDetailPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getProduct).mockResolvedValue({
      id: 'p1',
      externalSource: 'interpdv',
      externalId: '41',
      name: 'MOLHO',
      unit: null,
      firstSeenParseAttemptId: 'a1',
    })
    vi.mocked(getDashboard).mockResolvedValue({
      revenueTotal: '100',
      quantityTotal: '2',
      salesCount: 1,
      itemCount: 2,
      averageTicket: '100',
      firstMovementAt: null,
      lastMovementAt: null,
      daily: [{ date: '2026-07-01', quantity: '2', revenue: '100' }],
      topProducts: [],
    })
  })

  it('renders product metrics and daily chart', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'MOLHO' })).toBeInTheDocument()
    expect(screen.getByText(/Código 41/)).toBeInTheDocument()
    expect(screen.getByText(/Primeira movimentação/)).toBeInTheDocument()
  })

  it('covers loading and error branches', async () => {
    vi.mocked(getProduct).mockImplementation(() => new Promise(() => {}))
    const pendingProduct = renderPage()
    expect(await screen.findByLabelText('Carregando produto')).toBeInTheDocument()
    pendingProduct.unmount()

    vi.mocked(getProduct).mockResolvedValue({
      id: 'p1',
      externalSource: 'interpdv',
      externalId: '41',
      name: 'MOLHO',
      unit: null,
      firstSeenParseAttemptId: 'a1',
    })
    vi.mocked(getDashboard).mockImplementation(() => new Promise(() => {}))
    const pendingDashboard = renderPage()
    expect(await screen.findByLabelText('Carregando produto')).toBeInTheDocument()
    pendingDashboard.unmount()

    vi.mocked(getProduct).mockRejectedValue(new Error('product err'))
    vi.mocked(getDashboard).mockResolvedValue({
      revenueTotal: '0',
      quantityTotal: '0',
      salesCount: 0,
      itemCount: 0,
      averageTicket: null,
      firstMovementAt: null,
      lastMovementAt: null,
      daily: [],
      topProducts: [],
    })
    const productError = renderPage()
    expect(await screen.findByText('product err')).toBeInTheDocument()
    productError.unmount()

    vi.mocked(getProduct).mockResolvedValue({
      id: 'p1',
      externalSource: 'interpdv',
      externalId: '41',
      name: 'MOLHO',
      unit: null,
      firstSeenParseAttemptId: 'a1',
    })
    vi.mocked(getDashboard).mockRejectedValue(new Error('dash err'))
    const dashError = renderPage()
    expect(await screen.findByText('dash err')).toBeInTheDocument()
    dashError.unmount()

    vi.mocked(getDashboard).mockResolvedValue(null as never)
    renderPage()
    expect(await screen.findByText('Produto não encontrado')).toBeInTheDocument()
  })

  it('shows empty daily series message', async () => {
    vi.mocked(getDashboard).mockResolvedValue({
      revenueTotal: '0',
      quantityTotal: '0',
      salesCount: 0,
      itemCount: 0,
      averageTicket: null,
      firstMovementAt: null,
      lastMovementAt: null,
      daily: [],
      topProducts: [],
    })
    renderPage()
    expect(await screen.findByText('Sem série publicada para este produto.')).toBeInTheDocument()
  })
})
