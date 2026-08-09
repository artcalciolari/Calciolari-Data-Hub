import { useEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

// eslint-disable-next-line react-hooks/exhaustive-deps
export function useAsync<T>(task: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ data: null, loading: true, error: null })
  const taskRef = useRef(task)
  taskRef.current = task

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    let alive = true
    setState({ data: null, loading: true, error: null })
    taskRef.current()
      .then((data) => {
        if (alive) setState({ data, loading: false, error: null })
      })
      .catch((error: unknown) => {
        if (!alive) return
        const message = error instanceof Error ? error.message : 'Falha ao carregar dados'
        setState({ data: null, loading: false, error: message })
      })
    return () => {
      alive = false
    }
  }, deps)

  return state
}
