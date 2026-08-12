import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { TableSkeleton } from '../TableSkeleton'

describe('TableSkeleton', () => {
  it('renders the requested rows and columns', () => {
    render(<TableSkeleton rows={2} cols={3} />)
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getAllByRole('row')).toHaveLength(3)
  })
})
