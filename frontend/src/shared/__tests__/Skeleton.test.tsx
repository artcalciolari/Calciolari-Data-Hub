import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Skeleton } from '../Skeleton'

describe('Skeleton', () => {
  it('renders with default and custom class names', () => {
    const { container, rerender } = render(<Skeleton />)
    expect(container.querySelector('.skeleton')).toBeInTheDocument()
    rerender(<Skeleton className="line w-40" />)
    expect(container.querySelector('.skeleton.line.w-40')).toBeInTheDocument()
  })
})
