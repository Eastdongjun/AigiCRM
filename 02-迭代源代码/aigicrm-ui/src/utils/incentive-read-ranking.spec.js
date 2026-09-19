import { describe, expect, it } from 'vitest'
import {
  currentMonth,
  medalOf,
  monthLabel,
  normalizeRanking,
  rateTone,
  readRate,
  readRateText
} from './incentive-read-ranking'

describe('incentive-read-ranking', () => {
  it('currentMonth 返回 YYYY-MM', () => {
    expect(currentMonth(new Date('2026-09-13T10:00:00'))).toBe('2026-09')
    expect(currentMonth(new Date('2026-01-05T10:00:00'))).toBe('2026-01')
  })

  it('monthLabel 把周期转成中文月份，脏值回退"本月"', () => {
    expect(monthLabel('2026-09')).toBe('2026年9月')
    expect(monthLabel('2026-12')).toBe('2026年12月')
    expect(monthLabel('')).toBe('本月')
    expect(monthLabel('bad')).toBe('本月')
  })

  it('readRate 与后端同口径（分母 0 → 0，保留 1 位小数）', () => {
    expect(readRate(8, 10)).toBe(80)
    expect(readRate(1, 3)).toBe(33.3)
    expect(readRate(0, 0)).toBe(0)
    expect(readRate(5, null)).toBe(0)
    expect(readRateText(8, 10)).toBe('80%')
    expect(readRateText(0, 0)).toBe('—')
  })

  it('medalOf 只给前三名奖牌', () => {
    expect(medalOf(1)).toBe('🥇')
    expect(medalOf(2)).toBe('🥈')
    expect(medalOf(3)).toBe('🥉')
    expect(medalOf(4)).toBe('')
    expect(medalOf(undefined)).toBe('')
  })

  it('rateTone 分档', () => {
    expect(rateTone(90)).toBe('excellent')
    expect(rateTone(60)).toBe('good')
    expect(rateTone(10)).toBe('fair')
    expect(rateTone(0)).toBe('none')
  })

  it('normalizeRanking 补齐字段并标记是否有阅读记录', () => {
    const rows = normalizeRanking([
      { userId: 1, realName: '甲', readCount: 8, publishedCount: 10, unreadCount: 2 },
      { userId: 2, realName: '乙', readCount: 0, publishedCount: 10, unreadCount: 10, rank: 5 }
    ])
    expect(rows[0]).toMatchObject({ rank: 1, readRate: 80, isReader: true })
    expect(rows[1]).toMatchObject({ rank: 5, readRate: 0, isReader: false })
    expect(normalizeRanking(null)).toEqual([])
  })
})
