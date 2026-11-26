import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】书目浏览（library.vue = 原型 P4）与同属一人的「我的书摘」（highlights.vue）。
// 用户裁决“PC 端按最新的原型来做”。这里钉住这些事：
//   ① 骨架跟原型走：没有页头标题块，页面根节点是 .fx-page，筛选条 → 左栏类别 → 右侧两列书卡；
//   ② 书卡照原型：书脊、《书名》+ 电子书标签、元信息（有哪项显示哪项）、版本链（同名的各版本各一个章，点一个切过去）+「共 N 版本」、
//      [在线阅读 / 暂无电子书] [去诵读]；原有的「写心得」「加入书架」是操作行最右的文字按钮；
//   ③ 接口参数一个没多：搜索、类别、翻页照旧走接口（接口只按书名搜）；排序、电子书筛选只在已加载的书目上做，不发请求；
//      作者 / 出版社 / 版本类型命中的书接口不返回，前端补上——搜索框写的是“书名 / 作者 / 版本”，就得搜得到；
//   ④ 新交互：去诵读（发布器预选当前那一版）、＋ 提交书目（弹窗表单，字段与书目治理页「新增书目」一致）、路由带 ?bookId= 时定位并高亮；
//   ⑤ 图标不用表情符号，公共样式 fabushi-v8.css 有引入，没有书卷时期写死的颜色。
// 口径与 paged-list-mount.spec.js 相同：真实视图组件挂载；接口、路由、状态仓库、消息、发布器是替身；Element Plus 组件用会渲染插槽的桩。
// 抓不到：真实布局与配色（靠截图和原型并排核对）。提交书目的弹窗截图工具截不到（没有“点开提交书目”这个动作），只有这里的单元测试。

const hoisted = vi.hoisted(() => ({
  route: { params: {}, query: {} },
  push: vi.fn(),
  permissions: ['*'],
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => hoisted.route,
  useRouter: () => ({ push: hoisted.push, back: vi.fn() }),
}))
// 发布器的替身声明了三个属性, 好用 props() 读“开没开 / 默认类型 / 预选哪一版”
const PublishStub = vi.hoisted(() => ({
  props: { modelValue: Boolean, defaultScope: String, presetBookId: [Number, String] },
  template: '<div class="publish-stub" />',
}))
vi.mock('@/components/fabushi/FabushiPublishDialog.vue', () => ({ default: PublishStub }))
vi.mock('@/api/fabushi', () => ({
  getFabushiBooks: vi.fn(), addFabushiShelf: vi.fn(), submitFabushiBook: vi.fn(),
  getMyFabushiBookmarks: vi.fn(), removeFabushiBookmark: vi.fn(),
}))
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    hasPermission: (code) => hoisted.permissions.includes('*') || hoisted.permissions.includes(code),
  }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), alert: vi.fn(), prompt: vi.fn() },
}))

