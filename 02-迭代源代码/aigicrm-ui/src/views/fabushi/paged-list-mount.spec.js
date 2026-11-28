import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

// 【GL-14 2026-11-24】法布施列表翻页的视图接线：每个用 usePagedList 的视图做一次挂载冒烟。
// 缘起: d7f560c9——book.vue / detail.vue 用了 watch、usePagedList 却没有 import，构建不报错，
//       到浏览器里才在 setup 阶段抛 ReferenceError。此后端到端没有任何一层能拦住这类问题。
// 口径: 用真实的视图组件（真实编译的模板与 script）挂载；接口、路由、状态仓库、消息、PDF 阅读器是替身；
//       Element Plus 组件用会渲染插槽的桩，模板里的表达式照样会被求值。
//       每个视图断言三件事:
//         ① 挂载与首屏数据回填全程不抛错、没有任何 Vue 警告（缺导入、模板引用了不存在的名字都会在这里现形）；
//         ② 分页切片与接口回来的数据对得上（第一页满页、第二页是余数）；
//         ③ 翻到第二页后切片跟着变（分页状态与模板绑定的名字对得上）。
// 抓不到: Element Plus 组件自身的行为、真实布局、接口的真实返回形状（夹具是按各视图的消费方式写的）。

const hoisted = vi.hoisted(() => ({
  route: { params: {}, query: {} },
  apiNames: [
  'getFabushiBooks', 'getFabushiBook', 'getPendingFabushiBooks', 'submitFabushiBook', 'approveFabushiBook',
  'rejectFabushiBook', 'disableFabushiBook', 'deleteFabushiBook', 'attachBookPdf', 'getFabushiPosts',
  'getFabushiPostDetail', 'createFabushiPost', 'restoreFabushiPost', 'offlineFabushiPost', 'addFabushiComment',
  'getFabushiComments', 'toggleFabushiLike', 'markFabushiRead', 'getFabushiDashboard', 'getFabushiTeamStats',
  'getFabushiPersonalStats', 'getFabushiAwakenStatus', 'getFabushiShelf', 'addFabushiShelf', 'batchFabushiShelf',
  'getFabushiShelfGroups', 'createFabushiShelfGroup', 'renameFabushiShelfGroup', 'deleteFabushiShelfGroup',
  'updateFabushiShelf', 'removeFabushiShelf', 'getFabushiProgress', 'saveFabushiProgress', 'getFabushiBookmarks',
  'getMyFabushiBookmarks', 'addFabushiBookmark', 'removeFabushiBookmark', 'updateFabushiBookLayout',
  'getFabushiRecords', 'getFabushiSettings', 'saveFabushiSettings', 'addFabushiAttachment',
  'removeFabushiAttachment', 'getFabushiSections', 'createFabushiSection', 'deleteFabushiSection',
  'getFabushiActivities', 'joinFabushiActivity', 'createFabushiActivity', 'endFabushiActivity',
  'getFabushiMonthly', 'updateFabushiPost', 'deleteFabushiPost', 'getDeletedFabushiPosts',
  'restoreDeletedFabushiPost', 'getFabushiRecitations', 'addFabushiRecitation',
  ],
  push: vi.fn(),
  reader: /** @type {any} */ ({}),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => hoisted.route,
  useRouter: () => ({ push: hoisted.push, back: vi.fn() }),
}))

vi.mock('@/api/fabushi', () => {
  const api = {}
  for (const name of hoisted.apiNames) api[name] = vi.fn()
  return api
})
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/utils/token', () => ({
  getToken: vi.fn(() => 'token-for-test'),
  refreshDownloadToken: vi.fn(() => Promise.resolve('token-for-test')),
  getDownloadToken: vi.fn(() => 'token-for-test'),
}))
vi.mock('@/utils/download', () => ({ downloadFile: vi.fn() }))
vi.mock('@/utils/pdf-inspect', () => ({ inspectPdfFile: vi.fn() }))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ hasPermission: () => true, userId: 7, username: 'tester' }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), alert: vi.fn(), prompt: vi.fn() },
}))
// PDF 阅读器有自己的单测(useFabushiPdfReader.spec.js); 这里给一组什么都不做的替身
vi.mock('@/composables/useFabushiPdfReader', () => ({
  useFabushiPdfReader: () => hoisted.reader,
}))

