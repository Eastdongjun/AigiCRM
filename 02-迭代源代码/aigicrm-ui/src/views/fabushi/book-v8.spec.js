import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】书目管理（book.vue = 原型 P10：左书目面板 + 右版本表格）。
// 用户裁决“PC 端按最新的原型来做”。这里钉住五件事：
//   ① 骨架跟原型走：根节点 .fx-page，没有页头标题块；类别页签 → 筛选条 → 主体（左 .sub-panel、右 .table-card）；
//   ② 原有能操作的东西一个都不少：类别切换、状态 / 关键词筛选、待审核入口、新增书目、左栏搜索与选中、
//      行内全部操作（按状态出现）、五个弹窗；权限判断照旧；
//   ③ “短词不再折成几行”的结构前提：状态 / 电子书 / 版式都是 .tag，数字放在不换行的元素里，每个表格列都给了宽度
//      （到底折不折行 jsdom 量不出来，靠截图和原型并排核对）；
//   ④ 电子书筛选只在前端已有的全量书目上做：不给接口加参数；
//   ⑤ 原型里给评审看的标注（权限码、D 编号）不显示给使用者；图标不用表情符号；公共样式有引入。
// 口径与 paged-list-mount.spec.js 相同：真实视图挂载；接口、状态仓库、消息是替身。
// 表格与图标组件在 book.vue 里是显式引入的（挂载冒烟测试没有全局注册），所以这里表格是真实的 el-table，
// 断言落在真实渲染出来的行上；其余 Element Plus 组件用会渲染插槽的桩。

const hoisted = vi.hoisted(() => ({
  permissions: ['*'],
  apiNames: [
    'getFabushiBooks', 'submitFabushiBook', 'approveFabushiBook', 'rejectFabushiBook', 'disableFabushiBook',
    'attachBookPdf', 'deleteFabushiBook', 'getFabushiSections', 'createFabushiSection', 'deleteFabushiSection',
    'updateFabushiBookLayout',
  ],
}))

vi.mock('@/api/fabushi', () => {
  const api = {}
  for (const name of hoisted.apiNames) api[name] = vi.fn()
  return api
})
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    hasPermission: (code) => hoisted.permissions.includes('*') || hoisted.permissions.includes(code),
    userId: 7,
  }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), alert: vi.fn(), prompt: vi.fn() },
}))
vi.mock('@/utils/pdf-inspect', () => ({ inspectPdfFile: vi.fn() }))

