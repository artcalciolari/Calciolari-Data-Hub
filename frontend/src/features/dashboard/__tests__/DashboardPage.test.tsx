import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { DashboardPage } from '../DashboardPage'

vi.mock('@/shared/api', () => ({
  getDashboard: vi.fn().mockResolvedValue({
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
    ],
  }),
  listSales: vi.fn().mockResolvedValue({
    content: [
      { id: 's1', externalSource: 'interpdv', externalSaleId: '101', occurredAt: '2026-07-01T12:05:35', total: '55.90' },
    ],
    page: 0,
    size: 5,
    totalElements: 1,
    totalPages: 1,
  }),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <DashboardPage />
    </MemoryRouter>
  )
}

describe('DashboardPage', () => {
  it('renders hero, KPIs, top products and recent sales', async () => {
    renderPage()
    expect(await screen.findByText('Faturamento no período')).toBeInTheDocument()
    expect(screen.getByText(/3\.705,88/)).toBeInTheDocument()
    expect(screen.getByText(/100 vendas · ticket médio/)).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Evolução diária' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Top produtos' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Vendas recentes' })).toBeInTheDocument()

    expect(screen.getByRole('link', { name: /MOLHO POMODORO/ })).toHaveAttribute('href', '/products/p1')
    expect(screen.getByRole('link', { name: /#101/ })).toHaveAttribute('href', '/sales/s1')
  })

  it('switches the daily chart metric from revenue to quantity', async () => {
    renderPage()
    expect(await screen.findByText(/210,50/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Qtd' }))
    expect(screen.getByRole('button', { name: 'Qtd' })).toHaveClass('active')
    expect(screen.getByText(/^5$/)).toBeInTheDocument()
  })
})
