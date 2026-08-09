import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { listImports, uploadQrp } from '@/shared/api'
import type { ImportJob } from '@/shared/api'
import { formatDateTime } from '@/shared/format'
import { StateMessage } from '@/shared/StateMessage'
import { StatusBadge } from '@/shared/StatusBadge'
import { Skeleton } from '@/shared/Skeleton'
import { useAsync } from '@/shared/useAsync'
import { Icon } from '@/shared/icons'

export function ImportsPage() {
  const [refresh, setRefresh] = useState(0)
  const jobs = useAsync(() => listImports({ size: 10 }), [refresh])
  const inputRef = useRef<HTMLInputElement>(null)
  const [selected, setSelected] = useState<File[]>([])
  const [uploading, setUploading] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [drag, setDrag] = useState(false)

  async function submit() {
    if (!selected.length) return
    setUploading(true)
    setNotice(null)
    try {
      const job = await uploadQrp(selected)
      setNotice(`Importação ${job.status.toLowerCase()} (${job.files.length} arquivo(s)).`)
      setSelected([])
      setRefresh((value) => value + 1)
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Falha ao importar')
    } finally {
      setUploading(false)
    }
  }

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
            {uploading ? 'Enviando…' : `Importar${selected.length ? ` (${selected.length})` : ''}`}
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
        {notice && <p className="muted">{notice}</p>}
      </section>

      <section className="section">
        <div className="section-head">
          <h2>Histórico</h2>
          <span className="muted">{jobs.data ? `${jobs.data.totalElements} job(s)` : ''}</span>
        </div>
        {jobs.loading ? (
          <TableSkeleton rows={5} cols={4} />
        ) : jobs.error ? (
          <StateMessage tone="error" title="Erro ao carregar histórico">{jobs.error}</StateMessage>
        ) : jobs.data && jobs.data.content.length > 0 ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Job</th>
                  <th>Status</th>
                  <th>Arquivos</th>
                  <th>Criado</th>
                </tr>
              </thead>
              <tbody>
                {jobs.data.content.map((job: ImportJob) => (
                  <tr key={job.id} className="link-row" onClick={() => { window.location.assign(`/imports/${job.id}`) }}>
                    <td><Link to={`/imports/${job.id}`}>{job.id.slice(0, 8)}…</Link></td>
                    <td><StatusBadge status={job.status} /></td>
                    <td>{job.files.length}</td>
                    <td>{formatDateTime(job.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">Nenhuma importação ainda.</div>
        )}
      </section>
    </div>
  )
}

function TableSkeleton({ rows, cols }: { rows: number; cols: number }) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {Array.from({ length: cols }).map((_, i) => (
              <th key={i}><Skeleton className="line w-40" /></th>
            ))}
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: rows }).map((_, i) => (
            <tr key={i}>
              {Array.from({ length: cols }).map((_, j) => (
                <td key={j}><Skeleton className={j === 0 ? 'line w-60' : 'line w-40'} /></td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
