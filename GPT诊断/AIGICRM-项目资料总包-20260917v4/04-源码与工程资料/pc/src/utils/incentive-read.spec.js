import { describe, it, expect } from 'vitest'
import { isCurrentMonth, sortByScore } from './incentive-read'

describe('激励查询阅读与排序规则', () => {
  it('当月发布记录标记为新记录', () => {
    expect(isCurrentMonth('2026-09-01T10:00:00', new Date('2026-09-05'))).toBe(true)
    expect(isCurrentMonth('2026-08-31T10:00:00', new Date('2026-09-05'))).toBe(false)
  })
  it('按客户总分排序且不改变原数组', () => {
    const source = [{ totalScore: 3 }, { totalScore: 10 }, { totalScore: 1 }]
    expect(sortByScore(source, 'asc').map(x => x.totalScore)).toEqual([1, 3, 10])
    expect(sortByScore(source, 'desc').map(x => x.totalScore)).toEqual([10, 3, 1])
    expect(source.map(x => x.totalScore)).toEqual([3, 10, 1])
  })
})
