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

type DateTimeParts = {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  seconds: number | null
}

type DateTimeParseOptions = {
  endOfMinute?: boolean
}

const isoLocalDateTimePattern = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{1,2}))?$/
const brazilDateTimePattern = /^(\d{1,2})\/(\d{1,2})\/(\d{4})[ \t]+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?$/

function partsFromMatch(
  value: string,
  pattern: RegExp,
  indexes: { year: number; month: number; day: number; hour: number; minute: number; seconds: number },
): DateTimeParts | null {
  const match = value.match(pattern)
  if (!match) return null
  return {
    year: Number(match[indexes.year]),
    month: Number(match[indexes.month]),
    day: Number(match[indexes.day]),
    hour: Number(match[indexes.hour]),
    minute: Number(match[indexes.minute]),
    seconds: match[indexes.seconds] == null ? null : Number(match[indexes.seconds]),
  }
}

function isValidDateTime(parts: DateTimeParts): boolean {
  if (parts.year < 1 || parts.year > 9999 || parts.month < 1 || parts.month > 12 || parts.hour > 23 || parts.minute > 59) {
    return false
  }
  const leapYear = parts.year % 4 === 0 && (parts.year % 100 !== 0 || parts.year % 400 === 0)
  const daysInMonth = parts.month === 2
    ? leapYear ? 29 : 28
    : [4, 6, 9, 11].includes(parts.month) ? 30 : 31
  return parts.day >= 1 && parts.day <= daysInMonth && (parts.seconds == null || (parts.seconds >= 0 && parts.seconds <= 59))
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function toIsoLocalDateTime(parts: DateTimeParts, options: DateTimeParseOptions = {}): string {
  const date = `${String(parts.year).padStart(4, '0')}-${pad(parts.month)}-${pad(parts.day)}`
  const time = `${pad(parts.hour)}:${pad(parts.minute)}`
  const seconds = parts.seconds == null && options.endOfMinute ? 59 : parts.seconds
  return `${date}T${time}${seconds == null ? '' : `:${pad(seconds)}`}`
}

function toBrazilDateTime(parts: DateTimeParts): string {
  const date = `${pad(parts.day)}/${pad(parts.month)}/${String(parts.year).padStart(4, '0')}`
  const time = `${pad(parts.hour)}:${pad(parts.minute)}`
  return `${date} ${time}${parts.seconds == null ? '' : `:${pad(parts.seconds)}`}`
}

/** Converts the API's local ISO value to a stable Brazilian text-input value without timezone conversion. */
export function formatDateTimeInput(value: string | null | undefined): string {
  if (!value || typeof value !== 'string') return ''
  const parts = partsFromMatch(value, isoLocalDateTimePattern, { year: 1, month: 2, day: 3, hour: 4, minute: 5, seconds: 6 })
  return parts ? toBrazilDateTime(parts) : value
}

/**
 * Converts a Brazilian date-time input to the API's local ISO representation.
 * An empty value is valid for an optional filter; null means malformed or incomplete input.
 */
export function parseDateTimeInput(value: string, options: DateTimeParseOptions = {}): string | null {
  const trimmed = value.trim()
  if (!trimmed) return ''
  const parts = partsFromMatch(trimmed, brazilDateTimePattern, { year: 3, month: 2, day: 1, hour: 4, minute: 5, seconds: 6 })
    ?? partsFromMatch(trimmed, isoLocalDateTimePattern, { year: 1, month: 2, day: 3, hour: 4, minute: 5, seconds: 6 })
  return parts && isValidDateTime(parts) ? toIsoLocalDateTime(parts, options) : null
}

export function normalizeDateTimeFilter(value: string | null | undefined, options: DateTimeParseOptions = {}): string {
  if (!value || typeof value !== 'string') return ''
  return parseDateTimeInput(value, options) ?? ''
}

export function isDateTimeRangeInverted(from: string, to: string): boolean {
  return from !== '' && to !== '' && from > to
}

export function dateTimeInputError(value: string): string | undefined {
  if (!value.trim() || parseDateTimeInput(value) !== null) return undefined
  return 'Use dd/mm/yyyy HH:mm ou dd/mm/yyyy HH:mm:ss.'
}

export function sumStrings(values: (string | null)[]): Decimal {
  return values.reduce<Decimal>((acc, value) => acc.plus(decimal(value) ?? 0), new Decimal(0))
}
