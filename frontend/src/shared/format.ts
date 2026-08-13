import Decimal from 'decimal.js'

Decimal.set({ precision: 40 })

export function decimal(value: string | null | undefined): Decimal | null {
  if (value == null || value === '') return null
  try {
    return new Decimal(value)
  } catch {
    return null
  }
}

function groupPtBrInteger(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

function formatSignedPtBr(value: Decimal, fractionDigits: number): string {
  const neg = value.isNeg()
  const abs = value.abs()
  const fixed = abs.toFixed(fractionDigits)
  const [intPart = '0', fracPart] = fixed.split('.')
  const grouped = groupPtBrInteger(intPart)
  const sign = neg ? '-' : ''
  if (fractionDigits === 0) {
    return `${sign}${grouped}`
  }
  return `${sign}${grouped},${fracPart}`
}

export function formatMoney(value: string | null | undefined): string {
  const parsed = decimal(value)
  if (!parsed) return '—'
  const formatted = formatSignedPtBr(parsed, 2)
  if (formatted.startsWith('-')) {
    return `R$ -${formatted.slice(1)}`
  }
  return `R$ ${formatted}`
}

export function formatQuantity(value: string | null | undefined): string {
  const parsed = decimal(value)
  if (!parsed) return '—'
  if (parsed.isInteger()) {
    return formatSignedPtBr(parsed, 0)
  }
  const neg = parsed.isNeg()
  const trimmed = parsed.abs().toFixed(3).replace(/0+$/, '')
  const [intPart = '0', fracPart] = trimmed.split('.')
  const grouped = groupPtBrInteger(intPart)
  const sign = neg ? '-' : ''
  return `${sign}${grouped},${fracPart}`
}

export function formatPercent(value: string | null | undefined): string {
  const parsed = decimal(value)
  if (!parsed) return '—'
  return `${parsed.toDecimalPlaces(2).toString().replace(/\.?0+$/, '')}%`
}

export function formatInteger(value: number): string {
  const neg = value < 0
  const grouped = groupPtBrInteger(Math.abs(Math.trunc(value)).toString())
  return `${neg ? '-' : ''}${grouped}`
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
