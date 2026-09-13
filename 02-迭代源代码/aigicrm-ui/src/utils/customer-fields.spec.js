import { describe, expect, it } from 'vitest'
import {
  TERMINAL_OPTIONS, terminalLabel,
  INDICATOR_FIELDS, INDICATOR_MAX_SCORE, INDICATOR_CORE_THRESHOLD, sumIndicatorScore,
} from './customer-fields'

describe('customer-fields', () => {
  it('exposes terminal options consistent with the import template (1=是 0=否)', () => {
    expect(TERMINAL_OPTIONS).toEqual([
      { label: '否', value: 0 },
      { label: '是', value: 1 }
    ])
  })

  it('labels terminal values including string and boolean forms from JSON', () => {
    expect(terminalLabel(1)).toBe('是')
    expect(terminalLabel('1')).toBe('是')
    expect(terminalLabel(true)).toBe('是')
    expect(terminalLabel(0)).toBe('否')
    expect(terminalLabel('0')).toBe('否')
    expect(terminalLabel(false)).toBe('否')
  })

  it('falls back to placeholder for empty or dirty values', () => {
    expect(terminalLabel(null)).toBe('—')
    expect(terminalLabel(undefined)).toBe('—')
    expect(terminalLabel('')).toBe('—')
    expect(terminalLabel(2)).toBe('—')
  })
})

describe('customer-fields 客情五项指标', () => {
  it('定义五项评分字段，每项 0-3 分四档', () => {
    expect(INDICATOR_FIELDS.map(f => f.field)).toEqual([
      'relationshipScore', 'indicator02', 'indicator03', 'indicator04', 'indicator05',
    ])
    INDICATOR_FIELDS.forEach((f) => {
      expect(f.options.map(o => o.dictValue)).toEqual(['0', '1', '2', '3'])
    })
    expect(INDICATOR_MAX_SCORE).toBe(15)
    expect(INDICATOR_CORE_THRESHOLD).toBe(12)
  })

  it('合计五项得分，缺失/脏值按 0 计', () => {
    expect(sumIndicatorScore({ relationshipScore: 3, indicator02: 3, indicator03: 3, indicator04: 3, indicator05: 3 })).toBe(15)
    expect(sumIndicatorScore({ relationshipScore: 2 })).toBe(2)
    expect(sumIndicatorScore({ relationshipScore: '3', indicator02: '2' })).toBe(5)
    expect(sumIndicatorScore({ relationshipScore: null, indicator02: undefined, indicator03: '' })).toBe(0)
    expect(sumIndicatorScore(null)).toBe(0)
  })
})
