import { useState } from 'react'
import { Link } from 'react-router-dom'
import { listProducts } from '@/shared/api'
import { StateMessage } from '@/shared/StateMessage'
import { Skeleton } from '@/shared/Skeleton'
import { useAsync } from '@/shared/useAsync'

export function ProductsPage() {
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState('')
  const state = useAsync(() => listProducts({ q: submitted || undefined, size: 50 }), [submitted])

  return (
    <div className="grid">
      <div className="page-head">
        <div>
          <h1>Produtos</h1>
          <p className="muted">Somente itens com publicação ativa</p>
        </div>
      </div>

      <form
        className="form-row"
        onSubmit={(event) => {
          event.preventDefault()
          setSubmitted(query.trim())
        }}
      >
        <input
          aria-label="Buscar produto"
          placeholder="Nome ou código"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <button className="btn primary" type="submit">Buscar</button>
      </form>

      <section className="section">
        {state.loading ? (
          <TableSkeleton rows={6} cols={3} />
        ) : state.error ? (
          <StateMessage tone="error" title="Erro ao carregar produtos">{state.error}</StateMessage>
        ) : state.data && state.data.content.length > 0 ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Produto</th>
                  <th>Código</th>
                  <th>Fonte</th>
                </tr>
              </thead>
              <tbody>
                {state.data.content.map((product) => (
                  <tr key={product.id} className="link-row" onClick={() => { window.location.assign(`/products/${product.id}`) }}>
                    <td><Link to={`/products/${product.id}`}>{product.name}</Link></td>
                    <td>{product.externalId}</td>
                    <td><span className="chip">{product.externalSource}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">Nenhum produto publicado.</div>
        )}
      </section>
    </div>
  )
}

function TableSkeleton({ rows, cols }: { rows: number; cols: number }) {
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
