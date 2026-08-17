import type { DailyPoint } from '@/shared/api'
import { formatDate } from '@/shared/format'

const MAX_VISIBLE_TICK_LABELS = 5

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

export function DailyBars({
  points,
  metric,
  format,
  maxHeight = 140,
  ariaLabel,
}: {
  points: DailyPoint[]
  metric: 'revenue' | 'quantity'
  format: (value: string) => string
  maxHeight?: number
  ariaLabel: string
}) {
  const max = Math.max(...points.map((point) => Number(point[metric])), 0.001)
  const tickIndices = visibleTickIndices(points.length)
  const barDetails = points.map((point) => {
    const value = Number(point[metric])
    const date = formatDate(point.date)
    return {
      point,
      date,
      compactDate: date.slice(0, 5),
      formattedValue: format(point[metric]),
      isMax: value === max,
      height: Math.max(4, (value / max) * maxHeight),
    }
  })

  return (
    <>
      <div className="bars" role="img" aria-label={ariaLabel}>
        {barDetails.map(({ point, date, compactDate, formattedValue, isMax, height }, index) => (
          <div key={point.date} className="bar-wrap">
            {isMax && <div className="bar-value">{formattedValue}</div>}
            <div
              className={isMax ? 'bar bar-max' : 'bar'}
              style={{ height }}
              title={`${date}: ${formattedValue}`}
            />
            <div className="bar-label">{tickIndices.has(index) ? compactDate : ''}</div>
          </div>
        ))}
      </div>
      <ul className="sr-only" aria-label={`${ariaLabel} — detalhes`}>
        {barDetails.map(({ point, date, formattedValue }) => (
          <li key={point.date} aria-label={`${date}: ${formattedValue}`}>{date}: {formattedValue}</li>
        ))}
      </ul>
    </>
  )
}