import * as fabushiApiModule from '@/api/fabushi'
import { getDictData as getDictDataMock } from '@/api/system'
// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const getDictData = mocked(getDictDataMock)
const {
  getFabushiBooks, getFabushiPostDetail, getFabushiComments, getFabushiBookmarks, getMyFabushiBookmarks,
  getFabushiShelf, getFabushiRecords, getFabushiDashboard, getFabushiBook, getFabushiSettings,
} = fabushiApi
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)
import BookView from './book.vue'
import DetailView from './detail.vue'
import FabushiMuyu from '@/components/fabushi/FabushiMuyu.vue'
import HighlightsView from './highlights.vue'
import ReadView from './read.vue'
import ShelfView from './shelf.vue'
import StatsView from './stats.vue'

// Element Plus 组件的桩: 渲染默认插槽与 header/footer 插槽, 事件与属性原样透传。
// 分页组件单独声明属性与事件, 让 v-model:current-page 真正双向绑定——模板绑的名字不对时这里会断。
const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, pageSizes: Array, layout: String },
  emits: ['update:currentPage', 'update:pageSize', 'current-change', 'size-change', 'change'],
  template: '<div class="el-stub el-pager" />',
}
const stubs = {
  'el-button': SLOT_STUB, 'el-option': SLOT_STUB, 'el-input': SLOT_STUB, 'el-select': SLOT_STUB,
  'el-form-item': SLOT_STUB, 'el-dialog': SLOT_STUB, 'el-radio-button': SLOT_STUB, 'el-switch': SLOT_STUB,
  'el-radio-group': SLOT_STUB, 'el-form': SLOT_STUB, 'el-time-picker': SLOT_STUB, 'el-input-number': SLOT_STUB,
  'el-image': SLOT_STUB, 'el-empty': SLOT_STUB, 'el-date-picker': SLOT_STUB, 'el-checkbox': SLOT_STUB,
  'el-pagination': PagerStub,
}

const range = (count, make) => Array.from({ length: count }, (_, i) => make(i + 1))
const book = (n) => ({ id: n, title: `书目 ${n}`, author: `作者 ${n}`, categoryCode: 'liaofan', categoryName: '了凡四训',
  editionType: 'paper', status: 'approved', layoutType: 'scan', pages: 100 + n })

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.route = { params: {}, query: {} }
  hoisted.reader = {
    state: ref('idle'), loadPct: ref(0), errorMsg: ref(''), currentPage: ref(1), totalPages: ref(0),
    load: vi.fn(), goPage: vi.fn(), markPage: vi.fn(), retry: vi.fn(), pageText: ref(''), detectTextLayer: vi.fn(),
    extractAllText: vi.fn(), searchInPages: vi.fn(),
  }
  // 其余接口默认返回“空数据”
  for (const name of hoisted.apiNames) fabushiApi[name].mockResolvedValue({ data: [] })})

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

function expectQuiet(label) {
  // 这六个视图在替身下挂载时一条 Vue 警告都没有, 所以按“零警告”判:
  // 缺导入会直接抛错; 模板引用了不存在的名字、组件或指令解析不了, 都会以警告的形式出现
  expect(warnings, `${label}: Vue 警告`).toEqual([])
}

const pagers = (wrapper) => wrapper.findAllComponents(PagerStub)
const pagerTotals = (wrapper) => pagers(wrapper).map((pager) => pager.props('total'))
const pagerPages = (wrapper) => pagers(wrapper).map((pager) => pager.props('currentPage'))

