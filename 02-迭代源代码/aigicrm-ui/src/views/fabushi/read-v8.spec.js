import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { ref } from 'vue'

// 【2026-11-26 按 PC 原型 v8】在线阅读（read.vue = 原型 P5）。用户裁决“PC 端按最新的原型来做”。
// 这里钉住的是骨架与面板开合（版式、书签、书内搜索、目录），渲染与进度逻辑有自己的单测（useFabushiPdfReader.spec.js），
// 书签 / 搜索的分页接线在 paged-list-mount.spec.js 里：
//   ① 外壳跟原型走：深色阅读壳 .reader-shell，顶栏 .r-top（返回书目、书名、版式设置、书签、书内搜索、下载），
//      左侧面板 .r-toc，底部 .r-foot（上一页 / 进度条 / 页码 / 下一页 / 加书签）；
//   ② 左侧面板有三种内容：目录（默认）、书签、书内搜索，由顶栏的按钮切换；版式设置是右上角的浮层，默认收起；
//   ③ 目录来自章节接口（GET /fabushi/books/{id}/sections），按“序 / 部分 / 章 / 节”缩进；接口没有页码，所以目录行不是按钮（不做点不了的假入口）；
//   ④ 图标不用表情符号；原型里给评审看的标注（D 编号、“已上线”“演示”）不做成界面文案。
// 口径与 paged-list-mount.spec.js 相同：真实视图组件挂载；接口、路由、状态仓库、下载是替身；PDF 阅读器是替身。

const hoisted = vi.hoisted(() => ({
  route: { params: { bookId: '3' }, query: {} },
  apiNames: [
    'getFabushiBooks', 'getFabushiBook', 'getFabushiSections', 'getFabushiBookmarks', 'getMyFabushiBookmarks',
    'addFabushiBookmark', 'removeFabushiBookmark',
  ],
  back: vi.fn(),
  download: vi.fn(),
  reader: /** @type {any} */ ({}),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => hoisted.route,
  useRouter: () => ({ push: vi.fn(), back: hoisted.back }),
}))
vi.mock('@/api/fabushi', () => {
  const api = {}
  for (const name of hoisted.apiNames) api[name] = vi.fn()
  return api
})
vi.mock('@/utils/token', () => ({
  getToken: vi.fn(() => 'token-for-test'),
  refreshDownloadToken: vi.fn(() => Promise.resolve('token-for-test')),
  getDownloadToken: vi.fn(() => 'token-for-test'),
}))
vi.mock('@/utils/download', () => ({ downloadFile: hoisted.download }))
vi.mock('@/store/user', () => ({ useUserStore: () => ({ hasPermission: () => true, userId: 7 }) }))
vi.mock('element-plus', async (importOriginal) => ({ ...await importOriginal() }))
vi.mock('@/composables/useFabushiPdfReader', () => ({ useFabushiPdfReader: () => hoisted.reader }))

import * as fabushiApiModule from '@/api/fabushi'
import ReadView from './read.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const { getFabushiBook, getFabushiSections, getFabushiBookmarks } = fabushiApi
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" />',
}
const stubs = {
  'el-button': BUTTON_STUB, 'el-input-number': SLOT_STUB, 'el-empty': SLOT_STUB, 'el-pagination': PagerStub,
}

const theBook = { id: 3, title: '道德经', author: '陈鼓应', pages: 512, pdfFileId: 77, layoutType: 'text' }

beforeEach(() => {
  vi.clearAllMocks()
  hoisted.route = { params: { bookId: '3' }, query: {} }
  hoisted.reader = {
    state: ref('ready'), loadPct: ref(100), errorMsg: ref(''), currentPage: ref(36), totalPages: ref(512),
    load: vi.fn(), goPage: vi.fn(), markPage: vi.fn(), retry: vi.fn(), pageText: vi.fn(), detectTextLayer: vi.fn(),
    extractAllText: vi.fn(), searchInPages: vi.fn(),
  }
  for (const name of hoisted.apiNames) fabushiApi[name].mockResolvedValue({ data: [] })
  getFabushiBook.mockResolvedValue({ data: { book: theBook } })
  getFabushiBookmarks.mockResolvedValue({ data: [{ id: 1, page: 36 }, { id: 2, page: 80 }] })
})

