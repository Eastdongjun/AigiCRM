import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】我的觉醒（shelf.vue = 原型 P9 觉醒总览 / P9B 我的书架）。用户裁决“PC 端按最新的原型来做”。
// 这里钉住四件事：
//   ① 骨架跟原型走：页面根节点是 .fx-page、没有页头标题块；觉醒总览 = 三张板块卡 + 书架小表 / 阅读日历 / 提醒卡 / 我的本月；
//      我的书架 = 继续阅读条 + 筛选条 + 管理模式批量条 + 封面网格 + 右栏；
//   ② 数字只用接口真有的数据：没有数据的格子留空（不编）；原型画了而后端没有的数字不放；
//   ③ 原有功能一个都不少：四个页签、板块卡直达发布器、提醒设置能保存、书架的状态 / 分组 / 排序 / 搜索 / 两种视图 /
//      管理模式勾选与批量操作、单本的置顶 / 上移 / 下移 / 移出 / 改状态、分组的新建 / 改名 / 删除、阅读记录、我的书摘；
//   ④ 图标不用表情符号；模板里用显式引入的 ElIcon（paged-list-mount.spec.js 没有给 el-icon 准备桩）。
// 口径与 paged-list-mount.spec.js 相同：真实视图组件挂载；接口、路由、状态仓库、消息是替身；Element Plus 组件用会渲染插槽的桩。
// 抓不到：真实布局与配色（靠截图和原型并排核对）。

const hoisted = vi.hoisted(() => ({
  route: { params: {}, query: {} },
  push: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => hoisted.route,
  useRouter: () => ({ push: hoisted.push, back: vi.fn() }),
}))
vi.mock('@/api/fabushi', () => ({
  getFabushiShelf: vi.fn(), getFabushiAwakenStatus: vi.fn(), updateFabushiShelf: vi.fn(), batchFabushiShelf: vi.fn(),
  removeFabushiShelf: vi.fn(), getFabushiRecords: vi.fn(), getFabushiSettings: vi.fn(), saveFabushiSettings: vi.fn(),
  getFabushiShelfGroups: vi.fn(), createFabushiShelfGroup: vi.fn(), renameFabushiShelfGroup: vi.fn(),
  deleteFabushiShelfGroup: vi.fn(), getFabushiPersonalStats: vi.fn(), getMyFabushiBookmarks: vi.fn(),
}))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ realName: '测试员', userId: 7, hasPermission: () => true }),
}))
// 确认 / 输入框的替身要返回 Promise（视图里是 await 它的结果）
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: {
    confirm: vi.fn(() => Promise.resolve('confirm')),
    alert: vi.fn(),
    prompt: vi.fn(() => Promise.resolve({ value: '新名字' })),
  },
}))

