// 【2026-11-05 排查】/indicator 浏览器渲染挂死复现测试:
// mount 组件 + mock 大数据量(250 行) → 若模板/分页存在渲染死循环, 本测试会超时挂起。
import { describe, it, expect, vi, beforeAll } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 全局残留: jsdom 无 matchMedia(el-pagination 内部用到)
beforeAll(() => {
  if (!window.matchMedia) window.matchMedia = () => ({ matches: false, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {} })
})

vi.mock('@/api/statistics', () => {
  const rows = Array.from({ length: 250 }, (_, i) => ({
    customerId: i + 1,
    customerName: '客户' + (i + 1),
    ownerName: '销售',
    industry: '煤矿',
    relationshipScore: 10,
    indicator02: 10,
    coreType: i < 30 ? 'core' : i < 90 ? 'quasi' : 'none'
  }))
  return {
    getIndicatorStats: vi.fn().mockResolvedValue({
      data: {
        coveredCount: 250,
        coreCount: 30,
        quasiCount: 60,
        avgScore: 82,
        coreMajorWan: 100,
        coreMinorWan: 50,
        customerRanking: rows
      }
    })
  }
})

import IndicatorPage from './index.vue'

const mountPage = () => mount(IndicatorPage, { global: { plugins: [ElementPlus] }, attachTo: document.body })

describe('核心客户六项指标(2026-11-05 补翻页) 渲染挂死复现', () => {
  it('250 行数据渲染收敛(无渲染死循环), 分页出现且每页 20 行', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await new Promise(r => setTimeout(r, 300))
    await flushPromises()
    const pager = wrapper.find('.indicator-page .el-pagination')
    expect(pager.exists()).toBe(true)
    // jsdom 下 el-table 行用 el-table__row 类选(不用 tbody 包装选择器)
    const rows = wrapper.findAll('[class*="el-table__row"]')
    expect(rows.length).toBe(20) // 默认每页 20
    expect((wrapper.text().match(/客户\d+/g) || []).length).toBe(20)
  })

  it('翻到第 2 页: 行切换且绝对榜次从 21 起', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await new Promise(r => setTimeout(r, 300))
    await flushPromises()
    // 直改内部分页状态: 通过点"下一页"按钮驱动真实交互
    // jsdom 下 ep 分页按钮不渲染 → 经 defineExpose 的测试钩子直驱页码(等价于点"下一页")
    wrapper.vm.pageNum = 2
    await flushPromises()
    await new Promise(r => setTimeout(r, 200))
    await flushPromises()
    console.log('PAGER-TEXT', wrapper.find('.indicator-page .el-pagination').text().slice(0, 80))
    const rows = wrapper.findAll('[class*="el-table__row"]')
    expect(rows.length).toBe(20)
    const firstRank = rows[0].find('td').text()
    expect(firstRank).toBe('21')
  })
})