describe('法布施列表翻页的视图接线（挂载冒烟）', () => {
  it('book.vue：左栏书目每页 10 条，13 本 → 第一页 10、第二页 3；改筛选回到第一页', async () => {
    getFabushiBooks.mockResolvedValue({ data: { records: range(13, book), total: 13 } })
    getDictData.mockResolvedValue({ data: [] })

    const wrapper = await mountQuietly(BookView)
    expectQuiet('book.vue')

    expect(vm(wrapper).pagedLeftBooks).toHaveLength(10)
    expect(vm(wrapper).leftTotalPages).toBe(2)
    // 左栏(书目全量)与右侧(服务端分页)各一个分页条
    expect(pagerTotals(wrapper)).toEqual([13, 13])
    expect(pagerPages(wrapper)).toEqual([1, 1])

    vm(wrapper).leftPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedLeftBooks.map((item) => item.id)).toEqual([11, 12, 13])
    expect(pagerPages(wrapper)).toEqual([2, 1])

    // 改左栏关键字(仍能匹配 13 本, 两页装得下): 页码应回到第一页, 而不是停在第二页
    vm(wrapper).leftKeyword = '书目'
    await flushPromises()
    expect(vm(wrapper).leftPage).toBe(1)
    expect(vm(wrapper).pagedLeftBooks).toHaveLength(10)
  })

  it('detail.vue：评论每页 10 条、复诵每页 5 条，12 条评论 + 7 条复诵', async () => {
    hoisted.route = { params: { id: '501' }, query: {} }
    getFabushiPostDetail.mockResolvedValue({ data: {
      post: { id: 501, title: '今日诵读', content: '内容', userId: 7, scopeType: 'songdu', bookId: 1, createdAt: '2026-11-20T08:00:00' },
      attachments: [], likeCount: 2, likedByMe: false, recitationCount: 7,
      recitations: range(7, (n) => ({ id: n, audioFileId: 900 + n, durationSeconds: 12, userName: '同学' })),
    } })
    getFabushiComments.mockResolvedValue({ data: range(12, (n) => ({ id: n, content: `评论 ${n}`, userName: '同学', createdAt: '2026-11-20T09:00:00' })) })
    getFabushiBooks.mockResolvedValue({ data: { records: [book(1)], total: 1 } })
    getFabushiSettings.mockResolvedValue({ data: { soundEnabled: 1 } })

    const wrapper = await mountQuietly(DetailView)
    expectQuiet('detail.vue')

    expect(vm(wrapper).pagedComments).toHaveLength(10)
    expect(vm(wrapper).commentTotalPages).toBe(2)
    expect(vm(wrapper).pagedRecitations).toHaveLength(5)
    expect(vm(wrapper).recitationTotalPages).toBe(2)
    // 模板里复诵的分页条在前、评论的在后
    expect(pagerTotals(wrapper)).toEqual([7, 12])

    // 两个分页条各绑各的页码: 一次只动一个, 绑错了对象就会在这里现形
    vm(wrapper).commentPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedComments.map((item) => item.id)).toEqual([11, 12])
    expect(pagerPages(wrapper)).toEqual([1, 2])
    vm(wrapper).recPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedRecitations.map((item) => item.id)).toEqual([6, 7])
    expect(pagerPages(wrapper)).toEqual([2, 2])
  })

  it('highlights.vue：按书分组每页 6 组，7 本书的书摘 → 第一页 6 组、第二页 1 组；改搜索回到第一页', async () => {
    getMyFabushiBookmarks.mockResolvedValue({ data: range(7, (n) => ({ id: n, bookId: n, bookTitle: `书目 ${n}`, page: n, note: `书摘 ${n}`, createdAt: '2026-11-20T09:00:00' })) })

    const wrapper = await mountQuietly(HighlightsView)
    expectQuiet('highlights.vue')

    expect(vm(wrapper).pagedGroups).toHaveLength(6)
    expect(vm(wrapper).totalPages).toBe(2)
    expect(pagerTotals(wrapper)).toEqual([7])

    vm(wrapper).current = 2
    await flushPromises()
    expect(vm(wrapper).pagedGroups.map((group) => group.bookId)).toEqual([7])
    expect(pagerPages(wrapper)).toEqual([2])

    // 搜索词改成仍能命中全部 7 本的“书摘”: 页码应回到第一页
    vm(wrapper).keyword = '书摘'
    await flushPromises()
    expect(vm(wrapper).current).toBe(1)
    expect(vm(wrapper).pagedGroups).toHaveLength(6)
  })

  it('read.vue：书签每页 8 条，9 个书签；书内搜索命中每页 20 条', async () => {
    hoisted.route = { params: { bookId: '3' }, query: {} }
    getFabushiBook.mockResolvedValue({ data: { book: { ...book(3), pdfFileId: 77 } } }) // 挂了电子书: 阅读区才会渲染(阅读器本身是替身)
    getFabushiBookmarks.mockResolvedValue({ data: range(9, (n) => ({ id: n, page: n })) })

    const wrapper = await mountQuietly(ReadView)
    expectQuiet('read.vue')

    expect(hoisted.reader.load).toHaveBeenCalledTimes(1)
    expect(vm(wrapper).pagedBookmarks).toHaveLength(8)
    expect(vm(wrapper).bookmarkTotalPages).toBe(2)
    // 书签的分页条在书签超过一页时出现; 书内搜索的分页条要有命中才出现
    expect(pagerTotals(wrapper)).toEqual([9])

    vm(wrapper).bookmarkPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedBookmarks.map((item) => item.page)).toEqual([9])
    expect(pagerPages(wrapper)).toEqual([2])

    // 书内搜索的命中来自 PDF 文本层(阅读器是替身, 有自己的单测); 这里直接给命中列表, 验分页接线
    vm(wrapper).searchHits = range(21, (n) => ({ page: n, snippet: `命中 ${n}` }))
    await flushPromises()
    expect(vm(wrapper).pagedSearchHits).toHaveLength(20)
    expect(vm(wrapper).searchTotalPages).toBe(2)
    // 模板里书内搜索的分页条在前、书签的在后
    expect(pagerTotals(wrapper)).toEqual([21, 9])
    expect(pagerPages(wrapper)).toEqual([1, 2])
    vm(wrapper).searchPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedSearchHits.map((item) => item.page)).toEqual([21])
    expect(pagerPages(wrapper)).toEqual([2, 2])
  })

  it('shelf.vue：书架每页 12 本、阅读记录每页 10 天，13 本 + 11 天；改筛选回到第一页', async () => {
    getFabushiShelf.mockResolvedValue({ data: {
      records: range(13, (n) => ({ ...book(n), status: 'reading', progress: 10, pinned: 0 })),
      groups: [], summary: { total: 13, currentBook: null },
    } })
    getFabushiRecords.mockResolvedValue({ data: { days: range(11, (n) => ({ date: `2026-11-${String(n).padStart(2, '0')}`, readCount: 1 })), readDays: 11, readCount: 11 } })
    getMyFabushiBookmarks.mockResolvedValue({ data: [] })
    getFabushiSettings.mockResolvedValue({ data: {} })

    const wrapper = await mountQuietly(ShelfView)
    expectQuiet('shelf.vue')

    expect(vm(wrapper).pagedShelf).toHaveLength(12)
    expect(vm(wrapper).shelfTotalPages).toBe(2)
    expect(vm(wrapper).pagedRecordDays).toHaveLength(10)

    // 书架在“我的书架”页签里: 切过去, 翻到第二页只剩第 13 本
    vm(wrapper).active = 'shelf'
    await flushPromises()
    expect(pagerTotals(wrapper)).toEqual([13])
    vm(wrapper).shelfPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedShelf.map((item) => item.id)).toEqual([13])
    expect(pagerPages(wrapper)).toEqual([2])
    // 改关键字(仍能匹配全部 13 本): 页码回到第一页
    vm(wrapper).keyword = '书目'
    await flushPromises()
    expect(vm(wrapper).shelfPage).toBe(1)

    // 阅读记录在“阅读记录”页签里: 11 天 → 第一页 10 天、第二页 1 天
    vm(wrapper).active = 'records'
    await flushPromises()
    expect(pagerTotals(wrapper)).toEqual([11])
    vm(wrapper).recordPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedRecordDays.map((day) => day.date)).toEqual(['2026-11-11'])
    expect(pagerPages(wrapper)).toEqual([2])
  })

  it('stats.vue：书目阅读榜每页 8 本、部门进度每页 8 个，9 本 + 17 个部门；两栏都要有翻页条，各绑各的', async () => {
    // 两个列表条数不同(9 → 2 页, 17 → 3 页): 分页条的总数写错成另一个列表的, 就会在这里现形
    getFabushiDashboard.mockResolvedValue({ data: {
      period: 'week', memberCount: 5, ranking: [], deptProgress: range(17, (n) => ({ deptId: n, deptName: `部门 ${n}`, readCount: n })),
      categoryReads: [], trend: [], bookRanking: range(9, (n) => ({ ...book(n), readCount: n })),
    } })

    const wrapper = await mountQuietly(StatsView)
    expectQuiet('stats.vue')

    expect(vm(wrapper).pagedBookRanking).toHaveLength(8)
    expect(vm(wrapper).pagedDeptProgress).toHaveLength(8)
    // 部门进度每页只取 8 个: 没有翻页条的话, 第 9 个部门起在界面上永远看不到
    expect(pagerTotals(wrapper)).toEqual([9, 17])

    vm(wrapper).bookRankPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedBookRanking.map((item) => item.id)).toEqual([9])
    expect(pagerPages(wrapper)).toEqual([2, 1])
    vm(wrapper).deptPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedDeptProgress.map((item) => item.deptId)).toEqual([9, 10, 11, 12, 13, 14, 15, 16])
    expect(pagerPages(wrapper)).toEqual([2, 2])
    vm(wrapper).deptPage = 3
    await flushPromises()
    expect(vm(wrapper).pagedDeptProgress.map((item) => item.deptId)).toEqual([17])
    expect(pagerPages(wrapper)).toEqual([2, 3])
  })

  describe('detail.vue 复诵录音的环境检查（修复 a93eed71）', () => {
    /** 浏览器本身能录音: getUserMedia + MediaRecorder(只支持 given 列表里的格式) */
    const capableRecorder = (supported) => {
      vi.stubGlobal('MediaRecorder', class {
        static isTypeSupported(mime) { return supported.includes(mime) }
      })
      Object.defineProperty(navigator, 'mediaDevices', {
        value: { getUserMedia: vi.fn() },
        configurable: true,
      })
    }
    const openDetail = async () => {
      hoisted.route = { params: { id: '501' }, query: {} }
      getFabushiPostDetail.mockResolvedValue({ data: { post: { id: 501, title: '今日诵读', scopeType: 'songdu' }, attachments: [], recitations: [] } })
      const wrapper = await mountQuietly(DetailView)
      expectQuiet('detail.vue 复诵')
      return wrapper
    }
    afterEach(() => {
      vi.unstubAllGlobals()
      if ('mediaDevices' in navigator) delete /** @type {any} */ (navigator).mediaDevices
    })

    it('HTTP 环境: 不去要麦克风, 明确提示改用 HTTPS', async () => {
      capableRecorder(['audio/webm'])
      vi.stubGlobal('isSecureContext', false)
      const wrapper = await openDetail()

      expect(vm(wrapper).pickReciteMime()).toBeNull()
      await vm(wrapper).startRecite()

      expect(ElMessage.warning).toHaveBeenLastCalledWith('当前是 HTTP 环境，浏览器禁止麦克风；请改用 HTTPS 打开，或在微信小程序端复诵')
      expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
      expect(vm(wrapper).reciting).toBe(false)
    })

    it('HTTPS 但浏览器不支持录音: 提示去小程序端复诵', async () => {
      vi.stubGlobal('isSecureContext', true)
      Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true })
      vi.stubGlobal('MediaRecorder', undefined)
      const wrapper = await openDetail()

      await vm(wrapper).startRecite()

      expect(ElMessage.warning).toHaveBeenLastCalledWith('当前浏览器不支持录音，请在微信小程序端复诵')
      expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
    })

    it('格式选择: mp4 优先(Safari 不支持 webm 回放), 其次 opus, 再退到 webm; 都不支持返回空', async () => {
      vi.stubGlobal('isSecureContext', true)
      const wrapper = await openDetail()

      capableRecorder(['audio/webm;codecs=opus', 'audio/webm'])
      expect(vm(wrapper).pickReciteMime()).toBe('audio/webm;codecs=opus')
      capableRecorder(['audio/webm'])
      expect(vm(wrapper).pickReciteMime()).toBe('audio/webm')
      // mp4 与 webm 都支持的浏览器(新版 Safari / Chrome): 必须选 mp4, Safari 放不了 webm
      capableRecorder(['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm'])
      expect(vm(wrapper).pickReciteMime()).toBe('audio/mp4')
      capableRecorder([])
      expect(vm(wrapper).pickReciteMime()).toBeNull()
    })
  })
})

