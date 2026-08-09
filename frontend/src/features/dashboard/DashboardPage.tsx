import { getDashboard } from '@/shared/api'
import { formatInteger, formatMoney, formatQuantity, formatDateTime } from '@/shared/format'
import { StateMessage } from '@/shared/StateMessage'
import { useAsync } from '@/shared/useAsync'

export function DashboardPage() {
  const state = useAsync(() => getDashboard(), [])

  if (state.loading) {
    return <StateMessage title="Carregando resumo…" />
  }
  if (state.error) {
    return <StateMessage tone="error" title="Não foi possível carregar o resumo">{state.error}</StateMessage>
  }
  const data = state.data
  if (!data) {
    return <StateMessage title="Nenhum dado disponível" />
  }

  return (
    <div className="grid">
      <div className="page-head">
        <div>
          <h1>Resumo</h1>
          <p className="muted">Somente dados publicados (active parse attempt)</p>
        </div>
      </div>

      <section className="grid cards-4">
        <div className="card">
          <div className="k">Faturamento</div>
          <div className="v">{formatMoney(data.revenueTotal)}</div>
        </div>
        <div className="card">
          <div className="k">Quantidade</div>
          <div className="v">{formatQuantity(data.quantityTotal)}</div>
        </div>
        <div className="card">
          <div className="k">Vendas</div>
          <div className="v">{formatInteger(data.salesCount)}</div>
        </div>
        <div className="card">
          <div className="k">Ticket médio</div>
          <div className="v">{data.averageTicket ? formatMoney(data.averageTicket) : '—'}</div>
        </div>
      </section>

      <section className="section">
        <div className="section-head">
          <h2>Evolução diária</h2>
          <span className="muted">{formatDateTime(data.firstMovementAt)} → {formatDateTime(data.lastMovementAt)}</span>
        </div>
        {data.daily.length === 0 ? (
          <div className="empty-state">Sem movimentações publicadas no período.</div>
        ) : (
          <div className="bars" role="img" aria-label="Quantidade vendida por dia">
            {data.daily.map((point) => {
              const max = Math.max(...data.daily.map((p) => Number(p.quantity)), 0.001)
              const height = Math.max(4, (Number(point.quantity) / max) * 110)
              return (
                <div key={point.date} className="bar-wrap">
                  <div className="bar" style={{ height }} title={`${point.date}: ${formatQuantity(point.quantity)} / ${formatMoney(point.revenue)}`} />
                  <div className="bar-label">{point.date.slice(8, 10)}/{point.date.slice(5, 7)}</div>
                </div>
              )
            })}
          </div>
        )}
      </section>
    </div>
  )
}
