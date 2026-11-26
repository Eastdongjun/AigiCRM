import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { ref } from 'vue'

// 【2026-11-26 按小程序原型 v7】小程序里的在线阅读（views/mp/FabushiReader.vue = 原型 B1，web-view 里的 H5）。
// 用户裁决“按照最新的原型来做，还有小程序的”。这里钉住的是外壳与版式面板：
//   ① 深色阅读壳：顶栏（返回、书名、“第 x / N 页 · 版式标记”、版式按钮）、书页区、底部（上一页 / 进度条 / 页码 / 下一页）；
//   ② 版式设置是一张底部卡片，默认收起，点“版式”才出来：阅读模式（重排 / 原样）、背景（纸白 / 护眼 / 夜间）、
//      对比度增强、裁掉页边白边、字号行距（重排时）、书内搜索；
//   ③ 点背景 / 对比度 / 裁边马上作用到书页（CSS 变量），并按书记到本地；
//   ④ 图标不用表情符号；原型里给评审看的标注不做成界面文案。
// 渲染 / 进度的逻辑有自己的单测（useFabushiPdfReader.spec.js），这里是替身。

const hoisted = vi.hoisted(() => ({
  route: { params: {}, query: { ticket: 'ticket-for-test', bookId: '3', pdfFileId: '77', title: '%E9%81%93%E5%BE%B7%E7%BB%8F', layoutType: 'text' } },
  back: vi.fn(),
  miniProgramPost: vi.fn(),
  reader: /** @type {any} */ ({}),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => hoisted.route,
  useRouter: () => ({ push: vi.fn(), back: hoisted.back }),
}))
vi.mock('@/composables/useFabushiPdfReader', () => ({ useFabushiPdfReader: () => hoisted.reader }))

import MpReader from './FabushiReader.vue'

// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }

const realUA = window.navigator.userAgent

beforeEach(() => {
  vi.clearAllMocks()
  hoisted.reader = {
    state: ref('ready'), loadPct: ref(100), errorMsg: ref(''), currentPage: ref(36), totalPages: ref(512),
    load: vi.fn(), goPage: vi.fn(), markPage: vi.fn(), retry: vi.fn(), pageText: vi.fn(), detectTextLayer: vi.fn(),
    extractAllText: vi.fn(), searchInPages: vi.fn(),
  }
  Object.defineProperty(window.navigator, 'userAgent', { value: 'Mozilla/5.0 (iPhone) AppleWebKit/605 MicroMessenger/8.0.50 Mobile', configurable: true })
  window.localStorage.clear()
})
afterEach(() => {
  Object.defineProperty(window.navigator, 'userAgent', { value: realUA, configurable: true })
  document.head.querySelectorAll('script[src^="https://res.wx.qq.com"]').forEach((node) => node.remove())
})

async function mountReader() {
  const warnings = []
  const wrapper = mount(MpReader, {
    attachTo: document.body,
    global: { stubs: { 'el-icon': { template: '<i class="el-stub"><slot /></i>' } }, config: { warnHandler: (msg) => warnings.push(msg) } },
  })
  await flushPromises()
  await flushPromises()
  expect(warnings, 'Vue 警告').toEqual([])
  return wrapper
}

const chips = (wrapper) => wrapper.findAll('.mfr-chip').map((c) => c.text())

