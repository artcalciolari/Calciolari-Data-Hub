import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useAsync } from '../useAsync'

function Probe({ task, deps }: { task: () => Promise<string>; deps: unknown[] }) {
  const state = useAsync(task, deps)
  if (state.loading) return <div>loading</div>
  if (state.error) return <div>error:{state.error}</div>
  return <div>data:{state.data}</div>
}

describe('useAsync', () => {
  it('resolves data on success', async () => {
    render(<Probe task={() => Promise.resolve('ok')} deps={[]} />)
    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(await screen.findByText('data:ok')).toBeInTheDocument()
  })

  it('surfaces Error message on failure', async () => {
    render(<Probe task={() => Promise.reject(new Error('boom'))} deps={[]} />)
    expect(await screen.findByText('error:boom')).toBeInTheDocument()
  })

  it('uses fallback message for non-Error rejection', async () => {
    render(<Probe task={() => Promise.reject('nope')} deps={[]} />)
    expect(await screen.findByText('error:Falha ao carregar dados')).toBeInTheDocument()
  })

  it('ignores stale resolution after unmount', async () => {
    let resolve!: (value: string) => void
    const task = () =>
      new Promise<string>((res) => {
        resolve = res
      })
    const { unmount } = render(<Probe task={task} deps={[]} />)
    unmount()
    resolve('late')
    await waitFor(() => {
      expect(screen.queryByText('data:late')).not.toBeInTheDocument()
    })
  })

  it('ignores stale rejection after unmount', async () => {
    let reject!: (reason: unknown) => void
    const task = () =>
      new Promise<string>((_, rej) => {
        reject = rej
      })
    const { unmount } = render(<Probe task={task} deps={[]} />)
    unmount()
    reject(new Error('late error'))
    await waitFor(() => {
      expect(screen.queryByText('error:late error')).not.toBeInTheDocument()
    })
  })

  it('re-runs when deps change', async () => {
    const task = vi.fn().mockResolvedValueOnce('a').mockResolvedValueOnce('b')
    const { rerender } = render(<Probe task={task} deps={['x']} />)
    expect(await screen.findByText('data:a')).toBeInTheDocument()
    rerender(<Probe task={task} deps={['y']} />)
    expect(await screen.findByText('data:b')).toBeInTheDocument()
  })
})
