import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { listProducts } from '@/shared/api'
import { ProductsPage } from '../ProductsPage'

vi.mock('@/shared/api', () => ({
  listProducts: vi.fn(),
}))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/products']}>
      <Routes>
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/products/:id" element={<div>product detail</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('ProductsPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
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
    expect(listProducts).toHaveBeenLastCalledWith({ q: 'molho', page: 0, size: 50 })
  })

  it('restores persisted product search', async () => {
    sessionStorage.setItem('datahub.filters.products', JSON.stringify({ q: 'nhoque' }))
    renderPage()
    expect(await screen.findByRole('link', { name: 'MOLHO' })).toBeInTheDocument()
    expect(listProducts).toHaveBeenCalledWith({ q: 'nhoque', page: 0, size: 50 })
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

  it('navigates on row click and keyboard', async () => {
    renderPage()
    const link = await screen.findByRole('link', { name: 'MOLHO' })
    fireEvent.click(link.closest('tr')!)
    expect(await screen.findByText('product detail')).toBeInTheDocument()
  })

  it('paginates and keyboard-activates a row', async () => {
    vi.mocked(listProducts).mockResolvedValue({
      content: [{ id: 'p1', externalSource: 'interpdv', externalId: '41', name: 'MOLHO', unit: null }],
      page: 0,
      size: 50,
      totalElements: 80,
      totalPages: 2,
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Próxima' }))
    expect(listProducts).toHaveBeenCalledWith({ q: undefined, page: 1, size: 50 })
    expect(await screen.findByRole('link', { name: 'MOLHO' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(listProducts).toHaveBeenCalledWith({ q: undefined, page: 0, size: 50 })
    expect(await screen.findByRole('link', { name: 'MOLHO' })).toBeInTheDocument()
    const row = screen.getByRole('link', { name: 'MOLHO' }).closest('tr')!
    fireEvent.keyDown(row, { key: 'Escape' })
    fireEvent.keyDown(row, { key: 'Enter' })
    expect(await screen.findByText('product detail')).toBeInTheDocument()
  })
})
