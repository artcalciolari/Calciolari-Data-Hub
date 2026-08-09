import { Link, useParams } from 'react-router-dom'
import { getDashboard, getProduct } from '@/shared/api'
import { formatMoney, formatQuantity, formatInteger } from '@/shared/format'
import { Icon } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { useAsync } from '@/shared/useAsync'

export function ProductDetailPage() {
  const { id = '' } = useParams()
  const product = useAsync(() => getProduct(id), [id])
  const dashboard = useAsync(() => getDashboard({ productId: id }), [id])

  if (product.loading || dashboard.loading) return <StateMessage title="Carregando produto…" />
  if (product.error) return <StateMessage tone="error" title="Erro ao carregar produto">{product.error}</StateMessage>
  if (dashboard.error) return <StateMessage tone="error" title="Erro ao carregar métricas">{dashboard.error}</StateMessage>
  if (!product.data || !dashboard.data) return <StateMessage title="Produto não encontrado" />

  return (
    <div className="grid">
      <Link className="back-link" to="/products"><Icon name="chevron-left" size={16} /> Produtos</Link>
      <div className="page-head">
        <div>
          <h1>{product.data.name}</h1>
          <p className="muted">Código {product.data.externalId} · {product.data.externalSource}</p>
        </div>
      </div>

      <section className="grid cards-4">
        <div className="card"><div className="k">Faturamento</div><div className="v">{formatMoney(dashboard.data.revenueTotal)}</div></div>
        <div className="card"><div className="k">Quantidade</div><div className="v">{formatQuantity(dashboard.data.quantityTotal)}</div></div>
        <div className="card"><div className="k">Vendas</div><div className="v">{formatInteger(dashboard.data.salesCount)}</div></div>
        <div className="card"><div className="k">Itens</div><div className="v">{formatInteger(dashboard.data.itemCount)}</div></div>
      </section>

      <section className="section">
        <div className="section-head">
          <h2>Diário</h2>
          <span className="muted">quantidade por dia</span>
        </div>
        {dashboard.data.daily.length === 0 ? (
          <div className="empty-state">Sem série publicada para este produto.</div>
        ) : (
          <div className="bars" role="img" aria-label="Série diária do produto">
            {dashboard.data.daily.map((point) => {
              const daily = dashboard.data!.daily
              const max = Math.max(...daily.map((p) => Number(p.quantity)), 0.001)
              const height = Math.max(4, (Number(point.quantity) / max) * 110)
              return (
                <div key={point.date} className="bar-wrap">
                  <div className="bar" style={{ height }} title={`${point.date}: ${formatQuantity(point.quantity)}`} />
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
