import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】统计看板（stats.vue = 原型 P12）。用户裁决“PC 端按最新的原型来做”。这里钉住五件事：
//   ① 骨架跟原型走：主视觉条 → 五张数字卡 → 成员榜 / 类别热度分布 / 每日达成趋势 → 书目阅读榜 → 部门进度 → 月度报告 → 我的法布施 → 口径说明；
//   ② 数字只用接口真有的：五张卡逐张对接口字段，接口给不了的显示“—”，不编环比；
//   ③ 成员榜：奖牌名次按全榜名次、原有各列都在，活跃度条用排行项里的 activityPct；
//   ④ 原有功能一个都不少：周期切换即查、刷新、导出报表（CSV）、按阅读 / 按发布、成员榜翻页、两个分页条的先后与各自绑定、月度报告导出；
//   ⑤ 图标不用表情符号，公共样式 fabushi-v8.css 有引入；el-icon 用显式引入的 ElIcon（paged-list-mount.spec.js 挂载本视图时没有 el-icon 的桩）。
// 口径与 paged-list-mount.spec.js 相同：真实视图组件挂载；接口、路由、状态仓库、下载是替身；Element Plus 组件用会渲染插槽的桩。
// 抓不到：真实布局与配色（靠截图和原型并排核对）。

const hoisted = vi.hoisted(() => ({
  push: vi.fn(),
  permissions: ['*'],
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: hoisted.push, back: vi.fn() }),
}))
vi.mock('@/api/fabushi', () => ({
  getFabushiDashboard: vi.fn(), getFabushiMonthly: vi.fn(), getFabushiPersonalStats: vi.fn(),
}))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    hasPermission: (code) => hoisted.permissions.includes('*') || hoisted.permissions.includes(code),
  }),
}))
vi.mock('@/utils/token', () => ({ getToken: vi.fn(() => 'token-for-test') }))
vi.mock('@/utils/download', () => ({ downloadFile: vi.fn() }))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import * as fabushiApiModule from '@/api/fabushi'
import { downloadFile as downloadFileMock } from '@/utils/download'
import StatsView from './stats.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const { getFabushiDashboard, getFabushiMonthly, getFabushiPersonalStats } = fabushiApi
const downloadFile = mocked(downloadFileMock)
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 渲染插槽; 按钮单独一个类名, 好在主视觉条里按文字找(点击会透传到根节点)
const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String, size: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" />',
}
const stubs = { 'el-button': BUTTON_STUB, 'el-date-picker': SLOT_STUB, 'el-pagination': PagerStub }

// ---- 夹具: 形状照 /tmp 的接口样本(成员榜一行含 songduCount / suishiCount / zaiduCount / activityPct, 给的数是自己编的) ----
const range = (count, make) => Array.from({ length: count }, (_, i) => make(i + 1))
// 按阅读(默认)排出来是 1 → 12(readCount 递减); 按发布排出来是 12 → 1(postCount 递增)
const member = (n) => ({
  userId: n, userName: `成员${n}`, deptName: `部门${n}`,
  readDays: 12 - n, readCount: 20 - n, postCount: n,
  songduCount: 1, suishiCount: 1, zaiduCount: 0, likesReceived: n, recitationsReceived: 0,
  activityPct: n * 8,
})
const dept = (n) => ({ deptId: n, deptName: `部门 ${n}`, memberCount: 10, readUsers: n, coveragePct: n * 10, posts: n, likes: n, comments: n, readDays: n })
const book = (n) => ({
  bookId: n, title: `书目 ${n}`, category: 'guoxue', categoryName: '国学建议书目', readUsers: n, readCount: 10 - n, latestReadAt: '2026-11-25T08:00:00',
})
const TREND = [
  { date: '2026-11-20', label: '11-20', count: 3 },
  { date: '2026-11-21', label: '11-21', count: 0 },
  { date: '2026-11-22', label: '11-22', count: 2 },
  { date: '2026-11-23', label: '11-23', count: 2 },
  { date: '2026-11-24', label: '11-24', count: 0 },
  { date: '2026-11-25', label: '11-25', count: 2 },
  { date: '2026-11-26', label: '11-26', count: 4 },
]
const CATEGORIES = [
  { categoryCode: 'guoxue', categoryName: '国学建议书目', readCount: 30 },
  { categoryCode: 'team', categoryName: '团队必读', readCount: 20 },
  { categoryCode: 'mgmt', categoryName: '管理类', readCount: 10 },
]
const dashboardData = (extra) => ({
  period: 'week', periodStart: '2026-11-23', memberCount: -1,
  todayReadUsers: 4, coveragePct: null,
  periodPosts: 5, periodLikes: 2, periodComments: 1,
  periodReadUsers: 3, periodCoveragePct: null, dailyGoalRate: null,
  ranking: range(12, member), deptProgress: range(17, dept), categoryReads: CATEGORIES, trend: TREND,
  bookRanking: range(9, book),
  ...extra,
})
const MONTHLY = {
  month: '2026-11', memberCount: -1, posts: 6, likes: 29, comments: 7, readCount: 7, readUsers: 3, activeUsers: 28, coveragePct: null,
  prev: { month: '2026-10', posts: 0, likes: 0, comments: 0, readUsers: 0, postsPct: null, likesPct: -20, commentsPct: null, readUsersPct: 50 },
}
const PERSONAL = {
  myPosts: 6, myComments: 6, myReadCount: 5, myLikesReceived: 29, myReadDays: 3, myStreakDays: 3, myTopCategory: '国学建议书目',
}

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.permissions = ['*']
  getFabushiDashboard.mockImplementation((period) => Promise.resolve({ data: dashboardData(period === 'month' ? { period: 'month', periodReadUsers: 9 } : {}) }))
  getFabushiMonthly.mockResolvedValue({ data: MONTHLY })
  getFabushiPersonalStats.mockResolvedValue({ data: PERSONAL })
})

