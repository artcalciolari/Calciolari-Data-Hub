import { useState } from 'react'
import { getDebugStatus, resetDataset } from '@/shared/api'
import { Icon } from '@/shared/icons'
import { useAsync } from '@/shared/useAsync'

export function DebugResetPanel({ onCleared }: { onCleared: () => void }) {
  const status = useAsync(() => getDebugStatus(), [])
  const [confirming, setConfirming] = useState(false)
  const [working, setWorking] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (status.loading || status.error || !status.data?.enabled) {
    return null
  }

  async function confirm() {
    setWorking(true)
    setError(null)
    setNotice(null)
    try {
      await resetDataset()
      setConfirming(false)
      setNotice('Dados apagados. Você pode importar os .QRP novamente.')
      onCleared()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Falha ao apagar dados')
    } finally {
      setWorking(false)
    }
  }

  return (
    <section className="section debug-panel">
      <div className="section-head">
        <h2>Modo debug</h2>
        <span className="muted">Apenas neste ambiente</span>
      </div>
      <div className="debug-body">
        <p className="muted">
          Apaga vendas, produtos, histórico de importação e os arquivos .QRP armazenados
          para você poder importar de novo.
        </p>
        {notice && <p className="notice ok">{notice}</p>}
        {error && <p className="notice error">{error}</p>}
        {confirming ? (
          <div className="debug-confirm">
            <p className="notice warn">Isso não tem volta. Apagar o dataset atual?</p>
            <div className="form-row">
              <button className="btn danger" type="button" disabled={working} onClick={() => void confirm()}>
                {working ? 'Apagando…' : 'Confirmar exclusão'}
              </button>
              <button className="btn secondary" type="button" disabled={working} onClick={() => setConfirming(false)}>
                Cancelar
              </button>
            </div>
          </div>
        ) : (
          <button className="btn danger" type="button" onClick={() => setConfirming(true)}>
            <Icon name="trash" size={18} />
            Apagar dados
          </button>
        )}
      </div>
    </section>
  )
}