async function mountReader() {
  const warnings = []
  const wrapper = mount(ReadView, {
    global: {
      stubs,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  await flushPromises()
  expect(warnings, 'Vue 警告').toEqual([])
  return wrapper
}

describe('在线阅读按 PC 原型 v8', () => {
  it('骨架：深色阅读壳、顶栏、左侧面板、底部翻页条', async () => {
    const wrapper = await mountReader()
    const root = wrapper.find('.fx-page.fabushi-reader')
    expect(root.exists()).toBe(true)
    expect(root.find('.reader-shell').exists()).toBe(true)
    const top = root.find('.r-top')
    expect(top.exists()).toBe(true)
    expect(top.text()).toContain('返回书目')
    expect(top.text()).toContain('《道德经》')
    // 顶栏按钮：版式设置 / 书签（带条数）/ 书内搜索 / 下载
    expect(top.findAll('.r-btn').map((b) => b.text())).toEqual(['版式设置', '书签2', '书内搜索', '下载'])
    expect(root.find('.r-toc').exists()).toBe(true)
    expect(root.find('.paper-zone').exists()).toBe(true)
    // 底部：页码、进度、加书签
    const foot = root.find('.r-foot')
    expect(foot.exists()).toBe(true)
    expect(foot.text()).toContain('36 / 512')
    expect(foot.text()).toContain('7%')
    expect(foot.find('.rprog i').attributes('style') || '').toContain('width: 7%')
    expect(foot.text()).toContain('已加书签')
  })

  it('版式设置是右上角的浮层：默认收起，点“版式设置”才出现', async () => {
    const wrapper = await mountReader()
    expect(wrapper.find('.r-set').exists()).toBe(false)
    await wrapper.findAll('.r-top .r-btn')[0].trigger('click')
    expect(wrapper.find('.r-set').exists()).toBe(true)
    const labels = wrapper.findAll('.r-set .row .row-label > span').map((s) => s.text())
    expect(labels).toEqual(['模式', '背景', '字号 / 行距', '对比度增强', '裁掉页边白边', '缩放', '翻页'])
    await wrapper.findAll('.r-top .r-btn')[0].trigger('click')
    expect(wrapper.find('.r-set').exists()).toBe(false)
  })

  it('左侧面板：默认是目录；点“书签”“书内搜索”切过去，再点一次回到目录', async () => {
    const wrapper = await mountReader()
    expect(vm(wrapper).sidePanel).toBe('toc')
    await wrapper.findAll('.r-top .r-btn')[1].trigger('click')
    expect(vm(wrapper).sidePanel).toBe('bookmarks')
    expect(wrapper.find('.toc-panel-bookmarks').isVisible()).toBe(true)
    expect(wrapper.find('.toc-panel-search').isVisible()).toBe(false)
    await wrapper.findAll('.r-top .r-btn')[2].trigger('click')
    expect(vm(wrapper).sidePanel).toBe('search')
    await wrapper.findAll('.r-top .r-btn')[2].trigger('click')
    expect(vm(wrapper).sidePanel).toBe('toc')
  })

  it('目录来自章节接口，按“序 / 部分 / 章 / 节”缩进；接口没有页码，目录行不是按钮', async () => {
    getFabushiSections.mockResolvedValue({ data: { bookId: 3, sections: [
      { id: 1, levelCode: 'preface', title: '序言', sortOrder: 1 },
      { id: 2, levelCode: 'part', title: '上篇 · 道经', sortOrder: 2 },
      { id: 3, levelCode: 'chapter', title: '第八章 · 上善若水', sortOrder: 3 },
      { id: 4, levelCode: 'section', title: '一', sortOrder: 4 },
    ] } })
    const wrapper = await mountReader()
    expect(getFabushiSections).toHaveBeenCalledWith('3')
    const rows = wrapper.findAll('.toc-panel-toc .toc-row')
    expect(rows.map((r) => r.text())).toEqual(['序言', '上篇 · 道经', '第八章 · 上善若水', '一'])
    // 缩进按级别：序 0 / 部分 1 / 章 2 / 节 3
    expect(rows.map((r) => r.classes().find((c) => /^lv\d$/.test(c)))).toEqual(['lv0', 'lv1', 'lv2', 'lv3'])
    expect(wrapper.findAll('.toc-panel-toc button.toc-row')).toHaveLength(0)
  })

  it('书没有章节时，目录给一句空状态', async () => {
    const wrapper = await mountReader()
    expect(wrapper.find('.toc-panel-toc .r-toc-empty').text()).toContain('还没有章节目录')
  })

  it('书签面板：每条能跳页、能删除；点“给本页加书签”走接口', async () => {
    const { addFabushiBookmark } = fabushiApi
    const wrapper = await mountReader()
    await wrapper.findAll('.r-top .r-btn')[1].trigger('click')
    const rows = wrapper.findAll('.toc-panel-bookmarks .bm-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].find('.bm-jump').text()).toBe('第 36 页')
    await rows[1].find('.bm-jump').trigger('click')
    expect(hoisted.reader.goPage).toHaveBeenLastCalledWith(80)
    // 第 36 页已经有书签 → 按钮是“取消本页书签”，点了走删除
    const toggle = wrapper.find('.toc-panel-bookmarks .r-action')
    expect(toggle.text()).toContain('取消本页书签')
    await toggle.trigger('click')
    await flushPromises()
    expect(fabushiApi.removeFabushiBookmark).toHaveBeenCalledWith(1)
    expect(addFabushiBookmark).not.toHaveBeenCalled()
  })

  it('“返回书目”走路由返回；“下载”带鉴权头下载电子书', async () => {
    const wrapper = await mountReader()
    await wrapper.find('.r-top .back').trigger('click')
    expect(hoisted.back).toHaveBeenCalled()
    await wrapper.findAll('.r-top .r-btn')[3].trigger('click')
    expect(hoisted.download).toHaveBeenLastCalledWith('/api/file/download/77', expect.objectContaining({ name: '道德经.pdf' }))
  })

  it('图标不用表情符号；公共样式有引入；评审标注没有做成界面文案', () => {
    const src = readFileSync('src/views/fabushi/read.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    const template = src.slice(0, src.indexOf('<script'))
    expect(template).not.toMatch(/🐟|🎤|⚙|⏹|📖|⚠|🔒/)
    expect(template).not.toMatch(/（D\d+|D19 扩充|— 演示|已上线（/)
  })
})