// 【2026-11-25 用户裁决】“PC 端的木鱼需要做成和小程序的木鱼一样，注意尺寸就行”。
// 小程序的木鱼图是发布成功页那只纯 CSS 画的木鱼（pages/fabushi/upload/index.wxml 的 muyu-stage: 光晕、波纹、鱼身、坐垫、木槌）。
// 详情页用的是 FabushiMuyu 组件——它自己的测试拿小程序的 wxml / wxss 逐项对账；这里钉住详情页这一侧的三件事:
//   ① 按钮里就是这个组件的五个部件、图里没有表情符号；
//   ② 敲一下时动画类挂上，等最长的一段动画放完才摘掉；+1 只在新增点赞时出现；
//   ③ PC 上取的尺寸是小程序原尺寸的一半。
// 抓不到: 真实的样子与比例（要靠截图比对）、音效。
describe('detail.vue 木鱼图：与小程序发布成功页那只木鱼同源', () => {
  const { toggleFabushiLike } = fabushiApi
  const openDetail = async (likedByMe) => {
    hoisted.route = { params: { id: '501' }, query: {} }
    getFabushiPostDetail.mockResolvedValue({ data: {
      post: { id: 501, title: '今日诵读', content: '内容', userId: 7, scopeType: 'songdu', bookId: 1, createdAt: '2026-11-20T08:00:00' },
      attachments: [], likeCount: 2, likedByMe, recitationCount: 0, recitations: [],
    } })
    getFabushiComments.mockResolvedValue({ data: [] })
    getFabushiBooks.mockResolvedValue({ data: { records: [book(1)], total: 1 } })
    getFabushiSettings.mockResolvedValue({ data: { soundEnabled: 1 } })
    const wrapper = await mountQuietly(DetailView)
    expectQuiet('detail.vue 木鱼')
    return wrapper
  }
  const muyuButton = (wrapper) => wrapper.find('button.muyu')
  beforeEach(() => {
    // 敲木鱼会调音效的 play(): 测试环境里的 Audio 没有实现它, 会打一行 “Not implemented”。换成什么都不做的替身
    vi.stubGlobal('Audio', class { currentTime = 0; play() {} })
  })
  afterEach(() => { vi.unstubAllGlobals() })

  it('按钮里是小程序那只木鱼(S2 SVG 美术源)：舞台件 + 内联 SVG 的鱼身/音槽闪金/木槌，没有 🐟', async () => {
    const wrapper = await openDetail(false)
    const button = muyuButton(wrapper)
    expect(button.exists()).toBe(true)

    const stage = button.find('.muyu-stage')
    expect(stage.exists(), '木鱼舞台 .muyu-stage').toBe(true)
    // 舞台件与小程序模板一一对应（S2 起：鱼身/木槌 = 内联 SVG，不再是 CSS 拼装件）
    for (const part of ['muyu-glow', 'muyu-ripple', 'muyu-ground']) {
      expect(stage.find(`.${part}`).exists(), `缺少 ${part}`).toBe(true)
    }
    expect(stage.find('svg.muyu-svg').exists(), '内联 SVG 美术').toBe(true)
    expect(stage.find('.m-fish').exists(), '鱼身组').toBe(true)
    expect(stage.find('.m-mallet').exists(), '木槌组').toBe(true)
    expect(stage.find('.m-slotflash').exists(), '音槽闪金').toBe(true)
    // 舞台只是图: 里面不该有表情符号(+1 计数件平时不渲染)
    expect(stage.text()).not.toContain('🐟')
    expect(button.text()).not.toContain('🐟')
    expect(button.text()).toContain('木鱼')
    expect(button.text()).toContain('2')
    expect(button.find('.muyu-plus').exists(), '平时不该有 +1').toBe(false)
  })

  it('PC 上取小程序原尺寸的一半：鱼身约 42×30 像素，整只连坐垫木槌约 72×54 像素', async () => {
    const wrapper = await openDetail(false)
    const fish = muyuButton(wrapper).findComponent(FabushiMuyu)
    expect(fish.exists(), '按钮里用的是 FabushiMuyu 组件').toBe(true)
    expect(fish.props('scale')).toBe(0.5)
    expect(fish.attributes('style') || '').toContain('--muyu-s: 0.5')
    // 只此一处：按钮里不再另画一份木鱼
    expect(muyuButton(wrapper).findAll('.muyu-stage').length).toBe(1)
  })

  it('敲一下：动画类挂上，小程序那几段动画里最长的 0.8 秒放完才摘掉', async () => {
    const wrapper = await openDetail(false)
    toggleFabushiLike.mockResolvedValue({ data: { liked: true, likeCount: 3 } })
    // 只把 setTimeout/clearTimeout 换成假的: flushPromises 靠 setImmediate, 留成真的才不会卡住
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      await muyuButton(wrapper).trigger('click')
      await flushPromises()
      const button = muyuButton(wrapper)
      expect(button.classes()).toContain('hit')
      expect(button.classes()).toContain('on')
      expect(button.text()).toContain('3')
      expect(vm(wrapper).muyuPlus).toBe(1)
      // +1 在舞台里(与小程序同一个位置), 这一下敲完就收起
      expect(button.find('.muyu-stage .muyu-plus').exists()).toBe(true)

      // 小程序的木槌 0.55 秒、波纹/光晕各 0.8 秒: 0.8 秒之内动画类必须在。
      // 推进时钟后要等界面重新渲染再读, 否则读到的是旧状态(第一版就因为没等而红在了错的那一行)
      vi.advanceTimersByTime(600)
      await flushPromises()
      expect(muyuButton(wrapper).classes()).toContain('hit')
      vi.advanceTimersByTime(219)
      await flushPromises()
      expect(muyuButton(wrapper).classes()).toContain('hit')
      vi.advanceTimersByTime(1)
      await flushPromises()
      expect(muyuButton(wrapper).classes()).not.toContain('hit')
      expect(muyuButton(wrapper).find('.muyu-plus').exists()).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('取消点赞也会敲一下（挂动画类），但不显示 +1', async () => {
    const wrapper = await openDetail(true)
    toggleFabushiLike.mockResolvedValue({ data: { liked: false, likeCount: 1 } })
    await muyuButton(wrapper).trigger('click')
    await flushPromises()
    const button = muyuButton(wrapper)
    expect(button.classes()).toContain('hit')
    expect(button.classes()).not.toContain('on')
    expect(vm(wrapper).muyuPlus).toBe(0)
    expect(button.find('.muyu-plus').exists()).toBe(false)
  })
})
