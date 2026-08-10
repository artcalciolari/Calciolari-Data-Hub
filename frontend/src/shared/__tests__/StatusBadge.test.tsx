import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusBadge } from '../StatusBadge'

describe('StatusBadge', () => {
  it.each([
    ['IMPORTED', 'Importado', 'badge ok'],
    ['VALID', 'Válido', 'badge ok'],
    ['SUCCEEDED', 'Concluído', 'badge ok'],
    ['WARNING', 'Atenção', 'badge warn'],
    ['PARTIAL_SUCCESS', 'Parcial', 'badge warn'],
    ['PROCESSING', 'Processando', 'badge muted'],
    ['PENDING', 'Pendente', 'badge muted'],
    ['INVALID', 'Inválido', 'badge danger'],
    ['FAILED', 'Falhou', 'badge danger'],
  ] as const)('maps %s to label and class', (status, label, className) => {
    render(<StatusBadge status={status} />)
    const badge = screen.getByText(label)
    expect(badge).toHaveClass(...className.split(' '))
  })

  it('falls back for unknown status', () => {
    render(<StatusBadge status="custom" />)
    expect(screen.getByText('CUSTOM')).toHaveClass('badge', 'muted')
  })
})
