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

// 【⑬B 2026-10-17】页签分支：非"全部"页签 total=0 区分"该分类为空"与"数据范围无客户"
// （NM005/郑磊案：成熟创新=0 的销售员 14/31 打开列表被"联系管理员"文案误导成权限问题）
describe('客户列表空状态·页签分支', () => {
  it('非全部页签 total=0 且全部页签有客户时提示该分类为空（不再误导去找管理员）', () => {
    expect(customerEmptyText([], 0, '', { allTotal: 11, tabLabel: '成熟创新' }))
      .toBe('「成熟创新」分类下暂无客户，可切换「全部」页签查看（共 11 个）')
  })

  it('计数未回时先按该分类为空口径，不闪"联系管理员"文案', () => {
    expect(customerEmptyText([], 0, '', { tabLabel: '成熟创新' }))
      .toBe('「成熟创新」分类下暂无客户，可切换「全部」页签查看')
  })

  it('非全部页签且全部页签计数确证为 0 时回落数据范围口径', () => {
    expect(customerEmptyText([], 0, '', { allTotal: 0, tabLabel: '成熟创新' })).toContain('数据范围')
  })

  it('有搜索词时搜索口径优先于页签口径', () => {
    expect(customerEmptyText([], 0, '热连轧', { allTotal: 11, tabLabel: '成熟创新' })).toContain('未找到与')
  })

  it('全部页签（不传 tabLabel）保持数据范围口径不受第四参影响', () => {
    expect(customerEmptyText([], 0, '', { allTotal: 0 })).toContain('数据范围')
  })

  it('页签空表但 total>0（页码越界等）仍走筛选口径', () => {
    expect(customerEmptyText([], 5, '', { allTotal: 11, tabLabel: '成熟创新' })).toBe('暂无符合筛选条件的客户')
  })
})
