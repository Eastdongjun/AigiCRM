// 激励周期口径回归测试（2026-09-12）
// 缺陷背景：四个页面各自内联生成周期选项，只生成「上半年/下半年」，而库内历史与生产数据
// 用的是「全年」；激励页还默认 '2026上半年' 并每次请求都带上 ⇒ 打开即 0 条、且选不到"全年"。
import { describe, it, expect } from 'vitest'
import { buildPeriodOptions, periodQueryValue, defaultRecordPeriod, PERIOD_ALL } from './incentive-period'

describe('激励周期口径（incentive-period）', () => {
  it('选项含「全部」与「<年>全年/下半年/上半年」', () => {
    const values = buildPeriodOptions().map((o) => o.value)
    expect(buildPeriodOptions()[0]).toEqual(PERIOD_ALL)
    const year = new Date().getFullYear()
    expect(values).toContain(`${year}全年`)
    expect(values).toContain(`${year}下半年`)
    expect(values).toContain(`${year}上半年`)
  })

  it('范围可配置，且不失序', () => {
    const options = buildPeriodOptions({ fromYear: 2026, toYear: 2026 })
    expect(options.map((o) => o.value)).toEqual(['', '2026全年', '2026下半年', '2026上半年'])
  })

  it('「全部」（空串）在请求侧省略为 undefined，具体周期原样传递', () => {
    expect(periodQueryValue('')).toBeUndefined()
    expect(periodQueryValue(null)).toBeUndefined()
    expect(periodQueryValue('2026全年')).toBe('2026全年')
  })

  it('写入口兜底为当年「全年」，不落空周期', () => {
    expect(defaultRecordPeriod(new Date('2026-09-12T00:00:00Z'))).toBe('2026全年')
  })
})
