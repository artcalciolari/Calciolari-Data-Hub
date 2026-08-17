export function BrazilDateTimeInput({
  id,
  label,
  value,
  onChange,
  error,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
}) {
  const hintId = `${id}-hint`
  const errorId = `${id}-error`

  return (
    <div className="date-time-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="text"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="dd/mm/yyyy HH:mm"
        maxLength={19}
        inputMode="text"
        autoComplete="off"
        spellCheck={false}
        aria-describedby={error ? `${hintId} ${errorId}` : hintId}
        aria-invalid={Boolean(error)}
      />
      <span id={hintId} className="date-time-hint">Formato: dd/mm/yyyy HH:mm ou dd/mm/yyyy HH:mm:ss</span>
      {error && <span id={errorId} className="date-time-error" role="alert">{error}</span>}
    </div>
  )
}
