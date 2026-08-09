import Decimal from 'decimal.js'

Decimal.set({ precision: 40 })

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const integer = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })

export function decimal(value: string | null | undefined): Decimal | null {
  if (value == null || value === '') return null
  try {
    return new Decimal(value)
  } catch {
    return null
  }
}

export function formatMoney(value: string | null | undefined): string {
  const parsed = decimal(value)
  if (!parsed) return '—'
  return currency.format(parsed.toNumber())
}

export function formatQuantity(value: string | null | undefined): string {
  const parsed = decimal(value)
  if (!parsed) return '—'
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 3 }).format(parsed.toNumber())
}

export function formatPercent(value: string | null | undefined): string {
  const parsed = decimal(value)
  if (!parsed) return '—'
  return `${parsed.toNumber()}%`
}

export function formatInteger(value: number): string {
  return integer.format(value)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2}(?::\d{2})?)/)
  if (!parsed) return value
  const [, year, month, day, time] = parsed
  return `${day}/${month}/${year} ${time}`
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!parsed) return value
  const [, year, month, day] = parsed
  return `${day}/${month}/${year}`
}

export function sumStrings(values: (string | null)[]): Decimal {
  return values.reduce<Decimal>((acc, value) => acc.plus(decimal(value) ?? 0), new Decimal(0))
}
