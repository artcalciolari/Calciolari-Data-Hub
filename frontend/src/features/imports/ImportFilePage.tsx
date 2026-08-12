import { Link, useParams } from 'react-router-dom'
import { getImportFile } from '@/shared/api'
import { formatDateTime } from '@/shared/format'
import { Icon } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { StatusBadge } from '@/shared/StatusBadge'
import { useAsync } from '@/shared/useAsync'

export function ImportFilePage() {
  const { jobId = '', fileId = '' } = useParams()
  const state = useAsync(() => getImportFile(jobId, fileId), [jobId, fileId])

  if (state.loading) return <StateMessage title="Carregando arquivo…" />
  if (state.error) return <StateMessage tone="error" title="Erro ao carregar arquivo">{state.error}</StateMessage>
  const file = state.data
  if (!file) return <StateMessage title="Arquivo não encontrado" />

  return (
    <div className="grid">
      <Link className="back-link" to={`/imports/${jobId}`}><Icon name="chevron-left" size={16} /> Importação</Link>
      <div className="page-head">
        <div>
          <h1>{file.originalFilename || 'Arquivo sem nome'}</h1>
          <p className="muted">SHA-256 {file.sha256.slice(0, 12)}… · {(file.byteSize / 1024).toFixed(0)} KB</p>
        </div>
        <StatusBadge status={file.status} />
      </div>

      <section className="detail-top section">
        <div className="d">
          <div className="label">Parser</div>
          <div className="value">{file.parserName ?? '—'}</div>
        </div>
        <div className="d">
          <div className="label">Versão</div>
          <div className="value">{file.parserVersion ?? '—'}</div>
        </div>
        <div className="d">
          <div className="label">Registros</div>
          <div className="value">{file.recordsFound ?? 0}</div>
        </div>
        <div className="d">
          <div className="label">Status parse</div>
          <div className="value">{file.parseStatus ?? '—'}</div>
        </div>
      </section>

      <section className="section">
        <div className="section-head">
          <h2>Pistas do nome do arquivo</h2>
          <span className="muted">INFERRED_DATA — não substitui o conteúdo</span>
        </div>
        <pre style={{ margin: 0, padding: 16, overflow: 'auto' }}>{JSON.stringify(file.filenameHints, null, 2)}</pre>
      </section>

      <section className="section">
        <div className="section-head">
          <h2>Validações</h2>
          <span className="muted">{file.validations.length}</span>
        </div>
        {file.validations.length === 0 ? (
          <div className="empty-state">Sem validações registradas.</div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Status</th>
                  <th className="num">Fonte</th>
                  <th className="num">Calculado</th>
                  <th className="num">Diferença</th>
                  <th className="num">Tolerância</th>
                  <th>Regra</th>
                </tr>
              </thead>
              <tbody>
                {file.validations.map((validation) => (
                  <tr key={`${validation.code}-${validation.sourceLocator ?? ''}`}>
                    <td>{validation.code}</td>
                    <td><StatusBadge status={validation.status} /></td>
                    <td className="num">{validation.sourceValue ?? '—'}</td>
                    <td className="num">{validation.calculatedValue ?? '—'}</td>
                    <td className="num">{validation.difference ?? '—'}</td>
                    <td className="num">{validation.tolerance ?? '—'}</td>
                    <td>{validation.ruleVersion}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <p className="muted">Concluído: {formatDateTime(file.completedAt)}</p>
    </div>
  )
}
