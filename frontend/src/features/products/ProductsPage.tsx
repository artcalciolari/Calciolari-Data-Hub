import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listProducts } from '@/shared/api'
import { StateMessage } from '@/shared/StateMessage'
import { TableSkeleton } from '@/shared/TableSkeleton'
import { Pagination } from '@/shared/Pagination'
import { useAsync } from '@/shared/useAsync'
import { readSessionFilter, writeSessionFilter } from '@/shared/sessionFilters'

const FILTER_KEY = 'datahub.filters.products'

export function ProductsPage() {
  const navigate = useNavigate()
  const stored = readSessionFilter(FILTER_KEY, { q: '' })
  const [query, setQuery] = useState(stored.q)
  const [submitted, setSubmitted] = useState(stored.q)
  const [page, setPage] = useState(0)
  const state = useAsync(() => listProducts({ q: submitted || undefined, page, size: 50 }), [submitted, page])

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
          setPage(0)
          const next = query.trim()
          writeSessionFilter(FILTER_KEY, { q: next })
          setSubmitted(next)
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
          <>
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
                    <tr
                      key={product.id}
                      className="link-row"
                      tabIndex={0}
                      onClick={() => navigate(`/products/${product.id}`)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          navigate(`/products/${product.id}`)
                        }
                      }}
                    >
                      <td><Link to={`/products/${product.id}`}>{product.name}</Link></td>
                      <td>{product.externalId}</td>
                      <td><span className="chip">{product.externalSource}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={state.data.totalPages} onPage={setPage} />
          </>
        ) : (
          <div className="empty-state">Nenhum produto publicado.</div>
        )}
      </section>
    </div>
  )
}
