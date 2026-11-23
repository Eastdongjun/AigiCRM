import { describe, expect, it } from 'vitest'
import {
  FABUSHI_LAYOUT_TYPES,
  lineHeightRatio,
  normalizeReaderSettings,
  readingPercent,
  readerCssVars,
  resolveReaderDefaults,
  storageKey,
  backdropOf
} from './fabushi-reader-settings'

describe('法布施阅读器版式设置（D19/D24 批1）', () => {
  it('版式标记决定保守默认值：文本版纸白 / 影印版米黄增强', () => {
    expect(resolveReaderDefaults('text')).toMatchObject({ backdrop: 'paper', contrast: 100 })
    expect(resolveReaderDefaults('scan')).toMatchObject({ backdrop: 'sepia', contrast: 112 })
    expect(resolveReaderDefaults(null).backdrop).toBe('sepia')
    expect(FABUSHI_LAYOUT_TYPES.map((x) => x.value)).toEqual(['text', 'scan'])
  })

  it('本地记忆合并时钳制越界值，脏值不进 CSS', () => {
    const s = normalizeReaderSettings({ zoom: 999, contrast: 'abc', crop: -5, continuous: 'yes' }, 'text')
    expect(s.zoom).toBe(160)
    expect(s.contrast).toBe(100)
    expect(s.crop).toBe(0)
    expect(s.continuous).toBe(false)
    const vars = readerCssVars(s)
    expect(vars['--reader-page-bg']).toBe(backdropOf('paper').bg)
    expect(vars['--reader-zoom']).toBe('1.6')
  })

  it('页码百分比同显，总页数缺失回 0 而非 100', () => {
    expect(readingPercent(3, 10)).toBe(30)
    expect(readingPercent(12, 10)).toBe(100)
    expect(readingPercent(3, 0)).toBe(0)
    expect(readingPercent(null, null)).toBe(0)
    expect(storageKey(9)).toBe('fabushi:reader-settings:9')
  })

  // 【D24 2026-11-23】重排/字号/行距：脏值钳制 + 行距存十分位整数、出 CSS 转倍数
  it('重排字号行距越界钳制，行距十分位整数转倍数', () => {
    const s = normalizeReaderSettings({ reflow: true, fontSize: 999, lineHeight: 1 }, 'text')
    expect(s.reflow).toBe(true)
    expect(s.fontSize).toBe(30)
    expect(s.lineHeight).toBe(14)
    expect(lineHeightRatio(s.lineHeight)).toBe(1.4)
    expect(lineHeightRatio(19)).toBe(1.9)
    expect(lineHeightRatio('abc')).toBe(1.9)
    // 默认不重排：影印件占多数，默认开会一进来就是"不可用"
    expect(resolveReaderDefaults('scan').reflow).toBe(false)
    const vars = readerCssVars(s)
    expect(vars['--reader-font-size']).toBe('30px')
    expect(vars['--reader-line-height']).toBe('1.4')
  })
})
