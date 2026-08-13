import { useState } from 'react'
import { Link } from 'react-router-dom'
import { getDashboard, listSales } from '@/shared/api'
import { decimal, formatDateTime, formatInteger, formatMoney, formatQuantity } from '@/shared/format'
import { Icon, type IconName } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { Skeleton } from '@/shared/Skeleton'
import { DailyBars } from '@/shared/DailyBars'
import { useAsync } from '@/shared/useAsync'
import { readSessionFilter, writeSessionFilter } from '@/shared/sessionFilters'

type Metric = 'revenue' | 'quantity'
const FILTER_KEY = 'datahub.filters.dashboard'

export function DashboardPage() {
  const stored = readSessionFilter(FILTER_KEY, { from: '', to: '' })
  const [metric, setMetric] = useState<Metric>('revenue')
  const [from, setFrom] = useState(stored.from)
  const [to, setTo] = useState(stored.to)
  const [applied, setApplied] = useState(stored)
  const state = useAsync(
    () => getDashboard({
      from: applied.from || undefined,
      to: applied.to || undefined,
    }),
    [applied],
  )
  const recent = useAsync(() => listSales({ size: 5 }), [])

  if (state.loading) {
    return <DashboardSkeleton />
  }
  if (state.error) {
    return <StateMessage tone="error" title="Não foi possível carregar o resumo">{state.error}</StateMessage>
  }
  const data = state.data
  if (!data) {
    return <StateMessage title="Nenhum dado disponível" />
  }

  const formatMetric = metric === 'revenue' ? formatMoney : formatQuantity
  const featured = data.topProducts[0]

  return (
    <div className="grid">
      <div className="page-head">
        <div>
          <h1>Resumo</h1>
          <p className="muted">Somente dados publicados</p>
        </div>
      </div>

      <form
        className="form-row"
        onSubmit={(event) => {
          event.preventDefault()
          const next = { from, to }
          writeSessionFilter(FILTER_KEY, next)
          setApplied(next)
        }}
      >
        <input aria-label="De" type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} />
        <input aria-label="Até" type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} />
        <button className="btn primary" type="submit">Filtrar</button>
      </form>

      <section className="hero">
        <div className="hero-k">Faturamento no período</div>
        <div className="hero-v">{formatMoney(data.revenueTotal)}</div>
        <div className="hero-sub">
          {formatInteger(data.salesCount)} vendas · ticket médio {data.averageTicket ? formatMoney(data.averageTicket) : '—'}
        </div>
        <div className="hero-range">
          <Icon name="clock" size={13} />
          Primeira movimentação {formatDateTime(data.firstMovementAt)} → última {formatDateTime(data.lastMovementAt)}
        </div>
      </section>

      <section className="grid cards-4">
        <Kpi icon="inventory" label="Quantidade" value={formatQuantity(data.quantityTotal)} />
        <Kpi icon="cart" label="Vendas" value={formatInteger(data.salesCount)} />
        <Kpi icon="items" label="Itens" value={formatInteger(data.itemCount)} />
        <Kpi icon="ticket" label="Ticket médio" value={data.averageTicket ? formatMoney(data.averageTicket) : '—'} />
      </section>

      {featured && (
        <section className="section featured">
          <div className="section-head">
            <h2>Produto em destaque</h2>
            <span className="muted">maior faturamento</span>
          </div>
          <Link className="featured-name" to={`/products/${featured.productId}`}>{featured.name}</Link>
          <p className="muted">
            {formatMoney(featured.revenue)} · {formatQuantity(featured.quantity)}
          </p>
          <p className="muted">
            Primeira movimentação {formatDateTime(data.firstMovementAt)} · última {formatDateTime(data.lastMovementAt)}
          </p>
          <div className="form-row">
            <Link className="btn primary" to="/sales">Ver vendas</Link>
            <Link className="btn secondary" to="/imports">Importar arquivos</Link>
          </div>
        </section>
      )}

      <section className="section">
        <div className="section-head">
          <h2>Evolução diária</h2>
          <div className="seg" role="group" aria-label="Métrica do gráfico">
            <button
              type="button"
              className={metric === 'revenue' ? 'seg-btn active' : 'seg-btn'}
              onClick={() => setMetric('revenue')}
            >
              R$
            </button>
            <button
              type="button"
              className={metric === 'quantity' ? 'seg-btn active' : 'seg-btn'}
              onClick={() => setMetric('quantity')}
            >
              Qtd
            </button>
          </div>
        </div>
        {data.daily.length === 0 ? (
          <div className="empty-state">Sem movimentações publicadas no período.</div>
        ) : (
          <DailyBars points={data.daily} metric={metric} format={formatMetric} ariaLabel="Evolução diária" />
        )}
      </section>

      <div className="dash-pair">
        <section className="section">
          <div className="section-head">
            <h2>Top produtos</h2>
            <span className="muted">por faturamento</span>
          </div>
          {data.topProducts.length === 0 ? (
            <div className="empty-state">Nenhum produto publicado ainda.</div>
          ) : (
            <ol className="top-list">
              {data.topProducts.map((product, index) => {
                const maxTopRevenue = data.topProducts.reduce((max, product) => {
                  const value = decimal(product.revenue) ?? 0
                  return value.greaterThan(max) ? value : max
                }, decimal('0.001')!)
                const width = Math.max(4, ((decimal(product.revenue) ?? 0).div(maxTopRevenue).times(100).toNumber()))
                return (
                  <li key={product.productId}>
                    <Link className="top-row" to={`/products/${product.productId}`}>
                      <span className={index === 0 ? 'rank rank-first' : 'rank'}>
                        {index === 0 ? <Icon name="trophy" size={16} /> : index + 1}
                      </span>
                      <div className="top-main">
                        <div className="top-line">
                          <span className="top-name">{product.name}</span>
                          <span className="top-rev">{formatMoney(product.revenue)}</span>
                        </div>
                        <div className="top-bar"><span style={{ width: `${width}%` }} /></div>
                        <div className="top-sub">cód {product.externalId} · {formatQuantity(product.quantity)}</div>
                      </div>
                    </Link>
                  </li>
                )
              })}
            </ol>
          )}
        </section>

        <section className="section">
          <div className="section-head">
            <h2>Vendas recentes</h2>
            <Link className="see-all" to="/sales">ver todas</Link>
          </div>
          {recent.loading ? (
            <p className="muted">Carregando vendas…</p>
          ) : recent.error ? (
            <StateMessage tone="error" title="Não foi possível carregar as vendas recentes">{recent.error}</StateMessage>
          ) : !recent.data || recent.data.content.length === 0 ? (
            <div className="empty-state">Nenhuma venda publicada ainda.</div>
          ) : (
            <div className="recent">
              {recent.data.content.map((sale) => (
                <Link key={sale.id} className="recent-row" to={`/sales/${sale.id}`}>
                  <span className="recent-id">#{sale.externalSaleId}</span>
                  <span className="recent-when">{formatDateTime(sale.occurredAt)}</span>
                  <span className="recent-total">{formatMoney(sale.total)}</span>
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function Kpi({ icon, label, value }: { icon: IconName; label: string; value: string }) {
  return (
    <div className="card kpi">
      <span className="kpi-icon"><Icon name={icon} size={20} /></span>
      <div>
        <div className="k">{label}</div>
        <div className="v">{value}</div>
      </div>
    </div>
  )
}

function DashboardSkeleton() {
  return (
    <div className="grid" aria-busy="true" aria-label="Carregando resumo">
      <div className="page-head">
        <div>
          <Skeleton className="line w-40" />
          <Skeleton className="line w-60" />
        </div>
      </div>
      <Skeleton className="section-skeleton" />
      <section className="grid cards-4">
        <Skeleton className="card-skeleton" />
        <Skeleton className="card-skeleton" />
        <Skeleton className="card-skeleton" />
        <Skeleton className="card-skeleton" />
      </section>
      <Skeleton className="section-skeleton" />
      <div className="dash-pair">
        <Skeleton className="section-skeleton" />
        <Skeleton className="section-skeleton" />
      </div>
    </div>
  )
}
