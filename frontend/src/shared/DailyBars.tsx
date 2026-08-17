import { useState, type CSSProperties, type KeyboardEvent, type PointerEvent } from 'react'
import type { DailyPoint } from '@/shared/api'
import { formatDate } from '@/shared/format'

const MAX_VISIBLE_TICK_LABELS = 5
const OUTLIER_RATIO = 2.2
const CLIP_HEADROOM = 1.25

function visibleTickIndices(pointCount: number): Set<number> {
  const tickCount = Math.min(pointCount, MAX_VISIBLE_TICK_LABELS)
  const indices = new Set<number>()
  for (let tick = 0; tick < tickCount; tick += 1) {
    indices.add(pointCount <= MAX_VISIBLE_TICK_LABELS
      ? tick
      : Math.round(tick * (pointCount - 1) / (tickCount - 1)))
  }
  return indices
}

function resolveChartScale(values: number[]): { scaleMax: number; clipped: boolean } {
  const max = Math.max(...values, 0.001)
  if (values.length < 3) {
    return { scaleMax: max, clipped: false }
  }
  const sorted = [...values].sort((a, b) => a - b)
  const second = sorted[sorted.length - 2]
  if (!(second > 0 && max > second * OUTLIER_RATIO)) {
    return { scaleMax: max, clipped: false }
  }
  return { scaleMax: second * CLIP_HEADROOM, clipped: true }
}

function indexFromPointer(clientX: number, rect: DOMRect, count: number): number | null {
  if (rect.width <= 0) return null
  const ratio = (clientX - rect.left) / rect.width
  return Math.max(0, Math.min(count - 1, Math.floor(ratio * count)))
}

export function DailyBars({
  points,
  metric,
  format,
  maxHeight = 148,
  ariaLabel,
}: {
  points: DailyPoint[]
  metric: 'revenue' | 'quantity'
  format: (value: string) => string
  maxHeight?: number
  ariaLabel: string
}) {
  const lastIndex = Math.max(0, points.length - 1)
  const [selectedIndex, setSelectedIndex] = useState(lastIndex)
  const selected = Math.min(Math.max(0, selectedIndex), lastIndex)

  if (points.length === 0) {
    return null
  }

  const values = points.map((point) => Number(point[metric]))
  const max = Math.max(...values, 0.001)
  const average = values.reduce((sum, value) => sum + value, 0) / values.length
  const { scaleMax, clipped } = resolveChartScale(values)
  const peakIndex = values.lastIndexOf(max)
  const tickIndices = visibleTickIndices(points.length)
  const barDetails = points.map((point, index) => {
    const value = values[index]
    const date = formatDate(point.date)
    const exceedsScale = clipped && value > scaleMax
    return {
      point,
      date,
      compactDate: date.slice(0, 5),
      formattedValue: format(point[metric]),
      exceedsScale,
      height: Math.max(3, (Math.min(value, scaleMax) / scaleMax) * maxHeight),
      value,
    }
  })
  const selectedBar = barDetails[selected]
  const peakBar = barDetails[peakIndex]
  const averageHeight = Math.max(0, Math.min(100, (average / scaleMax) * 100))
  const axisMax = format(scaleMax.toFixed(2))
  const axisMid = format((scaleMax / 2).toFixed(2))
  const axisZero = format('0')
  const averageLabel = format(average.toFixed(2))

  function clampSelect(next: number) {
    setSelectedIndex(Math.max(0, Math.min(lastIndex, next)))
  }

  function selectFromPointer(event: PointerEvent<HTMLDivElement>) {
    const next = indexFromPointer(event.clientX, event.currentTarget.getBoundingClientRect(), points.length)
    if (next == null) return
    clampSelect(next)
  }

  function onPlotPointerDown(event: PointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture?.(event.pointerId)
    selectFromPointer(event)
  }

  function onPlotPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (event.buttons === 0) return
    selectFromPointer(event)
  }

  function onPlotKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      clampSelect(selected - 1)
      return
    }
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      clampSelect(selected + 1)
      return
    }
    if (event.key === 'Home') {
      event.preventDefault()
      clampSelect(0)
      return
    }
    if (event.key === 'End') {
      event.preventDefault()
      clampSelect(lastIndex)
    }
  }

  return (
    <div className="chart">
      <div className="chart-readout">
        <span className="chart-readout-date">{selectedBar.date}</span>
        <strong className="chart-readout-value">{selectedBar.formattedValue}</strong>
      </div>
      <p className="chart-hint">Toque o gráfico para ver outro dia</p>
      <div className="chart-body">
        <div className="chart-y" aria-hidden="true">
          <span>{axisMax}</span>
          <span>{axisMid}</span>
          <span>{axisZero}</span>
        </div>
        <div
          className="chart-plot"
          role="slider"
          tabIndex={0}
          aria-label={ariaLabel}
          aria-orientation="horizontal"
          aria-valuemin={0}
          aria-valuemax={lastIndex}
          aria-valuenow={selected}
          aria-valuetext={`${selectedBar.date}: ${selectedBar.formattedValue}`}
          onPointerDown={onPlotPointerDown}
          onPointerMove={onPlotPointerMove}
          onKeyDown={onPlotKeyDown}
        >
          <div className="chart-grid" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
          <div
            className="chart-avg"
            style={{ bottom: `${averageHeight}%` }}
            title={`Média ${averageLabel}`}
          />
          <div className="bars">
            {barDetails.map(({ point, date, formattedValue, exceedsScale, height, value }, index) => {
              const className = [
                'bar',
                index === selected ? 'is-selected' : '',
                exceedsScale ? 'is-clipped' : '',
              ].filter(Boolean).join(' ')
              return (
                <div key={point.date} className="bar-wrap">
                  <div
                    className={className}
                    style={{
                      height,
                      '--bar-fill': `${Math.round(28 + 72 * (Math.min(value, scaleMax) / scaleMax))}%`,
                    } as CSSProperties}
                    title={`${date}: ${formattedValue}`}
                  />
                </div>
              )
            })}
          </div>
        </div>
        <div className="chart-x" aria-hidden="true">
          {barDetails.map(({ point, compactDate }, index) => (
            <span key={point.date} className="bar-label">{tickIndices.has(index) ? compactDate : ''}</span>
          ))}
        </div>
      </div>
      <p className="chart-summary">
        Pico {peakBar.formattedValue} em {peakBar.compactDate}
        {' · '}
        média {averageLabel}
        {clipped ? ` · escala até ${axisMax}` : ''}
      </p>
      <ul className="sr-only" aria-label={`${ariaLabel} — detalhes`}>
        {barDetails.map(({ point, date, formattedValue }) => (
          <li key={point.date} aria-label={`${date}: ${formattedValue}`}>{date}: {formattedValue}</li>
        ))}
      </ul>
    </div>
  )
}
