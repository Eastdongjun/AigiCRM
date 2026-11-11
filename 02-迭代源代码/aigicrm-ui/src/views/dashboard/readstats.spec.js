import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { getIncentiveReadStats } from '@/api/incentive'

// 【全局债务审计 B3 2026-10-20】工作台"我的阅读完成"卡数据源守卫：
// 原写法 `m.getReadStats ? m.getReadStats() : ...` 引用不存在的导出, 条件恒 false,
// 阅读卡永远 0/0 且不报错——死调用必须换真实导出, 并按 incentive:read-stats 权限门控
// (该权限默认仅 admin, 其他角色需勾选; 无权限直调必 403 弹错)。
describe('工作台阅读完成卡数据源(B3)', () => {
  const src = readFileSync('src/views/dashboard/index.vue', 'utf-8')

  it('不再引用不存在的 getReadStats 导出(死条件恒 false → 卡片恒 0/0)', () => {
    expect(src).not.toContain('m.getReadStats')
  })

  it('改调真实导出 getIncentiveReadStats(无参=本月口径, 与卡片文案一致)', () => {
    expect(typeof getIncentiveReadStats).toBe('function')
    expect(src).toContain('getIncentiveReadStats()')
  })

  it("调用前有 incentive:read-stats 权限门控, 无权限不发起请求(防 403 弹错)", () => {
    // 【字面量收敛第一批 2026-11-12】门控改生成物常量形态
    expect(src).toContain("hasPermission(PERMISSION_CODES.INCENTIVE_READ_STATS)")
  })
})
