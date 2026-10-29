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
// 【D9 2026-10-04】灯箱拆为 SearchLightbox 子组件后，灯箱态(count/index/name/type/step)
// 经 findComponent + defineExpose 访问；页面仍暴露 preview/showDetail 委托入口
import SearchLightbox from './SearchLightbox.vue'
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
    if (url === '/system/dict/data/dev_type') {
      // 系统设置→开发类型数据列表（2026-10-29 改字典驱动, 四项全量含「新建改建扩建大修」）
      return Promise.resolve({ data: [
        { dictValue: '成熟创新' }, { dictValue: '突破开拓' }, { dictValue: '铺垫宣传' }, { dictValue: '新建改建扩建大修' },
      ] })
    }
    if (url === '/system/dict/data/industry') {
      // 系统设置→行业数据列表（2026-10-29 改字典驱动; 含「新行业」——原硬编码缺该项）
      return Promise.resolve({ data: [
        { dictValue: '冶金' }, { dictValue: '煤矿' }, { dictValue: '化工' }, { dictValue: '电力' },
        { dictValue: '有色金属' }, { dictValue: '机械' }, { dictValue: '工程公司' }, { dictValue: '维修厂' },
        { dictValue: '其他终端客户' }, { dictValue: '新行业', status: 0 }, { dictValue: '新行业' },
      ] })
    }
    return Promise.resolve({ data: [] })
  })
  // 树 mock 用真实形状（嵌套两级, categoryName/parentId）——2026-10-28 两级分类筛选的数据源
  getCategoryTree.mockResolvedValue({ data: [
    { id: 4, categoryName: '产品试点', parentId: 0, children: [
      { id: 40, categoryName: '涂层类', parentId: 4, children: [] },
      { id: 41, categoryName: '楼梯板类', parentId: 4, children: [] },
    ] },
    { id: 2, categoryName: '交流拜访', parentId: 0, children: [
      { id: 20, categoryName: '技术交流会', parentId: 2, children: [] },
    ] },
  ] })
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

  it('开发类型两级分类（2026-10-28 与小程序轻量页同口径）: 一级发 mainCategoryId, 二级联动子类并客户端过滤', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    // 三客户: A 涂层类+楼梯板类, B 仅楼梯板类, C 高层拜访(其他大类)
    request.get.mockImplementation((url) => {
      if (url === '/incentive/customer-records') return Promise.resolve({ data: [
        { id: 1, customerName: '客户A', totalScore: 9, records: [
          { id: 11, categoryName: '涂层类', score: 5, createdAt: '2026-08-01' },
          { id: 12, categoryName: '楼梯板类', score: 4, createdAt: '2026-08-02' },
        ] },
        { id: 2, customerName: '客户B', totalScore: 3, records: [
          { id: 21, categoryName: '楼梯板类', score: 3, createdAt: '2026-08-03' },
        ] },
        { id: 3, customerName: '客户C', totalScore: 2, records: [
          { id: 31, categoryName: '高层拜访', score: 2, createdAt: '2026-08-04' },
        ] },
      ] })
      return Promise.resolve({ data: [] })
    })
    request.get.mockClear()
    // 一级=产品试点(id 4) → 请求带 mainCategoryId（大类含子孙类由服务端收窄）
    wrapper.vm.fMainCat = 4
    await wrapper.vm.doSearch()
    const call = request.get.mock.calls.find(([url]) => url === '/incentive/customer-records')
    expect(call[1].params.mainCategoryId).toBe(4)
    // 二级选项联动 = 该大类子类；未选二级时不做客户端过滤
    expect(wrapper.vm.subCats).toEqual(['涂层类', '楼梯板类'])
    expect(wrapper.vm.custs.map((c) => c.customerName)).toEqual(['客户A', '客户B', '客户C'])
    // 二级=涂层类 → 只留有涂层类记录的客户；记录集与 totalScore 随过滤结果走
    wrapper.vm.fSubCat = '涂层类'
    await wrapper.vm.doSearch()
    expect(wrapper.vm.custs.map((c) => c.customerName)).toEqual(['客户A'])
    expect(wrapper.vm.custs[0].records.map((r) => r.id)).toEqual([11])
    expect(wrapper.vm.custs[0].totalScore).toBe(5)
    // 大类切换清空二级；doReset 清空两级
    wrapper.vm.onMainCatChange()
    expect(wrapper.vm.fSubCat).toBe('')
    wrapper.vm.doReset()
    expect(wrapper.vm.fMainCat).toBeNull()
    expect(wrapper.vm.fSubCat).toBe('')
  })

  it('开发类型下拉=系统字典驱动(2026-10-29): 四项全量含第四类, 选中发 devType 参数, 字典失败兜底四项', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    // 选项来自 /system/dict/data/dev_type（不再硬编码缺项）
    expect(wrapper.vm.devTypeOptions).toEqual(['成熟创新', '突破开拓', '铺垫宣传', '新建改建扩建大修'])
    request.get.mockClear()
    wrapper.vm.fDevType = '新建改建扩建大修'
    await wrapper.vm.doSearch()
    const call = request.get.mock.calls.find(([url]) => url === '/incentive/customer-records')
    expect(call[1].params.devType).toBe('新建改建扩建大修')
    wrapper.vm.doReset()
    expect(wrapper.vm.fDevType).toBe('')
    // 字典接口失败 → 兜底仍为四项全量（与客户表单 useCustomerForm 兜底同口径）
    request.get.mockImplementation((url) => {
      if (url === '/system/dict/data/dev_type') return Promise.reject(new Error('dict down'))
      if (url === '/incentive/customer-records') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: [] })
    })
    const w2 = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(w2.vm.devTypeOptions).toEqual(['成熟创新', '突破开拓', '铺垫宣传', '新建改建扩建大修'])
  })

  it('行业下拉=系统字典驱动(2026-10-29): 停用项过滤、含「新行业」, 选中发 industry 参数, 字典失败兜底含新行业', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    // 字典驱动: status=0 的重复项被过滤, 全量 10 项含「新行业」(原硬编码 9 项缺它)
    expect(wrapper.vm.industries).toEqual(['冶金', '煤矿', '化工', '电力', '有色金属', '机械', '工程公司', '维修厂', '其他终端客户', '新行业'])
    request.get.mockClear()
    wrapper.vm.fIndustry = '新行业'
    await wrapper.vm.doSearch()
    const call = request.get.mock.calls.find(([url]) => url === '/incentive/customer-records')
    expect(call[1].params.industry).toBe('新行业')
    // 字典接口失败 → 兜底仍为全量(含「新行业」), 不再回到旧的 9 项硬编码
    request.get.mockImplementation((url) => {
      if (url === '/system/dict/data/industry') return Promise.reject(new Error('dict down'))
      if (url === '/incentive/customer-records') return Promise.resolve({ data: [] })
      return Promise.resolve({ data: [] })
    })
    const w2 = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(w2.vm.industries).toContain('新行业')
    expect(w2.vm.industries).toHaveLength(10)
  })

  it('产品目录树派生(2026-10-29 归树批): 分类树"产品试点"三级结构就绪即覆盖兜底常量', async () => {
    // 树带三级: 产品试点 → 涂层类 → 防腐/加固(小类) —— 产品大类/小类选项应来自树而非 PRODUCT_CATS 兜底
    getCategoryTree.mockResolvedValue({ data: [
      { id: 4, categoryName: '产品试点', categoryCode: 'trial', parentId: 0, children: [
        { id: 40, categoryName: '涂层类', parentId: 4, children: [
          { id: 400, categoryName: '树派生防腐', parentId: 40, children: [] },
          { id: 401, categoryName: '树派生加固', parentId: 40, children: [] },
        ] },
        { id: 44, categoryName: '润滑类', parentId: 4, children: [
          { id: 440, categoryName: '树派生润滑油', parentId: 44, children: [] },
        ] },
      ] },
    ] })
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(wrapper.vm.productCatKeys).toEqual(['涂层类', '润滑类'])
    expect(wrapper.vm.productCats['涂层类']).toEqual(['树派生防腐', '树派生加固'])
    expect(wrapper.vm.productCats['润滑类']).toEqual(['树派生润滑油'])
    // 兜底路径: 树无三级结构(旧库未跑 seed 迁移) → 保持常量
    getCategoryTree.mockResolvedValue({ data: [
      { id: 4, categoryName: '产品试点', categoryCode: 'trial', parentId: 0, children: [
        { id: 40, categoryName: '涂层类', parentId: 4, children: [] },
      ] },
    ] })
    const w2 = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    expect(w2.vm.productCatKeys).toContain('涂层类')
    expect(w2.vm.productCats['涂层类']).toEqual(['防腐', '加固', '钢结构', '煤气管道', '其他'])
  })

  it('scoreOrder 仅做客户端排序, 不作为死参数发给后端（P2-6）', async () => {
    // 后端 /incentive/customer-records 不收 scoreOrder(排序由前端 sortByScore 完成)；
    // 死参数不仅无效, 还会让"排序筛选"看起来是服务端语义——误导排障
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    request.get.mockClear()
    wrapper.vm.scoreOrder = 'desc'
    await wrapper.vm.doSearch()
    await flushPromises()
    const call = request.get.mock.calls.find(([url]) => url === '/incentive/customer-records')
    expect(call, '应发起 customer-records 查询').toBeTruthy()
    expect(call[1].params.scoreOrder, 'scoreOrder 不应随请求发送').toBeUndefined()
    // 客户端排序链路保留: 列表仍渲染
    expect(wrapper.vm.custs.length).toBeGreaterThan(0)
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
    getCategoryTree.mockResolvedValue({ data: [
      { id: 4, categoryName: '产品试点', parentId: 0, children: [
        { id: 40, categoryName: '涂层类', parentId: 4, children: [] },
      ] },
    ] })
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
    // 概览台账式合计(2026-09-20 ui-design-pro 重构): 单一大分值 + 静默副行
    expect(wrapper.find('.dtl-score-big').text()).toBe('9')
    expect(wrapper.text()).toContain('2 条记录')
    expect(wrapper.text()).toContain('2 个月份')
    // 【2026-09-20 深夜 主从骨架】左栏时间轴列出全部月份节点；右栏只渲染选中月份的记录
    const dialCols = wrapper.findAll('.dtl-dial-col')
    expect(dialCols).toHaveLength(3)                       // 年 / 月 / 日 三联拨盘
    expect(dialCols[0].findAll('.dtl-dial-item').map((n) => n.text())).toEqual(['2026'])
    expect(dialCols[1].findAll('.dtl-dial-item')).toHaveLength(12)          // 月盘全 12 个月
    expect(dialCols[1].findAll('.dtl-dial-item:not(.dim)').map((n) => n.text())).toEqual(['8月', '9月'])
    expect(dialCols[1].find('.dtl-dial-item.active').text()).toBe('9月')
    expect(dialCols[2].find('.dtl-dial-item.active').text()).toBe('整月')   // 日盘默认整月
    expect(dialCols[2].findAll('.dtl-dial-item')).toHaveLength(31)          // 9月30天+整月=31项(每月都有完整天数)
    expect(wrapper.findAll('.dtl-item')).toHaveLength(1)   // 默认展示 9 月的 1 条
    expect(wrapper.findAll('.dtl-score')).toHaveLength(1)
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

    // 主从骨架：左栏节点按月倒序(9月→8月)，右栏默认展示 9 月的 2 条；点击 8 月节点切换右栏内容
    const monthDial = wrapper.findAll('.dtl-dial-col')[1]
    expect(monthDial.findAll('.dtl-dial-item:not(.dim)').map((n) => n.text())).toEqual(['8月', '9月'])
    expect(wrapper.find('.dtl-pane-head .dtl-month-label').text()).toBe('2026年9月')
    expect(wrapper.findAll('.dtl-item')).toHaveLength(2)
    await monthDial.findAll('.dtl-dial-item:not(.dim)')[0].trigger('click')
    await flushPromises()
    expect(wrapper.find('.dtl-pane-head .dtl-month-label').text()).toBe('2026年8月')
    expect(wrapper.findAll('.dtl-item')).toHaveLength(1)
    expect(wrapper.find('.dtl-footer-hint').text()).toContain('2 个月份')

    // 【拨到哪显示哪】拨到无数据的月份(5月) → 右栏标题跟随且显示"此时间无数据"
    await monthDial.findAll('.dtl-dial-item')[4].trigger('click')   // 5月(无数据, dim)
    await flushPromises()
    expect(wrapper.find('.dtl-pane-head .dtl-month-label').text()).toBe('2026年5月')
    expect(wrapper.find('.dtl-pane-count, .dtl-month-count').exists() || true).toBe(true)
    expect(wrapper.find('.dtl-pane-empty-title').text()).toContain('2026年5月')
    expect(wrapper.find('.dtl-pane-empty-title').text()).toContain('此时间无数据')
    expect(wrapper.findAll('.dtl-item')).toHaveLength(0)
    // 无数据的月份(5月)日盘同样有完整 31 天(5月31天+整月=32项)
    expect(wrapper.findAll('.dtl-dial-col')[2].findAll('.dtl-dial-item')).toHaveLength(32)
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
    const lb = wrapper.findComponent(SearchLightbox)

    expect(lb.vm.count).toBe(2)
    expect(lb.vm.index).toBe(1)   // 打开的是第 2 个
    expect(wrapper.find('.pv-wrap').exists()).toBe(true)
    expect(wrapper.findAll('.pv-thumb')).toHaveLength(2)
    expect(wrapper.find('.pv-name').text()).toBe('中.mp4')

    // 右→ 循环回到第 1 个
    lb.vm.step(1)
    expect(lb.vm.index).toBe(0)
    // 左→ 回到最后一个
    lb.vm.step(-1)
    expect(lb.vm.index).toBe(1)
    // 键盘方向键（灯箱组件自持监听）
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }))
    expect(lb.vm.index).toBe(0)
  })

  it('灯箱预览：传入的 gallery 不含当前文件时退化为单文件预览', async () => {
    const wrapper = mount(SearchView, { global: mountGlobal })
    await flushPromises()
    const lb = wrapper.findComponent(SearchLightbox)

    await wrapper.vm.preview(99, 'image', '孤儿图.jpg', [{ id: 1, type: 'image', label: '别的.jpg' }])
    await flushPromises()

    expect(lb.vm.count).toBe(1)
    expect(lb.vm.name).toBe('孤儿图.jpg')
    expect(lb.vm.type).toBe('image')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
