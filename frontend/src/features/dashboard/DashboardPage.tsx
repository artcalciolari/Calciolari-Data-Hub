import { useState } from 'react'
import { Link } from 'react-router-dom'
import { getDashboard, listSales } from '@/shared/api'
import { formatDateTime, formatInteger, formatMoney, formatQuantity } from '@/shared/format'
import { Icon, type IconName } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { useAsync } from '@/shared/useAsync'

type Metric = 'revenue' | 'quantity'

export function DashboardPage() {
  const [metric, setMetric] = useState<Metric>('revenue')
  const state = useAsync(() => getDashboard(), [])
  const recent = useAsync(() => listSales({ size: 5 }), [])

  if (state.loading || recent.loading) {
    return <StateMessage title="Carregando resumo…" />
  }
  if (state.error) {
    return <StateMessage tone="error" title="Não foi possível carregar o resumo">{state.error}</StateMessage>
  }
  if (recent.error) {
    return <StateMessage tone="error" title="Não foi possível carregar as vendas recentes">{recent.error}</StateMessage>
  }
  const data = state.data
  const recentSales = recent.data
  if (!data || !recentSales) {
    return <StateMessage title="Nenhum dado disponível" />
  }

  const maxMetric = Math.max(...data.daily.map((p) => Number(p[metric])), 0.001)
  const maxTopRevenue = Math.max(...data.topProducts.map((p) => Number(p.revenue)), 0.001)
  const formatMetric = metric === 'revenue' ? formatMoney : formatQuantity

  return (
    <div className="grid">
      <div className="page-head">
        <div>
          <h1>Resumo</h1>
          <p className="muted">Somente dados publicados (active parse attempt)</p>
        </div>
      </div>

      <section className="hero">
        <div className="hero-k">Faturamento no período</div>
        <div className="hero-v">{formatMoney(data.revenueTotal)}</div>
        <div className="hero-sub">
          {formatInteger(data.salesCount)} vendas · ticket médio {data.averageTicket ? formatMoney(data.averageTicket) : '—'}
        </div>
        <div className="hero-range">
          <Icon name="clock" size={13} />
          {formatDateTime(data.firstMovementAt)} → {formatDateTime(data.lastMovementAt)}
        </div>
      </section>

      <section className="grid cards-4">
        <Kpi icon="inventory" label="Quantidade" value={formatQuantity(data.quantityTotal)} />
        <Kpi icon="cart" label="Vendas" value={formatInteger(data.salesCount)} />
        <Kpi icon="items" label="Itens" value={formatInteger(data.itemCount)} />
        <Kpi icon="ticket" label="Ticket médio" value={data.averageTicket ? formatMoney(data.averageTicket) : '—'} />
      </section>

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
          <div className="bars" role="img" aria-label="Evolução diária">
            {data.daily.map((point) => {
              const value = Number(point[metric])
              const isMax = value === maxMetric
              const height = Math.max(4, (value / maxMetric) * 140)
              return (
                <div key={point.date} className="bar-wrap">
                  {isMax && <div className="bar-value">{formatMetric(point[metric])}</div>}
                  <div
                    className={isMax ? 'bar bar-max' : 'bar'}
                    style={{ height }}
                    title={`${point.date}: ${formatQuantity(point.quantity)} / ${formatMoney(point.revenue)}`}
                  />
                  <div className="bar-label">{point.date.slice(8, 10)}/{point.date.slice(5, 7)}</div>
                </div>
              )
            })}
          </div>
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
                const width = Math.max(4, (Number(product.revenue) / maxTopRevenue) * 100)
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
                        <div className="top-sub">cód {product.externalId} · {formatQuantity(product.quantity)} un</div>
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
          {recentSales.content.length === 0 ? (
            <div className="empty-state">Nenhuma venda publicada ainda.</div>
          ) : (
            <div className="recent">
              {recentSales.content.map((sale) => (
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
