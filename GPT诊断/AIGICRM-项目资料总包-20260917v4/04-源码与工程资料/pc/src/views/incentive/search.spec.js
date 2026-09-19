import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// 该页面绕过 api 模块直接 import request，需 mock 底层
vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/api/incentive', () => ({ getCategoryTree: vi.fn(), markIncentiveRead: vi.fn(() => Promise.resolve()) }))
// 用真实 ref 模拟 token 模块：getDownloadToken 渲染期读 ref.value，与真实实现一致，
// 测试中改 ref 值即可验证「token 就绪后自动重渲染」
vi.mock('@/utils/token', async () => {
  const { ref } = await import('vue')
  const dlToken = ref('')
  return {
    getToken: vi.fn(() => 'test-token'),
    getDownloadToken: vi.fn(() => dlToken.value),
    refreshDownloadToken: vi.fn(() => Promise.resolve(null)),
    __dlToken: dlToken,
  }
})
// layout-cache 是模块级 Map 缓存，跨测试会污染，mock 成直通
vi.mock('@/utils/layout-cache', () => ({
  cachedGet: vi.fn((key, loader) => Promise.resolve(loader())),
}))

import SearchView from './search.vue'
import request from '@/utils/request'
import { getCategoryTree } from '@/api/incentive'
import { __dlToken } from '@/utils/token'

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
  'el-empty': { props: ['description'], template: '<div class="empty">{{ description }}</div>' },
}

// v-loading 是 Element Plus 指令；测试里给空指令桩，避免 "Failed to resolve directive" 噪声
const mountGlobal = { stubs, directives: { loading: {} } }

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

// 缩略图用例：带一张图片文件的客户数据
const imgRecords = [
  {
    customerId: 1, customerName: '客户A', industry: '冶金', totalScore: 10,
    records: [{ id: 11, categoryName: '涂层类', period: '2026上半年', userName: '张三', score: 5, filePreId: '99', filePreName: 'a.jpg' }],
  },
]

describe('incentive/search.vue 分页与搜索测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    __dlToken.value = ''
    mockData()
  })

  it('onMounted 并发加载分类/用户/部门并触发 doSearch', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/incentive/customer-records', expect.anything())
    expect(request.get).toHaveBeenCalledWith('/incentive/users')
    expect(request.get).toHaveBeenCalledWith('/system/depts')
    expect(getCategoryTree).toHaveBeenCalled()
  })

  it('doSearch 重置页码到 1（A 反模式正确行为被钉死）', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
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
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    // records 中 id2 无 records、id3 无名称 → 都被过滤，只剩 id1
    const names = wrapper.vm.custs.map((c) => c.customerName)
    expect(names).toEqual(['邯钢能嘉'])
  })

  it('doReset 清空筛选并重新搜索', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    wrapper.vm.fCustomer = '邯钢'
    wrapper.vm.doReset()
    expect(wrapper.vm.fCustomer).toBe('')
    expect(wrapper.vm.pageNum).toBe(1)
  })
})

