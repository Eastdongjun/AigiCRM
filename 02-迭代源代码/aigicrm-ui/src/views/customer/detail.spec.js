// 【B5 2026-10-21】客户详情页错误态/加载态组件测试
// 覆盖审计 B5 的三条验收：
//   1) 首屏 getCustomer 失败 → 页面级错误态（错误信息 + 重试/返回），不再整页空白只剩 toast
//   2) 错误态点重试 → 重新 loadAll，成功后恢复页面内容
//   3) v-loading 挂在 v-if 外层容器 → 首屏加载期间（customer 仍为 null）根容器已渲染
//   4) 分区单路失败 → 警示条展示失败分区，其余内容照常渲染
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' }, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))
vi.mock('@/api/customer', () => ({
  getCustomer: vi.fn(), getCustomerLogs: vi.fn(), addCustomerLog: vi.fn(), updateCustomer: vi.fn(),
  getInvoices: vi.fn(), getInvoiceSummary: vi.fn(), addInvoice: vi.fn(), deleteInvoice: vi.fn(),
  getPayments: vi.fn(), getPaymentSummary: vi.fn(), addPayment: vi.fn(), deletePayment: vi.fn(),
  getContacts: vi.fn(), addContact: vi.fn(), updateContact: vi.fn(), deleteContact: vi.fn(),
  getIndicator: vi.fn(), saveIndicator: vi.fn(), saveRelationship: vi.fn(),
  getShares: vi.fn(), addShares: vi.fn(), removeShare: vi.fn(), getSimpleUserList: vi.fn(),
  getStageAmounts: vi.fn(), saveStageAmount: vi.fn(),
}))
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/api/approval', () => ({ submitApproval: vi.fn() }))
vi.mock('@/api/incentive', () => ({ getScoreByCustomer: vi.fn(), getCategoryTree: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn().mockResolvedValue({ data: [] }) } }))
vi.mock('@/utils/token', () => ({
  getDownloadToken: vi.fn(() => 't'),
  refreshDownloadToken: vi.fn(() => Promise.resolve('t')),
}))
vi.mock('@/utils/download', () => ({ downloadFile: vi.fn() }))

import Detail from './detail.vue'
import { getCustomer, getCustomerLogs, getIndicator, getShares } from '@/api/customer'
import { getScoreByCustomer } from '@/api/incentive'

// 五个 tab 子组件整替（各自的展示逻辑有独立 spec），el-* 最小 stub（el-alert 渲染 slot 以便断言）
const stubs = {
  InfoTab: true, FinanceTab: true, IndicatorTab: true, IncentiveTab: true, LogsTab: true,
  'el-tabs': { template: '<div class="tabs"><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-dialog': { template: '<div><slot /></div>' },
  'el-alert': { props: ['title'], template: '<div class="alert">{{ title }}<slot /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-select': { template: '<div />' },
  'el-option': true,
  'el-icon': true,
}

function mockHappyPath() {
  getCustomer.mockResolvedValue({ data: { id: 1, customerName: '示例客户', ownerName: '张三', expireDate: '2026-12-31' } })
  getCustomerLogs.mockResolvedValue({ data: [] })
  getIndicator.mockResolvedValue({ data: null })
  getShares.mockResolvedValue({ data: [] })
  getScoreByCustomer.mockResolvedValue({ data: 0 })
}

describe('customer/detail.vue 错误态与加载态（B5）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockHappyPath()
  })

  it('首屏 getCustomer 失败渲染页面级错误态：错误信息 + 重试/返回，不渲染正文', async () => {
    getCustomer.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(Detail, { global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.detail-error').exists()).toBe(true)
    expect(wrapper.find('.detail-error-msg').text()).toContain('network down')
    const buttonTexts = wrapper.findAll('button').map((b) => b.text())
    expect(buttonTexts).toContain('重试')
    expect(buttonTexts).toContain('返回客户列表')
    // 正文（hero 卡）不再渲染
    expect(wrapper.find('.hero-card').exists()).toBe(false)
  })

  it('错误态点重试：重新拉取，成功后恢复页面内容', async () => {
    getCustomer.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(Detail, { global: { stubs } })
    await flushPromises()
    const retry = wrapper.findAll('button').find((b) => b.text() === '重试')
    await retry.trigger('click')
    await flushPromises()
    expect(getCustomer).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.detail-error').exists()).toBe(false)
    expect(wrapper.find('.hero-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('示例客户')
  })

  it('v-loading 容器在 customer 未就绪时已渲染（loading 在 v-if 外层）', async () => {
    // 手动控制 getCustomer 的 resolve 时机：挂载后、放行前 customer=null
    let release
    const gate = new Promise((resolve) => { release = resolve })
    getCustomer.mockReturnValueOnce(gate.then(() => ({ data: { id: 1, customerName: '慢客户' } })))
    const wrapper = mount(Detail, { global: { stubs } })
    await nextTick()
    // 首屏加载期间：根容器（v-loading 挂载点）存在，正文不存在
    expect(wrapper.find('.customer-detail').exists()).toBe(true)
    expect(wrapper.find('.hero-card').exists()).toBe(false)
    expect(wrapper.find('.detail-error').exists()).toBe(false)
    release()
    await flushPromises()
    expect(wrapper.find('.hero-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('慢客户')
  })

  it('分区单路失败：警示条标出失败分区，正文照常渲染', async () => {
    getIndicator.mockRejectedValueOnce(new Error('indicator boom'))
    const wrapper = mount(Detail, { global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.alert').exists()).toBe(true)
    expect(wrapper.find('.alert').text()).toContain('核心指标')
    // 其余内容正常（客户主体 + 警示条内提供重试）
    expect(wrapper.find('.hero-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('示例客户')
    expect(wrapper.find('.alert').text()).toContain('重试加载')
  })

  it('全部成功：无错误态、无警示条', async () => {
    const wrapper = mount(Detail, { global: { stubs } })
    await flushPromises()
    expect(wrapper.find('.detail-error').exists()).toBe(false)
    expect(wrapper.find('.alert').exists()).toBe(false)
    expect(wrapper.find('.hero-card').exists()).toBe(true)
  })
})
