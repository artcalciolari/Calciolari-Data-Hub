export function StatusBadge({ status }: { status: string }) {
  const normalized = status.toUpperCase()
  const map: Record<string, string> = {
    IMPORTED: 'badge ok',
    VALID: 'badge ok',
    SUCCEEDED: 'badge ok',
    WARNING: 'badge warn',
    PARTIAL_SUCCESS: 'badge warn',
    PROCESSING: 'badge muted',
    PENDING: 'badge muted',
    INVALID: 'badge danger',
    FAILED: 'badge danger',
  }
  const label: Record<string, string> = {
    IMPORTED: 'Importado',
    VALID: 'Válido',
    SUCCEEDED: 'Concluído',
    WARNING: 'Atenção',
    PARTIAL_SUCCESS: 'Parcial',
    PROCESSING: 'Processando',
    PENDING: 'Pendente',
    INVALID: 'Inválido',
    FAILED: 'Falhou',
  }
  return <span className={map[normalized] ?? 'badge muted'}>{label[normalized] ?? normalized}</span>
}
