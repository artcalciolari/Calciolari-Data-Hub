import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getDashboard, listSales } from '@/shared/api'
import { DashboardPage } from '../DashboardPage'

vi.mock('@/shared/api', () => ({
  getDashboard: vi.fn(),
  listSales: vi.fn(),
}))

const dashboardPayload = {
  revenueTotal: '3705.88',
  quantityTotal: '63.828',
  salesCount: 100,
  itemCount: 149,
  averageTicket: '37.06',
  firstMovementAt: '2026-07-01T12:05:35',
  lastMovementAt: '2026-08-07T17:33:48',
  daily: [
    { date: '2026-07-01', quantity: '3.5', revenue: '100.00' },
    { date: '2026-07-02', quantity: '5', revenue: '210.50' },
  ],
  topProducts: [
    { productId: 'p1', name: 'MOLHO POMODORO', externalId: '41', quantity: '52.986', revenue: '3013.07' },
    { productId: 'p2', name: 'OUTRO', externalId: '42', quantity: '1', revenue: '10' },
    { productId: 'p3', name: 'SEM TOTAL', externalId: '43', quantity: '1', revenue: '' },
  ],
}

const salesPayload = {
  content: [
    { id: 's1', externalSource: 'interpdv', externalSaleId: '101', occurredAt: '2026-07-01T12:05:35', total: '55.90' },
  ],
  page: 0,
  size: 5,
  totalElements: 1,
  totalPages: 1,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <DashboardPage />
    </MemoryRouter>
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(getDashboard).mockResolvedValue(dashboardPayload)
    vi.mocked(listSales).mockResolvedValue(salesPayload)
  })

  it('renders hero, KPIs, top products and recent sales', async () => {
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    expect(screen.getByText(/3\.705,88/)).toBeInTheDocument()
    expect(screen.getByText(/100 vendas · ticket médio/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Produto em destaque' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Evolução diária' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Top produtos' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Vendas recentes' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver vendas' })).toHaveAttribute('href', '/sales')
    expect(screen.getAllByRole('link', { name: /MOLHO POMODORO/ })[0]).toHaveAttribute('href', '/products/p1')
    expect(screen.getByRole('link', { name: /#101/ })).toHaveAttribute('href', '/sales/s1')
    expect(screen.getByLabelText('De')).toHaveAttribute('type', 'text')
    expect(screen.getByLabelText('De')).toHaveAttribute('placeholder', 'dd/mm/yyyy HH:mm')
  })

  it('switches the daily chart metric from revenue to quantity', async () => {
    renderPage()
    expect(await screen.findByText('R$ 210,50', { selector: '.chart-readout-value' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Qtd' }))
    expect(screen.getByRole('button', { name: 'Qtd' })).toHaveClass('active')
    expect(screen.getByText(/^5$/, { selector: '.chart-readout-value' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'R$' }))
    expect(screen.getByRole('button', { name: 'R$' })).toHaveClass('active')
  })

  it('shows skeleton while loading', () => {
    vi.mocked(getDashboard).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(screen.getByLabelText('Carregando resumo')).toBeInTheDocument()
  })

  it('shows error states for dashboard and recent sales independently', async () => {
    vi.mocked(getDashboard).mockRejectedValue(new Error('dash fail'))
    renderPage()
    expect(await screen.findByText('dash fail')).toBeInTheDocument()

    vi.mocked(getDashboard).mockResolvedValue(dashboardPayload)
    vi.mocked(listSales).mockRejectedValue(new Error('recent fail'))
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    expect(await screen.findByText('recent fail')).toBeInTheDocument()
  })

  it('shows empty data message when payloads are missing', async () => {
    vi.mocked(getDashboard).mockResolvedValue(null as never)
    renderPage()
    expect(await screen.findByText('Nenhum dado disponível')).toBeInTheDocument()
  })

  it('renders empty sections, null average ticket and date filter', async () => {
    vi.mocked(getDashboard).mockResolvedValue({
      ...dashboardPayload,
      averageTicket: null,
      daily: [],
      topProducts: [],
    })
    vi.mocked(listSales).mockResolvedValue({ ...salesPayload, content: [] })
    renderPage()
    expect(await screen.findByText('Sem movimentações publicadas no período.')).toBeInTheDocument()
    expect(screen.getByText('Nenhum produto publicado ainda.')).toBeInTheDocument()
    expect(screen.getByText('Nenhuma venda publicada ainda.')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '01/07/2026 00:00' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '31/07/2026 23:59' } })
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
    expect(getDashboard).toHaveBeenCalledWith({ from: '2026-07-01T00:00', to: '2026-07-31T23:59:59' })
  })

  it('restores persisted date filters', async () => {
    sessionStorage.setItem('datahub.filters.dashboard', JSON.stringify({ from: '2026-07-01T00:00', to: '2026-07-31T23:59' }))
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    expect(screen.getByLabelText('De')).toHaveValue('01/07/2026 00:00')
    expect(screen.getByLabelText('Até')).toHaveValue('31/07/2026 23:59:59')
    expect(getDashboard).toHaveBeenCalledWith({ from: '2026-07-01T00:00', to: '2026-07-31T23:59:59' })
  })

  it('does not apply a persisted inverted range', async () => {
    sessionStorage.setItem('datahub.filters.dashboard', JSON.stringify({ from: '2026-07-02T00:00', to: '2026-07-01T23:59' }))
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    expect(screen.getByText('Até deve ser igual ou posterior a De.')).toBeInTheDocument()
    expect(getDashboard).not.toHaveBeenCalledWith({ from: '2026-07-02T00:00', to: '2026-07-01T23:59:59' })
  })

  it('shows an error and does not submit malformed date filters', async () => {
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '31/02/2026 00:00' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '31/07/2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
    expect(screen.getAllByRole('alert')).toHaveLength(2)
    expect(getDashboard).not.toHaveBeenCalledWith({ from: '31/02/2026 00:00', to: '31/07/2026' })
  })

  it('rejects an inverted range and associates the error with Até', async () => {
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '02/07/2026 00:00' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '01/07/2026 23:59' } })
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
    expect(screen.getByText('Até deve ser igual ou posterior a De.')).toBeInTheDocument()
    expect(screen.getByLabelText('Até')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Até')).toHaveAttribute('aria-describedby', 'dashboard-to-hint dashboard-to-error')
    expect(getDashboard).not.toHaveBeenCalledWith({ from: '2026-07-02T00:00', to: '2026-07-01T23:59:59' })
    expect(sessionStorage.getItem('datahub.filters.dashboard')).toBeNull()
  })

  it('shows recent sales loading state', async () => {
    vi.mocked(listSales).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(await screen.findByText('Carregando vendas…')).toBeInTheDocument()
  })
})
