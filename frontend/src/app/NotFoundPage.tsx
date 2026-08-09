import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="empty-state">
      <h1>Página não encontrada</h1>
      <p>Verifique o endereço ou volte para o resumo.</p>
      <Link className="btn primary" to="/">
        Ir para Resumo
      </Link>
    </div>
  )
}