async function mountQuietly() {
  const wrapper = mount(StatsView, {
    global: {
      stubs,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  return wrapper
}

const texts = (wrapper, selector) => wrapper.findAll(selector).map((node) => node.text())
const styleOf = (node) => node.attributes('style') || ''
const clickText = async (wrapper, selector, text) => {
  const node = wrapper.findAll(selector).find((item) => item.text() === text)
  await node.trigger('click')
  await flushPromises()
}
const pagers = (wrapper) => wrapper.findAllComponents(PagerStub)
const pagerTotals = (wrapper) => pagers(wrapper).map((pager) => pager.props('total'))
const pagerPages = (wrapper) => pagers(wrapper).map((pager) => pager.props('currentPage'))
// 一个节点下各个子区块的名字(只取 names 里认识的类名, 其余如 table-card / panel-pad 不算)
const blockNames = (node, names) => Array.from(node.element.children)
  .map((el) => el.className.split(' ').filter((name) => names.includes(name)).join(' '))
  .filter(Boolean)
const BLOCKS = ['stat-hero', 'kcards', 'dash-grid', 'book-card', 'sec-band', 'dept-card', 'monthly-card', 'personal-card', 'glossary']

describe('统计看板按 PC 原型 v8', () => {
  it('骨架：根节点是 .fx-page，没有旧的页头标题块；区块的先后跟原型走', async () => {
    const wrapper = await mountQuietly()
    expect(warnings, 'Vue 警告').toEqual([])
    const root = wrapper.find('.fx-page')
    expect(root.exists()).toBe(true)
    expect(root.classes()).toContain('fabushi-stats')
    expect(root.find('.page-head').exists()).toBe(false)
    expect(root.find('.stats-hero').exists()).toBe(false)
    expect(blockNames(root, BLOCKS)).toEqual(['stat-hero', 'kcards', 'dash-grid', 'book-card', 'sec-band', 'dept-card', 'monthly-card', 'personal-card', 'glossary'])
    // 成员榜在前，类别热度分布与每日达成趋势在它后面
    expect(blockNames(root.find('.dash-grid'), ['rank-card', 'dash-side'])).toEqual(['rank-card', 'dash-side'])
    expect(blockNames(root.find('.dash-side'), ['heat-card', 'trend-card'])).toEqual(['heat-card', 'trend-card'])
    // 原型里给评审看的标注不是界面文案
    expect(wrapper.text()).not.toMatch(/D\d{1,2}\b|演示|已上线|从本表下方/)
  })

  it('主视觉条：标题与范围章来自接口；本周 / 本月切换即查，并回到成员榜第一页', async () => {
    const wrapper = await mountQuietly()
    const hero = wrapper.find('.stat-hero')
    expect(hero.find('.sh-t').text()).toBe('法布施 · 统计看板')
    expect(hero.find('.sh-sub').text()).toContain('数据截至')
    // memberCount = -1(全集团, 接口不给人数): 范围章照实写
    expect(hero.find('.sh-chip').text()).toBe('全集团 · 全部')

    expect(texts(hero, '.seg-item')).toEqual(['本周', '本月'])
    expect(hero.find('.seg-item.active').text()).toBe('本周')
    vm(wrapper).rankPage = 2
    await clickText(wrapper, '.stat-hero .seg-item', '本月')
    expect(getFabushiDashboard).toHaveBeenLastCalledWith('month')
    expect(vm(wrapper).period).toBe('month')
    expect(vm(wrapper).rankPage).toBe(1)
    expect(wrapper.find('.stat-hero .seg-item.active').text()).toBe('本月')
    expect(wrapper.find('.rank-card .panel-title').text()).toContain('成员榜 · 本月')
  })

  it('范围章：接口给了可见成员人数时显示人数', async () => {
    getFabushiDashboard.mockResolvedValue({ data: dashboardData({ memberCount: 5 }) })
    const wrapper = await mountQuietly()
    expect(wrapper.find('.stat-hero .sh-chip').text()).toBe('可见成员 5 人')
    expect(wrapper.find('.rank-pager .info').text()).toContain('可见范围 5 人')
  })

  it('刷新与导出报表（CSV）都在：刷新重取数据，导出按当前周期；没有导出权限的人看不到导出', async () => {
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.stat-hero .btn-stub')).toEqual(['刷新', '导出报表（CSV）'])
    const before = getFabushiDashboard.mock.calls.length
    await clickText(wrapper, '.stat-hero .btn-stub', '刷新')
    expect(getFabushiDashboard.mock.calls.length).toBe(before + 1)

    await clickText(wrapper, '.stat-hero .btn-stub', '导出报表（CSV）')
    expect(downloadFile).toHaveBeenLastCalledWith('/api/fabushi/stats/export?period=week', {
      name: '法布施统计-week.csv',
      headers: { Authorization: 'Bearer token-for-test' },
    })
    await clickText(wrapper, '.stat-hero .seg-item', '本月')
    await clickText(wrapper, '.stat-hero .btn-stub', '导出报表（CSV）')
    expect(downloadFile).toHaveBeenLastCalledWith('/api/fabushi/stats/export?period=month', expect.objectContaining({ name: '法布施统计-month.csv' }))

    hoisted.permissions = ['fabushi:stats:view']
    const limited = await mountQuietly()
    expect(texts(limited, '.stat-hero .btn-stub')).toEqual(['刷新'])
    expect(texts(limited, '.monthly-card .btn-stub')).toEqual([])
  })

  it('五张数字卡的内容都来自接口；接口给不了的显示“—”，不编环比', async () => {
    const wrapper = await mountQuietly()
    const cards = wrapper.findAll('.kcards .kcard2')
    expect(cards).toHaveLength(5)
    for (const card of cards) {
      expect(card.find('.kicon svg').exists(), '图标').toBe(true)
      expect(card.find('.kv2').exists(), '大数字').toBe(true)
      expect(card.find('.kl2').exists(), '小字说明').toBe(true)
      expect(card.find('.ktrend').exists(), '右上角的章').toBe(true)
      expect(card.findAll('.spark i')).toHaveLength(7)
    }
    expect(texts(wrapper, '.kcard2 .kl2')).toEqual(['今日已读人数', '周期已读人数', '阅读覆盖率', '每日一读达成率', '本周发布内容'])
    // 阅读覆盖率(periodCoveragePct)与每日一读达成率(dailyGoalRate)接口给的是 null: 显示“—”
    expect(texts(wrapper, '.kcard2 .kv2')).toEqual(['4人', '3人', '—', '—', '5篇'])
    expect(texts(wrapper, '.kcard2 .ksub')).toEqual([
      '今日已读 / 全集团 —', '本周内有过阅读的成员', '= 周期已读人数 / 全集团', '提醒应读人日中实读占比', '木鱼 2 次 · 评论 1 条',
    ])
    // 右上角的章只有第一张是环比(今日已读人数比昨天, 由 trend 算出); 其余是周期名, 不编别的环比
    expect(texts(wrapper, '.kcard2 .ktrend')).toEqual(['↑ 100%', '本周', '本周', '本周', '本周'])
    expect(wrapper.findAll('.kcard2 .ktrend')[0].classes()).toContain('up')
    // 迷你柱图 = 近几日每日已读人数; 没人读的日子是灰线
    const sparks = wrapper.findAll('.kcard2')[0].findAll('.spark i')
    expect(sparks.map(styleOf)).toEqual([
      'height: 75%;', 'height: 8%;', 'height: 50%;', 'height: 50%;', 'height: 8%;', 'height: 50%;', 'height: 100%;',
    ])
    expect(sparks.map((bar) => bar.classes().includes('zero'))).toEqual([false, true, false, false, true, false, false])
  })

  it('数字卡：接口给了覆盖率 / 达成率就照实显示；环比下降、与昨天持平是另外两种章', async () => {
    const richer = dashboardData({
      memberCount: 5, coveragePct: 80, periodCoveragePct: 33.3, dailyGoalRate: 80,
      trend: TREND.map((item, i) => (i === 5 ? { ...item, count: 4 } : i === 6 ? { ...item, count: 1 } : item)),
    })
    // 周期名跟着接口回来的 period 走: 切到本月要给本月的应答
    getFabushiDashboard.mockImplementation((period) => Promise.resolve({ data: period === 'month' ? { ...richer, period: 'month' } : richer }))
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.kcard2 .kv2')).toEqual(['4人', '3人', '33.3%', '80%', '5篇'])
    expect(texts(wrapper, '.kcard2 .ksub')[0]).toBe('今日已读 / 可见成员 80%')
    expect(texts(wrapper, '.kcard2 .ksub')[2]).toBe('= 周期已读人数 / 可见成员')
    expect(texts(wrapper, '.kcard2 .ktrend')[0]).toBe('↓ 75%')
    expect(wrapper.findAll('.kcard2 .ktrend')[0].classes()).toContain('down')
    // 切到本月: 周期名跟着变
    await clickText(wrapper, '.stat-hero .seg-item', '本月')
    expect(texts(wrapper, '.kcard2 .kl2')[4]).toBe('本月发布内容')
    expect(texts(wrapper, '.kcard2 .ktrend').slice(1)).toEqual(['本月', '本月', '本月', '本月'])
  })

  it('数字卡：今天与昨天持平是中性的灰章，不算上涨', async () => {
    getFabushiDashboard.mockResolvedValue({ data: dashboardData({ trend: TREND.map((item) => ({ ...item, count: 1 })) }) })
    const wrapper = await mountQuietly()
    const chip = wrapper.findAll('.kcard2 .ktrend')[0]
    expect(chip.text()).toBe('0%')
    expect(chip.classes()).toContain('flat')
  })

  it('成员榜：奖牌名次按全榜名次；原有各列都在；活跃度条用排行项里的 activityPct', async () => {
    const wrapper = await mountQuietly()
    const table = wrapper.find('.rank-card table')
    expect(texts(table, 'th')).toEqual(['名次', '成员', '部门', '诵读', '随施', '再读', '已读天数', '已读人次', '发布', '获木鱼', '被复诵', '阅读占比', '活跃度'])

    // 第一页 10 行: 名次章 1 → 10, 奖牌只属全榜前三
    expect(texts(table, '.rk2')).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9', '10'])
    expect(table.findAll('.rk2').slice(0, 3).map((node) => node.classes())).toEqual([expect.arrayContaining(['gold']), expect.arrayContaining(['silver']), expect.arrayContaining(['bronze'])])
    expect(table.findAll('.rk2').slice(3).some((node) => ['gold', 'silver', 'bronze'].some((medal) => node.classes().includes(medal)))).toBe(false)
    // 第一行(按阅读排第一的成员)各列的数
    const firstRow = table.findAll('tbody tr')[0]
    expect(firstRow.find('.who b').text()).toBe('成员1')
    expect(firstRow.find('.dept').text()).toBe('部门1')
    expect(texts(firstRow, 'td.num').slice(0, 8)).toEqual(['1', '1', '0', '11', '19', '1', '1', '0'])
    // 阅读占比 = 本人已读人次 / 全榜最高值
    expect(firstRow.find('.metric-bar .pc').text()).toBe('100%')
    expect(styleOf(firstRow.find('.metric-bar .tr i'))).toContain('width: 100%')
    // 活跃度 = 接口给的 activityPct
    expect(table.findAll('.rb i').map(styleOf)).toEqual(range(10, (n) => `width: ${n * 8}%;`))

    // 第二页: 名次接着全榜数, 没有奖牌
    vm(wrapper).rankPage = 2
    await flushPromises()
    expect(texts(table, '.rk2')).toEqual(['11', '12'])
    expect(table.find('.rk2.gold, .rk2.silver, .rk2.bronze').exists()).toBe(false)
    expect(wrapper.find('.rank-pager .info').text()).toBe('共 12 人上榜（可见范围 全集团）')
    expect(texts(wrapper, '.rank-pager .pager-btn')).toEqual(['‹ 上一页', '1', '2', '下一页 ›'])
    expect(wrapper.findAll('.rank-pager .pager-size option').map((option) => option.text())).toEqual(['每页 10 人', '每页 20 人'])

    // 切到“按发布”: 回到第一页, 按发布数排
    await clickText(wrapper, '.rank-card .seg-item', '按发布')
    expect(vm(wrapper).rankSort).toBe('post')
    expect(vm(wrapper).rankPage).toBe(1)
    expect(texts(table, 'th')).toContain('发布占比')
    expect(table.findAll('tbody tr')[0].find('.who b').text()).toBe('成员12')
    expect(styleOf(table.find('.rb i'))).toContain('width: 96%')
  })

  it('成员榜：没有数据时给出空态，不出翻页条', async () => {
    getFabushiDashboard.mockResolvedValue({ data: dashboardData({ ranking: [], categoryReads: [], trend: [] }) })
    const wrapper = await mountQuietly()
    expect(wrapper.find('.rank-card .empty').text()).toBe('本周期暂无阅读 / 发布数据')
    expect(wrapper.find('.rank-pager').exists()).toBe(false)
    expect(wrapper.find('.heat-card .empty').text()).toBe('本周期暂无书目阅读')
    expect(wrapper.findAll('.chart .bar')).toHaveLength(0)
    expect(wrapper.findAll('.kcard2 .spark i')).toHaveLength(0)
  })

  it('类别热度分布与每日达成趋势：用接口的 categoryReads 与 trend；今天那根标出数值', async () => {
    const wrapper = await mountQuietly()
    const heat = wrapper.find('.heat-card')
    expect(heat.find('.panel-title').text()).toContain('类别热度分布')
    expect(texts(heat, '.hbar-lbl')).toEqual(['国学建议书目', '团队必读', '管理类'])
    expect(texts(heat, '.hbar-val')).toEqual(['30', '20', '10'])
    // 条长 = 该类别 / 最高的类别; 占比 = 该类别 / 合计
    expect(heat.findAll('.hbar-track i').map(styleOf)).toEqual(['width: 100%;', 'width: 67%;', 'width: 33%;'])
    expect(texts(heat, '.hbar-share')).toEqual(['50%', '33%', '17%'])
    expect(heat.findAll('.hbar-track i').map((node) => node.classes())).toEqual([expect.arrayContaining(['h0']), expect.arrayContaining(['h1']), expect.arrayContaining(['h2'])])

    const trend = wrapper.find('.trend-card')
    expect(trend.find('.panel-title').text()).toContain('每日达成趋势')
    const bars = trend.findAll('.chart .bar')
    expect(bars.map((bar) => bar.find('.v').text())).toEqual(['3', '0', '2', '2', '0', '2', '4'])
    expect(bars.map(styleOf)).toEqual(['height: 75%;', 'height: 0%;', 'height: 50%;', 'height: 50%;', 'height: 0%;', 'height: 50%;', 'height: 100%;'])
    // 最后一根是今天(黄色); 没人读的日子是空柱
    expect(bars[6].classes()).toContain('today')
    expect(bars.filter((bar) => bar.classes().includes('today'))).toHaveLength(1)
    expect(bars.filter((bar) => bar.classes().includes('zero')).map((bar) => bar.find('.v').text())).toEqual(['0', '0'])
    expect(texts(trend, '.chart-x span')).toEqual(['11-20', '11-21', '11-22', '11-23', '11-24', '11-25', '今日'])
  })

  it('书目阅读榜在前、部门进度在后：两个分页条都在各自的面板里，各绑各的', async () => {
    const wrapper = await mountQuietly()
    expect(texts(wrapper, '.book-card th')).toEqual(['书名', '类别', '已读人数', '已读人次', '阅读占比', '最新阅读'])
    expect(texts(wrapper, '.dept-card th')).toEqual(['部门', '成员数', '已读人数', '覆盖率', '发布', '木鱼', '评论', '已读天数'])
    // 9 本 → 2 页, 17 个部门 → 3 页: 分页条的总数写错成另一个列表的, 就会在这里现形
    expect(pagerTotals(wrapper)).toEqual([9, 17])
    expect(pagers(wrapper)[0].element.closest('.book-card')).not.toBeNull()
    expect(pagers(wrapper)[1].element.closest('.dept-card')).not.toBeNull()

    vm(wrapper).bookRankPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedBookRanking.map((item) => item.bookId)).toEqual([9])
    expect(pagerPages(wrapper)).toEqual([2, 1])
    vm(wrapper).deptPage = 2
    await flushPromises()
    expect(vm(wrapper).pagedDeptProgress.map((item) => item.deptId)).toEqual([9, 10, 11, 12, 13, 14, 15, 16])
    expect(pagerPages(wrapper)).toEqual([2, 2])
  })

  it('月度报告、我的法布施、口径说明都还在；月度导出按所选月份', async () => {
    const wrapper = await mountQuietly()
    const monthly = wrapper.find('.monthly-card')
    expect(monthly.findAll('.el-stub').filter((node) => !node.classes().includes('el-pager'))).toHaveLength(1)
    expect(monthly.text()).toContain('阅读组')
    expect(monthly.text()).toContain('互动组')
    // 环比有值才显示: readUsersPct = 50, likesPct = -20, 其余接口给 null
    expect(texts(monthly, '.delta')).toEqual(['▲ 50%', '▼ 20%'])
    await clickText(wrapper, '.monthly-card .btn-stub', '导出')
    const month = vm(wrapper).monthlyMonth
    expect(downloadFile).toHaveBeenLastCalledWith(`/api/fabushi/stats/monthly/export?month=${month}`, {
      name: `法布施月度报告-${month}.csv`,
      headers: { Authorization: 'Bearer token-for-test' },
    })

    expect(texts(wrapper, '.personal-grid b')).toEqual(['3', '3', '国学建议书目', '5', '6', '29'])
    await clickText(wrapper, '.personal-card .btn-stub', '进入我的觉醒')
    // 按钮写的是“进入我的觉醒”: 就去“我的觉醒”那一页(与认知广场右栏的同名按钮同一个去处)。
    // 此前这里跳的是书目浏览(11-23 的一次修复只改了地址、没改文案), 写着东、去了西。
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/shelf')

    expect(wrapper.findAll('.glossary .g-row').length).toBeGreaterThanOrEqual(10)
    expect(wrapper.find('.glossary').text()).toContain('活跃度')
  })

  it('月度报告默认看本机当下的月份（按本机日期算，不按 UTC：月初的深夜、月末的傍晚会差一个月）', async () => {
    // 本机 12-01 00:30：在 UTC 之后的时区（如东八区），它的 UTC 时间还是 11-30，按 UTC 算会错看成 11 月
    const firstNight = new Date(2026, 11, 1, 0, 30)
    // 在 UTC 之前的时区，换成本机 12-31 23:30（它的 UTC 时间已经是次年 1-01）
    const lastNight = new Date(2026, 11, 31, 23, 30)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(firstNight.toISOString().startsWith('2026-11') ? firstNight : lastNight)
    try {
      const wrapper = await mountQuietly()
      expect(vm(wrapper).monthlyMonth).toBe('2026-12')
      expect(getFabushiMonthly).toHaveBeenCalledWith('2026-12')
    } finally {
      vi.useRealTimers()
    }
  })

  it('图标不用表情符号；公共样式 fabushi-v8.css 有引入；el-icon 用显式引入的 ElIcon', () => {
    const src = readFileSync('src/views/fabushi/stats.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).not.toMatch(/🐟|👁|🎤|🎙|↻|⬇/)
    // paged-list-mount.spec.js 挂载本视图时没有 el-icon 的桩: 模板里不能写自动引入的 <el-icon>
    expect(src).toContain("import { ElIcon } from 'element-plus'")
    expect(src).not.toMatch(/<el-icon[\s>]/)
  })
})
