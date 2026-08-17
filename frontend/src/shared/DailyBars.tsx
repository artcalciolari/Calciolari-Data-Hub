import { useEffect, useState, type CSSProperties, type KeyboardEvent, type PointerEvent } from 'react'
import { createPortal } from 'react-dom'
import type { DailyPoint } from '@/shared/api'
import { formatDate, formatMoney, formatQuantity } from '@/shared/format'
import { useMediaQuery } from '@/shared/useMediaQuery'

const DESKTOP_TICK_LABELS = 5
const MOBILE_TICK_LABELS = 3
const OUTLIER_RATIO = 2.2
const CLIP_HEADROOM = 1.25

function visibleTickIndices(pointCount: number, maxTicks: number): Set<number> {
  const tickCount = Math.min(pointCount, maxTicks)
  const indices = new Set<number>()
  for (let tick = 0; tick < tickCount; tick += 1) {
    indices.add(pointCount <= maxTicks
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

function tooltipData(day: DailyPoint): { date: string; revenue: string; quantity: string } {
  return {
    date: formatDate(day.date),
    revenue: formatMoney(day.revenue),
    quantity: formatQuantity(day.quantity),
  }
}

function DayValues({ day }: { day: DailyPoint }) {
  const data = tooltipData(day)
  return (
    <>
      <span className="day-stat">
        <span className="day-stat-label">Faturamento</span>
        <strong>{data.revenue}</strong>
      </span>
      <span className="day-stat">
        <span className="day-stat-label">Quantidade</span>
        <strong>{data.quantity}</strong>
      </span>
    </>
  )
}

function DailyBarsTooltip({ day }: { day: DailyPoint }) {
  return (
    <span className="bar-tip" role="tooltip">
      <span className="bar-tip-date">{formatDate(day.date)}</span>
      <DayValues day={day} />
    </span>
  )
}

function DailyBarsDayDialog({
  day,
  open,
  onClose,
}: {
  day: DailyPoint | null
  open: boolean
  onClose: () => void
}) {
  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
        return
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  if (!open || !day) return null
  const data = tooltipData(day)
  return createPortal(
    <div className="chart-sheet-overlay" role="presentation" onClick={onClose}>
      <div
        className="chart-sheet"
        role="dialog"
        aria-modal="true"
        aria-label={data.date}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="chart-sheet-head">
          <div>
            <p className="chart-sheet-label">Dia</p>
            <h3>{data.date}</h3>
          </div>
          <button type="button" className="btn secondary chart-sheet-close" onClick={onClose}>
            Fechar
          </button>
        </div>
        <div className="chart-sheet-stats">
          <DayValues day={day} />
        </div>
      </div>
    </div>,
    document.body,
  )
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
  const isMobile = useMediaQuery('(max-width: 760px)')
  const lastIndex = Math.max(0, points.length - 1)
  const [selectedIndex, setSelectedIndex] = useState(lastIndex)
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)
  const selected = Math.min(Math.max(0, selectedIndex), lastIndex)
  const hovered = hoveredIndex == null ? null : Math.min(Math.max(0, hoveredIndex), lastIndex)

  if (points.length === 0) {
    return null
  }

  const values = points.map((point) => Number(point[metric]))
  const max = Math.max(...values, 0.001)
  const average = values.reduce((sum, value) => sum + value, 0) / values.length
  const { scaleMax, clipped } = resolveChartScale(values)
  const peakIndex = values.lastIndexOf(max)
  const tickIndices = visibleTickIndices(points.length, isMobile ? MOBILE_TICK_LABELS : DESKTOP_TICK_LABELS)
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
      height: Math.max(4, (Math.min(value, scaleMax) / scaleMax) * maxHeight),
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

  function selectFromPointer(event: PointerEvent<HTMLDivElement>, openSheet: boolean) {
    const next = indexFromPointer(event.clientX, event.currentTarget.getBoundingClientRect(), points.length)
    if (next == null) return
    setHoveredIndex(next)
    clampSelect(next)
    if (openSheet) {
      setSheetOpen(true)
    }
  }

  function onPlotPointerDown(event: PointerEvent<HTMLDivElement>) {
    if (isMobile) {
      selectFromPointer(event, true)
      return
    }
    event.currentTarget.setPointerCapture?.(event.pointerId)
    selectFromPointer(event, false)
  }

  function onPlotPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (event.buttons === 0 || isMobile) return
    selectFromPointer(event, false)
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
      return
    }
    if (event.key === 'Enter' && isMobile) {
      event.preventDefault()
      setSheetOpen(true)
    }
  }

  return (
    <div className="chart">
      <div className="chart-readout">
        <span className="chart-readout-date">{selectedBar.date}</span>
        <strong className="chart-readout-value">{selectedBar.formattedValue}</strong>
      </div>
      <p className="chart-hint">{isMobile ? 'Toque no gráfico para ver o dia' : 'Toque o gráfico para ver outro dia'}</p>
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
          onPointerLeave={() => setHoveredIndex(null)}
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
              const isSelected = index === selected
              const isHovered = !isMobile && index === hovered
              const className = [
                'bar',
                isSelected ? 'is-selected' : '',
                isHovered ? 'is-hovered' : '',
                exceedsScale ? 'is-clipped' : '',
              ].filter(Boolean).join(' ')
              return (
                <div
                  key={point.date}
                  className="bar-wrap"
                  onPointerEnter={isMobile ? undefined : () => setHoveredIndex(index)}
                >
                  <div
                    className={className}
                    style={{
                      height,
                      '--bar-fill': `${Math.round(28 + 72 * (Math.min(value, scaleMax) / scaleMax))}%`,
                    } as CSSProperties}
                    title={isMobile ? undefined : `${date}: ${formattedValue}`}
                  />
                  {isHovered && <DailyBarsTooltip day={point} />}
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
      <DailyBarsDayDialog
        day={points[selected]}
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
      />
    </div>
  )
}
