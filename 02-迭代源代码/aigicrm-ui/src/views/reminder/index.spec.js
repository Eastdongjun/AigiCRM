import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/reminder', () => ({
  getExpiringList: vi.fn(),
  getReminderList: vi.fn(),
  processReminder: vi.fn(),
  ensureExpiring: vi.fn(),
}))
vi.mock('@/api/approval', () => ({ submitApproval: vi.fn() }))
vi.mock('@/api/system', () => ({ getScopeDepts: vi.fn() }))
vi.mock('@/api/customer', () => ({ getSimpleUserList: vi.fn() }))

import ReminderIndex from './index.vue'
import { getExpiringList, getReminderList, ensureExpiring } from '@/api/reminder'
import { getScopeDepts } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'

// EP 组件最小 stub：仅保留分页切片行为可测所需的 el-pagination
const stubs = {
  'el-input': { template: '<input />' },
  'el-tree-select': { template: '<div class="tree" />' },
  'el-select': { template: '<div class="select" />' },
  'el-option': true,
  'el-button': { template: '<button><slot /></button>' },
  'el-table': { template: '<div class="table"><slot /></div>' },
  // el-table-column 不渲染 slot：真实 el-table 通过作用域插槽传 row，
  // stub 无 row 上下文，渲染 slot 会访问 undefined._type
  'el-table-column': { template: '<div />' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div class="pager" />' },
}

function mockData() {
  ensureExpiring.mockResolvedValue({})
  getExpiringList.mockResolvedValue({ data: { records: [
    { id: 1, customerId: 101, customerName: '到期客户A', remainingDays: 5, expireDate: '2026-08-13', ownerName: '张三' },
    { id: 2, customerId: 102, customerName: '到期客户B', remainingDays: 45, expireDate: '2026-09-22', ownerName: '李四' },
  ] } })
  getReminderList.mockResolvedValue({ data: { records: [] } })
  getScopeDepts.mockResolvedValue({ data: [{ id: 10, deptName: '太原办' }] })
  getSimpleUserList.mockResolvedValue({ data: [{ id: 1, realName: '张三' }] })
}

describe('reminder/index.vue 组件冒烟', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockData()
  })

  it('onMounted 加载到期提醒并显示列表', async () => {
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    // 两张到期记录 → 客户端分页 total
    expect(getExpiringList).toHaveBeenCalled()
    const pagerInfo = wrapper.find('.rem-pager-info')
    expect(pagerInfo.text()).toContain('共 2 条')
  })

  it('pagedData 客户端分页切片（paginate 接入验证）', async () => {
    // 注入超过一页的数据（size=15，构造 20 条）
    const many = Array.from({ length: 20 }, (_, i) => ({
      id: i + 1, customerId: 1000 + i, customerName: `客户${i}`, remainingDays: 10, expireDate: '2026-12-31', ownerName: '张三',
    }))
    getExpiringList.mockResolvedValue({ data: { records: many } })
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    // total 应为 20（客户端分页 total = tableData.length）
    const pagerInfo = wrapper.find('.rem-pager-info')
    expect(pagerInfo.text()).toContain('共 20 条')
  })

  it('搜索 @change=search 会重置页码并重新加载', async () => {
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    // 先翻到第 2 页，再触发 search，验证页码被重置为 1
    wrapper.vm.query.current = 2
    await wrapper.vm.search()
    // 重置页码到 1（真钉住重置行为，而非只断言调用了 loadData）
    expect(wrapper.vm.query.current).toBe(1)
    // search 后 loadData → getExpiringList 再次调用
    expect(getExpiringList).toHaveBeenCalledTimes(2)
  })

  it('获取部门和负责人下拉数据', async () => {
    mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    expect(getScopeDepts).toHaveBeenCalled()
    expect(getSimpleUserList).toHaveBeenCalled()
  })
})

// @vue/test-utils 的 flushPromises 工具
function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
