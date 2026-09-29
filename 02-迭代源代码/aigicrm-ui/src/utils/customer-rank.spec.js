import { describe, it, expect } from 'vitest'
import { filterCustomerRankRows } from './customer-rank'

// 【2026-09-29 回归钉】客户排行销售视图: 过滤行但 rank 保留公司级序号
describe('filterCustomerRankRows 客户排行销售过滤', () => {
  const rows = [
    { customerId: 1, customerName: '甲(别人的)', ownerId: 8, rank: 1 },
    { customerId: 2, customerName: '乙(我的)', ownerId: 7, rank: 2 },
    { customerId: 3, customerName: '丙(别人的)', ownerId: 9, rank: 3 },
    { customerId: 4, customerName: '丁(我的)', ownerId: 7, rank: 4 },
  ]

  it('非销售(salesOnly=false)原样透传,不做过滤', () => {
    expect(filterCustomerRankRows(rows, 7, false)).toHaveLength(4)
  })

  it('销售只保留自己名下客户,且 rank 保留公司级序号(2/4,不重编号)', () => {
    const out = filterCustomerRankRows(rows, 7, true)
    expect(out.map((r) => r.customerName)).toEqual(['乙(我的)', '丁(我的)'])
    expect(out.map((r) => r.rank)).toEqual([2, 4])
  })

  it('ownerId 类型不稳(数字/字符串)也能按值匹配', () => {
    const stringId = rows.map((r) => ({ ...r, ownerId: String(r.ownerId) }))
    expect(filterCustomerRankRows(stringId, '7', true)).toHaveLength(2)
  })

  it('名下无客户时返回空数组(不报错)', () => {
    expect(filterCustomerRankRows(rows, 42, true)).toEqual([])
    expect(filterCustomerRankRows([], 7, true)).toEqual([])
  })
})
