import type { DailyPoint } from '@/shared/api'

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
  return (
    <div className="bars" role="img" aria-label={ariaLabel}>
      {points.map((point) => {
        const value = Number(point[metric])
        const isMax = value === max
        const height = Math.max(4, (value / max) * maxHeight)
        return (
          <div key={point.date} className="bar-wrap">
            {isMax && <div className="bar-value">{format(point[metric])}</div>}
            <div
              className={isMax ? 'bar bar-max' : 'bar'}
              style={{ height }}
              title={`${point.date}: ${format(point[metric])}`}
            />
            <div className="bar-label">{point.date.slice(8, 10)}/{point.date.slice(5, 7)}</div>
          </div>
        )
      })}
    </div>
  )
}
