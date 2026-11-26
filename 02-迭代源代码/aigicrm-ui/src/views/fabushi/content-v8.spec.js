import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { ElTable, ElTableColumn, ElIcon } from 'element-plus'

// 【2026-11-26 按 PC 原型 v8】内容管理（content.vue = 原型 P11；原型里这一屏用的是已废止的旧叫法，产品菜单名保持“内容管理”）。
// 用户裁决“PC 端按最新的原型来做”。这里钉住五件事：
//   ① 骨架跟原型走：根节点 .fx-page，没有页头标题块；类别页签 → 筛选条 → 表格卡；
//   ② 页签还是现有的五个（每日诵读 / 每日随施 / 再读一本 / 已下架 / 已删除，各带条数）——
//      原型里的“待审核”后端没有这个流程，不加；
//   ③ 原型筛选条里的四个筛选，只做接口确实支持的：“书目”（bookId）和“关键词”（keyword：服务端按人名 / 书籍 / 具体文字搜）。
//      作者、日期接口没有入参，不做假筛选；“类别”就是上面的页签。“已删除”走专用接口、只收分页，
//      关键词不发给它，只在已加载的这一页里筛；
//   ④ 原有能操作的东西一个都不少：行内 详情 / 下架 / 恢复 / 恢复已删（按状态与权限出现）、两个弹窗；
//      权限码（fabushi:content:manage）只留在权限判断里，不显示给使用者；
//   ⑤ 表格列与原型一致：类型、状态是标签；互动合成一列（木鱼 · 评论 · 复诵）；每列都给了宽度，数字不换行。
// 口径与 paged-list-mount.spec.js 相同：真实视图挂载；接口、状态仓库、消息是替身。
// 表格与图标在这里按模板自动解析（工程里由 unplugin 在构建时注入），测试里没有构建这一步，
// 所以用 global.components 登记成真实组件，断言落在真实渲染出来的行上；其余 Element Plus 组件用会渲染插槽的桩。
// 抓不到：真实布局与配色、单元格到底折不折行（靠截图和原型并排核对）。

const hoisted = vi.hoisted(() => ({
  permissions: ['*'],
}))

vi.mock('@/api/fabushi', () => ({
  getFabushiPosts: vi.fn(), getDeletedFabushiPosts: vi.fn(), getFabushiBooks: vi.fn(),
  offlineFabushiPost: vi.fn(), restoreFabushiPost: vi.fn(), restoreDeletedFabushiPost: vi.fn(),
}))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    hasPermission: (code) => hoisted.permissions.includes('*') || hoisted.permissions.includes(code),
    userId: 7,
  }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import * as fabushiApiModule from '@/api/fabushi'