import * as fabushiApiModule from '@/api/fabushi'
import { getDictData as getDictDataMock } from '@/api/system'
import ElementPlus, { ElMessage } from 'element-plus'
import LibraryView from './library.vue'
import HighlightsView from './highlights.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const { getFabushiBooks, addFabushiShelf, submitFabushiBook, getMyFabushiBookmarks } = fabushiApi
const getDictData = mocked(getDictDataMock)
const elMessage = /** @type {{ success: import('vitest').Mock, warning: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessage))
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 渲染默认插槽(与分页那个挂载冒烟同一个做法); 按钮单独一个类名, 好在筛选条、书卡的操作行里按文字找
const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const FORM_ITEM_STUB = { template: '<div class="el-stub form-item-stub"><slot /></div>' }
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" />',
}
const stubs = {
  'el-button': BUTTON_STUB, 'el-option': SLOT_STUB, 'el-input': SLOT_STUB, 'el-select': SLOT_STUB,
  'el-form': SLOT_STUB, 'el-form-item': FORM_ITEM_STUB, 'el-dialog': SLOT_STUB,
  'el-icon': SLOT_STUB, 'el-empty': SLOT_STUB, 'el-pagination': PagerStub,
}

const book = (n, extra) => ({
  id: n, title: `书目 ${n}`, author: null, publisher: null, editionType: null, pages: null, pdfFileId: null,
  categoryCode: 'liaofan', categoryName: '了凡四训', status: 'approved', createdAt: '2026-11-20T01:16:41', ...extra,
})
// 夹具按接口顺序（创建时间倒序）摆。「活法」有两个版本、分在两个类别；只有部分版本挂了电子书
const CATALOG = [
  book(17, { title: '易经', categoryCode: 'guoxue', categoryName: '国学建议书目', pdfFileId: 2730 }),
  book(21, { title: '道德经', author: '陈鼓应', publisher: '中华书局', editionType: 'zhushi', pages: 512, pdfFileId: 31, categoryCode: 'guoxue', categoryName: '国学建议书目' }),
  book(22, { title: '道德经', author: '王弼', editionType: 'yuanban', categoryCode: 'guoxue', categoryName: '国学建议书目' }),
  book(30, { title: '活法', author: '稻盛和夫', editionType: 'yiban', pdfFileId: 40, categoryCode: 'guoxue', categoryName: '国学建议书目' }),
  book(13, { title: '活法', categoryCode: 'tuanduibidu', categoryName: '团队必读书目' }),
  book(14, { title: '认知觉醒', categoryCode: 'tuanduibidu', categoryName: '团队必读书目' }),
]
const DICTS = {
  fabushi_category: [{ dictValue: 'guoxue', dictLabel: '国学建议书目' }, { dictValue: 'tuanduibidu', dictLabel: '团队必读书目' }],
  fabushi_edition_type: [{ dictValue: 'zhushi', dictLabel: '注释本' }, { dictValue: 'yuanban', dictLabel: '原版' }, { dictValue: 'yiban', dictLabel: '译本' }],
}
const DEFAULT_CARDS = ['《易经》电子书', '《道德经》电子书', '《活法》电子书', '《认知觉醒》']
/** 书目接口本来认的参数（后端 FabushiBookController.page）——改版不该多出来 */
const API_PARAMS = ['categoryCode', 'current', 'keyword', 'size', 'status']
const SCROLL_COLORS = /#2d5a4a|#b5543a|#f7f3ec|#fffdf8|#e8e0d3|#8d7fc7|#5a4c9b|#2e2a24|#6b6257|#9c9184|#a99d8c|#244839|#f8e9e0|#e4efe6|#f4ead9|#a67c52/i

/** “服务端”照后端 FabushiBookService.page 的做法：status 认 approved、keyword 只按书名模糊匹配、按夹具顺序分页 */
let catalog = []
const serve = (params) => {
  const status = params.status === 'all' ? '' : (params.status || 'approved')
  const kw = String(params.keyword || '').toLowerCase()
  const rows = catalog
    .filter((item) => !status || item.status === status)
    .filter((item) => !params.categoryCode || item.categoryCode === params.categoryCode)
    .filter((item) => !kw || String(item.title).toLowerCase().includes(kw))
  const current = Number(params.current) || 1
  const size = Number(params.size) || 10
  return Promise.resolve({ data: { records: rows.slice((current - 1) * size, current * size), total: rows.length } })
}

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.route = { params: {}, query: {} }
  hoisted.permissions = ['*']
  catalog = CATALOG.map((item) => ({ ...item }))
  getFabushiBooks.mockImplementation(serve)
  getDictData.mockImplementation((code) => Promise.resolve({ data: DICTS[code] || [] }))
  addFabushiShelf.mockResolvedValue({ data: null })
  submitFabushiBook.mockResolvedValue({ data: { id: 99, status: 'pending' } })
  getMyFabushiBookmarks.mockResolvedValue({ data: [] })
})

