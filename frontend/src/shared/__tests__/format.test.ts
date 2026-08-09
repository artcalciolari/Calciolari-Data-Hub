import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatInteger, formatMoney, formatPercent, formatQuantity, decimal, sumStrings } from '../format'

describe('format', () => {
  it('formats money as BRL pt-BR without float drift', () => {
    expect(formatMoney('3013.07')).toMatch(/3\.013,07/)
    expect(formatMoney('21.78')).toMatch(/21,78/)
    expect(formatMoney(null)).toBe('—')
    expect(formatMoney('')).toBe('—')
  })

  it('formats quantity with up to 3 decimals', () => {
    expect(formatQuantity('52.986')).toMatch(/52,986/)
    expect(formatQuantity('0.416')).toMatch(/0,416/)
    expect(formatQuantity(null)).toBe('—')
  })

  it('parses decimal strings exactly', () => {
    expect(decimal('52.986')?.toString()).toBe('52.986')
    expect(decimal('junk')).toBeNull()
    expect(decimal('')).toBeNull()
    expect(sumStrings(['0.510', '0.416', null]).toString()).toBe('0.926')
  })

  it('formats ISO LocalDateTime as pt-BR date/time', () => {
    expect(formatDateTime('2026-07-19T13:07:03')).toBe('19/07/2026 13:07:03')
    expect(formatDateTime('2026-07-19T13:07')).toBe('19/07/2026 13:07')
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('not-iso')).toBe('not-iso')
  })

  it('formats ISO date as pt-BR', () => {
    expect(formatDate('2026-07-19')).toBe('19/07/2026')
    expect(formatDate(null)).toBe('—')
    expect(formatDate('bad')).toBe('bad')
  })

  it('formats integers with pt-BR grouping', () => {
    expect(formatInteger(1234)).toMatch(/1\.234/)
  })

  it('formats discount percentage as integer percent', () => {
    expect(formatPercent('8')).toBe('8%')
    expect(formatPercent(undefined)).toBe('—')
  })
})
