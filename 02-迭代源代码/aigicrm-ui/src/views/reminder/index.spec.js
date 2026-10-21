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
vi.mock('@/api/system', () => ({ getScopeDepts: vi.fn(), getScopeUsers: vi.fn() }))
vi.mock('@/api/customer', () => ({ getSimpleUserList: vi.fn() }))

// 部分 mock vue-router：保留 createRouter 真实现（utils/request 会 import 真实 router 实例），
// 只替换 useRouter 以捕获"点击行 → 跳详情页"的跳转参数
const push = vi.fn()
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRouter: () => ({ push }) }
})

import ReminderIndex from './index.vue'
import { getExpiringList, getReminderList, ensureExpiring } from '@/api/reminder'
import { getScopeDepts, getScopeUsers } from '@/api/system'

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
  ], total: 2 } })
  getReminderList.mockResolvedValue({ data: { records: [], total: 0 } })
  getScopeDepts.mockResolvedValue({ data: [{ id: 10, deptName: '太原办' }] })
  getScopeUsers.mockResolvedValue({ data: [{ id: 1, realName: '张三' }] })
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
    // 服务端分页：total = 六段服务端 total 之和（到期 2 + 分享/开票 0）
    expect(getExpiringList).toHaveBeenCalled()
    const pagerInfo = wrapper.find('.rem-pager-info')
    expect(pagerInfo.text()).toContain('共 2 条')
    expect(wrapper.vm.tableData).toHaveLength(2)
  })

  // 【B2 二轮·服务端分页】分页器 total = 各段服务端真实 total 之和，非本地已加载行数；
  // 当前页行 = 按段拼接序对全局页区间取数后的精确切片
  it('分页器 total=六段服务端 total 之和；页 1 行数为跨段切片', async () => {
    const shareRows = [1, 2, 3].map((i) => ({ id: 400 + i, customerId: 300 + i, customerName: `分享客户${i}`, reminderType: 'share_to', ownerName: '王五' }))
    const expRows = Array.from({ length: 15 }, (_, i) => ({ id: i + 1, customerId: 1000 + i, customerName: `客户${i}`, remainingDays: 10, expireDate: '2026-12-31', ownerName: '张三' }))
    getReminderList.mockImplementation((params) =>
      params.type === 'share_to' ? { data: { records: shareRows, total: 3 } } : { data: { records: [], total: 0 } })
    getExpiringList.mockResolvedValue({ data: { records: expRows, total: 20 } })
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    // 3（share_to）+ 0×4 + 20（expiring）= 23，而非本地行数 15
    expect(wrapper.find('.rem-pager-info').text()).toContain('共 23 条')
    // 页 1 全局区间 [0,15)：share_to 段 3 行 + expiring 段内 [0,12) = 15 行
    expect(wrapper.vm.tableData).toHaveLength(15)
    expect(wrapper.vm.tableData[0]._type).toBe('share')
    expect(wrapper.vm.tableData[3]._type).toBe('expiring')
  })

  // 【B2 二轮·服务端分页】翻页重新取数：页码变化对触及段发出新的服务端请求（含跨块），
  // 不再是 paginate() 客户端切片——超过任何旧上限（15/200）的记录都可翻到
  it('翻到第 2 页重新请求服务端（跨块：expiring 段内 [5,20) 触发 current=1 与 current=2 两块）', async () => {
    const rows15 = Array.from({ length: 15 }, (_, i) => ({ id: i + 1, customerId: 1000 + i, customerName: `客户${i}`, remainingDays: 10, expireDate: '2026-12-31', ownerName: '张三' }))
    const rows5 = rows15.slice(0, 5).map((r) => ({ ...r, id: r.id + 100 }))
    const shareRows = Array.from({ length: 10 }, (_, i) => ({ id: 500 + i, customerId: 300 + i, customerName: `分享客户${i}`, reminderType: 'share_to', ownerName: '王五' }))
    getExpiringList.mockImplementation(({ current }) =>
      ({ data: { records: current === 1 ? rows15 : rows5, total: 20 } }))
    getReminderList.mockImplementation((params) =>
      params.type === 'share_to' ? { data: { records: shareRows, total: 10 } } : { data: { records: [], total: 0 } })
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    // 页 1：share_to 10 行 + expiring [0,5) = 15 行；total = 10 + 20 = 30
    expect(wrapper.vm.tableData).toHaveLength(15)
    expect(wrapper.vm.total).toBe(30)
    // 翻页 → 重新取数（@current-change=loadData 的同一逻辑入口）
    wrapper.vm.query.current = 2
    await wrapper.vm.loadData()
    await flushPromises()
    // 页 2 全局 [15,30)：share_to 段已越过，expiring 段内 [5,20) 跨两块 → current=1 与 current=2
    const expCalls = getExpiringList.mock.calls.map((c) => c[0]).filter((p) => p.size === 15)
    expect(expCalls.some((p) => p.current === 1)).toBe(true)
    expect(expCalls.some((p) => p.current === 2)).toBe(true)
    expect(wrapper.vm.tableData).toHaveLength(15)
    expect(wrapper.vm.total).toBe(30)
  })

  // 【B2 二轮】三路统一传 keyword：/reminder/list 自 C1 起服务端消费 keyword（applyKeywordScope，
  // total 与筛选同口径），分享/开票路不再依赖"合并后客户端过滤"
  it('搜索时三路（到期/分享/开票）请求统一携带 keyword', async () => {
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    const reminderCallsBefore = getReminderList.mock.calls.length
    const expCallsBefore = getExpiringList.mock.calls.length
    wrapper.vm.query.keyword = '客户'
    await wrapper.vm.search()
    await flushPromises()
    // 只断言 search 之后的新请求（挂载时的请求 keyword 为空串）
    const reminderCalls = getReminderList.mock.calls.slice(reminderCallsBefore).map((c) => c[0])
    expect(reminderCalls.length).toBeGreaterThanOrEqual(5)
    expect(reminderCalls.every((p) => p.keyword === '客户')).toBe(true)
    expect(getExpiringList.mock.calls.slice(expCallsBefore).some((c) => c[0].keyword === '客户')).toBe(true)
  })

  // 【B2 二轮·服务端分页】旧实现单类型最多取 200 条，超出部分永不可见；
  // 服务端分页后无任何取数上限——页 15（全局 [210,225)，超出旧 200 上限）照常取到
  it('超过旧 200 上限的记录可翻页取到（share_to total=450，页 15 请求该段 current=15）', async () => {
    const mk = (n, base) => Array.from({ length: n }, (_, i) => ({ id: base + i, customerId: 9000 + base + i, customerName: `分享客户${base + i}`, reminderType: 'share_to', ownerName: '王五' }))
    getReminderList.mockImplementation((p) => p.type === 'share_to'
      ? { data: { records: mk(Math.min(15, 450 - (p.current - 1) * 15), (p.current - 1) * 15), total: 450 } }
      : { data: { records: [], total: 0 } })
    getExpiringList.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    wrapper.vm.query.current = 15
    await wrapper.vm.loadData()
    await flushPromises()
    const stCalls = getReminderList.mock.calls.map((c) => c[0]).filter((p) => p.type === 'share_to' && p.size === 15)
    expect(stCalls.some((p) => p.current === 15)).toBe(true)
    expect(wrapper.vm.tableData).toHaveLength(15)
    expect(wrapper.vm.total).toBe(450)
    expect(wrapper.find('.rem-pager-info').text()).toContain('共 450 条')
  })

  it('搜索 @change=search 会重置页码并重新加载', async () => {
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    // 先翻到第 2 页，再触发 search，验证页码被重置为 1
    wrapper.vm.query.current = 2
    const before = getExpiringList.mock.calls.length
    await wrapper.vm.search()
    // 重置页码到 1（真钉住重置行为，而非只断言调用了 loadData）
    expect(wrapper.vm.query.current).toBe(1)
    // search 后 loadData → 到期路重新发起服务端请求（探测+取块）
    expect(getExpiringList.mock.calls.length).toBeGreaterThan(before)
  })

  it('获取部门和负责人下拉数据(负责人=范围感知接口)', async () => {
    mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    expect(getScopeDepts).toHaveBeenCalled()
    expect(getScopeUsers).toHaveBeenCalled()
  })

  it('点击行（openDetail）跳转到提醒详情页并带上提醒/客户/类型', async () => {
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    push.mockClear()

    // 分享/开票提醒行只有 reminderId；到期提醒行只有 id（客户维度）—— 两种都要能跳
    wrapper.vm.openDetail({ reminderId: 541, customerId: 295, reminderType: 'expiring' })
    expect(push).toHaveBeenCalledWith({
      name: 'ReminderDetail',
      params: { id: '541' },
      query: { customerId: '295', type: 'expiring' },
    })

    wrapper.vm.openDetail({ id: 540, customerId: 346, _type: 'expiring' })
    expect(push).toHaveBeenLastCalledWith({
      name: 'ReminderDetail',
      params: { id: '540' },
      query: { customerId: '346', type: 'expiring' },
    })
  })

  it('到期提醒缺失提醒实体时用 none 占位，详情页仍可打开（降级展示客户信息）', async () => {
    const wrapper = mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    push.mockClear()

    wrapper.vm.openDetail({ customerId: 999, _type: 'expiring', remainingDays: 3 })
    expect(push).toHaveBeenCalledWith({
      name: 'ReminderDetail',
      params: { id: 'none' },
      query: { customerId: '999', type: 'expiring' },
    })
  })

  // 【2026-09-23】ensure-expiring 后端 admin-only(P1-09): 非 admin 挂载不得触发(否则每次进页 403 弹错)
  it('非 admin 挂载不触发 ensure-expiring，列表照常加载', async () => {
    mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    expect(ensureExpiring).not.toHaveBeenCalled()
    expect(getExpiringList).toHaveBeenCalled()
  })

  it('admin 挂载仍触发 ensure-expiring 物化今日到期提醒', async () => {
    const { useUserStore } = await import('@/store/user')
    useUserStore().roles = ['admin']
    mount(ReminderIndex, { global: { stubs } })
    await flushPromises()
    expect(ensureExpiring).toHaveBeenCalledTimes(1)
    expect(getExpiringList).toHaveBeenCalled()
  })
})

// @vue/test-utils 的 flushPromises 工具
function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
