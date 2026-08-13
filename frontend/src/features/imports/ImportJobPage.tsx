import { Link, useNavigate, useParams } from 'react-router-dom'
import { getImportJob } from '@/shared/api'
import { formatDateTime } from '@/shared/format'
import { Icon } from '@/shared/icons'
import { StateMessage } from '@/shared/StateMessage'
import { StatusBadge } from '@/shared/StatusBadge'
import { useAsync } from '@/shared/useAsync'

export function ImportJobPage() {
  const navigate = useNavigate()
  const { jobId = '' } = useParams()
  const state = useAsync(() => getImportJob(jobId), [jobId])

  if (state.loading) return <StateMessage title="Carregando importação…" />
  if (state.error) return <StateMessage tone="error" title="Erro ao carregar importação">{state.error}</StateMessage>
  const job = state.data
  if (!job) return <StateMessage title="Importação não encontrada" />

  const title = job.files.map((file) => file.originalFilename).find(Boolean) || 'Importação'

  return (
    <div className="grid">
      <Link className="back-link" to="/imports"><Icon name="chevron-left" size={16} /> Histórico</Link>
      <div className="page-head">
        <div>
          <h1>{title}</h1>
          <p className="muted">{formatDateTime(job.createdAt)}</p>
        </div>
        <StatusBadge status={job.status} />
      </div>

      <section className="section">
        <div className="section-head">
          <h2>Arquivos</h2>
          <span className="muted">{job.files.length}</span>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Arquivo</th>
                <th>Status</th>
                <th>Duplicata</th>
                <th>Concluído</th>
              </tr>
            </thead>
            <tbody>
              {job.files.map((file) => (
                <tr
                  key={file.id}
                  className="link-row"
                  tabIndex={0}
                  onClick={() => navigate(`/imports/${job.id}/files/${file.id}`)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault()
                      navigate(`/imports/${job.id}/files/${file.id}`)
                    }
                  }}
                >
                  <td><Link to={`/imports/${job.id}/files/${file.id}`}>{file.originalFilename || '—'}</Link></td>
                  <td><StatusBadge status={file.status} /></td>
                  <td>{file.deduplicated ? 'sim' : 'não'}</td>
                  <td>{formatDateTime(file.completedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
