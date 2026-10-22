import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { CUSTOMER_LIST_DEFAULT_TAB, CUSTOMER_ALL_COLS } from './useCustomerList'

// 【⑬A 2026-10-17 用户裁决】客户列表默认页签 'innovate'→'all' 钉：
// 成熟创新=0 的销售员(14/31)打开列表即空表(郑磊/NM005案)，裁决默认展示全部。
// 此前无任何钉，历史设计"首屏展核心客户"悄悄存活至今——默认值变更按欠账七式①补钉。
describe('客户列表默认页签', () => {
  it("默认页签='all'（全部），不得回退 'innovate'", () => {
    expect(CUSTOMER_LIST_DEFAULT_TAB).toBe('all')
  })

  it('默认页签不是任何具体分类页签（首屏不得按 devType 收窄）', () => {
    expect(['innovate', 'break', 'found', 'engineering']).not.toContain(CUSTOMER_LIST_DEFAULT_TAB)
  })
})

// 【全局债务审计 B4 2026-10-20】列设置白名单一致性：
// MainTable 所有 vis('x') 门控列的 x 必须存在于白名单——
// 缺位=该列被 v-if 永久隐藏, 且列设置器只遍历白名单, 用户没有任何开关能打开。
describe('客户列表列设置白名单一致性', () => {
  it("MainTable 的每个 vis('x') 列都必须在白名单内", () => {
    const whitelist = new Set(CUSTOMER_ALL_COLS.map((c) => c.key))
    // vitest cwd = aigicrm-ui 根; jsdom 下 import.meta.url 非 file 协议, 用 cwd 相对路径
    const mainTableSrc = readFileSync('src/views/customer/MainTable.vue', 'utf-8')
    const usedKeys = [...new Set([...mainTableSrc.matchAll(/vis\('([a-zA-Z]+)'\)/g)].map((m) => m[1]))]
    expect(usedKeys.length).toBeGreaterThan(0)
    const missing = usedKeys.filter((k) => !whitelist.has(k))
    expect(missing, `白名单缺少列: ${missing.join(', ')}（对应列永久隐藏且设置器无开关）`).toEqual([])
  })

  it('负责人/上级两列在白名单内（B4 修复本体）', () => {
    const keys = CUSTOMER_ALL_COLS.map((c) => c.key)
    expect(keys).toContain('ownerName')
    expect(keys).toContain('managerName')
  })
})