import { ElMessage } from 'element-plus'
import ContentView from './content.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const {
  getFabushiPosts, getDeletedFabushiPosts, getFabushiBooks,
  offlineFabushiPost, restoreFabushiPost, restoreDeletedFabushiPost,
} = fabushiApi
const elMessage = /** @type {{ success: import('vitest').Mock, warning: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessage))
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 渲染插槽(与挂载冒烟同一个做法)。按钮单独一个类名, 好按文字找;
// 选项、弹窗、分页条把要核对的东西写进 data-* 属性
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const INPUT_STUB = { template: '<div class="el-stub el-input"><slot name="prefix" /><slot /></div>' }
const SELECT_STUB = { template: '<div class="el-stub el-select"><slot /></div>' }
const OPTION_STUB = {
  props: { label: String, value: [String, Number] },
  template: '<span class="el-stub el-option" :data-label="label" />',
}
const DIALOG_STUB = {
  props: { title: String },
  template: '<div class="el-stub el-dialog" :data-title="title"><slot name="header" /><slot /><slot name="footer" /></div>',
}
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" :data-total="total" :data-page="currentPage" :data-layout="layout" />',
}
const stubs = {
  'el-button': BUTTON_STUB, 'el-option': OPTION_STUB, 'el-input': INPUT_STUB, 'el-select': SELECT_STUB,
  'el-dialog': DIALOG_STUB, 'el-pagination': PagerStub,
}
const components = { 'el-table': ElTable, 'el-table-column': ElTableColumn, 'el-icon': ElIcon }

const post = (n, extra = {}) => ({
  id: n, userName: '管理员', scopeType: 'songdu', title: null, content: `内容 ${n}`,
  likeCount: 0, commentCount: 0, recitationCount: 0, status: 'published',
  createdAt: '2026-11-23T15:16:42', bookTitle: null, sectionTitle: null, ...extra,
})
// 接口按“类型:状态”回不同列表（条数请求 size=1 也走这里）
const POSTS = {
  'songdu:published': [
    post(9, { title: '冒烟-诵读-第一章', bookTitle: 'UI回归-验收书B', sectionTitle: '第一章 立命之学', commentCount: 1, recitationCount: 1 }),
    post(14, { title: '第二条诵读', bookTitle: '易经', likeCount: 12 }),
  ],
  'suishi:published': [post(13, { scopeType: 'suishi', content: '冒烟验证内容 B（随施，无书目）', likeCount: 9, commentCount: 2 })],
  'zaidu:published': [post(12, { scopeType: 'zaidu', title: 'UI回归-推荐卡验证', bookTitle: '易经', likeCount: 21, commentCount: 8, recitationCount: 2 })],
  ':offline': [post(20, { status: 'offline', title: '已下架的诵读', offlineReason: '与本月书目不符' })],
}
// 已删除的帖子走专用接口，行里的 status 还是删除前的值（库里的旧值）
const DELETED_POSTS = [post(30, { title: '被删除的内容', bookTitle: '易经' })]
const BOOK_OPTIONS = [{ id: 6, title: 'UI回归-验收书B' }, { id: 17, title: '易经' }]

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.permissions = ['*']
  getFabushiPosts.mockImplementation((params) => {
    const list = POSTS[`${params.scopeType || ''}:${params.status}`] || []
    return Promise.resolve({ data: { records: list.map((item) => ({ ...item })), total: list.length } })
  })
  getDeletedFabushiPosts.mockResolvedValue({ data: { records: DELETED_POSTS.map((item) => ({ ...item })), total: DELETED_POSTS.length } })
  getFabushiBooks.mockResolvedValue({ data: { records: BOOK_OPTIONS.map((item) => ({ ...item })), total: BOOK_OPTIONS.length } })
})

async function mountQuietly() {
  const wrapper = mount(ContentView, {
    global: {
      stubs,
      components,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  return wrapper
}

/** 真实 el-table 渲染出来的行 */
const tableRows = (wrapper) => wrapper.findAll('.el-table__body tr.el-table__row')
const cells = (tr) => tr.findAll('td.el-table__cell')
const cellText = (tr, index) => cells(tr)[index].text()
const actionTexts = (tr) => {
  const list = cells(tr)
  return list[list.length - 1].findAll('.btn-stub').map((button) => button.text())
}
const texts = (wrapper, selector) => wrapper.findAll(selector).map((node) => node.text())
const lastCallArgs = (fn) => /** @type {any[][]} */ (fn.mock.calls)[fn.mock.calls.length - 1]