async function mountQuietly(view) {
  const wrapper = mount(view, {
    global: {
      stubs,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  return wrapper
}

const texts = (root, selector) => root.findAll(selector).map((node) => node.text())
const cards = (wrapper) => wrapper.findAll('.book-grid .bcard')
const cardTitles = (wrapper) => cards(wrapper).map((card) => card.find('.bcard-title').text())
const cardOf = (wrapper, title) => cards(wrapper).find((card) => card.find('.bcard-title').text().startsWith(`《${title}》`))
const actsOf = (wrapper, title) => cardOf(wrapper, title).findAll('.bcard-acts .btn-stub')
const chipOf = (wrapper, title, index) => cardOf(wrapper, title).findAll('.ver-chip')[index]
/** 发布器当前收到的：开没开 / 默认类型 / 预选哪一版 */
const publisher = (wrapper) => wrapper.findComponent(PublishStub).props()
/** 这个用例里对书目接口的每一次调用都没有多出参数 */
const expectNoExtraParams = () => {
  for (const call of getFabushiBooks.mock.calls) {
    expect(Object.keys(call[0]).filter((key) => !API_PARAMS.includes(key))).toEqual([])
  }
}

describe('书目浏览按 PC 原型 v8', () => {
  it('骨架：根节点是 .fx-page、没有页头标题块；筛选条 → 左栏类别 → 两列书卡；评审标注没有做成文案；接口照旧', async () => {
    const wrapper = await mountQuietly(LibraryView)
    expect(warnings, 'Vue 警告').toEqual([])
    const root = wrapper.find('.fx-page.fabushi-library')
    expect(root.exists()).toBe(true)
    expect(root.find('.page-head').exists()).toBe(false)
    expect(Array.from(root.element.children).map((el) => el.classList[0]).slice(0, 2)).toEqual(['filter-card', 'lib-layout'])

    expect(texts(root, '.filter-card > .fl')).toEqual(['搜索', '排序', '电子书'])
    expect(root.find('.filter-card .el-stub').attributes('placeholder')).toBe('书名 / 作者 / 版本')
    expect(root.findAll('.filter-card .el-stub .el-stub').map((option) => option.attributes('label')))
      .toEqual(['默认排序', '书名排序', '全部', '有电子书', '暂无电子书'])
    expect(texts(root, '.filter-card .btn-stub')).toEqual(['查询', '重置', '我的觉醒', '＋ 提交书目'])
    await root.findAll('.filter-card .btn-stub')[2].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/shelf')

    const layout = root.find('.lib-layout')
    expect(Array.from(layout.element.children).map((el) => el.classList[0])).toEqual(['cat-list', 'book-main'])
    expect(texts(layout, '.cat-row')).toEqual(['全部书目6', '国学建议书目4', '团队必读书目2'])
    expect(layout.find('.cat-row.active').text()).toBe('全部书目6')
    expect(layout.find('.cat-row .cnt').text()).toBe('6')
    expect(root.findAll('.book-grid .bcard')).toHaveLength(4)
    expect(wrapper.findComponent(PagerStub).props('total')).toBe(6)
    expect(root.text()).not.toMatch(/\(D\d+\)|fabushi:[a-z:]+|演示|见 P\d/)

    // 进页面：取一次全量已审核书目（算类别条数、拼版本链），再取第一页
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
    expect(getFabushiBooks).toHaveBeenNthCalledWith(1, { status: 'approved', current: 1, size: 200 })
    expect(getFabushiBooks).toHaveBeenNthCalledWith(2, { categoryCode: undefined, keyword: undefined, status: 'approved', current: 1, size: 12 })
    expectNoExtraParams()
  })

  it('书卡照原型：书脊、《书名》+ 电子书标签、元信息有哪项显示哪项、版本链 + 共 N 版本、操作行', async () => {
    const wrapper = await mountQuietly(LibraryView)
    expect(cardTitles(wrapper)).toEqual(DEFAULT_CARDS)

    const card = cardOf(wrapper, '道德经')
    expect(card.find('.book-spine').text()).toBe('道德经')
    expect(card.find('.bcard-title .tag.blue').text()).toBe('电子书')
    expect(card.find('.bcard-meta').text()).toBe('陈鼓应 · 中华书局 · 注释本 · 512 页')
    expect(texts(card, '.ver-chip')).toEqual(['陈鼓应·注释本', '王弼·原版'])
    expect(texts(card, '.ver-chip.active')).toEqual(['陈鼓应·注释本'])
    expect(card.find('.bcard-tags .tag.gray').text()).toBe('共 2 版本')
    // 主操作在前（原型的两颗）；原有的“写心得 / 加入书架”是操作行最右的文字按钮
    expect(texts(card, '.bcard-acts .btn-stub')).toEqual(['在线阅读', '去诵读', '写心得', '加入书架'])
    expect(actsOf(wrapper, '道德经').map((b) => b.attributes('link') !== undefined)).toEqual([false, false, true, true])

    // 作者 / 出版社 / 版本类型 / 页数都没填：元信息行回落显示类别名；没有电子书就不给“在线阅读”，照原型写“暂无电子书”
    const plain = cardOf(wrapper, '认知觉醒')
    expect(plain.find('.bcard-title .tag').exists()).toBe(false)
    expect(plain.find('.bcard-meta').text()).toBe('团队必读书目')
    expect(texts(plain, '.bcard-tags > *')).toEqual(['未标注版本', '共 1 版本'])
    expect(plain.find('.no-pdf').text()).toBe('暂无电子书')
    expect(texts(plain, '.bcard-acts .btn-stub')).toEqual(['去诵读', '写心得', '加入书架'])
    expect(cardOf(wrapper, '易经').find('.bcard-meta').text()).toBe('国学建议书目')

    // 一本书有几个版本时，书卡先按接口顺序的第一版显示（活法：国学的译本在前、团队必读的那版在后）
    expect(cardOf(wrapper, '活法').find('.bcard-meta').text()).toBe('稻盛和夫 · 译本')
    expect(texts(cardOf(wrapper, '活法'), '.bcard-tags > *')).toEqual(['稻盛和夫·译本', '未标注版本', '共 2 版本'])
  })

  it('同一书名下几版的字都一样（没填作者 / 版本）：后面的加序号，免得几个章分不清', async () => {
    catalog.push(book(50, { title: '心经', categoryCode: 'guoxue', categoryName: '国学建议书目' }), book(51, { title: '心经', categoryCode: 'guoxue', categoryName: '国学建议书目' }))
    const wrapper = await mountQuietly(LibraryView)
    expect(texts(cardOf(wrapper, '心经'), '.bcard-tags > *')).toEqual(['未标注版本', '未标注版本 2', '共 2 版本'])
  })

  it('版本链：点另一版，书卡切到那一版（标签、元信息、按钮都跟着换），不动接口', async () => {
    const wrapper = await mountQuietly(LibraryView)
    expect(chipOf(wrapper, '道德经', 0).attributes('aria-pressed')).toBe('true')
    expect(chipOf(wrapper, '道德经', 1).attributes('aria-pressed')).toBe('false')

    await chipOf(wrapper, '道德经', 1).trigger('click')
    const card = cardOf(wrapper, '道德经')
    expect(texts(card, '.ver-chip.active')).toEqual(['王弼·原版'])
    expect(card.find('.bcard-title').text()).toBe('《道德经》')
    expect(card.find('.bcard-meta').text()).toBe('王弼 · 原版')
    expect(card.find('.no-pdf').text()).toBe('暂无电子书')
    expect(texts(card, '.bcard-acts .btn-stub')).toEqual(['去诵读', '写心得', '加入书架'])
    expect(card.find('.bcard-tags .tag.gray').text()).toBe('共 2 版本')
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
  })

  it('原有的操作都在：在线阅读（只认挂了电子书的版本）、写心得、加入书架', async () => {
    const wrapper = await mountQuietly(LibraryView)
    await actsOf(wrapper, '道德经')[0].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/21')
    // 切到没挂电子书的那一版后，“在线阅读”就没有了，只能去诵读 / 写心得 / 加入书架
    await chipOf(wrapper, '道德经', 1).trigger('click')
    await actsOf(wrapper, '道德经')[1].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith({ path: '/fabushi/feed', query: { bookId: 22 } })

    await actsOf(wrapper, '认知觉醒')[1].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith({ path: '/fabushi/feed', query: { bookId: 14 } })
    await actsOf(wrapper, '认知觉醒')[2].trigger('click')
    await flushPromises()
    expect(addFabushiShelf).toHaveBeenLastCalledWith(14)
    expect(elMessage.success).toHaveBeenLastCalledWith('已加入书架')

    const before = hoisted.push.mock.calls.length
    vm(wrapper).openReader({ id: 5, pdfFileId: null })
    expect(hoisted.push.mock.calls.length).toBe(before)
  })

  it('“去诵读”：打开发布器，预选书卡当前那一版；发布后刷新书目', async () => {
    const wrapper = await mountQuietly(LibraryView)
    expect(actsOf(wrapper, '道德经')[1].text()).toBe('去诵读')
    await actsOf(wrapper, '道德经')[1].trigger('click')
    expect(publisher(wrapper)).toMatchObject({ modelValue: true, defaultScope: 'songdu', presetBookId: 21 })

    await chipOf(wrapper, '道德经', 1).trigger('click')
    await actsOf(wrapper, '道德经')[0].trigger('click')
    expect(publisher(wrapper)).toMatchObject({ modelValue: true, defaultScope: 'songdu', presetBookId: 22 })

    vm(wrapper).publishVisible = false
    await flushPromises()
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
    await vm(wrapper).onPublished()
    expect(getFabushiBooks).toHaveBeenCalledTimes(4)
  })

  it('没有发布权限（fabushi:create）：看不到“＋ 提交书目”，点“去诵读”只提示、不打开发布器', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await mountQuietly(LibraryView)
    expect(texts(wrapper, '.filter-card .btn-stub')).toEqual(['查询', '重置', '我的觉醒'])
    await actsOf(wrapper, '活法')[1].trigger('click')
    expect(publisher(wrapper).modelValue).toBe(false)
    expect(elMessage.warning).toHaveBeenLastCalledWith('当前账号没有发布权限')
  })

  it('搜索照旧走接口：输入框里的字要点“查询”才生效；同一页里同名的版本并成一张卡', async () => {
    const wrapper = await mountQuietly(LibraryView)
    vm(wrapper).keyword = '经'
    await flushPromises()
    expect(cardTitles(wrapper)).toEqual(DEFAULT_CARDS)
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)

    await vm(wrapper).onSearch()
    expect(getFabushiBooks).toHaveBeenLastCalledWith({ categoryCode: undefined, keyword: '经', status: 'approved', current: 1, size: 12 })
    expect(cardTitles(wrapper)).toEqual(['《易经》电子书', '《道德经》电子书'])

    vm(wrapper).keyword = '道德'
    await vm(wrapper).onSearch()
    expect(cardTitles(wrapper)).toEqual(['《道德经》电子书'])
    expect(cardOf(wrapper, '道德经').find('.bcard-tags .tag.gray').text()).toBe('共 2 版本')
    expectNoExtraParams()
  })

  it('搜索框写的是“书名 / 作者 / 版本”：作者、出版社、版本类型命中的书也搜得到（接口只按书名搜，前端补上，排在书名命中的后面）', async () => {
    catalog.push(book(40, { title: '练习册', author: '认知老师', categoryCode: 'guoxue', categoryName: '国学建议书目' }))
    const wrapper = await mountQuietly(LibraryView)

    vm(wrapper).keyword = '王弼'
    await vm(wrapper).onSearch()
    // 接口照旧带上关键词（那边书名没命中）
    expect(getFabushiBooks).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '王弼' }))
    expect(cardTitles(wrapper)).toEqual(['《道德经》'])
    // 书卡落在命中的那一版上，看得出为什么搜到它
    expect(cardOf(wrapper, '道德经').find('.bcard-meta').text()).toBe('王弼 · 原版')
    expect(vm(wrapper).pagerTotal).toBe(1)

    vm(wrapper).keyword = '中华书局'
    await vm(wrapper).onSearch()
    expect(cardOf(wrapper, '道德经').find('.bcard-meta').text()).toBe('陈鼓应 · 中华书局 · 注释本 · 512 页')

    vm(wrapper).keyword = '译本'
    await vm(wrapper).onSearch()
    expect(cardTitles(wrapper)).toEqual(['《活法》电子书'])

    // 书名命中的在前，作者命中的接在后面
    vm(wrapper).keyword = '认知'
    await vm(wrapper).onSearch()
    expect(cardTitles(wrapper)).toEqual(['《认知觉醒》', '《练习册》'])
    expect(vm(wrapper).pagerTotal).toBe(2)
  })

  it('类别筛选照旧走接口；版本链只算当前类别里的版本', async () => {
    const wrapper = await mountQuietly(LibraryView)
    vm(wrapper).current = 3
    await vm(wrapper).selectCategory('tuanduibidu')
    expect(getFabushiBooks).toHaveBeenLastCalledWith({ categoryCode: 'tuanduibidu', keyword: undefined, status: 'approved', current: 1, size: 12 })
    expect(wrapper.find('.cat-row.active').text()).toBe('团队必读书目2')
    expect(cardTitles(wrapper)).toEqual(['《活法》', '《认知觉醒》'])
    expect(texts(cardOf(wrapper, '活法'), '.bcard-tags > *')).toEqual(['未标注版本', '共 1 版本'])
    expect(cardOf(wrapper, '活法').find('.bcard-meta').text()).toBe('团队必读书目')

    await wrapper.findAll('.cat-row')[0].trigger('click')
    expect(getFabushiBooks).toHaveBeenLastCalledWith({ categoryCode: undefined, keyword: undefined, status: 'approved', current: 1, size: 12 })
    expect(cardTitles(wrapper)).toEqual(DEFAULT_CARDS)
  })

  it('电子书筛选：只在前端已有的书目上做——不加接口参数、不发请求；书卡落在有电子书的那一版', async () => {
    const wrapper = await mountQuietly(LibraryView)
    // 先点过“王弼·原版”（没挂电子书）
    await chipOf(wrapper, '道德经', 1).trigger('click')
    expect(cardOf(wrapper, '道德经').find('.no-pdf').exists()).toBe(true)

    vm(wrapper).ebook = 'yes'
    await vm(wrapper).onFrontFilter()
    expect(cardTitles(wrapper)).toEqual(['《易经》电子书', '《道德经》电子书', '《活法》电子书'])
    expect(cards(wrapper).map((card) => texts(card, '.bcard-acts .btn-stub')[0])).toEqual(['在线阅读', '在线阅读', '在线阅读'])
    expect(texts(cardOf(wrapper, '道德经'), '.ver-chip.active')).toEqual(['陈鼓应·注释本'])
    expect(vm(wrapper).current).toBe(1)
    expect(vm(wrapper).pagerTotal).toBe(3)

    vm(wrapper).ebook = 'no'
    await vm(wrapper).onFrontFilter()
    // 「活法」有一个版本挂了电子书，就不算“暂无电子书”
    expect(cardTitles(wrapper)).toEqual(['《认知觉醒》'])

    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
    expectNoExtraParams()
  })

  it('排序只在前端做：书名排序排的是全部结果，不只是接口当前这一页', async () => {
    // 14 条：第一页 12 条，“安徒生童话”在接口的第二页
    const extra = Array.from({ length: 7 }, (_, i) => book(101 + i, { title: `补充 ${i + 1}`, categoryCode: 'tuanduibidu', categoryName: '团队必读书目' }))
    catalog.push(...extra, book(108, { title: '安徒生童话', categoryCode: 'tuanduibidu', categoryName: '团队必读书目' }))
    const wrapper = await mountQuietly(LibraryView)
    expect(cardTitles(wrapper)).toHaveLength(10)

    vm(wrapper).sort = 'title'
    await vm(wrapper).onFrontFilter()
    expect(cardTitles(wrapper)).toEqual(['《安徒生童话》', ...extra.map((item) => `《${item.title}》`), '《道德经》电子书', '《活法》电子书', '《认知觉醒》', '《易经》电子书'])
    expect(vm(wrapper).pagerTotal).toBe(12)
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
    expectNoExtraParams()
  })

  it('翻页：默认视图照旧走接口翻页；前端筛选激活时在前端结果里翻页、不发请求', async () => {
    // 15 条、13 个书名：接口分两页，前端按书名排完是 13 张卡、也分两页
    const extra = Array.from({ length: 8 }, (_, i) => book(101 + i, { title: `补充 ${i + 1}`, categoryCode: 'tuanduibidu', categoryName: '团队必读书目' }))
    catalog.push(...extra, book(109, { title: '安徒生童话', categoryCode: 'tuanduibidu', categoryName: '团队必读书目' }))
    const wrapper = await mountQuietly(LibraryView)
    expect(cardTitles(wrapper)).toHaveLength(10)

    vm(wrapper).current = 2
    await vm(wrapper).onPageChange()
    expect(getFabushiBooks).toHaveBeenLastCalledWith({ categoryCode: undefined, keyword: undefined, status: 'approved', current: 2, size: 12 })
    expect(cardTitles(wrapper)).toEqual(['《补充 7》', '《补充 8》', '《安徒生童话》'])
    expect(vm(wrapper).pagerTotal).toBe(15)

    vm(wrapper).current = 1
    await vm(wrapper).onPageChange()
    vm(wrapper).sort = 'title'
    await vm(wrapper).onFrontFilter()
    const calls = getFabushiBooks.mock.calls.length
    vm(wrapper).current = 2
    await vm(wrapper).onPageChange()
    expect(getFabushiBooks.mock.calls.length).toBe(calls)
    expect(cardTitles(wrapper)).toEqual(['《易经》电子书'])
    expect(wrapper.findComponent(PagerStub).props('currentPage')).toBe(2)
  })

  it('重置：清空搜索、排序、电子书筛选，回到第一页的默认视图', async () => {
    const wrapper = await mountQuietly(LibraryView)
    vm(wrapper).keyword = '经'
    await vm(wrapper).onSearch()
    vm(wrapper).ebook = 'yes'
    vm(wrapper).sort = 'title'
    await vm(wrapper).onFrontFilter()
    vm(wrapper).current = 2

    await vm(wrapper).resetFilters()
    expect(vm(wrapper).keyword).toBe('')
    expect(vm(wrapper).sort).toBe('latest')
    expect(vm(wrapper).ebook).toBe('all')
    expect(vm(wrapper).current).toBe(1)
    expect(cardTitles(wrapper)).toEqual(DEFAULT_CARDS)
    expect(getFabushiBooks).toHaveBeenLastCalledWith({ categoryCode: undefined, keyword: undefined, status: 'approved', current: 1, size: 12 })
  })

  it('空态：没有书写“暂无书目”；筛不出结果照实说', async () => {
    catalog = []
    const empty = await mountQuietly(LibraryView)
    // 一本都没有：字典里的类别照样列出来，条数是 0
    expect(texts(empty, '.cat-row')).toEqual(['全部书目0', '国学建议书目0', '团队必读书目0'])
    expect(empty.find('.book-grid > .el-stub[description]').attributes('description')).toBe('暂无书目')

    const wrapper = await mountQuietly(LibraryView)
    vm(wrapper).keyword = '认知'
    await vm(wrapper).onSearch()
    vm(wrapper).ebook = 'yes'
    await vm(wrapper).onFrontFilter()
    expect(cards(wrapper)).toHaveLength(0)
    expect(wrapper.find('.book-grid > .el-stub[description]').attributes('description')).toBe('没有符合条件的书目')
  })

  it('路由带 ?bookId=（认知广场的「书目详情」）：搜索框带上书名，这一版在版本链里选中，这张卡高亮', async () => {
    hoisted.route = { params: {}, query: { bookId: '13' } }
    const wrapper = await mountQuietly(LibraryView)
    expect(vm(wrapper).keyword).toBe('活法')
    expect(getFabushiBooks).toHaveBeenLastCalledWith({ categoryCode: undefined, keyword: '活法', status: 'approved', current: 1, size: 12 })
    expect(cardTitles(wrapper)).toEqual(['《活法》'])
    const card = cardOf(wrapper, '活法')
    expect(card.classes()).toContain('hl')
    expect(texts(card, '.ver-chip.active')).toEqual(['未标注版本'])
    expect(card.find('.bcard-meta').text()).toBe('团队必读书目')
    expect(card.find('.no-pdf').text()).toBe('暂无电子书')
  })

  it('?bookId= 指的书不在已审核书目里：照常显示全部，不高亮', async () => {
    hoisted.route = { params: {}, query: { bookId: '999' } }
    const wrapper = await mountQuietly(LibraryView)
    expect(vm(wrapper).keyword).toBe('')
    expect(cardTitles(wrapper)).toEqual(DEFAULT_CARDS)
    expect(cards(wrapper).some((card) => card.classes().includes('hl'))).toBe(false)
  })

  it('＋ 提交书目：弹窗的字段与书目治理页「新增书目」一致（类别、书名必填）；校验不过不发请求', async () => {
    const wrapper = await mountQuietly(LibraryView)
    await wrapper.findAll('.filter-card .btn-stub')[3].trigger('click')
    expect(vm(wrapper).submitVisible).toBe(true)
    expect(wrapper.findAll('.form-item-stub').map((item) => [item.attributes('label'), item.attributes('required') !== undefined]))
      .toEqual([['类别', true], ['书名', true], ['作者', false], ['版本', false]])
    expect(texts(wrapper, '.dialog-footer .btn-stub')).toEqual(['取消', '提交'])

    await vm(wrapper).doSubmit()
    expect(elMessage.warning).toHaveBeenLastCalledWith('请选择类别')
    vm(wrapper).submitForm = { categoryCode: 'guoxue', title: '   ', author: '', editionType: '' }
    await vm(wrapper).doSubmit()
    expect(elMessage.warning).toHaveBeenLastCalledWith('书名不能为空')
    expect(submitFabushiBook).not.toHaveBeenCalled()
    expect(vm(wrapper).submitVisible).toBe(true)
  })

  it('提交成功：只传 { 类别, 书名, 作者, 版本 }；提示待审核、关弹窗、清表单、刷新书目', async () => {
    const wrapper = await mountQuietly(LibraryView)
    vm(wrapper).openSubmit()
    vm(wrapper).submitForm = { categoryCode: 'guoxue', title: ' 活法 ', author: ' 稻盛和夫 ', editionType: 'yiban' }
    await vm(wrapper).doSubmit()

    expect(submitFabushiBook).toHaveBeenCalledTimes(1)
    expect(submitFabushiBook).toHaveBeenCalledWith({ categoryCode: 'guoxue', title: '活法', author: '稻盛和夫', editionType: 'yiban' })
    expect(Object.keys(submitFabushiBook.mock.calls[0][0]).sort((a, b) => a.localeCompare(b))).toEqual(['author', 'categoryCode', 'editionType', 'title'])
    expect(elMessage.success).toHaveBeenLastCalledWith('已提交，待管理员审核')
    expect(vm(wrapper).submitVisible).toBe(false)
    expect(vm(wrapper).submitForm).toEqual({ categoryCode: '', title: '', author: '', editionType: '' })
    expect(getFabushiBooks).toHaveBeenCalledTimes(4)
  })

  it('提交失败：弹窗留着、填的内容不丢、不提示成功；请求没回来之前不能重复提交', async () => {
    let rejectSubmit = /** @type {(reason: unknown) => void} */ (() => {})
    submitFabushiBook.mockImplementation(() => new Promise((_, reject) => { rejectSubmit = reject }))
    const wrapper = await mountQuietly(LibraryView)
    const form = { categoryCode: 'guoxue', title: '活法', author: '', editionType: '' }
    vm(wrapper).openSubmit()
    vm(wrapper).submitForm = { ...form }

    const pending = vm(wrapper).doSubmit()
    expect(vm(wrapper).submitting).toBe(true)
    await vm(wrapper).doSubmit()
    expect(submitFabushiBook, '请求没回来之前不重复提交').toHaveBeenCalledTimes(1)

    rejectSubmit(new Error('500'))
    await pending
    expect(vm(wrapper).submitting).toBe(false)
    expect(vm(wrapper).submitVisible).toBe(true)
    expect(vm(wrapper).submitForm).toEqual(form)
    expect(elMessage.success).not.toHaveBeenCalled()
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
  })

  it('没有 fabushi:create：不显示“＋ 提交书目”，直接调用也不发请求', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await mountQuietly(LibraryView)
    vm(wrapper).openSubmit()
    expect(vm(wrapper).submitVisible).toBe(false)
    vm(wrapper).submitForm = { categoryCode: 'guoxue', title: '活法', author: '', editionType: '' }
    await vm(wrapper).doSubmit()
    expect(submitFabushiBook).not.toHaveBeenCalled()
  })

  it('提交书目弹窗用真实的 Element Plus 渲染：fx-dialog 落在 .el-dialog 上，四个字段和两颗按钮都在（截图工具截不到这个弹窗）', async () => {
    const wrapper = mount(LibraryView, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { FabushiPublishDialog: PublishStub },
        config: { warnHandler: (msg) => warnings.push(msg) },
      },
    })
    await flushPromises()
    expect(warnings, 'Vue 警告').toEqual([])

    const open = () => wrapper.findAll('.filter-card .el-button').find((button) => button.text() === '＋ 提交书目')
    await open().trigger('click')
    await flushPromises()
    const dialog = document.body.querySelector('.el-dialog')
    expect(dialog.classList.contains('fx-dialog')).toBe(true)
    expect(dialog.querySelector('.el-dialog__title').textContent).toBe('提交书目')
    expect(Array.from(dialog.querySelectorAll('.el-form-item__label')).map((el) => el.textContent.trim())).toEqual(['类别', '书名', '作者', '版本'])
    // 提交后要等审核才出现在书目里：弹窗里说清楚
    expect(dialog.querySelector('.submit-hint').textContent).toContain('待审核')
    expect(Array.from(dialog.querySelectorAll('.dialog-footer .el-button')).map((button) => button.textContent.trim())).toEqual(['取消', '提交'])
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)

    // 点「取消」：弹窗关掉，不碰接口
    await wrapper.findAll('.dialog-footer .el-button')[0].trigger('click')
    await flushPromises()
    expect(vm(wrapper).submitVisible).toBe(false)
    expect(submitFabushiBook).not.toHaveBeenCalled()
    expect(getFabushiBooks).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('图标不用表情符号；公共样式 fabushi-v8.css 有引入；没有书卷时期写死的颜色；弹窗用 fx-dialog', () => {
    const src = readFileSync('src/views/fabushi/library.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).toMatch(/<el-dialog[^>]*class="fx-dialog"/)
    expect(src).not.toMatch(/🎤|🎙|⤓|🐟|📖|📚|📄/)
    expect(src).not.toMatch(SCROLL_COLORS)
  })
})

