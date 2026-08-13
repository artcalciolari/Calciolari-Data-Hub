import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listImports, uploadQrp } from '@/shared/api'
import type { ImportFileSummary, ImportJob } from '@/shared/api'
import { formatDateTime, formatMoney, formatQuantity } from '@/shared/format'
import { StateMessage } from '@/shared/StateMessage'
import { StatusBadge } from '@/shared/StatusBadge'
import { TableSkeleton } from '@/shared/TableSkeleton'
import { Pagination } from '@/shared/Pagination'
import { useAsync } from '@/shared/useAsync'
import { Icon } from '@/shared/icons'

export function ImportsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [refresh, setRefresh] = useState(0)
  const jobs = useAsync(() => listImports({ page, size: 10 }), [refresh, page])
  const inputRef = useRef<HTMLInputElement>(null)
  const [selected, setSelected] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState<{ loaded: number; total: number } | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [result, setResult] = useState<ImportJob | null>(null)
  const [drag, setDrag] = useState(false)

  async function submit() {
    setUploading(true)
    setNotice(null)
    setResult(null)
    setProgress(null)
    try {
      const job = await uploadQrp(selected, (_file, loaded, total) => setProgress({ loaded, total }))
      setResult(job)
      setSelected([])
      setRefresh((value) => value + 1)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Falha ao importar')
    } finally {
      setUploading(false)
      setProgress(null)
    }
  }

  const percent =
    progress && progress.total > 0 ? Math.min(100, Math.round((progress.loaded / progress.total) * 100)) : null

  return (
    <div className="grid">
      <div className="page-head">
        <div>
          <h1>Importar</h1>
          <p className="muted">Envie arquivos .QRP exportados do InterPDV</p>
        </div>
      </div>

      <section
        className={`drop${drag ? ' drag' : ''}`}
        onDragOver={(event) => {
          event.preventDefault()
          setDrag(true)
        }}
        onDragLeave={() => setDrag(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDrag(false)
          setSelected(Array.from(event.dataTransfer.files).filter((f) => f.name.toLowerCase().endsWith('.qrp')))
        }}
      >
        <strong>Arraste os .QRP aqui</strong>
        <p className="muted">Até 20 arquivos por envio, máximo 32MB cada.</p>
        <div className="form-row" style={{ justifyContent: 'center' }}>
          <input
            ref={inputRef}
            type="file"
            accept=".qrp,application/octet-stream"
            multiple
            hidden
            onChange={(event) => setSelected(Array.from(event.target.files ?? []))}
          />
          <button className="btn secondary" type="button" onClick={() => inputRef.current?.click()}>
            <Icon name="upload" size={18} />
            Selecionar arquivos
          </button>
          <button className="btn primary" type="button" disabled={!selected.length || uploading} onClick={submit}>
            {uploading ? (percent != null ? `Enviando… ${percent}%` : 'Enviando…') : `Importar${selected.length ? ` (${selected.length})` : ''}`}
          </button>
        </div>
        {selected.length > 0 && (
          <ul className="muted" style={{ margin: '12px auto 0', maxWidth: 560, textAlign: 'left' }}>
            {selected.map((file) => (
              <li key={file.name}>
                {file.name} · {(file.size / 1024).toFixed(0)} KB
              </li>
            ))}
          </ul>
        )}
        {notice && <p className="notice error">{notice}</p>}
      </section>

      {result && (
        <section className="section" aria-live="polite">
          <div className="section-head">
            <h2>Resultado</h2>
            <StatusBadge status={result.status} />
          </div>
          <div className="result-grid">
            {result.files.map((file) => (
              <ImportResultCard key={file.id} jobId={result.id} file={file} />
            ))}
          </div>
          <button className="btn secondary" type="button" onClick={() => navigate(`/imports/${result.id}`)}>
            Ver detalhes
          </button>
        </section>
      )}

      <section className="section">
        <div className="section-head">
          <h2>Histórico</h2>
          <span className="muted">{jobs.data ? `${jobs.data.totalElements} envio(s)` : ''}</span>
        </div>
        {jobs.loading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : jobs.error ? (
          <StateMessage tone="error" title="Erro ao carregar histórico">{jobs.error}</StateMessage>
        ) : jobs.data && jobs.data.content.length > 0 ? (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Arquivo</th>
                    <th>Status</th>
                    <th>Arquivos</th>
                    <th>Criado</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.data.content.map((job: ImportJob) => (
                    <tr
                      key={job.id}
                      className="link-row"
                      tabIndex={0}
                      onClick={() => navigate(`/imports/${job.id}`)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          navigate(`/imports/${job.id}`)
                        }
                      }}
                    >
                      <td>
                        <Link to={`/imports/${job.id}`}>
                          {job.files[0]?.originalFilename || 'Importação'}
                        </Link>
                      </td>
                      <td><StatusBadge status={job.status} /></td>
                      <td>{job.files.length}</td>
                      <td>{formatDateTime(job.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={jobs.data.totalPages} onPage={setPage} />
          </>
        ) : (
          <div className="empty-state">Nenhuma importação ainda.</div>
        )}
      </section>
    </div>
  )
}

function ImportResultCard({ jobId, file }: { jobId: string; file: ImportFileSummary }) {
  const title = file.productName || file.originalFilename || 'Arquivo'
  const mismatch = file.quantityValidationStatus === 'INVALID'
  return (
    <Link className="result-card" to={`/imports/${jobId}/files/${file.id}`}>
      <div className="result-head">
        <strong>{title}</strong>
        <StatusBadge status={file.status} />
      </div>
      {file.deduplicated && <p className="muted">Já importado (mesmo conteúdo)</p>}
      <p className="muted">
        {file.recordsFound ?? 0} registros
        {file.parsedQuantity ? ` · ${formatQuantity(file.parsedQuantity)}` : ''}
        {file.parsedRevenue ? ` · ${formatMoney(file.parsedRevenue)}` : ''}
      </p>
      {mismatch ? (
        <p className="notice warn">
          Arquivo importado com divergências
          {file.sourceQuantity && file.parsedQuantity
            ? ` — InterPDV: ${formatQuantity(file.sourceQuantity)} / lido: ${formatQuantity(file.parsedQuantity)}`
            : ''}
        </p>
      ) : file.status === 'IMPORTED' || file.status === 'WARNING' ? (
        <p className="muted">Arquivo validado</p>
      ) : null}
    </Link>
  )
}