import * as fabushiApiModule from '@/api/fabushi'
import { getDictData as getDictDataMock } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'
import BookView from './book.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const getDictData = mocked(getDictDataMock)
const {
  getFabushiBooks, approveFabushiBook, rejectFabushiBook, disableFabushiBook, deleteFabushiBook,
  getFabushiSections, updateFabushiBookLayout,
} = fabushiApi
const elMessage = /** @type {{ success: import('vitest').Mock, warning: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessage))
const elMessageBox = /** @type {{ confirm: import('vitest').Mock, alert: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessageBox))
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 渲染插槽(与挂载冒烟同一个做法)。按钮单独一个类名, 好按文字找;
// 选项、表单项、弹窗、分页条把要核对的东西写进 data-* 属性
const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const INPUT_STUB = { template: '<div class="el-stub el-input"><slot name="prefix" /><slot /></div>' }
const SELECT_STUB = { template: '<div class="el-stub el-select"><slot /></div>' }
const OPTION_STUB = {
  props: { label: String, value: String },
  template: '<span class="el-stub el-option" :data-label="label" :data-value="value" />',
}
const FORM_ITEM_STUB = {
  props: { label: String },
  template: '<div class="el-stub el-form-item" :data-label="label"><slot /></div>',
}
const DIALOG_STUB = {
  props: { title: String },
  template: '<div class="el-stub el-dialog" :data-title="title"><slot name="header" /><slot /><slot name="footer" /></div>',
}
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, pageSizes: Array, layout: String },
  emits: ['update:currentPage', 'update:pageSize', 'current-change', 'size-change', 'change'],
  template: '<div class="el-stub el-pager" :data-total="total" :data-page="currentPage" :data-layout="layout" />',
}
const stubs = {
  'el-button': BUTTON_STUB, 'el-option': OPTION_STUB, 'el-input': INPUT_STUB, 'el-select': SELECT_STUB,
  'el-form': SLOT_STUB, 'el-form-item': FORM_ITEM_STUB, 'el-dialog': DIALOG_STUB,
  'el-radio-group': SLOT_STUB, 'el-radio-button': SLOT_STUB, 'el-pagination': PagerStub,
}

const CATEGORIES = [
  { dictValue: 'guoxue', dictLabel: '国学建议书目' },
  { dictValue: 'xiaoshuo', dictLabel: '小说类' },
]
const EDITIONS = [
  { dictValue: 'annotated', dictLabel: '注释本' },
  { dictValue: 'original', dictLabel: '原版' },
]
const book = (n, extra = {}) => ({
  id: n, title: `书目 ${n}`, author: null, editionType: null, pages: null,
  categoryCode: 'guoxue', categoryName: '国学建议书目', status: 'approved', layoutType: 'scan',
  pdfFileId: null, memberCount: 0, postCount: 0, submitterName: '管理员', createdAt: '2026-11-20T01:16:41',
  ...extra,
})
// 六个版本、五个书名: 道德经有两个版本(一个通过一个待审核), 其余各一个, 状态各不相同
const BOOKS = [
  book(1, { title: '道德经', author: '陈鼓应', editionType: 'annotated', pages: 512, pdfFileId: 2730, layoutType: 'text', memberCount: 8, postCount: 36 }),
  book(2, { title: '道德经', status: 'pending', submitterName: '李长青' }),
  book(3, { title: '论语', status: 'rejected', categoryCode: 'xiaoshuo', categoryName: '小说类', rejectReason: '重复提交' }),
  book(4, { title: '大学', status: 'disabled' }),
  book(5, { title: '易经' }),
  book(6, { title: '活法', pdfFileId: 3000 }),
]

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.permissions = ['*']
  getFabushiBooks.mockResolvedValue({ data: { records: BOOKS.map((item) => ({ ...item })), total: BOOKS.length } })
  getFabushiSections.mockResolvedValue({ data: { sections: [] } })
  getDictData.mockImplementation((code) => Promise.resolve({ data: code === 'fabushi_category' ? CATEGORIES : EDITIONS }))
})

async function mountQuietly() {
  const wrapper = mount(BookView, {
    global: {
      stubs,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  return wrapper
}

/** 手写表格渲染出来的行 */
const tableRows = (wrapper) => wrapper.findAll('.data-table tbody tr')
const cells = (tr) => tr.findAll('td')
const cellText = (tr, index) => cells(tr)[index].text()
const actionTexts = (tr) => {
  const list = cells(tr)
  return list[list.length - 1].findAll('.btn-stub').map((button) => button.text())
}
const texts = (wrapper, selector) => wrapper.findAll(selector).map((node) => node.text())
const viewSource = () => readFileSync('src/views/fabushi/book.vue', 'utf-8')
const scopedStyle = () => (viewSource().match(/<style scoped>([\s\S]*?)<\/style>/) || [])[1] || ''

