import { Link, useParams } from 'react-router-dom'
import { getSale } from '@/shared/api'
import { formatDateTime, formatMoney, formatPercent, formatQuantity } from '@/shared/format'
import { Icon } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { useAsync } from '@/shared/useAsync'

export function SaleDetailPage() {
  const { id = '' } = useParams()
  const state = useAsync(() => getSale(id), [id])

  if (state.loading) return <StateMessage title="Carregando venda…" />
  if (state.error) return <StateMessage tone="error" title="Erro ao carregar venda">{state.error}</StateMessage>
  const sale = state.data
  if (!sale) return <StateMessage title="Venda não encontrada" />

  return (
    <div className="grid">
      <Link className="back-link" to="/sales"><Icon name="chevron-left" size={16} /> Vendas</Link>
      <div className="page-head">
        <div>
          <h1>Venda {sale.externalSaleId}</h1>
          <p className="muted">{formatDateTime(sale.occurredAt)}</p>
        </div>
      </div>

      <section className="section">
        <div className="section-head">
          <h2>Itens</h2>
          <span className="muted">{sale.items.length}</span>
        </div>
        {sale.items.length === 0 ? (
          <div className="empty-state">Sem itens publicados.</div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>#</th>
                  <th>Produto</th>
                  <th className="num">Qtd.</th>
                  <th className="num">Preço</th>
                  <th className="num">Desc.</th>
                  <th className="num">Total</th>
                </tr>
              </thead>
              <tbody>
                {sale.items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.sourceRecordIndex + 1}</td>
                    <td>
                      <Link to={`/products/${item.productId}`}>{item.productExternalId} · {item.productName}</Link>
                    </td>
                    <td className="num">{formatQuantity(item.quantity)}</td>
                    <td className="num">{formatMoney(item.unitPrice)}</td>
                    <td className="num">{formatPercent(item.discountPercentage)}</td>
                    <td className="num">{formatMoney(item.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
