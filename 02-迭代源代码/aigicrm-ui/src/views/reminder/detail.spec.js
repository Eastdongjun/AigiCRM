import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const push = vi.fn()
const route = { params: { id: '541' }, query: { customerId: '295', type: 'expiring' } }
// 部分 mock：保留 createRouter/createWebHistory 真实现（utils/request 会 import 真实 router 实例）
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRoute: () => route, useRouter: () => ({ push }) }
})
vi.mock('@/api/reminder', () => ({
  getReminderDetail: vi.fn(),
  processReminder: vi.fn(),
}))
vi.mock('@/api/customer', () => ({ getCustomer: vi.fn() }))

import ReminderDetail from './detail.vue'
import { getReminderDetail, processReminder } from '@/api/reminder'
import { getCustomer } from '@/api/customer'

const stubs = {
  'el-button': { template: '<button><slot /></button>' },
  'el-tag': { template: '<span class="tag"><slot /></span>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}</div>' },
  'el-skeleton': { template: '<div class="skeleton" />' },
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
  'el-descriptions': { template: '<div class="descs"><slot /></div>' },
  'el-descriptions-item': {
    props: ['label'],
    template: '<div class="desc-item"><span class="lbl">{{ label }}</span><slot /></div>',
  },
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function detailPayload() {
  return {
    id: 541,
    reminderType: 'expiring',
    status: 'pending',
    overdueDays: 12,
    createdAt: '2026-09-13T11:20:38',
    updatedAt: '2026-09-13T11:20:38',
    processedAt: null,
    customerId: 295,
    ownerName: '白晓炯',
    officeName: '内蒙办销售部',
    customer: {
      id: 295,
      customerCode: 'NM-202607-037',
      customerName: '山西平阳重工机械有限责任公司',
      status: 'warning',
      industry: '机械',
      region: '山西省临汾市曲沃县',
      expireDate: '2099-09-23',
      lastInvoiceDate: '2025-09-28',
      ownerId: 44,
    },
  }
}

describe('reminder/detail.vue 组件冒烟', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    route.params.id = '541'
    route.query = { customerId: '295', type: 'expiring' }
    getReminderDetail.mockResolvedValue({ data: detailPayload() })
    getCustomer.mockResolvedValue({ data: { id: 295, customerName: '山西平阳重工机械有限责任公司' } })
    processReminder.mockResolvedValue({})
  })

  it('按路由 ID 拉取提醒详情并渲染提醒/客户信息', async () => {
    const wrapper = mount(ReminderDetail, { global: { stubs } })
    await flushPromises()

    expect(getReminderDetail).toHaveBeenCalledWith('541')
    // 详情接口已带客户块 → 不再二次请求客户接口
    expect(getCustomer).not.toHaveBeenCalled()
    const text = wrapper.text()
    expect(text).toContain('到期提醒')
    expect(text).toContain('待处理')
    expect(text).toContain('山西平阳重工机械有限责任公司')
    expect(text).toContain('NM-202607-037')
    expect(text).toContain('预警')
    expect(text).toContain('白晓炯')
    expect(text).toContain('内蒙办销售部')
    expect(text).toMatch(/剩余 \d+ 天到期/)
  })

  it('无提醒实体（id=none）时降级：不请求详情接口、用客户接口补客户信息并提示', async () => {
    route.params.id = 'none'
    route.query = { customerId: '295', type: 'expiring' }

    const wrapper = mount(ReminderDetail, { global: { stubs } })
    await flushPromises()

    expect(getReminderDetail).not.toHaveBeenCalled()
    expect(getCustomer).toHaveBeenCalledWith('295')
    expect(wrapper.find('.alert').text()).toContain('尚未生成')
    expect(wrapper.text()).toContain('山西平阳重工机械有限责任公司')
  })

  it('待处理提醒可标记已处理并重新加载详情', async () => {
    const wrapper = mount(ReminderDetail, { global: { stubs } })
    await flushPromises()

    await wrapper.vm.markProcessed()
    await flushPromises()

    expect(processReminder).toHaveBeenCalledWith('541')
    expect(getReminderDetail).toHaveBeenCalledTimes(2)
  })

  it('详情加载失败时展示错误提示而不白屏', async () => {
    getReminderDetail.mockRejectedValue({ response: { data: { message: '无权查看该提醒' } } })

    const wrapper = mount(ReminderDetail, { global: { stubs } })
    await flushPromises()

    expect(wrapper.find('.alert').text()).toContain('无权查看该提醒')
  })
})