import * as fabushiApiModule from '@/api/fabushi'
import { ElMessageBox } from 'element-plus'
import ShelfView from './shelf.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const {
  getFabushiShelf, getFabushiAwakenStatus, updateFabushiShelf, batchFabushiShelf, removeFabushiShelf,
  getFabushiRecords, getFabushiSettings, saveFabushiSettings, getFabushiShelfGroups, createFabushiShelfGroup,
  renameFabushiShelfGroup, deleteFabushiShelfGroup, getFabushiPersonalStats, getMyFabushiBookmarks,
} = fabushiApi
const elMessageBox = /** @type {{ confirm: import('vitest').Mock, prompt: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessageBox))
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 渲染插槽(与分页那个挂载冒烟同一个做法); 按钮单独一个类名, 好按文字找
const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const DIALOG_STUB = { template: '<div class="el-stub el-dialog-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const FORM_ITEM_STUB = { template: '<div class="el-stub el-form-item"><slot /></div>' }
const SelectStub = {
  props: { modelValue: { type: null, default: null } },
  emits: ['update:modelValue', 'change'],
  template: '<div class="el-stub el-select"><slot /></div>',
}
// 开关 / 时间: 点一下就按真实组件的顺序抛 update:modelValue → change, 用来验证“改了就保存”的接线
const SwitchStub = {
  props: {
    modelValue: { type: [Number, Boolean, String], default: 0 },
    activeValue: { type: [Number, Boolean, String], default: true },
    inactiveValue: { type: [Number, Boolean, String], default: false },
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const toggle = () => {
      const next = props.modelValue === props.activeValue ? props.inactiveValue : props.activeValue
      emit('update:modelValue', next)
      emit('change', next)
    }
    return { toggle }
  },
  template: '<button type="button" class="el-stub el-switch" :data-on="modelValue === activeValue" @click="toggle" />',
}
const TimeStub = {
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const pick = () => { emit('update:modelValue', '21:30'); emit('change', '21:30') }
    return { pick }
  },
  template: '<button type="button" class="el-stub el-time" @click="pick" />',
}
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" />',
}
const stubs = {
  'el-button': BUTTON_STUB, 'el-option': SLOT_STUB, 'el-input': SLOT_STUB, 'el-select': SelectStub,
  'el-form': SLOT_STUB, 'el-form-item': FORM_ITEM_STUB, 'el-dialog': DIALOG_STUB,
  'el-switch': SwitchStub, 'el-time-picker': TimeStub, 'el-pagination': PagerStub,
}

// ---- 夹具（形状照测试环境的接口应答样本）----
const shelfBook = (n, extra = {}) => ({
  id: n, bookId: 100 + n, title: `书目 ${n}`, author: `作者 ${n}`, pages: 288, pdfFileId: null,
  categoryCode: 'tuanduibidu', status: 'reading', pinned: 0, sortNo: n, groupId: null, finishedAt: null,
  createdAt: '2026-11-10T09:00:00', updatedAt: `2026-11-${String(10 + n).padStart(2, '0')}T09:00:00`,
  lastSectionId: null, lastPage: 1, totalPages: null, progressPct: 0, cover: `书目 ${n}`.slice(0, 4), ...extra,
})
/** 五本书：置顶且有电子书的在读 1、没挂电子书的在读 2（后端认定的“当前在读”）、读完 3、想读 4、收藏 5 */
const sampleShelf = () => [
  shelfBook(1, { pinned: 1, pdfFileId: 2694, lastPage: 15, totalPages: 29, progressPct: 52, updatedAt: '2026-11-26T08:10:00' }),
  shelfBook(2, { author: null, pages: null, updatedAt: '2026-11-25T09:30:00' }),
  shelfBook(3, {
    status: 'finished', pdfFileId: 3001, lastPage: 96, totalPages: 96, pages: 96, progressPct: 100,
    finishedAt: '2026-11-02T10:00:00', updatedAt: '2026-11-02T10:00:00',
  }),
  shelfBook(4, { status: 'wish', updatedAt: '2026-11-12T09:00:00' }),
  shelfBook(5, { status: 'favorite', pages: null, updatedAt: '2026-11-18T09:00:00' }),
]
const groups = [
  { id: 1, name: '团队必读书目', groupType: 'auto', categoryCode: 'tuanduibidu', sortNo: 1, bookCount: 2 },
  { id: 9, name: '案头常读', groupType: 'custom', sortNo: 1, bookCount: 0 },
]
const shelfData = (records, extra = {}) => {
  const count = (status) => records.filter((b) => b.status === status).length
  return {
    records, groups,
    summary: {
      total: records.length, wish: count('wish'), reading: count('reading'), finished: count('finished'), favorite: count('favorite'),
      finishedThisMonth: 1, currentBook: null, ...extra,
    },
  }
}
const currentBook = (records, n) => ({ ...records.find((b) => b.id === n) })
const monthRecords = {
  month: '2026-11', days: [{ date: '2026-11-24', count: 1 }, { date: '2026-11-25', count: 2 }, { date: '2026-11-26', count: 2 }],
  readDays: 3, readCount: 5,
}
/** 13 本在读、都没置顶：分页用 */
const thirteen = (extra = {}) => shelfData(Array.from({ length: 13 }, (_, i) => shelfBook(i + 1, extra)), { currentBook: null })

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.route = { params: {}, query: {} }
  // 把“今天”定在 2026-11-26（周四）: 日历、相对日期的断言才稳定。只换 Date, flushPromises 靠的 setImmediate 留成真的
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 10, 26, 9, 0, 0))
  const records = sampleShelf()
  getFabushiShelf.mockResolvedValue({ data: shelfData(records, { currentBook: currentBook(records, 2) }) })
  getFabushiAwakenStatus.mockResolvedValue({ data: {
    date: '2026-11-26', todayRecitations: 0, todayGifts: 2, todayPosts: 2, rereadingBooks: 2,
    finishedThisMonth: 1, totalReadDays: 9, currentBook: currentBook(records, 2),
  } })
  getFabushiRecords.mockResolvedValue({ data: monthRecords })
  getFabushiSettings.mockResolvedValue({ data: { reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 } })
  getFabushiPersonalStats.mockResolvedValue({ data: {
    myLikesReceived: 29, myComments: 6, myReadDays: 9, myStreakDays: 3,
    monthSongdu: 1, monthSuishi: 3, monthZaidu: 2, monthPosts: 6,
  } })
  getMyFabushiBookmarks.mockResolvedValue({ data: [{ id: 10, bookId: 17, page: 3, note: null, bookTitle: '易经' }] })
  getFabushiShelfGroups.mockResolvedValue({ data: groups })
  updateFabushiShelf.mockResolvedValue({ data: {} })
  batchFabushiShelf.mockResolvedValue({ data: {} })
  removeFabushiShelf.mockResolvedValue({ data: {} })
  saveFabushiSettings.mockResolvedValue({ data: {} })
  createFabushiShelfGroup.mockResolvedValue({ data: {} })
  renameFabushiShelfGroup.mockResolvedValue({ data: {} })
  deleteFabushiShelfGroup.mockResolvedValue({ data: {} })
})
afterEach(() => {
  vi.useRealTimers()
  expect(warnings, 'Vue 警告').toEqual([])
})

