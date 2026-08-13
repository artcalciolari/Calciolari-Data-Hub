import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
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
      <Routes>
        <Route path="/sales" element={<SalesPage />} />
        <Route path="/sales/:id" element={<div>sale detail</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('SalesPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
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
      page: 0,
      size: 50,
    })
  })

  it('restores persisted sales filters', async () => {
    sessionStorage.setItem(
      'datahub.filters.sales',
      JSON.stringify({ productId: 'p1', from: '2026-07-01T00:00', to: '2026-07-31T23:59' }),
    )
    renderPage()
    expect(await screen.findByRole('link', { name: '101' })).toBeInTheDocument()
    expect(listSales).toHaveBeenCalledWith({
      productId: 'p1',
      from: '2026-07-01T00:00',
      to: '2026-07-31T23:59',
      page: 0,
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
    renderPage()
    const link = await screen.findByRole('link', { name: '101' })
    fireEvent.click(link.closest('tr')!)
    expect(await screen.findByText('sale detail')).toBeInTheDocument()
  })

  it('paginates and keyboard-activates a row', async () => {
    vi.mocked(listSales).mockResolvedValue({
      content: [{ id: 's1', externalSource: 'interpdv', externalSaleId: '101', occurredAt: '2026-07-01T12:00:00', total: '55.90' }],
      page: 0,
      size: 50,
      totalElements: 80,
      totalPages: 2,
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Próxima' }))
    expect(listSales).toHaveBeenCalledWith({
      productId: undefined,
      from: undefined,
      to: undefined,
      page: 1,
      size: 50,
    })
    expect(await screen.findByRole('link', { name: '101' })).toBeInTheDocument()
    const row = screen.getByRole('link', { name: '101' }).closest('tr')!
    fireEvent.keyDown(row, { key: 'Escape' })
    fireEvent.keyDown(row, { key: 'Enter' })
    expect(await screen.findByText('sale detail')).toBeInTheDocument()
  })
})
