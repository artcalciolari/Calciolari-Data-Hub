import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { listProducts, listSales } from '@/shared/api'
import { SalesPage } from '../SalesPage'

vi.mock('@/shared/api', () => ({
  listProducts: vi.fn(),
  listSales: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/sales']}>
      <SalesPage />
    </MemoryRouter>
  )
}

describe('SalesPage', () => {
  beforeEach(() => {
    vi.mocked(listProducts).mockResolvedValue({
      content: [{ id: 'p1', externalSource: 'interpdv', externalId: '41', name: 'MOLHO', unit: null }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(listSales).mockResolvedValue({
      content: [{ id: 's1', externalSource: 'interpdv', externalSaleId: '101', occurredAt: '2026-07-01T12:00:00', total: '55.90' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('filters and lists sales', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: '101' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'p1' } })
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-07-01T00:00' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-07-31T23:59' } })
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }))
    expect(listSales).toHaveBeenLastCalledWith({
      productId: 'p1',
      from: '2026-07-01T00:00',
      to: '2026-07-31T23:59',
      size: 50,
    })
  })

  it('shows loading, error and empty states', async () => {
    vi.mocked(listSales).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(await screen.findByRole('table')).toBeInTheDocument()

    vi.mocked(listSales).mockRejectedValue(new Error('sales err'))
    renderPage()
    expect(await screen.findByText('sales err')).toBeInTheDocument()

    vi.mocked(listSales).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    renderPage()
    expect(await screen.findByText('Nenhuma venda no filtro atual.')).toBeInTheDocument()
  })

  it('navigates on row click', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    renderPage()
    await screen.findByRole('link', { name: '101' })
    fireEvent.click(screen.getByRole('link', { name: '101' }).closest('tr')!)
    expect(assign).toHaveBeenCalledWith('/sales/s1')
    vi.unstubAllGlobals()
  })
})
