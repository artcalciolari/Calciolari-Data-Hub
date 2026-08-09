import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StateMessage } from '../StateMessage'

describe('StateMessage', () => {
  it('renders title and optional children', () => {
    render(
      <StateMessage title="Carregando">
        <p>Aguarde</p>
      </StateMessage>
    )
    expect(screen.getByRole('heading', { name: 'Carregando' })).toBeInTheDocument()
    expect(screen.getByText('Aguarde')).toBeInTheDocument()
  })

  it('applies error tone class', () => {
    const { container } = render(<StateMessage tone="error" title="Falhou" />)
    expect(container.querySelector('.empty-state.error')).toBeInTheDocument()
  })
})
