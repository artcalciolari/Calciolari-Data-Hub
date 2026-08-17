import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DailyBars } from '../DailyBars'

describe('DailyBars', () => {
  it('renders a labelled bar chart', () => {
    render(
      <DailyBars
        points={[
          { date: '2026-07-01', quantity: '1', revenue: '10' },
          { date: '2026-07-02', quantity: '2', revenue: '20' },
        ]}
        metric="revenue"
        format={(value) => value}
        ariaLabel="chart"
      />
    )
    expect(screen.getByRole('img', { name: 'chart' })).toBeInTheDocument()
    expect(screen.getByText('20')).toBeInTheDocument()
    expect(screen.getByText('01/07')).toBeInTheDocument()
    expect(screen.getByTitle('02/07/2026: 20')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'chart — detalhes' })).toBeInTheDocument()
    expect(screen.getByRole('listitem', { name: '02/07/2026: 20' })).toBeInTheDocument()
  })

  it('keeps all 31 days represented while sampling compact mobile tick labels', () => {
    const points = Array.from({ length: 31 }, (_, index) => ({
      date: `2026-07-${String(index + 1).padStart(2, '0')}`,
      quantity: String(index + 1),
      revenue: String(index + 1),
    }))
    const { container } = render(
      <DailyBars
        points={points}
        metric="revenue"
        format={(value) => `R$ ${value}`}
        ariaLabel="Série diária"
      />
    )

    expect(container.querySelectorAll('.bar-wrap')).toHaveLength(31)
    expect(screen.getAllByTitle(/\/2026:/)).toHaveLength(31)
    expect(screen.getAllByRole('listitem')).toHaveLength(31)
    const labels = Array.from(container.querySelectorAll('.bar-label')).map((label) => label.textContent)
    expect(labels.filter(Boolean)).toHaveLength(5)
    expect(labels[0]).toBe('01/07')
    expect(labels[30]).toBe('31/07')
    expect(screen.getByRole('listitem', { name: '31/07/2026: R$ 31' })).toBeInTheDocument()
  })
})
