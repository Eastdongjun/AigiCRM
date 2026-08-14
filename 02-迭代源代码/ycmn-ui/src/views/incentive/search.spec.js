import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// 该页面绕过 api 模块直接 import request，需 mock 底层
vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/api/incentive', () => ({ getCategoryTree: vi.fn() }))
vi.mock('@/utils/token', () => ({ getToken: vi.fn(() => 'test-token') }))
// layout-cache 是模块级 Map 缓存，跨测试会污染，mock 成直通
vi.mock('@/utils/layout-cache', () => ({
  cachedGet: vi.fn((key, loader) => Promise.resolve(loader())),
}))

import SearchView from './search.vue'
import request from '@/utils/request'
import { getCategoryTree } from '@/api/incentive'

const stubs = {
  'el-button': { template: '<button><slot /></button>' },
  'el-input': { template: '<input />' },
  'el-tree-select': { template: '<div class="tree" />' },
  'el-select': { template: '<div class="select" />' },
  'el-option': true,
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': { template: '<div class="pager" />' },
  'el-dialog': { template: '<div class="dialog"><slot /></div>' },
  'el-date-picker': { template: '<div class="datepicker" />' },
  'el-icon': { template: '<i><slot /></i>' },
}

const records = [
  { id: 1, customerName: '邯钢能嘉', records: [{ id: 11, createdAt: '2026-08-01' }] },
  { id: 2, customerName: '空记录客户', records: [] },
  { id: 3, customerName: '', records: [{ id: 13 }] },
]

function mockData() {
  request.get.mockImplementation((url) => {
    if (url === '/incentive/customer-records') {
      return Promise.resolve({ data: records })
    }
    if (url === '/incentive/users') {
      return Promise.resolve({ data: [{ id: 1, realName: '张三' }] })
    }
    if (url === '/system/depts') {
      return Promise.resolve({ data: [{ id: 2, parentId: 1, deptName: '山西大区', deptType: 2 }] })
    }
    return Promise.resolve({ data: [] })
  })
  getCategoryTree.mockResolvedValue({ data: [{ id: 1, name: '涂层类', children: [] }] })
}

describe('incentive/search.vue 分页与搜索测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockData()
  })

  it('onMounted 并发加载分类/用户/部门并触发 doSearch', async () => {
    const wrapper = mount(SearchView, { global: { stubs } })
    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/incentive/customer-records', expect.anything())
    expect(request.get).toHaveBeenCalledWith('/incentive/users')
    expect(request.get).toHaveBeenCalledWith('/system/depts')
    expect(getCategoryTree).toHaveBeenCalled()
  })

  it('doSearch 重置页码到 1（A 反模式正确行为被钉死）', async () => {
    const wrapper = mount(SearchView, { global: { stubs } })
    await flushPromises()
    // 手动翻到第 3 页
    wrapper.vm.pageNum = 3
    // 触发 doSearch → 页码必须重置为 1
    await wrapper.vm.doSearch()
    expect(wrapper.vm.pageNum).toBe(1)
    // 重置后重新查询 customer-records（只断言关键端点，避免与并发加载次数硬耦合）
    const recordsCalls = request.get.mock.calls.filter(([url]) => url === '/incentive/customer-records')
    expect(recordsCalls.length).toBeGreaterThanOrEqual(2)
  })

  it('只展示有有效 records 且有名称的客户', async () => {
    const wrapper = mount(SearchView, { global: { stubs } })
    await flushPromises()
    // records 中 id2 无 records、id3 无名称 → 都被过滤，只剩 id1
    const names = wrapper.vm.custs.map((c) => c.customerName)
    expect(names).toEqual(['邯钢能嘉'])
  })

  it('doReset 清空筛选并重新搜索', async () => {
    const wrapper = mount(SearchView, { global: { stubs } })
    await flushPromises()
    wrapper.vm.fCustomer = '邯钢'
    wrapper.vm.doReset()
    expect(wrapper.vm.fCustomer).toBe('')
    expect(wrapper.vm.pageNum).toBe(1)
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