describe('书目管理按 PC 原型 v8（P10）', () => {
  it('骨架：根节点是 .fx-page（保留 .fabushi-book）；类别页签 → 筛选条 → 左书目面板 → 右表格卡；不把权限码显示给使用者', async () => {
    const wrapper = await mountQuietly()
    expect(warnings, 'Vue 警告').toEqual([])
    const root = wrapper.find('.fx-page.fabushi-book')
    expect(root.exists()).toBe(true)
    const html = root.html()
    const at = (marker) => html.indexOf(marker)
    expect(at('class="cat-tabs')).toBeGreaterThanOrEqual(0)
    expect(at('class="cat-tabs')).toBeLessThan(at('class="filter-card'))
    expect(at('class="filter-card')).toBeLessThan(at('class="sub-panel'))
    expect(at('class="sub-panel')).toBeLessThan(at('class="table-card'))
    expect(root.find('.page-head').exists()).toBe(false)
    // 原型里“全部操作需 fabushi:book:manage”是给评审看的标注
    expect(root.text()).not.toContain('fabushi:')
    expect(root.text()).not.toMatch(/演示|见 P\d/)
  })

  it('类别页签：“全部”加字典里的类别，每个带条数；点页签切换类别并回到第一页', async () => {
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.cat-tab')).toEqual(['全部6', '国学建议书目5', '小说类1'])
    expect(wrapper.find('.cat-tab.active').text()).toBe('全部6')

    await wrapper.findAll('.cat-tab')[2].trigger('click')
    await flushPromises()
    expect(vm(wrapper).query.categoryCode).toBe('xiaoshuo')
    expect(vm(wrapper).query.current).toBe(1)
    expect(wrapper.find('.cat-tab.active').text()).toBe('小说类1')
    expect(getFabushiBooks).toHaveBeenLastCalledWith(expect.objectContaining({ categoryCode: 'xiaoshuo', current: 1 }))
  })

  it('筛选条：状态、电子书、关键词（只搜书名，照实写）、查询、重置；右侧待审核入口带条数、新增书目；没有“类别字典”', async () => {
    const wrapper = await mountQuietly()
    const card = wrapper.find('.filter-card')
    expect(card.exists()).toBe(true)
    const selects = card.findAll('.el-select')
    expect(selects).toHaveLength(2)
    expect(selects[0].findAll('.el-option').map((option) => option.attributes('data-label'))).toEqual(['全部', '已通过', '待审核', '已驳回', '已停用'])
    expect(selects[1].findAll('.el-option').map((option) => option.attributes('data-label'))).toEqual(['全部', '已挂载', '未挂载'])
    // 后端书目分页的 keyword 只按书名查（FabushiBookService.page），占位符不承诺作者 / 版本 / 提交人
    expect(card.find('.el-input').attributes('placeholder')).toBe('搜索书名')
    expect(card.findAll('.btn-stub').map((button) => button.text())).toEqual(['查询', '重置', '待审核 (1)', '＋ 新增书目'])
    expect(card.text()).not.toContain('类别字典')
  })

  it('没有新增权限的人看不到“＋ 新增书目”；待审核入口把状态切到待审核', async () => {
    hoisted.permissions = ['fabushi:view', 'fabushi:book:manage']
    const wrapper = await mountQuietly()
    expect(wrapper.findAll('.filter-card .btn-stub').map((button) => button.text())).toEqual(['查询', '重置', '待审核 (1)'])

    await wrapper.findAll('.filter-card .btn-stub')[2].trigger('click')
    await flushPromises()
    expect(vm(wrapper).query.status).toBe('pending')
    expect(getFabushiBooks).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'pending', current: 1 }))
  })

  it('左栏：标题“类别 · 书目 N”；按书名去重，一行一个书名，多版本带“N 版本”小章；有搜索框和脚注', async () => {
    const wrapper = await mountQuietly()
    const panel = wrapper.find('.sub-panel')
    expect(panel.find('.sub-title').text()).toBe('全部 · 书目 5')
    expect(texts(wrapper, '.sub-row .sub-name')).toEqual(['《道德经》', '《论语》', '《大学》', '《易经》', '《活法》'])
    expect(texts(wrapper, '.sub-row .cnt')).toEqual(['2 版本', '1', '1', '1', '1'])
    expect(panel.find('.el-input').classes()).toContain('sub-search')
    expect(panel.find('.el-input').attributes('placeholder')).toBe('搜索书目')
    // 原型脚注的意思保留，去掉评审口吻
    const foot = panel.find('.sub-foot').text()
    expect(foot).toContain('按书名去重')
    expect(foot).toContain('分别管理')
    expect(foot).toContain('按版本计数')

    vm(wrapper).selectCategory('guoxue')
    await flushPromises()
    expect(wrapper.find('.sub-panel .sub-title').text()).toBe('国学建议书目 · 书目 4')
    expect(texts(wrapper, '.sub-row .sub-name')).toEqual(['《道德经》', '《大学》', '《易经》', '《活法》'])
  })

  it('左栏点一行：右表按该书名查询（接口参数不变，不带电子书之类的额外入参），工具条变成《书名》版本治理', async () => {
    const wrapper = await mountQuietly()
    await wrapper.findAll('.sub-row')[0].trigger('click')
    await flushPromises()
    expect(vm(wrapper).selectedBookId).toBe(1)
    expect(vm(wrapper).query.keyword).toBe('道德经')
    expect(wrapper.find('.sub-row.active').text()).toContain('《道德经》')
    const lastCall = /** @type {any[]} */ (getFabushiBooks.mock.calls[getFabushiBooks.mock.calls.length - 1])
    expect(lastCall[0]).toEqual(expect.objectContaining({ keyword: '道德经', current: 1 }))
    expect(Object.keys(lastCall[0]).sort()).toEqual(['categoryCode', 'current', 'keyword', 'size', 'status'])
    expect(wrapper.find('.toolbar .tt').text()).toBe('全部 · 《道德经》版本治理')
    // 通过 / 待审核按当前范围（这个书名）计数
    expect(texts(wrapper, '.toolbar .tag')).toEqual(['通过 1', '待审核 1'])
  })

  it('表格卡：列与原型一致（版式列保留）；标签和数字各占一行不折；每个列都给了宽度', async () => {
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.data-table th .cell')).toEqual([
      '书名 / 作者 / 版本', '类别', '状态', '电子书', '参与', '版式', '提交人 / 时间', '操作',
    ])
    const [daodejing, pending, rejected, disabled, yijing] = tableRows(wrapper)
    expect(tableRows(wrapper)).toHaveLength(6)

    // 书名一行、作者 · 版本 · 页数一行（书名下面的小字）
    expect(cellText(daodejing, 0)).toBe('《道德经》陈鼓应 · 注释本 · 512 页')
    expect(cellText(pending, 0)).toContain('未标注版本')
    expect(cellText(daodejing, 1)).toBe('国学建议书目')
    expect(daodejing.find('td:nth-child(3) .tag.green').text()).toBe('通过')
    expect(daodejing.find('td:nth-child(4) .tag.blue').text()).toBe('已挂载')
    expect(daodejing.find('td:nth-child(5) .num').text()).toBe('8 人/36 条')
    expect(daodejing.find('td:nth-child(6) .tag.cyan').text()).toBe('文本版')

    expect(pending.find('td:nth-child(3) .tag.orange').text()).toBe('待审核')
    expect(pending.find('td:nth-child(4) .tag.gray').text()).toBe('未挂载')
    expect(pending.find('td:nth-child(6) .tag.gray').text()).toBe('影印版')
    expect(pending.find('td:nth-child(5) .num').text()).toBe('0 人/0 条')
    expect(rejected.find('td:nth-child(3) .tag.red').text()).toBe('已驳回')
    expect(disabled.find('td:nth-child(3) .tag.gray').text()).toBe('已停用')
    expect(cellText(daodejing, 6)).toBe('管理员2026-11-20')
    expect(cellText(pending, 6)).toBe('李长青2026-11-20')

    // 工具条：没选书时是“全部 · 书目治理”，通过 / 待审核两个标签按当前类别计数
    expect(wrapper.find('.toolbar .tt').text()).toBe('全部 · 书目治理')
    expect(texts(wrapper, '.toolbar .tag')).toEqual(['通过 3', '待审核 1'])
    expect(wrapper.find('.toolbar').text()).not.toContain('驳回')

    // 结构前提：固定布局、每列定宽（书名列吃剩余宽度，表最窄 814px）、单元格不换行（折不折行靠截图核对）
    const src = viewSource()
    const style = scopedStyle()
    const cols = src.match(/<col\b[^>]*>/g) || []
    expect(cols.map((col) => (col.match(/class="([\w-]+)"/) || [])[1])).toEqual([
      'c-title', 'c-cat', 'c-status', 'c-ebook', 'c-join', 'c-layout', 'c-submitter', 'c-acts',
    ])
    expect(style).toMatch(/\.data-table\s*\{[^}]*table-layout:\s*fixed/)
    expect(style).toMatch(/\.data-table\s*\{[^}]*min-width:\s*\d+px/)
    for (const name of ['c-cat', 'c-status', 'c-ebook', 'c-join', 'c-layout', 'c-submitter', 'c-acts']) {
      expect(style, name).toMatch(new RegExp(`\\.${name}\\s*\\{[^}]*width:\\s*\\d+px`))
    }
    expect(style).toMatch(/\.cell\s*\{[^}]*white-space:\s*nowrap/)
    expect(style).toMatch(/\.num\s*\{[^}]*white-space:\s*nowrap/)
    const shared = readFileSync('src/assets/fabushi-v8.css', 'utf-8')
    expect(shared).toMatch(/\.fx-page \.tag\s*\{[^}]*white-space:\s*nowrap/)
  })

  it('行内操作一个都不少：按状态出现，顺序不变', async () => {
    const wrapper = await mountQuietly()
    const [daodejing, pending, rejected, disabled, yijing] = tableRows(wrapper)
    expect(actionTexts(daodejing)).toEqual(['换PDF', '章节', '版式', '停用', '删除'])
    expect(actionTexts(pending)).toEqual(['通过', '驳回', '删除'])
    expect(actionTexts(rejected)).toEqual(['查看原因', '删除'])
    expect(actionTexts(disabled)).toEqual(['删除'])
    // 通过了但还没挂电子书：动作是“挂PDF”
    expect(actionTexts(yijing)).toEqual(['挂PDF', '章节', '版式', '停用', '删除'])
  })

  it('没有管理权限：行里不出现状态操作，只显示“只读”；章节弹窗里也没有删除', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await mountQuietly()
    for (const tr of tableRows(wrapper)) {
      expect(tr.find('.btn-stub').exists()).toBe(false)
      expect(cells(tr)[cells(tr).length - 1].text()).toBe('只读')
    }
    expect(wrapper.findAll('.el-dialog .btn-stub').map((button) => button.text())).toEqual(['取消', '提交', '取消', '确认驳回', '添加', '取消', '保存标记'])
  })

  it('电子书筛选只在前端已有的全量书目上做：不给接口加参数，右表行数和分页条数跟着变', async () => {
    const wrapper = await mountQuietly()
    const callsBefore = getFabushiBooks.mock.calls.length
    expect(tableRows(wrapper)).toHaveLength(6)

    vm(wrapper).ebookFilter = 'yes'
    await flushPromises()
    expect(tableRows(wrapper)).toHaveLength(2)
    expect(texts(wrapper, '.data-table tbody td:nth-child(4) .tag')).toEqual(['已挂载', '已挂载'])
    expect(wrapper.find('.table-card .el-pager').attributes('data-total')).toBe('2')

    vm(wrapper).ebookFilter = 'no'
    await flushPromises()
    expect(tableRows(wrapper)).toHaveLength(4)
    expect(wrapper.find('.table-card .el-pager').attributes('data-total')).toBe('4')

    // 与类别、状态、关键词一起生效
    vm(wrapper).selectCategory('xiaoshuo')
    vm(wrapper).ebookFilter = 'yes'
    await flushPromises()
    expect(tableRows(wrapper)).toHaveLength(0)
    expect(getFabushiBooks.mock.calls.length).toBe(callsBefore + 1)
    const lastCall = /** @type {any[]} */ (getFabushiBooks.mock.calls[getFabushiBooks.mock.calls.length - 1])
    expect(Object.keys(lastCall[0]).sort()).toEqual(['categoryCode', 'current', 'keyword', 'size', 'status'])
  })

  it('电子书筛选与状态、关键词叠加时只认和接口同口径的行；重置回到“全部”', async () => {
    const wrapper = await mountQuietly()
    vm(wrapper).ebookFilter = 'no'
    vm(wrapper).query.status = 'pending'
    vm(wrapper).query.keyword = '道德经'
    await flushPromises()
    expect(tableRows(wrapper)).toHaveLength(1)
    expect(cellText(tableRows(wrapper)[0], 2)).toBe('待审核')

    vm(wrapper).resetFilters()
    await flushPromises()
    expect(vm(wrapper).ebookFilter).toBe('all')
    expect(vm(wrapper).query).toEqual({ status: 'all', categoryCode: '', keyword: '', current: 1, size: 10 })
    expect(tableRows(wrapper)).toHaveLength(6)
  })

  it('全量书目超过一次拉取的上限（200 本）时，电子书筛选旁边照实写明只覆盖了一部分；不筛时不出这句', async () => {
    getFabushiBooks.mockResolvedValue({ data: { records: BOOKS.map((item) => ({ ...item })), total: 260 } })
    const wrapper = await mountQuietly()
    expect(wrapper.find('.toolbar').text()).not.toContain('只覆盖')
    vm(wrapper).ebookFilter = 'yes'
    await flushPromises()
    expect(wrapper.find('.toolbar').text()).toContain('电子书筛选只覆盖最近 6 本（共 260 本）')
  })

  it('底部分页带每页条数和总条数；左栏书目不超过一页时不出左栏分页条', async () => {
    const wrapper = await mountQuietly()
    const pagers = wrapper.findAll('.el-pager')
    expect(pagers).toHaveLength(1)
    expect(pagers[0].attributes('data-layout')).toContain('sizes')
    expect(pagers[0].attributes('data-layout')).toContain('total')
    expect(pagers[0].attributes('data-total')).toBe('6')
    expect(wrapper.find('.table-card .el-pager').exists()).toBe(true)
  })

  it('五个弹窗都是 fx-dialog，底部按钮包在 .dialog-footer 里；原有表单项都在', async () => {
    const wrapper = await mountQuietly()
    const dialogs = wrapper.findAll('.el-dialog')
    expect(dialogs.map((dialog) => dialog.attributes('data-title'))).toEqual([
      '提交书目', '驳回书目', '章节结构', '挂载电子书 PDF', '书目版式标记',
    ])
    for (const dialog of dialogs) expect(dialog.classes()).toContain('fx-dialog')
    // 提交书目：类别（字典）、书名、作者、版本（字典）
    const submit = dialogs[0]
    expect(submit.findAll('.el-form-item').map((item) => item.attributes('data-label'))).toEqual(['类别', '书名', '作者', '版本'])
    expect(submit.findAll('.el-select')[0].findAll('.el-option').map((option) => option.attributes('data-label'))).toEqual(['国学建议书目', '小说类'])
    expect(submit.findAll('.el-select')[1].findAll('.el-option').map((option) => option.attributes('data-label'))).toEqual(['注释本', '原版'])
    expect(submit.find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['取消', '提交'])
    // 驳回：原因必填
    expect(dialogs[1].find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['取消', '确认驳回'])
    expect(dialogs[1].find('.el-input').exists()).toBe(true)
    // 章节：序 / 部分 / 章 / 节
    expect(dialogs[2].findAll('.el-option').map((option) => option.attributes('data-label'))).toEqual(['序', '部分', '章', '节'])
    expect(dialogs[2].find('input[type="file"]').exists()).toBe(false)
    // 挂 PDF：选文件的入口
    expect(dialogs[3].find('input[type="file"]').exists()).toBe(true)
    expect(dialogs[3].find('input[type="file"]').attributes('accept')).toBe('.pdf')
    // 版式：文本版 / 影印版
    expect(dialogs[4].find('.dialog-footer').findAll('.btn-stub').map((button) => button.text())).toEqual(['取消', '保存标记'])
    expect(dialogs[4].text()).toContain('文本版')
    expect(dialogs[4].text()).toContain('影印版')
  })

  it('原有动作接线不变：通过、驳回（原因必填）、停用、删除（二次确认）、查看原因、章节、版式', async () => {
    const wrapper = await mountQuietly()
    const rows = vm(wrapper).books
    const [daodejing, pending, rejected] = rows

    await vm(wrapper).doApprove(pending)
    expect(approveFabushiBook).toHaveBeenCalledWith(2)
    expect(elMessage.success).toHaveBeenLastCalledWith('已通过')

    vm(wrapper).openReject(pending)
    expect(vm(wrapper).rejectVisible).toBe(pending)
    await vm(wrapper).doReject()
    expect(elMessage.warning).toHaveBeenLastCalledWith('驳回原因不能为空')
    expect(rejectFabushiBook).not.toHaveBeenCalled()
    vm(wrapper).rejectReason = ' 重复提交 '
    await vm(wrapper).doReject()
    expect(rejectFabushiBook).toHaveBeenCalledWith(2, '重复提交')
    expect(vm(wrapper).rejectVisible).toBeNull()

    await vm(wrapper).doDisable(daodejing)
    expect(disableFabushiBook).toHaveBeenCalledWith(1)

    elMessageBox.confirm.mockResolvedValue('confirm')
    await vm(wrapper).doDelete(daodejing)
    expect(elMessageBox.confirm).toHaveBeenCalledWith(expect.stringContaining('道德经'), '删除书目', expect.any(Object))
    expect(deleteFabushiBook).toHaveBeenCalledWith(1)

    vm(wrapper).showRejectReason(rejected)
    expect(elMessageBox.alert).toHaveBeenLastCalledWith('重复提交', '驳回原因', expect.any(Object))

    await vm(wrapper).openSections(daodejing)
    expect(getFabushiSections).toHaveBeenCalledWith(1)
    expect(vm(wrapper).sectionVisible).toBe(true)

    vm(wrapper).openLayout(daodejing)
    expect(vm(wrapper).layoutVisible).toBe(daodejing)
    expect(vm(wrapper).layoutForm).toEqual({ layoutType: 'text' })
    await vm(wrapper).doSaveLayout()
    expect(updateFabushiBookLayout).toHaveBeenCalledWith(1, 'text')

    vm(wrapper).openPdf(daodejing)
    expect(vm(wrapper).pdfVisible).toBe(daodejing)
    expect(warnings, 'Vue 警告').toEqual([])
  })

  it('图标不用表情符号；样式只写令牌；公共样式有引入；图标组件显式引入并自己定了尺寸', () => {
    const src = viewSource()
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).not.toMatch(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u)
    // <style scoped> 里不出现写死的十六进制颜色（书卷皮肤那一套米色 / 墨绿 / 朱砂都在这里现形）
    const style = scopedStyle()
    expect(style.length).toBeGreaterThan(0)
    expect(style).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    // 挂载冒烟测试没有全局注册，图标在视图里显式引入、模板里写 <ElIcon>；显式引入不带 Element Plus 的 .el-icon 样式，尺寸得自己给
    expect(src).toMatch(/import \{[^}]*\bElIcon\b[^}]*\} from 'element-plus'/)
    // 模板里不用自动解析的 <el-icon>（只看模板，script 的注释里会提到它）
    const template = src.match(/^<template>([\s\S]*)\n<\/template>\s*\n+<script setup>/)
    expect(template, '模板块').not.toBeNull()
    expect(/** @type {string[]} */ (template)[1]).not.toMatch(/<el-icon\b/)
    expect(style).toMatch(/\.search-ico,\s*\.sub-ico\s*\{[^}]*width:\s*1em/)
    expect(style).toMatch(/\.search-ico :deep\(svg\),\s*\.sub-ico :deep\(svg\)\s*\{[^}]*width:\s*1em/)
    // 表格是手写的 <table>，不走 Element Plus 的表格（也就不能去 import 它的样式文件：vitest 加载不了；注释里会提到它）
    expect(src).not.toMatch(/^\s*import\s+['"]element-plus\/es\/components\//m)
  })
})
