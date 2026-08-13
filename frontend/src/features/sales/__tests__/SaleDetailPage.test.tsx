import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { getSale } from '@/shared/api'
import { SaleDetailPage } from '../SaleDetailPage'

vi.mock('@/shared/api', () => ({
  getSale: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/sales/s1']}>
      <Routes>
        <Route path="/sales/:id" element={<SaleDetailPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('SaleDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getSale).mockResolvedValue({
      id: 's1',
      externalSource: 'interpdv',
      externalSaleId: '101',
      occurredAt: '2026-07-01T12:00:00',
      items: [
        {
          id: 'i1',
          productId: 'p1',
          productName: 'MOLHO',
          productExternalId: '41',
          sourceRecordIndex: 0,
          quantity: '1',
          unitPrice: '10',
          discountPercentage: '8',
          total: '9.2',
          previousStock: '10',
          resultingStock: '9',
        },
      ],
    })
  })

  it('renders sale items', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Venda 101' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /41 · MOLHO/ })).toHaveAttribute('href', '/products/p1')
    expect(screen.getByText(/desc\. 8%/)).toBeInTheDocument()
    fireEvent.click(screen.getByText('Estoque'))
    expect(screen.getByText(/Anterior/)).toBeInTheDocument()
  })

  it('shows loading, error, not-found and empty item states', async () => {
    vi.mocked(getSale).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(await screen.findByText('Carregando venda…')).toBeInTheDocument()

    vi.mocked(getSale).mockRejectedValue(new Error('sale err'))
    renderPage()
    expect(await screen.findByText('sale err')).toBeInTheDocument()

    vi.mocked(getSale).mockResolvedValue(null as never)
    renderPage()
    expect(await screen.findByText('Venda não encontrada')).toBeInTheDocument()

    vi.mocked(getSale).mockResolvedValue({
      id: 's1',
      externalSource: 'interpdv',
      externalSaleId: '101',
      occurredAt: null,
      items: [{
        id: 'i2',
        productId: 'p1',
        productName: 'MOLHO',
        productExternalId: '41',
        sourceRecordIndex: 0,
        quantity: '1',
        unitPrice: '10',
        discountPercentage: null,
        total: '10',
        previousStock: null,
        resultingStock: null,
      }],
    })
    renderPage()
    expect(await screen.findByRole('link', { name: /41 · MOLHO/ })).toBeInTheDocument()
    expect(screen.queryByText('Estoque')).not.toBeInTheDocument()

    vi.mocked(getSale).mockResolvedValue({
      id: 's1',
      externalSource: 'interpdv',
      externalSaleId: '101',
      occurredAt: null,
      items: [],
    })
    renderPage()
    expect(await screen.findByText('Sem itens publicados.')).toBeInTheDocument()
  })
})
