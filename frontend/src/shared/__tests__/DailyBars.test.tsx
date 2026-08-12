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
  })
})