async function mountQuietly() {
  const wrapper = mount(ShelfView, {
    global: {
      stubs,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  return wrapper
}

const texts = (scope, selector) => scope.findAll(selector).map((node) => node.text())
const byText = (scope, selector, text) => scope.findAll(selector).find((node) => node.text() === text)
async function clickText(scope, selector, text) {
  const node = byText(scope, selector, text)
  expect(node, `找不到文字为「${text}」的 ${selector}`).toBeTruthy()
  await node.trigger('click')
  await flushPromises()
}
const gotoTab = async (wrapper, label) => clickText(wrapper, '.tabs .tab', label)
const pagers = (wrapper) => wrapper.findAllComponents(PagerStub)
const cells = (wrapper) => wrapper.findAll('.shelf-grid .scell')

describe('觉醒总览页签（原型 P9）', () => {
  it('骨架：根节点 .fx-page.fabushi-shelf，没有页头标题块；四个页签；总览里没有分页条', async () => {
    const wrapper = await mountQuietly()
    const root = wrapper.find('.fx-page.fabushi-shelf')
    expect(root.exists()).toBe(true)
    expect(root.find('.page-head').exists()).toBe(false)
    expect(texts(root, '.tabs .tab')).toEqual(['觉醒总览', '我的书架', '阅读记录', '提醒设置'])
    expect(root.find('.tabs .tab.active').text()).toBe('觉醒总览')
    // 最上面一排三张板块卡；第二行：左边 书架小表 + 阅读日历，右边 提醒卡 + 我的本月
    expect(texts(root, '.aw-cards .aw-card > .aw-head')).toEqual(['每日诵读', '每日随施', '再读一本'])
    expect(root.findAll('.aw-layout > div')).toHaveLength(2)
    expect(texts(root, '.aw-layout > div:first-child .panel-title')).toEqual([
      '我的书架（在读 / 已读完 / 收藏）进入书架管理 ›',
      '阅读记录 · 2026-11（已读 3/26 天）查看阅读记录 ›',
    ])
    expect(texts(root, '.aw-layout > div:last-child .panel-title')).toEqual([
      '每日提醒 · 闹钟式提醒设置 ›',
      '我的本月（诵读 1 / 随施 3 / 再读 2）2026 年 11 月 · 共 6 条',
    ])
    expect(pagers(wrapper)).toHaveLength(0)
  })

  it('三张板块卡：图标 + 标题 + 三行进度 + 底部的状态章和按钮；完成与否看接口数据', async () => {
    const wrapper = await mountQuietly()
    const cards = wrapper.findAll('.aw-card')
    expect(cards).toHaveLength(3)
    // 图标是图标组件（svg），不是表情符号或汉字图标
    for (const card of cards) expect(card.find('.aw-icon svg').exists(), card.find('.aw-head').text()).toBe(true)
    expect(cards.every((card) => card.findAll('.aw-stat > div').length === 3)).toBe(true)

    // 诵读：今天 0 条 → 待诵 + 去诵读；随施：今天 2 条 → 今日完成 + 再去随施；再读：有在读的书 → 今日完成 + 继续精读
    expect(texts(wrapper, '.aw-card .aw-foot > span:first-child')).toEqual(['今日待诵', '今日完成', '今日完成'])
    expect(texts(wrapper, '.aw-card .aw-foot .btn-stub')).toEqual(['去诵读', '再去随施', '继续精读'])
    expect(cards[0].find('.todo-chip').exists()).toBe(true)
    expect(cards[1].find('.done-chip').exists()).toBe(true)

    expect(texts(cards[0], '.aw-stat > div')).toEqual([
      '每天朗读背诵一小段经典，可传文字/图片后语音诵读。',
      '今日尚未诵读',
      '本月诵读 1 条',
    ])
    expect(texts(cards[1], '.aw-stat > div')).toEqual([
      '随手上传正能量，免标题、免关联书目。',
      '今日已施 · 今日 2 条',
      '本月随施 3 条',
    ])
    expect(texts(cards[2], '.aw-stat > div')).toEqual([
      '按章节精读同一本书，累计进度，形成自己的书摘。',
      '当前：《书目 2》',
      '在读 2 本 · 本月再读 2 条',
    ])
  })

  it('板块卡随数据变：都做过 / 都没做过', async () => {
    getFabushiAwakenStatus.mockResolvedValue({ data: { todayRecitations: 3, todayGifts: 0, rereadingBooks: 0, currentBook: null } })
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.aw-card .aw-foot > span:first-child')).toEqual(['今日完成', '今日待施', '未开始'])
    expect(texts(wrapper, '.aw-card .aw-foot .btn-stub')).toEqual(['再去诵一段', '去随施', '去选书'])
    expect(wrapper.findAll('.aw-card')[0].findAll('.aw-stat > div')[1].text()).toBe('今日已诵 · 今日 3 条')
    expect(wrapper.findAll('.aw-card')[2].find('.aw-stat').text()).toContain('还没有在读的书')
  })

  it('板块卡底部的按钮直达发布器：去认知广场对应类型并带上 scope（发布器在那里打开，有没有发布权限也由那边判断）', async () => {
    const wrapper = await mountQuietly()
    const buttons = wrapper.findAll('.aw-card .aw-foot .btn-stub')
    await buttons[0].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/feed?scope=songdu')
    await buttons[1].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/feed?scope=suishi')
    await buttons[2].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/feed?scope=zaidu')
    // 没有在读的书：不开发布器，去书目浏览挑一本
    getFabushiAwakenStatus.mockResolvedValue({ data: { rereadingBooks: 0, currentBook: null } })
    await vm(wrapper).loadAwaken()
    await wrapper.findAll('.aw-card .aw-foot .btn-stub')[2].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/library')
  })

  it('我的书架小表：只列在读 / 已读完 / 收藏，在读在前、当前在读排第一，最多 3 行；没有数据的格子留空', async () => {
    const wrapper = await mountQuietly()
    const card = wrapper.find('.aw-shelf')
    expect(card.find('.panel-title').text()).toBe('我的书架（在读 / 已读完 / 收藏）进入书架管理 ›')
    expect(texts(card, 'thead th')).toEqual(['书目', '最近', '进度', '页码', '操作'])
    const rows = card.findAll('tbody tr')
    // 想读的 4 不在这张表里；够格的有四本（2、1、3、5），只列前三行
    expect(rows.map((row) => row.find('td').text())).toEqual(['《书目 2》', '《书目 1》 · 作者 1', '《书目 3》 · 作者 3'])
    expect(texts(card, 'tbody tr > td:nth-child(2)')).toEqual(['昨天 09:30', '今天 08:10', '11-02 读完'])
    expect(texts(card, 'tbody tr > td:nth-child(3) .sub2')).toEqual(['', '15/29 页', '96/96 页'])
    expect(texts(card, 'tbody tr > td.num')).toEqual(['', '15/288 页', '96/96 页'])
    // 书 1 读到 52%，书 2 没有进度（进度条是空的，不编一个数）
    expect(rows[1].find('.rb i').attributes('style')).toContain('width: 52%')
    expect(rows[0].find('.rb i').attributes('style')).toContain('width: 0%')
    // 超过 3 本只列 3 行，其余去书架页签看
    getFabushiShelf.mockResolvedValue({ data: thirteen() })
    await vm(wrapper).loadShelf()
    expect(vm(wrapper).glanceRows).toHaveLength(3)
  })

  it('我的书架小表的操作：继续读 / 回顾进阅读器（要有电子书才给），动态去这本书的按书浏览（只看，不弹发布器）', async () => {
    const wrapper = await mountQuietly()
    const rows = wrapper.find('.aw-shelf').findAll('tbody tr')
    // 书 2 没挂电子书：只有“动态”；书 1 在读且有电子书：继续读；书 3 读完了：回顾
    expect(texts(rows[0], '.text-btn')).toEqual(['动态'])
    expect(texts(rows[1], '.text-btn')).toEqual(['继续读', '动态'])
    expect(texts(rows[2], '.text-btn')).toEqual(['回顾', '动态'])
    await byText(rows[1], '.text-btn', '继续读').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/101')
    await byText(rows[2], '.text-btn', '回顾').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/103')
    // 认知广场带着 bookId 进去时，页面自身会弹发布框；这里只是去看动态，所以带 publish=0
    await byText(rows[0], '.text-btn', '动态').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith({ path: '/fabushi/feed', query: { scope: 'zaidu', bookId: 102, publish: '0' } })
  })

  it('书架小表空着的时候给去书目浏览的入口', async () => {
    getFabushiShelf.mockResolvedValue({ data: shelfData([shelfBook(4, { status: 'wish' })]) })
    const wrapper = await mountQuietly()
    const card = wrapper.find('.aw-shelf')
    expect(card.find('table').exists()).toBe(false)
    expect(card.find('.aw-empty').text()).toContain('书架里还没有在读、读完或收藏的书')
    await card.find('.aw-empty .text-btn').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/library')
  })

  it('每日提醒卡：开关、时间、频率分段都接真实的提醒设置，改了就保存；刚打开时不保存', async () => {
    const wrapper = await mountQuietly()
    const card = wrapper.find('.aw-remind')
    expect(card.find('.panel-title').text()).toBe('每日提醒 · 闹钟式提醒设置 ›')
    expect(texts(card, '.set-row > span:first-child')).toEqual(['提醒开关', '提醒时间', '频率'])
    expect(texts(card, '.ver-chip')).toEqual(['每天', '工作日', '休息日'])
    expect(card.find('.ver-chip.active').text()).toBe('每天')
    expect(saveFabushiSettings).not.toHaveBeenCalled()

    await card.find('.el-switch').trigger('click')
    await flushPromises()
    expect(vm(wrapper).settings.reminderEnabled).toBe(1)
    expect(saveFabushiSettings).toHaveBeenLastCalledWith({ reminderEnabled: 1, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 })

    await card.find('.el-time').trigger('click')
    await flushPromises()
    expect(saveFabushiSettings).toHaveBeenLastCalledWith({ reminderEnabled: 1, freqCode: 'daily', remindTime: '21:30', soundEnabled: 1 })

    await clickText(card, '.ver-chip', '工作日')
    expect(saveFabushiSettings).toHaveBeenLastCalledWith({ reminderEnabled: 1, freqCode: 'weekday', remindTime: '21:30', soundEnabled: 1 })
    expect(card.find('.ver-chip.active').text()).toBe('工作日')
    // 木鱼音效只在“提醒设置”页签里改，但保存时整份设置一起提交，不会把它冲掉
    expect(vm(wrapper).settings.soundEnabled).toBe(1)
  })

  it('阅读日历：周一起始、当月天数自己算，读过的日子和今天各有标记', async () => {
    const wrapper = await mountQuietly()
    const card = wrapper.find('.aw-cal')
    expect(card.find('.panel-title').text()).toBe('阅读记录 · 2026-11（已读 3/26 天）查看阅读记录 ›')
    expect(texts(card, '.cal-cell.hd')).toEqual(['一', '二', '三', '四', '五', '六', '日'])
    const dayCells = card.findAll('.cal-cell:not(.hd)')
    // 2026-11-01 是周日：周一开头的前面空 6 格，11 月 30 天
    expect(dayCells).toHaveLength(6 + 30)
    expect(dayCells.slice(0, 6).every((node) => node.classes().includes('blank') && node.text() === '')).toBe(true)
    expect(dayCells[6].text()).toBe('1')
    expect(dayCells[35].text()).toBe('30')
    expect(texts(card, '.cal-cell.done')).toEqual(['24', '25', '26'])
    expect(texts(card, '.cal-cell.today')).toEqual(['26'])
    expect(card.findAll('.cal-cell:not(.hd):not(.blank):not(.done)')).toHaveLength(27)

    // 月份天数与起始星期：2026-06-01 是周一（不空格）、2028-02 有 29 天、1 号是周二（空 1 格）
    const calendar = vm(wrapper).calendarCells
    const june = calendar('2026-06', new Set(['2026-06-01']), '2026-06-26')
    expect(june.filter((cell) => cell.blank)).toHaveLength(0)
    expect(june).toHaveLength(30)
    expect(june[0]).toMatchObject({ key: '2026-06-01', day: 1, done: true })
    expect(june[25]).toMatchObject({ key: '2026-06-26', today: true })
    const feb = calendar('2028-02', new Set(), '2026-11-26')
    expect(feb.filter((cell) => !cell.blank)).toHaveLength(29)
    expect(feb.filter((cell) => cell.blank)).toHaveLength(1)
    expect(feb.filter((cell) => cell.done || cell.today)).toHaveLength(0)
  })

  it('我的本月：三类型条数、获木鱼、本月读完、书摘入口、已读天数、进度条；后端没有的数字不放', async () => {
    const wrapper = await mountQuietly()
    const card = wrapper.find('.aw-month')
    expect(card.find('.panel-title').text()).toBe('我的本月（诵读 1 / 随施 3 / 再读 2）2026 年 11 月 · 共 6 条')
    expect(card.find('.title-sub').text()).toBe('2026 年 11 月 · 共 6 条')
    const rows = card.findAll('.side-row')
    expect(rows[0].text()).toBe('获木鱼（累计）29')
    expect(rows[1].text()).toBe('本月读完1本')
    expect(rows[2].text()).toBe('我的书摘 1 条 ›')
    expect(rows[3].text()).toContain('已读天数')
    expect(rows[3].text()).toContain('累计 9 天')
    expect(rows[3].text()).toContain('连续 3 天')
    expect(card.find('.hbar-row').text()).toBe('本月已读3/26')
    expect(card.find('.hbar-track i').attributes('style')).toContain('width: 12%')
    await rows[2].find('button').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/highlights')
    // “收评论”（接口只有我发出的评论数）、“被复诵”（个人统计里没有）后端没有：不放，也不拿别的数顶替
    expect(card.text()).not.toContain('收评论')
    expect(card.text()).not.toContain('被复诵')
  })

  it('原来的几个入口都还在：进入书架管理 / 查看阅读记录 / 提醒设置 / 我的书摘 / 去书目浏览', async () => {
    const wrapper = await mountQuietly()
    await clickText(wrapper.find('.aw-shelf'), '.corner-link', '进入书架管理 ›')
    expect(vm(wrapper).active).toBe('shelf')
    await gotoTab(wrapper, '觉醒总览')
    await clickText(wrapper.find('.aw-cal'), '.corner-link', '查看阅读记录 ›')
    expect(vm(wrapper).active).toBe('records')
    await gotoTab(wrapper, '觉醒总览')
    await clickText(wrapper.find('.aw-remind'), '.corner-link', '提醒设置 ›')
    expect(vm(wrapper).active).toBe('settings')
    await gotoTab(wrapper, '觉醒总览')
    await byText(wrapper, '.aw-month button', '我的书摘 1 条 ›').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/highlights')
    // 页头的“去书目浏览”按钮挪到页签这一行的右边，四个页签下都在
    for (const label of ['觉醒总览', '我的书架', '阅读记录', '提醒设置']) {
      await gotoTab(wrapper, label)
      expect(byText(wrapper, '.tab-row .btn-stub', '去书目浏览'), label).toBeTruthy()
    }
    await byText(wrapper, '.tab-row .btn-stub', '去书目浏览').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/library')
  })
})

describe('我的书架页签（原型 P9B）', () => {
  async function mountShelfTab() {
    const wrapper = await mountQuietly()
    await gotoTab(wrapper, '我的书架')
    return wrapper
  }
  async function enterManage(wrapper) {
    await clickText(wrapper.find('.filter-card'), '.btn-stub', '批量管理')
  }

  it('继续阅读条：书脊、书名、读到哪、进度条，右边 [继续阅读][换一本]；换一本换到下一本在读的书', async () => {
    const wrapper = await mountShelfTab()
    const hero = () => wrapper.find('.shelf-hero')
    expect(hero().find('.book-spine').exists()).toBe(true)
    expect(hero().find('.shelf-kicker').text()).toBe('继续阅读')
    expect(hero().find('.shelf-bookname').text()).toBe('《书目 2》')
    expect(hero().find('.shelf-sub').text()).toBe('在读 · 读到第 1 页')
    expect(hero().find('.rb').exists()).toBe(true)
    // 书 2 没挂电子书：不给“继续阅读”，只给“换一本”
    expect(texts(hero(), '.shelf-acts .btn-stub')).toEqual(['换一本'])

    await clickText(hero(), '.btn-stub', '换一本')
    expect(hero().find('.shelf-bookname').text()).toBe('《书目 1》 · 作者 1')
    expect(hero().find('.shelf-sub').text()).toContain('读到第 15 / 29 页')
    expect(texts(hero(), '.shelf-acts .btn-stub')).toEqual(['继续阅读', '换一本'])
    await clickText(hero(), '.btn-stub', '继续阅读')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/101')
    // 只有一本在读时不给“换一本”
    getFabushiShelf.mockResolvedValue({ data: shelfData([shelfBook(1, { pdfFileId: 2694 })]) })
    await vm(wrapper).loadShelf()
    expect(texts(hero(), '.shelf-acts .btn-stub')).toEqual(['继续阅读'])
  })

  it('继续阅读条的空状态：没有在读的书', async () => {
    getFabushiShelf.mockResolvedValue({ data: shelfData([shelfBook(4, { status: 'wish' })]) })
    const wrapper = await mountShelfTab()
    const hero = wrapper.find('.shelf-hero')
    expect(hero.text()).toContain('书架里还没有在读的书')
    expect(texts(hero, '.shelf-acts .btn-stub')).toEqual(['去书目浏览'])
    await clickText(hero, '.btn-stub', '去书目浏览')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/library')
  })

  it('筛选条：状态分段带条数、分组、排序、封面 / 列表分段、书架内搜索、批量管理按钮（文字照原型）', async () => {
    const wrapper = await mountShelfTab()
    const bar = wrapper.find('.filter-card')
    expect(texts(bar, '.fl')).toEqual(['状态', '分组', '排序'])
    expect(texts(bar, '.ver-chip')).toEqual(['全部 5', '想读 1', '在读 2', '已读完 1', '收藏 1'])
    expect(bar.find('.ver-chip.active').text()).toBe('全部 5')
    // 下拉的选项（桩里选项文字在 label 属性上）
    const optionLabels = (select) => select.findAll('.el-stub').map((node) => node.attributes('label'))
    expect(optionLabels(bar.findAll('.el-select')[0])).toEqual(['全部分组', '团队必读书目', '案头常读'])
    expect(optionLabels(bar.findAll('.el-select')[1])).toEqual(['最近阅读', '加入时间', '手动排序', '书名'])
    expect(bar.find('[placeholder="书架内搜索"]').exists()).toBe(true)
    expect(texts(bar, '.seg-item')).toEqual(['封面', '列表'])
    // 原型里这个按钮写的是“批量管理”；和“完成管理”一样是四个字，进出管理模式时这一行不跳
    expect(texts(bar, '.btn-stub')).toEqual(['批量管理'])

    await clickText(bar, '.ver-chip', '在读 2')
    expect(vm(wrapper).statusFilter).toBe('reading')
    expect(cells(wrapper)).toHaveLength(2)
    // 条数跟着分组 / 搜索变：书目 3 本来是“已读完”，搜到它以后各状态分段是 1 / 0 / 0 / 1 / 0
    await clickText(bar, '.ver-chip', '全部 5')
    vm(wrapper).keyword = '书目 3'
    await flushPromises()
    expect(texts(bar, '.ver-chip')).toEqual(['全部 1', '想读 0', '在读 0', '已读完 1', '收藏 0'])
    vm(wrapper).keyword = ''
    await flushPromises()

    // 分组下拉真的改了分组筛选
    bar.findComponent(SelectStub).vm.$emit('update:modelValue', 9)
    await flushPromises()
    expect(vm(wrapper).groupFilter).toBe(9)
    expect(cells(wrapper)).toHaveLength(0)
    bar.findComponent(SelectStub).vm.$emit('update:modelValue', 'all')
    await flushPromises()
    expect(cells(wrapper)).toHaveLength(5)

    await clickText(bar, '.seg-item', '列表')
    expect(vm(wrapper).viewMode).toBe('list')
    expect(wrapper.find('.shelf-grid').exists()).toBe(false)
    expect(wrapper.find('.shelf-list').exists()).toBe(true)
  })

  it('封面网格：大书脊、书名、进度文字、进度条；置顶带“顶”标记、想读未开始带 NEW、读完带对勾图标', async () => {
    const wrapper = await mountShelfTab()
    // 顺序照后端：置顶的在前，其余按最近更新
    expect(texts(wrapper, '.scell .sname')).toEqual(['《书目 1》', '《书目 2》', '《书目 5》', '《书目 4》NEW', '《书目 3》'])
    expect(texts(wrapper, '.scell .book-spine')).toEqual(['书目 1', '书目 2', '书目 5', '书目 4', '书目 3'])
    expect(texts(wrapper, '.scell .spintop')).toEqual(['顶'])
    // 没挂电子书的书不带“续读 ›”；读完的写“回顾 ›”
    expect(texts(wrapper, '.scell .sprog')).toEqual([
      '15/29 页 · 52% · 续读 ›', '在读 · 刚加入', '收藏 · 未开始', '想读 · 未开始', '读完 · 回顾 ›',
    ])
    expect(wrapper.findAll('.scell')[3].find('.badge-new').text()).toBe('NEW')
    const finished = wrapper.findAll('.scell')[4]
    expect(finished.find('.sprog svg').exists(), '读完的对勾是图标').toBe(true)
    expect(finished.find('.rb i').attributes('style')).toContain('width: 100%')
    // 平时格子里没有操作按钮；点有电子书的格子进阅读器，没挂电子书的点了不跳
    expect(wrapper.find('.scell-acts').exists()).toBe(false)
    await cells(wrapper)[0].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/101')
    hoisted.push.mockClear()
    await cells(wrapper)[1].trigger('click')
    expect(hoisted.push).not.toHaveBeenCalled()
  })

  it('右栏：书架统计 + 分组管理（点分组即筛选；＋ 新建分组打开分组管理弹窗，改名 / 删除 / 新建都在）', async () => {
    const wrapper = await mountShelfTab()
    const side = wrapper.find('.shelf-body > .side-col')
    expect(texts(side, '.side-title')).toEqual(['书架统计 · 测试员', '分组管理'])
    expect(texts(side.findAll('.side-card')[0], '.side-row')).toEqual([
      '想读 / 在读 / 已读完 / 收藏1 / 2 / 1 / 1', '本月读完1本', '累计阅读天数9天', '当前精读《书目 2》',
    ])
    expect(texts(side, '.sub-row > .sub-name')).toEqual(['全部书目', '团队必读书目', '案头常读（自建）'])
    expect(texts(side, '.sub-row .cnt')).toEqual(['5', '2', '0'])
    await clickText(side, '.sub-row', '团队必读书目2')
    expect(vm(wrapper).groupFilter).toBe(1)
    expect(side.find('.sub-row.active .sub-name').text()).toBe('团队必读书目')
    await clickText(side, '.sub-row', '全部书目5')
    expect(vm(wrapper).groupFilter).toBe('all')

    expect(texts(side, '.rec-acts .btn-stub')).toEqual(['＋ 新建分组'])
    await clickText(side, '.btn-stub', '＋ 新建分组')
    expect(vm(wrapper).groupDialogVisible).toBe(true)
    // 弹窗里：自建分组的改名 / 删除、新建、关闭
    const dialog = wrapper.find('.el-dialog-stub')
    expect(dialog.text()).toContain('案头常读（0 本）')
    expect(dialog.find('[placeholder="新建分组名（如：案头常读）"]').exists()).toBe(true)
    vm(wrapper).newGroupName = '  案头二  '
    await clickText(dialog, '.btn-stub', '新建')
    expect(createFabushiShelfGroup).toHaveBeenLastCalledWith('案头二')
    await clickText(dialog, '.btn-stub', '改名')
    expect(elMessageBox.prompt).toHaveBeenCalledTimes(1)
    expect(renameFabushiShelfGroup).toHaveBeenLastCalledWith(9, '新名字')
    await clickText(dialog, '.btn-stub', '删除')
    expect(elMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(deleteFabushiShelfGroup).toHaveBeenLastCalledWith(9)
    await clickText(dialog, '.btn-stub', '关闭')
    expect(vm(wrapper).groupDialogVisible).toBe(false)
  })

  it('管理模式：批量条 + 点格子勾选；批量操作走原来的接口；完成管理退出', async () => {
    const wrapper = await mountShelfTab()
    expect(wrapper.find('.batch-bar').exists()).toBe(false)
    await enterManage(wrapper)
    expect(vm(wrapper).manageMode).toBe(true)
    const bar = () => wrapper.find('.batch-bar')
    expect(bar().find('b').text()).toBe('已选 0 本')
    expect(texts(bar(), '.btn-stub')).toEqual(['置顶', '取消置顶', '移出书架', '加入分组', '取消'])
    // 没选书的时候批量按钮都是灰的
    expect(bar().findAll('.btn-stub').every((node) => node.attributes('disabled') !== undefined)).toBe(true)
    expect(texts(wrapper.find('.filter-card'), '.btn-stub')).toEqual(['完成管理'])

    await cells(wrapper)[0].trigger('click')
    expect(bar().find('b').text()).toBe('已选 1 本')
    expect(cells(wrapper)[0].classes()).toContain('checked')
    expect(hoisted.push).not.toHaveBeenCalled() // 管理模式下点格子是勾选，不进阅读器
    await cells(wrapper)[0].trigger('click')
    expect(bar().find('b').text()).toBe('已选 0 本')
    await cells(wrapper)[0].trigger('click')
    await cells(wrapper)[3].trigger('click')
    expect(bar().find('b').text()).toBe('已选 2 本')
    expect(vm(wrapper).selected).toEqual([1, 4])

    await clickText(bar(), '.btn-stub', '置顶')
    expect(batchFabushiShelf).toHaveBeenLastCalledWith({ ids: [1, 4], action: 'pin', groupId: undefined })
    expect(vm(wrapper).selected).toEqual([])
    await cells(wrapper)[1].trigger('click')
    await clickText(bar(), '.btn-stub', '取消置顶')
    expect(batchFabushiShelf).toHaveBeenLastCalledWith({ ids: [2], action: 'unpin', groupId: undefined })
    // 加入分组：要先选分组，没选就不给点（免得把书悄悄移出原来的分组）
    await cells(wrapper)[1].trigger('click')
    expect(byText(bar(), '.btn-stub', '加入分组').attributes('disabled')).toBeDefined()
    bar().findComponent(SelectStub).vm.$emit('update:modelValue', 9)
    await flushPromises()
    expect(vm(wrapper).batchGroupId).toBe(9)
    expect(byText(bar(), '.btn-stub', '加入分组').attributes('disabled')).toBeUndefined()
    await clickText(bar(), '.btn-stub', '加入分组')
    expect(batchFabushiShelf).toHaveBeenLastCalledWith({ ids: [2], action: 'group', groupId: 9 })
    // 取消 = 清空选择；完成管理 = 退出管理模式
    await cells(wrapper)[1].trigger('click')
    await clickText(bar(), '.btn-stub', '取消')
    expect(vm(wrapper).selected).toEqual([])
    await clickText(wrapper.find('.filter-card'), '.btn-stub', '完成管理')
    expect(vm(wrapper).manageMode).toBe(false)
    expect(wrapper.find('.batch-bar').exists()).toBe(false)
    // 移出书架走批量接口（阅读进度与上传内容保留）
    await enterManage(wrapper)
    await cells(wrapper)[0].trigger('click')
    await clickText(wrapper.find('.batch-bar'), '.btn-stub', '移出书架')
    expect(batchFabushiShelf).toHaveBeenLastCalledWith({ ids: [1], action: 'remove', groupId: undefined })
  })

  it('管理模式：每本书下面有单独操作（改状态 / 置顶 / 上移 / 下移 / 移出），接口参数不变', async () => {
    const wrapper = await mountShelfTab()
    await enterManage(wrapper)
    const titles = (index) => cells(wrapper)[index].findAll('.scell-acts .mini-btn').map((node) => node.attributes('title'))
    expect(titles(0)).toEqual(['取消置顶', '上移', '下移', '移出书架'])
    expect(titles(1)[0]).toBe('置顶')
    expect(cells(wrapper).every((cell) => cell.find('.scell-acts .el-select').exists())).toBe(true)

    // 上移 / 下移：把当前顺序（置顶的在前，其余按最近更新）里相邻的两本对调，整份顺序交给批量接口
    await cells(wrapper)[1].findAll('.mini-btn')[1].trigger('click')
    await flushPromises()
    expect(batchFabushiShelf).toHaveBeenLastCalledWith({ ids: [2, 1, 5, 4, 3], action: 'reorder' })
    await cells(wrapper)[2].findAll('.mini-btn')[2].trigger('click')
    await flushPromises()
    expect(batchFabushiShelf).toHaveBeenLastCalledWith({ ids: [1, 2, 4, 5, 3], action: 'reorder' })
    // 移出书架（单本）
    await cells(wrapper)[0].findAll('.mini-btn')[3].trigger('click')
    await flushPromises()
    expect(removeFabushiShelf).toHaveBeenLastCalledWith(1)
    expect(getFabushiShelf.mock.calls.length).toBeGreaterThan(1)
    // 改状态：格子里的状态下拉
    cells(wrapper)[0].find('.scell-acts').findComponent(SelectStub).vm.$emit('change', 'finished')
    await flushPromises()
    expect(updateFabushiShelf).toHaveBeenLastCalledWith(1, { status: 'finished' })
    // 置顶 / 取消置顶：书 1 本来是置顶的，所以是“取消置顶”；书 2 没置顶，是“置顶”
    await cells(wrapper)[0].findAll('.mini-btn')[0].trigger('click')
    expect(updateFabushiShelf).toHaveBeenLastCalledWith(1, { pinned: 0 })
    await cells(wrapper)[1].findAll('.mini-btn')[0].trigger('click')
    expect(updateFabushiShelf).toHaveBeenLastCalledWith(2, { pinned: 1 })
    // 点格子里的操作按钮不会顺带勾选这本书
    expect(vm(wrapper).selected).toEqual([])
  })

  it('列表视图保留：每行有书脊、书名、状态、进度、续读；管理模式下操作另起一行，分页条还在', async () => {
    getFabushiShelf.mockResolvedValue({ data: thirteen({ pdfFileId: 2000 }) })
    const wrapper = await mountShelfTab()
    vm(wrapper).sortMode = 'manual'
    await clickText(wrapper.find('.filter-card'), '.seg-item', '列表')
    let rows = wrapper.findAll('.shelf-list .srow')
    expect(rows).toHaveLength(12)
    expect(rows[0].find('.srow-title').text()).toBe('《书目 1》在读')
    expect(rows[0].find('.srow-title .tag').text()).toBe('在读')
    expect(texts(rows[0], '.srow-meta')).toEqual(['作者 1 · 共 288 页 · 读到第 1 页'])
    expect(texts(rows[0], '.srow-acts .btn-stub')).toEqual(['续读'])
    await rows[0].find('.srow-acts .btn-stub').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/101')
    expect(pagers(wrapper).map((pager) => pager.props('total'))).toEqual([13])

    await enterManage(wrapper)
    rows = wrapper.findAll('.shelf-list .srow')
    expect(texts(rows[0], '.srow-acts .btn-stub')).toEqual(['置顶', '上移', '下移', '移出'])
    expect(rows[0].find('.srow-acts .el-select').exists()).toBe(true)
    await rows[0].trigger('click')
    expect(vm(wrapper).selected).toEqual([1])
    expect(rows[0].classes()).toContain('checked')
    await rows[0].find('.srow-acts .btn-stub').trigger('click')
    expect(updateFabushiShelf).toHaveBeenLastCalledWith(1, { pinned: 1 })
    expect(vm(wrapper).selected).toEqual([1])
    expect(pagers(wrapper).map((pager) => pager.props('total'))).toEqual([13])
  })

  it('书架分页条每页 12 本，只在书架页签里出现一个；筛选后空了给去书目浏览的入口', async () => {
    getFabushiShelf.mockResolvedValue({ data: thirteen() })
    const wrapper = await mountShelfTab()
    vm(wrapper).sortMode = 'manual'
    await flushPromises()
    expect(cells(wrapper)).toHaveLength(12)
    expect(pagers(wrapper).map((pager) => pager.props('total'))).toEqual([13])
    vm(wrapper).shelfPage = 2
    await flushPromises()
    expect(cells(wrapper)).toHaveLength(1)
    expect(wrapper.find('.shelf-grid').text()).toContain('共 13 本')
    vm(wrapper).keyword = '不存在的书'
    await flushPromises()
    expect(pagers(wrapper)).toHaveLength(0)
    expect(wrapper.find('.shelf-grid').text()).toContain('当前筛选下没有书架记录')
    await wrapper.find('.shelf-grid .text-btn').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/library')
    await gotoTab(wrapper, '觉醒总览')
    expect(pagers(wrapper)).toHaveLength(0)
  })
})

describe('阅读记录 / 提醒设置页签', () => {
  it('阅读记录：月份、本月阅读天数 / 次数、每天的记录、分页条（每页 10 天）', async () => {
    getFabushiRecords.mockResolvedValue({ data: {
      month: '2026-11', readDays: 11, readCount: 11,
      days: Array.from({ length: 11 }, (_, i) => ({ date: `2026-11-${String(i + 1).padStart(2, '0')}`, count: 1 })),
    } })
    const wrapper = await mountQuietly()
    await gotoTab(wrapper, '阅读记录')
    const card = wrapper.find('.records-card')
    expect(card.find('.panel-title').text()).toContain('阅读记录')
    expect(card.find('[placeholder="YYYY-MM"]').exists()).toBe(true)
    expect(texts(card, '.rec-kpi span')).toEqual(['本月阅读', '天', '阅读', '次'])
    expect(texts(card, '.rec-kpi b')).toEqual(['11', '11'])
    const rows = card.findAll('.record-row')
    expect(rows).toHaveLength(10)
    expect(rows[0].text()).toBe('2026-11-011 次')
    expect(pagers(wrapper).map((pager) => pager.props('total'))).toEqual([11])
    // 改月份重新取记录
    vm(wrapper).month = '2026-10'
    await vm(wrapper).loadRecords()
    expect(getFabushiRecords).toHaveBeenLastCalledWith('2026-10')
    // 觉醒总览的日历固定看当月，不跟着这里的月份走
    expect(getFabushiRecords).toHaveBeenCalledWith('2026-11')
    await gotoTab(wrapper, '觉醒总览')
    expect(wrapper.find('.aw-cal .panel-title').text()).toContain('阅读记录 · 2026-11')
  })

  it('阅读记录默认看本机当下的月份（按本机日期算，不按 UTC：月初的深夜、月末的傍晚两者会差一个月）', async () => {
    // 本机 12-01 00:30：在 UTC 之后的时区（如东八区），它的 UTC 时间还是 11-30，按 UTC 算会错看成 11 月
    const firstNight = new Date(2026, 11, 1, 0, 30)
    // 在 UTC 之前的时区，换成本机 12-31 23:30（它的 UTC 时间已经是次年 1-01）
    const lastNight = new Date(2026, 11, 31, 23, 30)
    vi.setSystemTime(firstNight.toISOString().startsWith('2026-11') ? firstNight : lastNight)
    const wrapper = await mountQuietly()
    await gotoTab(wrapper, '阅读记录')
    expect(vm(wrapper).month).toBe('2026-12')
    expect(getFabushiRecords).toHaveBeenCalledWith('2026-12')
  })

  it('提醒设置：缺卡提醒、频率、提醒时间、木鱼音效、保存设置（点了才保存）', async () => {
    const wrapper = await mountQuietly()
    await gotoTab(wrapper, '提醒设置')
    const card = wrapper.find('.settings-card')
    // 带 label 的四项（“保存设置”那一项没有 label）
    const items = card.findAll('.el-form-item').filter((node) => node.attributes('label') !== undefined)
    expect(items.map((node) => node.attributes('label'))).toEqual(['缺卡提醒', '频率', '提醒时间', '木鱼音效'])
    expect(items[1].findAll('.el-select .el-stub').map((node) => node.attributes('label'))).toEqual(['每天', '工作日', '休息日'])
    expect(card.findAll('.el-time')).toHaveLength(1)
    expect(saveFabushiSettings).not.toHaveBeenCalled()
    await card.find('.el-switch').trigger('click')
    await flushPromises()
    expect(vm(wrapper).settings.reminderEnabled).toBe(1)
    expect(saveFabushiSettings).not.toHaveBeenCalled()
    await clickText(card, '.btn-stub', '保存设置')
    expect(saveFabushiSettings).toHaveBeenLastCalledWith({ reminderEnabled: 1, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 })
    await gotoTab(wrapper, '觉醒总览')
    // 觉醒总览的提醒卡和这里改的是同一份设置
    expect(wrapper.find('.aw-remind .el-switch').attributes('data-on')).toBe('true')
  })
})

describe('静态检查', () => {
  const sfc = readFileSync('src/views/fabushi/shelf.vue', 'utf-8')
  const template = sfc.slice(0, sfc.indexOf('<script'))

  it('图标不用表情符号或符号字符；公共样式 fabushi-v8.css 有引入；ElIcon 显式引入', () => {
    expect(sfc).toContain("import '@/assets/fabushi-v8.css'")
    expect(template).not.toMatch(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}\u{2190}-\u{21FF}]/u)
    expect(sfc).toMatch(/import \{[^}]*\bElIcon\b[^}]*\} from 'element-plus'/)
    expect(template).not.toContain('<el-icon')
  })

  it('页面根节点保留 fabushi-shelf 类（截图工具靠它找到页面）', () => {
    expect(template).toContain('<div class="fx-page fabushi-shelf">')
  })
})
