import type { ReactNode } from 'react'

export function StateMessage({ title, children, tone }: { title: string; children?: ReactNode; tone?: 'error' | 'ok' }) {
  return (
    <div className={`empty-state${tone === 'error' ? ' error' : ''}`}>
      <h2>{title}</h2>
      {children}
    </div>
  )
}
