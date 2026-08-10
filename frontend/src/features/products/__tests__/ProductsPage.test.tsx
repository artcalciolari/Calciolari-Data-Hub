import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { listProducts } from '@/shared/api'
import { ProductsPage } from '../ProductsPage'

vi.mock('@/shared/api', () => ({
  listProducts: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/products']}>
      <ProductsPage />
    </MemoryRouter>
  )
}

describe('ProductsPage', () => {
  beforeEach(() => {
    vi.mocked(listProducts).mockResolvedValue({
      content: [{ id: 'p1', externalSource: 'interpdv', externalId: '41', name: 'MOLHO', unit: null }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('searches and lists products', async () => {
    renderPage()
    fireEvent.change(screen.getByLabelText('Buscar produto'), { target: { value: '  molho  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(await screen.findByRole('link', { name: 'MOLHO' })).toBeInTheDocument()
    expect(listProducts).toHaveBeenLastCalledWith({ q: 'molho', size: 50 })
  })

  it('shows loading, error and empty states', async () => {
    vi.mocked(listProducts).mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(await screen.findByRole('table')).toBeInTheDocument()

    vi.mocked(listProducts).mockRejectedValue(new Error('products err'))
    renderPage()
    expect(await screen.findByText('products err')).toBeInTheDocument()

    vi.mocked(listProducts).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    renderPage()
    expect(await screen.findByText('Nenhum produto publicado.')).toBeInTheDocument()
  })

  it('navigates on row click', async () => {
    const assign = vi.fn()
    vi.stubGlobal('location', { ...window.location, assign })
    renderPage()
    await screen.findByRole('link', { name: 'MOLHO' })
    fireEvent.click(screen.getByRole('link', { name: 'MOLHO' }).closest('tr')!)
    expect(assign).toHaveBeenCalledWith('/products/p1')
    vi.unstubAllGlobals()
  })
})
