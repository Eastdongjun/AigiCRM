import { describe, expect, it } from 'vitest'
import { CUSTOMER_LIST_DEFAULT_TAB } from './useCustomerList'

// 【⑬A 2026-10-17 用户裁决】客户列表默认页签 'innovate'→'all' 钉：
// 成熟创新=0 的销售员(14/31)打开列表即空表(郑磊/NM005案)，裁决默认展示全部。
// 此前无任何钉，历史设计"首屏展核心客户"悄悄存活至今——默认值变更按欠账七式①补钉。
describe('客户列表默认页签', () => {
  it("默认页签='all'（全部），不得回退 'innovate'", () => {
    expect(CUSTOMER_LIST_DEFAULT_TAB).toBe('all')
  })

  it("默认页签不是任何具体分类页签（首屏不得按 devType 收窄）", () => {
    expect(['innovate', 'break', 'found', 'engineering']).not.toContain(CUSTOMER_LIST_DEFAULT_TAB)
  })
})
