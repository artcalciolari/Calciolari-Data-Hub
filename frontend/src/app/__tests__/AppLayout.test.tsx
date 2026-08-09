import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AppLayout } from '../AppLayout'

describe('AppLayout', () => {
  it('renders brand logo and mobile bottom navigation', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <AppLayout />
      </MemoryRouter>
    )
    expect(screen.getByRole('img', { name: /Calciolari Cucina Italiana/i })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: /navegação inferior/i })
    expect(nav).toBeInTheDocument()
    for (const label of ['Resumo', 'Vendas', 'Produtos', 'Importar']) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
  })
})
