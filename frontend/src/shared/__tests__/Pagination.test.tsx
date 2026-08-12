import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from '../Pagination'

describe('Pagination', () => {
  it('hides when there is a single page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onPage={vi.fn()} />)
    expect(container.firstChild).toBeNull()
  })

  it('moves between pages', () => {
    const onPage = vi.fn()
    const { rerender } = render(<Pagination page={0} totalPages={3} onPage={onPage} />)
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    expect(onPage).toHaveBeenCalledWith(1)
    rerender(<Pagination page={2} totalPages={3} onPage={onPage} />)
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Anterior' }))
    expect(onPage).toHaveBeenCalledWith(1)
  })
})