describe('内容管理按 PC 原型 v8（P11）', () => {
  it('骨架：根节点是 .fx-page（保留 .fabushi-content）；类别页签 → 筛选条 → 表格卡；权限码不显示给使用者', async () => {
    const wrapper = await mountQuietly()
    expect(warnings, 'Vue 警告').toEqual([])
    const root = wrapper.find('.fx-page.fabushi-content')
    expect(root.exists()).toBe(true)
    const html = root.html()
    const at = (marker) => html.indexOf(marker)
    expect(at('class="cat-tabs')).toBeGreaterThanOrEqual(0)
    expect(at('class="cat-tabs')).toBeLessThan(at('class="filter-card'))
    expect(at('class="filter-card')).toBeLessThan(at('class="table-card'))
    expect(root.find('.page-head').exists()).toBe(false)
    expect(root.text()).not.toContain('fabushi:')
    expect(root.text()).not.toMatch(/演示|见 P\d/)
  })

  it('页签还是现有的五个，各带条数；没有“待审核”页签', async () => {
    const wrapper = await mountQuietly()
    // “已删除”的条数取自专用接口 getDeletedFabushiPosts 的 total：常规分页接口里没有“已删除”这个状态值，从它取恒为 0
    expect(texts(wrapper, '.cat-tab')).toEqual(['每日诵读2', '每日随施1', '再读一本1', '已下架1', '已删除1'])
    expect(wrapper.find('.cat-tab.active').text()).toBe('每日诵读2')
    expect(getFabushiPosts).not.toHaveBeenLastCalledWith(expect.objectContaining({ status: 'deleted' }))
    expect(getDeletedFabushiPosts).toHaveBeenCalledWith({ current: 1, size: 1 })
  })

  it('条数各算各的：某一个条数请求失败，只让那一个记 0，不拖垮其余，也不留未处理的异常', async () => {
    getFabushiPosts.mockImplementation((params) => (params.status === 'offline'
      ? Promise.reject(new Error('503'))
      : Promise.resolve({ data: { records: [], total: params.status === 'published' ? 3 : 0 } })))
    const wrapper = await mountQuietly()
    expect(vm(wrapper).counts).toEqual({ songdu: 3, suishi: 3, zaidu: 3, offline: 0, deleted: 1 })
  })

  it('没有管理权限时不问“已删除”的条数（那个接口要管理权限，问了就是一条“没有操作权限”）', async () => {
    hoisted.permissions = ['fabushi:content:view']
    await mountQuietly()
    expect(getDeletedFabushiPosts).not.toHaveBeenCalled()
  })

  it('筛选条：书目（接口支持 bookId）+ 关键词 + 查询 + 重置；类别 / 作者 / 日期不做假筛选', async () => {
    const wrapper = await mountQuietly()
    const card = wrapper.find('.filter-card')
    expect(texts(wrapper, '.filter-card .fl')).toEqual(['书目', '关键词'])
    expect(card.find('.el-select').findAll('.el-option').map((option) => option.attributes('data-label'))).toEqual(['UI回归-验收书B', '易经'])
    expect(card.find('.el-input').attributes('placeholder')).toBe('搜标题 / 正文 / 发布人')
    expect(card.findAll('.btn-stub').map((button) => button.text())).toEqual(['查询', '重置'])
    expect(card.text()).not.toMatch(/类别|作者|日期/)
    // 书目下拉的选项来自已有的书目查询（与认知广场同一个调用）
    expect(getFabushiBooks).toHaveBeenCalledWith({ status: 'approved', current: 1, size: 200 })
  })

  it('表格卡：列与原型一致（互动合成 木鱼 · 评论 · 复诵 一列）；类型、状态是标签；每列都给了宽度', async () => {
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.el-table__header th .cell')).toEqual([
      '作品 / 正文摘要', '类型', '关联书目', '作者', '互动', '状态', '发布时间', '操作',
    ])
    const [first, second] = tableRows(wrapper)
    expect(tableRows(wrapper)).toHaveLength(2)

    expect(cellText(first, 0)).toBe('冒烟-诵读-第一章')
    expect(first.find('td:nth-child(2) .tag.blue').text()).toBe('每日诵读')
    expect(cellText(first, 2)).toBe('《UI回归-验收书B》 › 第一章 立命之学')
    expect(cellText(first, 3)).toBe('管理员')
    expect(first.find('td:nth-child(5) .num').text()).toBe('木鱼0 · 评论1 · 复诵1')
    expect(first.find('td:nth-child(6) .tag.green').text()).toBe('发布中')
    expect(first.find('td:nth-child(7) .num').text()).toBe('2026-11-23 15:16')
    expect(cellText(second, 2)).toBe('《易经》')
    expect(second.find('td:nth-child(5) .num').text()).toBe('木鱼12 · 评论0 · 复诵0')

    // 结构前提：列宽都给了、数字不换行（折不折行靠截图核对）
    const src = readFileSync('src/views/fabushi/content.vue', 'utf-8')
    const columns = src.match(/<el-table-column\b[^>]*>/g) || []
    expect(columns).toHaveLength(8)
    for (const column of columns) expect(column, column).toMatch(/\s(?:min-)?width="/)
    // 定宽列加两个弹性列的最小宽度，不超过 1280 宽屏里表格卡的宽度（1048px），不出横向滚动条
    const widths = columns.map((column) => Number((column.match(/\s(?:min-)?width="(\d+)"/) || [])[1]))
    expect(widths.reduce((sum, width) => sum + width, 0)).toBeLessThanOrEqual(1048)
    expect(src).toMatch(/\.num\s*\{[^}]*white-space:\s*nowrap/)
    expect(readFileSync('src/assets/fabushi-v8.css', 'utf-8')).toMatch(/\.fx-page \.tag\s*\{[^}]*white-space:\s*nowrap/)
  })

  it('类型标签按原型配色：每日诵读蓝、每日随施橙、再读一本绿；随施不关联书目时照实写', async () => {
    const wrapper = await mountQuietly()
    await wrapper.findAll('.cat-tab')[1].trigger('click')
    await flushPromises()
    expect(firstRow(wrapper).find('td:nth-child(2) .tag.orange').text()).toBe('每日随施')
    expect(cellText(firstRow(wrapper), 2)).toBe('—（不关联）')
    // 没有标题的作品显示正文摘要
    expect(cellText(firstRow(wrapper), 0)).toBe('冒烟验证内容 B（随施，无书目）')

    await wrapper.findAll('.cat-tab')[2].trigger('click')
    await flushPromises()
    expect(firstRow(wrapper).find('td:nth-child(2) .tag.green').text()).toBe('再读一本')
    expect(cellText(firstRow(wrapper), 2)).toBe('《易经》')
  })

  it('状态标签与行内操作：发布中 → 详情 / 下架；已下架 → 详情 / 恢复；已删除 → 详情 / 恢复已删', async () => {
    const wrapper = await mountQuietly()
    expect(actionTexts(firstRow(wrapper))).toEqual(['详情', '下架'])

    await wrapper.findAll('.cat-tab')[3].trigger('click')
    await flushPromises()
    expect(firstRow(wrapper).find('td:nth-child(6) .tag.red').text()).toBe('已下架')
    expect(actionTexts(firstRow(wrapper))).toEqual(['详情', '恢复'])

    await wrapper.findAll('.cat-tab')[4].trigger('click')
    await flushPromises()
    // 已删除的帖子行里 status 还是旧值（published），标签按所在页签照实写“已删除”
    expect(vm(wrapper).posts[0].status).toBe('published')
    expect(firstRow(wrapper).find('td:nth-child(6) .tag.gray').text()).toBe('已删除')
    expect(actionTexts(firstRow(wrapper))).toEqual(['详情', '恢复已删'])
  })

  it('没有管理权限：三个页签里都只有“详情”', async () => {
    hoisted.permissions = ['fabushi:content:view']
    const wrapper = await mountQuietly()
    const tabs = wrapper.findAll('.cat-tab')
    for (const index of [0, 3, 4]) {
      await tabs[index].trigger('click')
      await flushPromises()
      expect(actionTexts(firstRow(wrapper))).toEqual(['详情'])
    }
  })

  it('切换页签：接口参数照旧（类型 + 状态 + 页码）；已删除走专用接口，只带分页', async () => {
    const wrapper = await mountQuietly()
    // 进页面时条数请求（size=1）与列表请求并发，这里只核对列表请求发过、参数没变
    expect(getFabushiPosts).toHaveBeenCalledWith(expect.objectContaining({ scopeType: 'songdu', status: 'published', current: 1, size: 10 }))
    await wrapper.findAll('.cat-tab')[1].trigger('click')
    await flushPromises()
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual(expect.objectContaining({ scopeType: 'suishi', status: 'published', current: 1, size: 10 }))

    await wrapper.findAll('.cat-tab')[4].trigger('click')
    await flushPromises()
    expect(getDeletedFabushiPosts).toHaveBeenLastCalledWith({ current: 1, size: 10 })
    // 已删除接口不支持按书目筛，这一页不出书目筛选
    expect(wrapper.find('.filter-card .el-select').exists()).toBe(false)
    expect(texts(wrapper, '.filter-card .fl')).toEqual(['关键词'])
    // 分页条：总条数 + 翻页
    expect(wrapper.find('.table-card .el-pager').attributes('data-layout')).toBe('total, prev, pager, next')
    expect(wrapper.find('.table-card .el-pager').attributes('data-total')).toBe('1')
  })

  it('书目筛选：选了书目就带 bookId 查询（其余参数不变）；换页签时清掉；重置也清掉', async () => {
    const wrapper = await mountQuietly()
    vm(wrapper).bookFilter = 6
    await vm(wrapper).resetPage()
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual({ scopeType: 'songdu', status: 'published', bookId: 6, current: 1, size: 10 })

    vm(wrapper).keyword = '诵读'
    vm(wrapper).search()
    await flushPromises()
    // 换书目时已经生效的关键词跟着走
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual({ scopeType: 'songdu', status: 'published', bookId: 6, keyword: '诵读', current: 1, size: 10 })

    vm(wrapper).switchTab('zaidu')
    await flushPromises()
    expect(vm(wrapper).bookFilter).toBeNull()
    expect(vm(wrapper).keyword).toBe('')
    expect(vm(wrapper).appliedKeyword).toBe('')
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual({ scopeType: 'zaidu', status: 'published', bookId: undefined, keyword: undefined, current: 1, size: 10 })

    vm(wrapper).bookFilter = 17
    vm(wrapper).keyword = '易经'
    vm(wrapper).search()
    await flushPromises()
    vm(wrapper).current = 2
    vm(wrapper).resetFilters()
    await flushPromises()
    expect(vm(wrapper).bookFilter).toBeNull()
    expect(vm(wrapper).keyword).toBe('')
    expect(vm(wrapper).appliedKeyword).toBe('')
    expect(vm(wrapper).current).toBe(1)
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual(expect.objectContaining({ scopeType: 'zaidu', current: 1 }))
    expect(lastCallArgs(getFabushiPosts)[0]).not.toHaveProperty('keyword', '易经')
  })

  it('关键词：点“查询”或按回车才生效，带着 keyword 发给接口并回到第 1 页；服务端搜过的结果不在本地再筛一遍', async () => {
    const wrapper = await mountQuietly()
    getFabushiPosts.mockClear()
    // 只是输入、还没查询：不发请求
    vm(wrapper).keyword = '易经'
    await flushPromises()
    expect(getFabushiPosts).not.toHaveBeenCalled()

    vm(wrapper).current = 3
    await wrapper.findAll('.filter-card .btn-stub')[0].trigger('click')
    await flushPromises()
    expect(vm(wrapper).current).toBe(1)
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual({ scopeType: 'songdu', status: 'published', bookId: undefined, keyword: '易经', current: 1, size: 10 })
    // 服务端按人名 / 书籍 / 具体文字搜；接口回了什么就显示什么。本地再筛一遍只认标题 / 正文 / 发布人，会把按书名命中的行筛掉
    expect(tableRows(wrapper)).toHaveLength(2)

    getFabushiPosts.mockClear()
    vm(wrapper).keyword = '  诵读  '
    await wrapper.find('.filter-card .el-input').trigger('keyup', { key: 'Enter' })
    await flushPromises()
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual(expect.objectContaining({ keyword: '诵读' }))

    getFabushiPosts.mockClear()
    vm(wrapper).keyword = '   '
    await wrapper.findAll('.filter-card .btn-stub')[0].trigger('click')
    await flushPromises()
    expect(lastCallArgs(getFabushiPosts)[0]).toEqual({ scopeType: 'songdu', status: 'published', bookId: undefined, current: 1, size: 10 })
  })

  it('“已删除”接口只收分页：关键词不发给接口，只在已加载的这一页里筛；没有匹配时写明是搜索无结果', async () => {
    const wrapper = await mountQuietly()
    await wrapper.findAll('.cat-tab')[4].trigger('click')
    await flushPromises()
    getDeletedFabushiPosts.mockClear()
    vm(wrapper).keyword = '不存在的词'
    await wrapper.findAll('.filter-card .btn-stub')[0].trigger('click')
    await flushPromises()
    expect(getDeletedFabushiPosts).toHaveBeenCalledTimes(1)
    expect(getDeletedFabushiPosts).toHaveBeenLastCalledWith({ current: 1, size: 10 })
    expect(tableRows(wrapper)).toHaveLength(0)
    expect(wrapper.find('.table-card').text()).toContain('没有匹配的作品')

    vm(wrapper).keyword = '易经'
    await wrapper.findAll('.filter-card .btn-stub')[0].trigger('click')
    await flushPromises()
    expect(tableRows(wrapper)).toHaveLength(1)
  })

  it('点“作品 / 正文摘要”整格都能打开详情（原有行为）；点别的格不会', async () => {
    const wrapper = await mountQuietly()
    const cellsOfFirstRow = cells(firstRow(wrapper))
    // 空白处也行：点的是 td 本身，不是标题文字
    await cellsOfFirstRow[0].trigger('click')
    await flushPromises()
    expect(vm(wrapper).detailVisible).toBe(true)
    expect(vm(wrapper).detailRow.id).toBe(9)
    vm(wrapper).detailVisible = false
    await cellsOfFirstRow[4].trigger('click')
    await cellsOfFirstRow[6].trigger('click')
    await flushPromises()
    expect(vm(wrapper).detailVisible).toBe(false)
    expect(offlineFabushiPost).not.toHaveBeenCalled()
    expect(restoreFabushiPost).not.toHaveBeenCalled()
  })

  it('弹窗都是 fx-dialog，底部按钮包在 .dialog-footer 里；详情里原有的信息都在', async () => {
    const wrapper = await mountQuietly()
    const dialogs = wrapper.findAll('.el-dialog')
    expect(dialogs.map((dialog) => dialog.attributes('data-title'))).toEqual(['作品详情', '下架内容 · 需留痕'])
    for (const dialog of dialogs) expect(dialog.classes()).toContain('fx-dialog')
    for (const dialog of dialogs) expect(dialog.find('.dialog-footer').exists()).toBe(true)
    expect(dialogs[1].find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['取消', '确认下架'])

    const target = vm(wrapper).posts[0]
    vm(wrapper).openDetail(target)
    await flushPromises()
    expect(vm(wrapper).detailVisible).toBe(true)
    const detail = dialogs[0].text()
    expect(detail).toContain('冒烟-诵读-第一章')
    expect(detail).toContain('内容 9')
    expect(detail).toContain('《UI回归-验收书B》 › 第一章 立命之学')
    expect(detail).toContain('每日诵读')
    expect(detail).toContain('木鱼 0 · 评论 1 · 复诵 1')
    expect(dialogs[0].find('.detail-head .tag.green').text()).toBe('发布中')
    expect(dialogs[0].find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['关闭', '下架该作品'])

    // 下架的作品：详情里写下架原因，底部是“恢复该作品”
    await wrapper.findAll('.cat-tab')[3].trigger('click')
    await flushPromises()
    vm(wrapper).openDetail(vm(wrapper).posts[0])
    await flushPromises()
    expect(dialogs[0].text()).toContain('下架原因：与本月书目不符')
    expect(dialogs[0].find('.detail-head .tag.red').text()).toBe('已下架')
    expect(dialogs[0].find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['关闭', '恢复该作品'])

    // 已删除的作品：详情里不再给“下架该作品”（接口对已删除的帖子只能回 404）
    await wrapper.findAll('.cat-tab')[4].trigger('click')
    await flushPromises()
    vm(wrapper).openDetail(vm(wrapper).posts[0])
    await flushPromises()
    expect(dialogs[0].find('.detail-head .tag.gray').text()).toBe('已删除')
    expect(dialogs[0].find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['关闭'])
  })

  it('原有动作接线不变：下架（原因必填）、恢复、恢复已删', async () => {
    const wrapper = await mountQuietly()
    const published = vm(wrapper).posts[0]

    vm(wrapper).openOffline(published)
    expect(vm(wrapper).offlineVisible).toBe(true)
    await flushPromises()
    expect(wrapper.findAll('.el-dialog')[1].text()).toContain('冒烟-诵读-第一章')
    await vm(wrapper).doOffline()
    expect(elMessage.warning).toHaveBeenLastCalledWith('下架原因不能为空')
    expect(offlineFabushiPost).not.toHaveBeenCalled()
    vm(wrapper).offlineReason = ' 与本月书目不符 '
    await vm(wrapper).doOffline()
    expect(offlineFabushiPost).toHaveBeenCalledWith(9, '与本月书目不符')
    expect(elMessage.success).toHaveBeenLastCalledWith('已下架（留痕）')
    expect(vm(wrapper).offlineVisible).toBe(false)

    await vm(wrapper).doRestore(published)
    expect(restoreFabushiPost).toHaveBeenCalledWith(9)
    await vm(wrapper).doRestoreDeleted(published)
    expect(restoreDeletedFabushiPost).toHaveBeenCalledWith(9)
    expect(warnings, 'Vue 警告').toEqual([])
  })

  it('图标不用表情符号；样式只写令牌；公共样式有引入', () => {
    const src = readFileSync('src/views/fabushi/content.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).not.toMatch(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u)
    // <style scoped> 里不出现写死的十六进制颜色（书卷皮肤那一套米色 / 墨绿 / 朱砂都在这里现形）
    const style = (src.match(/<style scoped>([\s\S]*?)<\/style>/) || [])[1] || ''
    expect(style.length).toBeGreaterThan(0)
    expect(style).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
  })
})

/** 第一行（各页签都至少有一行） */
function firstRow(wrapper) {
  return tableRows(wrapper)[0]
}
