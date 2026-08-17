import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DailyBars } from '../DailyBars'

const format = (value: string) => `R$ ${value}`

function day(index: number, revenue: string, quantity = '1') {
  return {
    date: `2026-07-${String(index).padStart(2, '0')}`,
    quantity,
    revenue,
  }
}

function renderChart(
  points: Array<{ date: string; quantity: string; revenue: string }>,
  metric: 'revenue' | 'quantity' = 'revenue',
) {
  return render(
    <DailyBars
      points={points}
      metric={metric}
      format={metric === 'revenue' ? format : (value) => value}
      ariaLabel="chart"
    />
  )
}

function mockPlotWidth(plot: HTMLElement, width = 300) {
  return vi.spyOn(plot, 'getBoundingClientRect').mockReturnValue({
    x: 0,
    y: 0,
    top: 0,
    left: 0,
    right: width,
    bottom: 160,
    width,
    height: 160,
    toJSON: () => ({}),
  })
}

describe('DailyBars (desktop)', () => {
  it('renders a labelled interactive chart with the latest day selected', () => {
    renderChart([
      { date: '2026-07-01', quantity: '1', revenue: '10' },
      { date: '2026-07-02', quantity: '2', revenue: '20' },
    ])
    const plot = screen.getByRole('slider', { name: 'chart' })
    expect(plot).toHaveAttribute('aria-valuetext', '02/07/2026: R$ 20')
    expect(screen.getByText('02/07/2026')).toBeInTheDocument()
    expect(screen.getByText('R$ 20', { selector: '.chart-readout-value' })).toBeInTheDocument()
    expect(screen.getByText('01/07')).toBeInTheDocument()
    expect(screen.getByTitle('02/07/2026: R$ 20')).toBeInTheDocument()
    expect(screen.getByText(/Pico R\$ 20 em 02\/07/)).toBeInTheDocument()
    expect(screen.getByText(/média/)).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'chart — detalhes' })).toBeInTheDocument()
    expect(screen.getByRole('listitem', { name: '02/07/2026: R$ 20' })).toBeInTheDocument()
    expect(screen.queryByText(/escala até/)).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('keeps all 31 days represented while sampling compact tick labels', () => {
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

  it('clips a single outlier so the rest of the series stays readable', () => {
    const { container } = renderChart([
      day(1, '80'),
      day(2, '90'),
      day(3, '85'),
      day(4, '692.81'),
    ])
    expect(screen.getByText(/escala até/)).toBeInTheDocument()
    expect(container.querySelectorAll('.bar.is-clipped')).toHaveLength(1)
    expect(container.querySelector('.bar.is-clipped.is-selected')).toBeTruthy()
  })

  it('does not clip when the highest day is close to the rest of the series', () => {
    const { container } = renderChart([
      day(1, '80'),
      day(2, '90'),
      day(3, '100'),
    ])
    expect(screen.queryByText(/escala até/)).not.toBeInTheDocument()
    expect(container.querySelector('.bar.is-clipped')).toBeNull()
  })

  it('does not clip when only one day has a value', () => {
    const { container } = renderChart([
      day(1, '0'),
      day(2, '0'),
      day(3, '400'),
    ])
    expect(container.querySelector('.bar.is-clipped')).toBeNull()
  })

  it('returns null when there are no points', () => {
    const { container } = renderChart([])
    expect(container).toBeEmptyDOMElement()
  })

  it('selects a day from a pointer on the plot and ignores zero-width geometry', () => {
    renderChart([
      { date: '2026-07-01', quantity: '1', revenue: '10' },
      { date: '2026-07-02', quantity: '2', revenue: '20' },
      { date: '2026-07-03', quantity: '3', revenue: '30' },
    ])
    const plot = screen.getByRole('slider', { name: 'chart' })
    mockPlotWidth(plot)
    fireEvent.pointerDown(plot, { clientX: 20, pointerId: 1 })
    expect(plot).toHaveAttribute('aria-valuenow', '0')
    expect(screen.getByText('01/07/2026', { selector: '.chart-readout-date' })).toBeInTheDocument()

    fireEvent.pointerMove(plot, { clientX: 20, buttons: 0 })
    expect(plot).toHaveAttribute('aria-valuenow', '0')

    fireEvent.pointerMove(plot, { clientX: 250, buttons: 1 })
    expect(plot).toHaveAttribute('aria-valuenow', '2')

    vi.spyOn(plot, 'getBoundingClientRect').mockReturnValue({
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      right: 0,
      bottom: 160,
      width: 0,
      height: 160,
      toJSON: () => ({}),
    })
    fireEvent.pointerDown(plot, { clientX: 10, pointerId: 2 })
    expect(plot).toHaveAttribute('aria-valuenow', '2')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('moves the selected day with keyboard shortcuts', () => {
    renderChart([
      { date: '2026-07-01', quantity: '1', revenue: '10' },
      { date: '2026-07-02', quantity: '2', revenue: '20' },
      { date: '2026-07-03', quantity: '3', revenue: '30' },
    ])
    const plot = screen.getByRole('slider', { name: 'chart' })
    expect(plot).toHaveAttribute('aria-valuenow', '2')

    fireEvent.keyDown(plot, { key: 'ArrowRight' })
    expect(plot).toHaveAttribute('aria-valuenow', '2')

    fireEvent.keyDown(plot, { key: 'ArrowLeft' })
    expect(plot).toHaveAttribute('aria-valuenow', '1')
    expect(screen.getByText('02/07/2026', { selector: '.chart-readout-date' })).toBeInTheDocument()

    fireEvent.keyDown(plot, { key: 'Home' })
    expect(plot).toHaveAttribute('aria-valuenow', '0')

    fireEvent.keyDown(plot, { key: 'ArrowLeft' })
    expect(plot).toHaveAttribute('aria-valuenow', '0')

    fireEvent.keyDown(plot, { key: 'End' })
    expect(plot).toHaveAttribute('aria-valuenow', '2')

    fireEvent.keyDown(plot, { key: 'PageDown' })
    expect(plot).toHaveAttribute('aria-valuenow', '2')

    fireEvent.keyDown(plot, { key: 'Enter' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows a hover tooltip with revenue and quantity for the hovered bar', () => {
    renderChart([
      { date: '2026-07-01', quantity: '2.5', revenue: '10' },
      { date: '2026-07-02', quantity: '4', revenue: '20' },
      { date: '2026-07-03', quantity: '1', revenue: '30' },
    ])
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    const plot = screen.getByRole('slider', { name: 'chart' })
    mockPlotWidth(plot)
    fireEvent.pointerDown(plot, { clientX: 150, pointerId: 1 })
    expect(screen.getByRole('tooltip')).toBeInTheDocument()
    expect(screen.getByText('Faturamento')).toBeInTheDocument()
    expect(screen.getByText('Quantidade')).toBeInTheDocument()
    expect(screen.getByText('R$ 20')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()

    fireEvent.pointerLeave(plot)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    const secondWrap = screen.getAllByTitle(/\/2026:/).map((bar) => bar.parentElement)[1]
    fireEvent.pointerEnter(secondWrap!)
    expect(screen.getByRole('tooltip')).toBeInTheDocument()
    expect(screen.getByText('R$ 20,00', { selector: '.bar-tip strong' })).toBeInTheDocument()
    fireEvent.pointerLeave(plot)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('renders quantity values and keeps selection when the series shrinks', () => {
    const { rerender } = renderChart([
      { date: '2026-07-01', quantity: '1.5', revenue: '10' },
      { date: '2026-07-02', quantity: '4', revenue: '20' },
      { date: '2026-07-03', quantity: '8', revenue: '30' },
    ], 'quantity')
    expect(screen.getByText('8', { selector: '.chart-readout-value' })).toBeInTheDocument()

    rerender(
      <DailyBars
        points={[
          { date: '2026-07-01', quantity: '1.5', revenue: '10' },
        ]}
        metric="quantity"
        format={(value) => value}
        ariaLabel="chart"
      />
    )
    expect(screen.getByRole('slider', { name: 'chart' })).toHaveAttribute('aria-valuenow', '0')
    expect(screen.getByText('1.5', { selector: '.chart-readout-value' })).toBeInTheDocument()
  })
})

describe('DailyBars (mobile)', () => {
  beforeEach(() => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: true,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      onchange: null,
      dispatchEvent: vi.fn(),
    }))
  })

  it('opens a details sheet on tap instead of a transient tooltip', () => {
    renderChart([
      { date: '2026-07-01', quantity: '2.5', revenue: '10' },
      { date: '2026-07-02', quantity: '4', revenue: '20' },
      { date: '2026-07-03', quantity: '1', revenue: '30' },
    ])
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    const plot = screen.getByRole('slider', { name: 'chart' })
    mockPlotWidth(plot)
    fireEvent.pointerDown(plot, { clientX: 150, pointerId: 1 })
    fireEvent.click(plot, { clientX: 150 })

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    const dialog = screen.getByRole('dialog', { name: '02/07/2026' })
    expect(dialog).toBeInTheDocument()
    expect(dialog).toHaveTextContent('Faturamento')
    expect(dialog).toHaveTextContent('Quantidade')
    expect(dialog).toHaveTextContent('R$ 20,00')
    expect(screen.getByText('Toque no gráfico para ver o dia')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Fechar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('closes the sheet with Escape, on overlay click, and ignores other keys', () => {
    renderChart([
      { date: '2026-07-01', quantity: '1', revenue: '10' },
      { date: '2026-07-02', quantity: '2', revenue: '20' },
    ])
    const plot = screen.getByRole('slider', { name: 'chart' })
    mockPlotWidth(plot)
    fireEvent.pointerDown(plot, { clientX: 40, pointerId: 1 })
    fireEvent.click(plot, { clientX: 40 })
    expect(screen.getByRole('dialog', { name: '01/07/2026' })).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'a' })
    expect(screen.getByRole('dialog', { name: '01/07/2026' })).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    fireEvent.pointerDown(plot, { clientX: 40, pointerId: 2 })
    fireEvent.click(plot, { clientX: 40 })
    const overlay = screen.getByRole('dialog', { name: '01/07/2026' }).parentElement
    fireEvent.click(overlay!)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('ignores pointer drag on mobile and shows fewer tick labels', () => {
    const points = Array.from({ length: 31 }, (_, index) => ({
      date: `2026-07-${String(index + 1).padStart(2, '0')}`,
      quantity: String(index + 1),
      revenue: String(index + 1),
    }))
    const { container } = renderChart(points)
    const plot = screen.getByRole('slider', { name: 'chart' })
    mockPlotWidth(plot)
    fireEvent.pointerMove(plot, { clientX: 200, buttons: 1 })
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    const labels = Array.from(container.querySelectorAll('.bar-label')).map((label) => label.textContent)
    expect(labels.filter(Boolean)).toHaveLength(3)
  })

  it('opens the sheet with Enter on mobile keyboard navigation', () => {
    renderChart([
      { date: '2026-07-01', quantity: '1', revenue: '10' },
      { date: '2026-07-02', quantity: '2', revenue: '20' },
    ])
    const plot = screen.getByRole('slider', { name: 'chart' })
    fireEvent.keyDown(plot, { key: 'Enter' })
    expect(screen.getByRole('dialog', { name: '02/07/2026' })).toBeInTheDocument()
  })
})
