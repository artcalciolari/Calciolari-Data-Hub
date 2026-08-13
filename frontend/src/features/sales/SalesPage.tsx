import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listProducts, listSales } from '@/shared/api'
import { formatDateTime, formatMoney } from '@/shared/format'
import { StateMessage } from '@/shared/StateMessage'
import { TableSkeleton } from '@/shared/TableSkeleton'
import { Pagination } from '@/shared/Pagination'
import { useAsync } from '@/shared/useAsync'
import { readSessionFilter, writeSessionFilter } from '@/shared/sessionFilters'

const FILTER_KEY = 'datahub.filters.sales'

export function SalesPage() {
  const navigate = useNavigate()
  const stored = readSessionFilter(FILTER_KEY, { productId: '', from: '', to: '' })
  const [productId, setProductId] = useState(stored.productId)
  const [from, setFrom] = useState(stored.from)
  const [to, setTo] = useState(stored.to)
  const [page, setPage] = useState(0)
  const [applied, setApplied] = useState(stored)

  const products = useAsync(() => listProducts({ size: 50 }), [])
  const sales = useAsync(
    () => listSales({
      productId: applied.productId || undefined,
      from: applied.from || undefined,
      to: applied.to || undefined,
      page,
      size: 50,
    }),
    [applied, page]
  )

  return (
    <div className="grid">
      <div className="page-head">
        <div>
          <h1>Vendas</h1>
          <p className="muted">Auditoria das linhas publicadas</p>
        </div>
      </div>

      <form
        className="form-row"
        onSubmit={(event) => {
          event.preventDefault()
          setPage(0)
          const next = { productId, from, to }
          writeSessionFilter(FILTER_KEY, next)
          setApplied(next)
        }}
      >
        <select aria-label="Produto" value={productId} onChange={(event) => setProductId(event.target.value)}>
          <option value="">Todos os produtos</option>
          {products.data?.content.map((product) => (
            <option key={product.id} value={product.id}>
              {product.externalId} · {product.name}
            </option>
          ))}
        </select>
        <input aria-label="De" type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} />
        <input aria-label="Até" type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} />
        <button className="btn primary" type="submit">Filtrar</button>
      </form>

      <section className="section">
        {sales.loading ? (
          <TableSkeleton rows={8} cols={3} />
        ) : sales.error ? (
          <StateMessage tone="error" title="Erro ao carregar vendas">{sales.error}</StateMessage>
        ) : sales.data && sales.data.content.length > 0 ? (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Venda</th>
                    <th>Data/Hora</th>
                    <th className="num">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {sales.data.content.map((sale) => (
                    <tr
                      key={sale.id}
                      className="link-row"
                      tabIndex={0}
                      onClick={() => navigate(`/sales/${sale.id}`)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          navigate(`/sales/${sale.id}`)
                        }
                      }}
                    >
                      <td><Link to={`/sales/${sale.id}`}>{sale.externalSaleId}</Link></td>
                      <td>{formatDateTime(sale.occurredAt)}</td>
                      <td className="num">{formatMoney(sale.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={sales.data.totalPages} onPage={setPage} />
          </>
        ) : (
          <div className="empty-state">Nenhuma venda no filtro atual.</div>
        )}
      </section>
    </div>
  )
}
