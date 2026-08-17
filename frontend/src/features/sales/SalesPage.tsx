import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listProducts, listSales } from '@/shared/api'
import { BrazilDateTimeInput } from '@/shared/BrazilDateTimeInput'
import {
  dateTimeInputError,
  formatDateTime,
  formatDateTimeInput,
  formatMoney,
  isDateTimeRangeInverted,
  normalizeDateTimeFilter,
  parseDateTimeInput,
} from '@/shared/format'
import { StateMessage } from '@/shared/StateMessage'
import { TableSkeleton } from '@/shared/TableSkeleton'
import { Pagination } from '@/shared/Pagination'
import { useAsync } from '@/shared/useAsync'
import { readSessionFilter, writeSessionFilter } from '@/shared/sessionFilters'

const FILTER_KEY = 'datahub.filters.sales'
const INVERTED_RANGE_ERROR = 'Até deve ser igual ou posterior a De.'

export function SalesPage() {
  const navigate = useNavigate()
  const stored = readSessionFilter(FILTER_KEY, { productId: '', from: '', to: '' })
  const initialFilters = {
    productId: stored.productId,
    from: normalizeDateTimeFilter(stored.from),
    to: normalizeDateTimeFilter(stored.to, { endOfMinute: true }),
  }
  const initialRangeInverted = isDateTimeRangeInverted(initialFilters.from, initialFilters.to)
  const [productId, setProductId] = useState(initialFilters.productId)
  const [from, setFrom] = useState(() => formatDateTimeInput(initialFilters.from))
  const [to, setTo] = useState(() => formatDateTimeInput(initialFilters.to))
  const [filterErrors, setFilterErrors] = useState<{ from?: string; to?: string }>(() => (
    initialRangeInverted ? { to: INVERTED_RANGE_ERROR } : {}
  ))
  const [page, setPage] = useState(0)
  const [applied, setApplied] = useState(initialRangeInverted ? { ...initialFilters, from: '', to: '' } : initialFilters)

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
          const fromValue = parseDateTimeInput(from)
          const toValue = parseDateTimeInput(to, { endOfMinute: true })
          const fromError = dateTimeInputError(from)
          const toError = dateTimeInputError(to)
          if (fromValue === null || toValue === null) {
            setFilterErrors({ from: fromError, to: toError })
            return
          }
          if (isDateTimeRangeInverted(fromValue, toValue)) {
            setFilterErrors({ from: undefined, to: INVERTED_RANGE_ERROR })
            return
          }
          setPage(0)
          const next = { productId, from: fromValue, to: toValue }
          writeSessionFilter(FILTER_KEY, next)
          setFilterErrors({})
          setFrom(formatDateTimeInput(next.from))
          setTo(formatDateTimeInput(next.to))
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
        <BrazilDateTimeInput
          id="sales-from"
          label="De"
          value={from}
          error={filterErrors.from}
          onChange={(value) => {
            setFrom(value)
            setFilterErrors((current) => ({ ...current, from: undefined }))
          }}
        />
        <BrazilDateTimeInput
          id="sales-to"
          label="Até"
          value={to}
          error={filterErrors.to}
          onChange={(value) => {
            setTo(value)
            setFilterErrors((current) => ({ ...current, to: undefined }))
          }}
        />
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