describe('小程序在线阅读按原型 v7 的 B1', () => {
  it('不在微信里打开：提示从小程序进入，不加载电子书', async () => {
    Object.defineProperty(window.navigator, 'userAgent', { value: 'Mozilla/5.0 (Macintosh) Chrome/120', configurable: true })
    const wrapper = await mountReader()
    expect(wrapper.find('.mfr-block').text()).toContain('请从微信小程序进入')
    expect(wrapper.find('.mfr-head').exists()).toBe(false)
    expect(hoisted.reader.load).not.toHaveBeenCalled()
  })

  it('深色阅读壳：顶栏（返回、书名、页码与版式标记、版式按钮）+ 书页区 + 底部翻页条', async () => {
    const wrapper = await mountReader()
    expect(hoisted.reader.load).toHaveBeenCalledTimes(1)
    const root = wrapper.find('.mfr')
    expect(root.exists()).toBe(true)
    expect(root.classes()).toContain('theme-paper')
    const head = root.find('.mfr-head')
    expect(head.find('.mfr-back').exists()).toBe(true)
    expect(head.find('.mfr-book').text()).toBe('道德经')
    expect(head.find('.mfr-sub').text()).toBe('36 / 512 页 · 文本版')
    expect(head.findAll('button').map((b) => b.text())).toEqual(['', '版式'])
    expect(root.find('.mfr-page').exists()).toBe(true)
    expect(root.find('canvas').exists()).toBe(true)
    const foot = root.find('.mfr-foot')
    expect(foot.find('.rprog i').attributes('style') || '').toContain('width: 7%')
    expect(foot.find('.r-page').text()).toBe('36 / 512 · 7%')
    // 版式卡片默认收起
    expect(root.find('.mfr-sheet').exists()).toBe(false)
  })

  it('底部翻页条：‹ › 各翻一页；到头了按钮禁用', async () => {
    const wrapper = await mountReader()
    const prev = wrapper.find('.mfr-foot .pg-btn-prev')
    const next = wrapper.find('.mfr-foot .pg-btn-next')
    await prev.trigger('click')
    expect(hoisted.reader.goPage).toHaveBeenLastCalledWith(35)
    await next.trigger('click')
    expect(hoisted.reader.goPage).toHaveBeenLastCalledWith(37)
    hoisted.reader.currentPage.value = 1
    await flushPromises()
    expect(wrapper.find('.mfr-foot .pg-btn-prev').attributes('disabled')).toBeDefined()
  })

  it('版式卡片：点“版式”展开，里面的项与原型 B1 一致；再点收起', async () => {
    const wrapper = await mountReader()
    await wrapper.find('.mfr-head .mfr-btn').trigger('click')
    const sheet = wrapper.find('.mfr-sheet')
    expect(sheet.exists()).toBe(true)
    expect(sheet.find('.mfr-sheet-title').text()).toContain('版式设置')
    expect(sheet.find('.chip2').text()).toBe('本书 · 文本版')
    expect(sheet.findAll('.mfr-sec-t').map((s) => s.text())).toEqual(['阅读模式', '背景', '书内搜索'])
    expect(chips(wrapper)).toEqual(['重排', '原样', '纸白', '护眼米黄', '夜间', '对比度增强', '裁掉页边白边'])
    // 影印件 / 还没探测出文本层时，“重排”不能点
    const reflowChip = wrapper.findAll('.mfr-chip')[0]
    expect(reflowChip.attributes('disabled')).toBeDefined()
    expect(sheet.find('input[type="search"]').exists()).toBe(true)
    await wrapper.find('.mfr-head .mfr-btn').trigger('click')
    expect(wrapper.find('.mfr-sheet').exists()).toBe(false)
  })

  it('点背景：马上作用到书页的 CSS 变量，并按书记到本地', async () => {
    const wrapper = await mountReader()
    await wrapper.find('.mfr-head .mfr-btn').trigger('click')
    const night = wrapper.findAll('.mfr-chip').find((c) => c.text() === '夜间')
    await night.trigger('click')
    expect(vm(wrapper).settings.backdrop).toBe('night')
    expect(wrapper.find('.mfr').classes()).toContain('theme-night')
    expect(wrapper.find('.mfr').attributes('style')).toContain('--reader-page-bg: #202525')
    expect(JSON.parse(window.localStorage.getItem('fabushi:reader-settings:3') || '{}').backdrop).toBe('night')
  })

  it('对比度增强 / 裁掉页边白边：点一下开、再点一下关', async () => {
    const wrapper = await mountReader()
    await wrapper.find('.mfr-head .mfr-btn').trigger('click')
    const contrast = () => wrapper.findAll('.mfr-chip').find((c) => c.text() === '对比度增强')
    const crop = () => wrapper.findAll('.mfr-chip').find((c) => c.text() === '裁掉页边白边')
    expect(vm(wrapper).settings.contrast).toBe(100)
    await contrast().trigger('click')
    expect(vm(wrapper).settings.contrast).toBe(125)
    expect(contrast().classes()).toContain('active')
    expect(wrapper.find('.mfr').attributes('style')).toContain('--reader-contrast: 1.25')
    await contrast().trigger('click')
    expect(vm(wrapper).settings.contrast).toBe(100)
    expect(vm(wrapper).settings.crop).toBe(0)
    await crop().trigger('click')
    expect(vm(wrapper).settings.crop).toBe(6)
    expect(wrapper.find('.mfr').attributes('style')).toContain('--reader-crop: 6%')
    await crop().trigger('click')
    expect(vm(wrapper).settings.crop).toBe(0)
  })

  it('图标不用表情符号；评审标注没有做成界面文案', () => {
    const src = readFileSync('src/views/mp/FabushiReader.vue', 'utf-8')
    const template = src.slice(0, src.indexOf('<script'))
    expect(template).not.toMatch(/🐟|🎤|⚙|⏹|📖|⚠|🔒/)
    expect(template).not.toMatch(/（D\d+|D19 扩充|— 演示|已上线/)
  })
})
