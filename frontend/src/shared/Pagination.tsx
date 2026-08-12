export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number
  totalPages: number
  onPage: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <div className="pager">
      <button type="button" className="btn secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Anterior
      </button>
      <span className="muted">{page + 1} / {totalPages}</span>
      <button
        type="button"
        className="btn secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        Próxima
      </button>
    </div>
  )
}
