import { describe, expect, it } from 'vitest'
import { customerEmptyText } from './customer-empty-state'

// 【FM3 2026-10-07】服务端分页后空态判定改基于服务端 total（新签名第二参数），断言同步更新
// 【UI 整改 2026-10-08】新增 searchKey 第三参数：搜索无匹配不再误导"联系管理员"
describe('客户列表空状态', () => {
  it('服务端 total 为 0 时提示检查数据范围', () => {
    expect(customerEmptyText([], 0)).toContain('数据范围')
  })

  it('当前页空但服务端 total>0 时提示筛选条件（不再误导去找管理员）', () => {
    expect(customerEmptyText([], 12)).toBe('暂无符合筛选条件的客户')
  })

  it('当前页有数据时按筛选口径（防御分支，空态展示时不会走到）', () => {
    expect(customerEmptyText([{ id: 1 }], 0)).toBe('暂无符合筛选条件的客户')
  })

  it('未传 total 时兜底按数据范围口径', () => {
    expect(customerEmptyText([])).toContain('数据范围')
  })

  it('total=0 且带搜索词时提示"未找到与XX匹配"（不再误导去找管理员）', () => {
    expect(customerEmptyText([], 0, '热连轧')).toBe('未找到与“热连轧”匹配的客户，试试清空搜索或调整筛选')
  })

  it('搜索词为纯空格时视同无搜索词，仍按数据范围口径', () => {
    expect(customerEmptyText([], 0, '   ')).toContain('数据范围')
  })

  it('有搜索词但 total>0（仍有其他匹配）时保持筛选口径', () => {
    expect(customerEmptyText([], 3, '热连轧')).toBe('暂无符合筛选条件的客户')
  })
})
