import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/incentive', () => ({ getIncentiveReadRanking: vi.fn() }))

import ReadRanking from './incentive-read-ranking.vue'
import { getIncentiveReadRanking } from '@/api/incentive'
import { currentMonth } from '@/utils/incentive-read-ranking'

const stubs = {
  'el-date-picker': { template: '<div class="month-picker" />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-table': { template: '<div class="table"><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-progress': { props: ['percentage'], template: '<div class="progress" />' },
  'el-pagination': { props: ['pageSizes', 'layout', 'total'], template: '<div class="pager" />' },
}

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

// v-loading 是 Element Plus 指令，测试里给个空指令桩，避免 "Failed to resolve directive" 噪声
const mountPage = () => mount(ReadRanking, { global: { stubs, directives: { loading: {} } } })

function payload() {
  return {
    period: '2026-09',
    publishedTotal: 31,
    readTotal: 20,
    participantCount: 2,
    userCount: 3,
    avgReadRate: 21.5,
    ranking: [
      { rank: 1, userId: 1, realName: '甲', deptName: '内蒙办', publishedCount: 31, readCount: 18, unreadCount: 13, readRate: 58.1 },
      { rank: 2, userId: 2, realName: '乙', deptName: '太原办', publishedCount: 31, readCount: 2, unreadCount: 29, readRate: 6.5 },
      { rank: 3, userId: 3, realName: '丙', deptName: '太原办', publishedCount: 31, readCount: 0, unreadCount: 31, readRate: 0 },
    ],
  }
}

describe('本月激励查询阅读排行榜页面', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    getIncentiveReadRanking.mockResolvedValue({ data: payload() })
  })

  it('默认按当月加载并渲染汇总卡与榜单行', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(getIncentiveReadRanking).toHaveBeenCalledWith({ period: currentMonth() })
    const text = wrapper.text()
    expect(text).toContain('31')   // 本月发布记录
    expect(text).toContain('21.5') // 平均已读率
    expect(wrapper.vm.rows).toHaveLength(3)
    expect(wrapper.vm.rows[0]).toMatchObject({ realName: '甲', rank: 1, isReader: true })
    expect(wrapper.vm.rows[2]).toMatchObject({ realName: '丙', readRate: 0, isReader: false })
  })

  it('切换月份会带新周期重新请求', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.period = '2026-08'
    await wrapper.vm.load()
    await flushPromises()

    expect(getIncentiveReadRanking).toHaveBeenLastCalledWith({ period: '2026-08' })
  })

  it('无权限/加载失败时给出提示而不是白屏', async () => {
    getIncentiveReadRanking.mockRejectedValue({ response: { data: { message: '没有权限' } } })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert').text()).toContain('没有权限')
    expect(wrapper.vm.rows).toEqual([])
  })

  it('分页切换每页条数后回到第 1 页', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.pageNum = 2
    wrapper.vm.onSizeChange(50)

    expect(wrapper.vm.pageSize).toBe(50)
    expect(wrapper.vm.pageNum).toBe(1)
  })
})
