import { Skeleton } from '@/shared/Skeleton'

export function TableSkeleton({ rows, cols }: { rows: number; cols: number }) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {Array.from({ length: cols }).map((_, i) => (
              <th key={i}><Skeleton className="line w-40" /></th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }).map((_, i) => (
            <tr key={i}>
              {Array.from({ length: cols }).map((_, j) => (
                <td key={j}><Skeleton className={j === 0 ? 'line w-60' : 'line w-40'} /></td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