describe('我的书摘（highlights.vue）跟着新配色走', () => {
  it('根节点是 .fx-page、搜索行是共享的 .filter-card、按书分组的卡用共享的 .panel；条数说明还在；功能没动', async () => {
    getMyFabushiBookmarks.mockResolvedValue({ data: Array.from({ length: 3 }, (_, i) => (
      { id: i + 1, bookId: i + 1, bookTitle: `书目 ${i + 1}`, page: i + 1, note: `书摘 ${i + 1}`, createdAt: '2026-11-20T09:00:00' }
    )) })
    const wrapper = await mountQuietly(HighlightsView)
    expect(warnings, 'Vue 警告').toEqual([])
    const root = wrapper.find('.fx-page.fabushi-highlights')
    expect(root.exists()).toBe(true)
    expect(root.find('.page-head').exists()).toBe(false)
    expect(root.find('.filter-card').exists()).toBe(true)
    expect(root.find('.filter-card .el-stub').attributes('placeholder')).toBe('搜书名或备注')
    expect(texts(root, '.filter-card .btn-stub')).toEqual(['回我的觉醒'])
    expect(root.find('.filter-card .filter-note').text()).toContain('共 3 条 · 3 本书')
    expect(root.findAll('.hl-group.panel')).toHaveLength(3)
    expect(texts(root, '.hl-book-title')).toEqual(['《书目 1》', '《书目 2》', '《书目 3》'])
    await root.find('.filter-card .btn-stub').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/shelf')
  })

  it('公共样式有引入；没有书卷时期写死的颜色；不写自动引入的 <el-icon>（分页那个挂载测试没给它备桩）', () => {
    const src = readFileSync('src/views/fabushi/highlights.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).not.toMatch(/#8d7fc7|#5a4c9b/)
    expect(src).not.toMatch(SCROLL_COLORS)
    expect(src).not.toMatch(/<el-icon/)
    expect(src).not.toMatch(/🎤|🎙|⤓|🐟|📖|📚|📄/)
  })
})
