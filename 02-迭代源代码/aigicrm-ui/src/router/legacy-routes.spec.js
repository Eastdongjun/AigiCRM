import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve(process.cwd(), 'src/router/index.js'), 'utf8')

describe('legacy menu route compatibility', () => {
  it('keeps legacy incentive ranking menu path usable for score ranking', () => {
    expect(source).toContain("path: 'incentive/ranking'")
    expect(source).toContain("name: 'LegacyIncentiveRanking'")
    expect(source).toContain("component: () => import('@/views/score/ranking.vue')")
    expect(source).toContain("permission: 'score:view'")
  })

  // 【2026-11-01 学分改名】score 模块路由标题统一学名分(菜单名随 V2026.11.01.1 迁移)
  it('titles score routes with 学分 wording after the credit rename', () => {
    expect(source).toContain("title: '学分录入'")
    expect(source).toContain("title: '学分排行'")
    expect(source).not.toContain("title: '积分")
  })

  it('keeps system dept organization route and legacy department menu path aligned', () => {
    expect(source).toContain("path: 'system/org'")
    expect(source).toContain("name: 'DeptManage'")
    expect(source).toContain("component: () => import('@/views/system/dept.vue')")
    expect(source).toContain("permission: 'system:dept:view'")

    expect(source).toContain("path: 'system/dept'")
    expect(source).toContain("name: 'LegacyDeptManage'")
    expect(source).toContain("component: () => import('@/views/system/dept.vue')")
    expect(source).toContain("permission: 'system:dept:view'")
  })
})