describe('incentive/search.vue 缩略图下载 token（空 token 403 修复 2026-08-14）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __dlToken.value = ''
    request.get.mockImplementation((url) => {
      if (url === '/incentive/customer-records') return Promise.resolve({ data: imgRecords })
      if (url === '/incentive/users') return Promise.resolve({ data: [] })
      if (url === '/system/depts') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: [] })
    })
    getCategoryTree.mockResolvedValue({ data: [{ id: 1, name: '涂层类', children: [] }] })
  })

  it('下载 token 未就绪时 thumbUrl 不拼接 token=（不产生必然 403 的无效请求）', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(wrapper.vm.thumbUrl(7)).toBe('/api/file/download/7')
    expect(wrapper.vm.thumbUrl(7)).not.toContain('token=')
    expect(wrapper.find('img').attributes('src')).not.toContain('token=')
  })

  it('下载 token 就绪后 thumbUrl 带 token 且保留 thumbnail=1', async () => {
    __dlToken.value = 'dl-tok'
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(wrapper.vm.thumbUrl(7)).toBe('/api/file/download/7?token=dl-tok&thumbnail=1')
    expect(wrapper.find('img').attributes('src')).toContain('token=dl-tok')
  })

  it('token 就绪后列表缩略图自动重渲染出带 token 的 URL（图片自动刷新）', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    // 初始未就绪：src 不含 token=
    expect(wrapper.find('img').attributes('src')).not.toContain('token=')
    // 模拟下载 token 拉取成功（模块级 ref 变化 → 渲染链重跑 → <img> 自动重载）
    __dlToken.value = 'dl-tok'
    await flushPromises()
    expect(wrapper.find('img').attributes('src')).toBe('/api/file/download/99?token=dl-tok&thumbnail=1')
  })

  it('查看详情：弹窗渲染客户概览与记录时间线（2026-09-13 UI 美化）', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()

    await wrapper.vm.showDetail({
      customerId: 1,
      customerName: '邯钢能嘉',
      industry: '冶金',
      count: 2,
      totalScore: 9,
      records: [
        { id: 11, categoryName: '高层拜访', score: 2, userName: '张三', customerName: '邯钢能嘉', createdAt: '2026-09-01 10:00:00' },
        { id: 12, categoryName: '技术交流会', score: 1, userName: '李四', createdAt: '2026-08-20 09:00:00' },
      ],
    })
    await flushPromises()

    // 概览区：吸顶条 + 头像 + 客户名 + 累计积分
    expect(wrapper.find('.dtl-bar-name').text()).toBe('邯钢能嘉')
    expect(wrapper.find('.dtl-hero-name').text()).toBe('邯钢能嘉')
    expect(wrapper.find('.dtl-avatar').text()).toBe('邯')
    // 概览三项指标：累计积分 / 激励条数 / 覆盖月份
    expect(wrapper.find('.dtl-metric-num.primary').text()).toBe('9')
    expect(wrapper.findAll('.dtl-metric')).toHaveLength(3)
    // 时间线：2 条记录 → 2 个内容卡 + 2 个积分胶囊
    expect(wrapper.findAll('.dtl-item')).toHaveLength(2)
    expect(wrapper.findAll('.dtl-score')).toHaveLength(2)
    expect(wrapper.text()).toContain('累计积分')
  })

  it('查看详情：无记录时展示空态而不是空白', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()

    await wrapper.vm.showDetail({ customerId: 2, customerName: '空记录客户', count: 0, totalScore: 0, records: [] })
    await flushPromises()

    expect(wrapper.find('.empty').text()).toContain('暂无激励记录')
    expect(wrapper.findAll('.dtl-item')).toHaveLength(0)
  })

  it('时间轴按月分组：不同月份各成一组，组内时间倒序', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()

    await wrapper.vm.showDetail({
      customerId: 3,
      customerName: '跨月客户',
      totalScore: 6,
      records: [
        { id: 21, categoryName: '高层拜访', score: 1, createdAt: '2026-08-20 09:00:00' },
        { id: 22, categoryName: '技术交流会', score: 2, createdAt: '2026-09-01 10:00:00' },
        { id: 23, categoryName: '项目方案', score: 3, createdAt: '2026-09-13 18:00:00' },
      ],
    })
    await flushPromises()

    const monthLabels = wrapper.findAll('.dtl-month-label').map((n) => n.text())
    expect(monthLabels).toEqual(['2026年9月', '2026年8月'])
    expect(wrapper.findAll('.dtl-item')).toHaveLength(3)
    expect(wrapper.find('.dtl-footer-hint').text()).toContain('2 个月份')
  })

  it('灯箱预览：可左右循环切换、支持键盘方向键、缩略图条渲染', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()

    const gallery = [
      { id: 1, type: 'image', label: '前.jpg', tag: '安装前' },
      { id: 2, type: 'video', label: '中.mp4', tag: '安装中' },
    ]
    await wrapper.vm.preview(2, 'video', '中.mp4', gallery)
    await flushPromises()

    expect(wrapper.vm.previewCount).toBe(2)
    expect(wrapper.vm.previewIndex).toBe(1)   // 打开的是第 2 个
    expect(wrapper.find('.pv-wrap').exists()).toBe(true)
    expect(wrapper.findAll('.pv-thumb')).toHaveLength(2)
    expect(wrapper.find('.pv-name').text()).toBe('中.mp4')

    // 右→ 循环回到第 1 个
    wrapper.vm.previewStep(1)
    expect(wrapper.vm.previewIndex).toBe(0)
    // 左→ 回到最后一个
    wrapper.vm.previewStep(-1)
    expect(wrapper.vm.previewIndex).toBe(1)
    // 键盘方向键
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }))
    expect(wrapper.vm.previewIndex).toBe(0)
  })

  it('灯箱预览：传入的 gallery 不含当前文件时退化为单文件预览', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()

    await wrapper.vm.preview(99, 'image', '孤儿图.jpg', [{ id: 1, type: 'image', label: '别的.jpg' }])
    await flushPromises()

    expect(wrapper.vm.previewCount).toBe(1)
    expect(wrapper.vm.previewName).toBe('孤儿图.jpg')
    expect(wrapper.vm.previewType).toBe('image')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
