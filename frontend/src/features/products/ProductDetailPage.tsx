import { Link, useParams } from 'react-router-dom'
import { getDashboard, getProduct } from '@/shared/api'
import { formatDateTime, formatMoney, formatQuantity, formatInteger } from '@/shared/format'
import { Icon } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { DailyBars } from '@/shared/DailyBars'
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

      <p className="muted">
        Primeira movimentação {formatDateTime(dashboard.data.firstMovementAt)} · última {formatDateTime(dashboard.data.lastMovementAt)}
      </p>

      <section className="section">
        <div className="section-head">
          <h2>Diário</h2>
          <span className="muted">quantidade por dia</span>
        </div>
        {dashboard.data.daily.length === 0 ? (
          <div className="empty-state">Sem série publicada para este produto.</div>
        ) : (
          <DailyBars
            points={dashboard.data.daily}
            metric="quantity"
            format={formatQuantity}
            maxHeight={110}
            ariaLabel="Série diária do produto"
          />
        )}
      </section>
    </div>
  )
}
