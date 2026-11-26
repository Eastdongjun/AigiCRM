import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】认知广场（feed.vue = 原型 P1 朋友圈式信息流 / P2 按书浏览）。
// 用户裁决“PC 端按最新的原型来做”。这里钉住四件事：
//   ① 骨架跟原型走：没有页头标题块，页面根节点是 .fx-page，四个类型页签带条数，三个发布入口在筛选条里；
//   ② “再读一本”页签就是按书浏览（左书目、右表格），页签文案不带条数；
//   ③ 卡片上的“⋯”是真的操作菜单（不做假按钮）：敲木鱼走详情页同一个接口，请求没回来之前不能重复点；
//      查看评论 / 我要复诵进详情时带上落点（detail.vue 按 ?focus= 处理）；
//   ④ 图标不用表情符号，公共样式 fabushi-v8.css 有引入。
// 口径与 paged-list-mount.spec.js 相同：真实视图组件挂载；接口、路由、状态仓库、消息是替身；Element Plus 组件用会渲染插槽的桩。
// 抓不到：真实布局与配色（靠截图和原型并排核对，见 rc-r6-20261124 的 PC 并排图）。

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
vi.mock('@/api/fabushi', () => ({
  getFabushiPosts: vi.fn(), getFabushiBooks: vi.fn(), toggleFabushiLike: vi.fn(),
  getFabushiAwakenStatus: vi.fn(), getFabushiSettings: vi.fn(),
}))
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/utils/token', () => ({
  refreshDownloadToken: vi.fn(() => Promise.resolve('token-for-test')),
  getDownloadToken: vi.fn(() => 'token-for-test'),
}))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    hasPermission: (code) => hoisted.permissions.includes('*') || hoisted.permissions.includes(code),
    realName: '测试员',
    userId: 7,
  }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))
vi.mock('@/components/fabushi/FabushiPublishDialog.vue', () => ({ default: { template: '<div class="publish-stub" />' } }))

import * as fabushiApiModule from '@/api/fabushi'
import { ElMessage } from 'element-plus'
import FeedView from './feed.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const { getFabushiPosts, getFabushiBooks, toggleFabushiLike, getFabushiAwakenStatus, getFabushiSettings } = fabushiApi
const elMessage = /** @type {{ warning: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessage))
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 渲染默认插槽(与分页那个挂载冒烟同一个做法); 按钮单独一个类名, 好在筛选条里按文字找
const SLOT_STUB = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" />',
}
// 表格与列的桩不渲染插槽: 列的插槽要靠真实的 el-table 传 row, 这里没有
const BLANK_STUB = { template: '<div class="el-stub" />' }
// 下拉的桩把组件上的监听原样接到包住触发元素的那一层（最宽松的情况：只要哪一层拦了冒泡都算过）
const DROPDOWN_STUB = { inheritAttrs: false, template: '<div class="dd-stub" v-bind="$attrs"><slot /></div>' }
const stubs = {
  'el-button': BUTTON_STUB, 'el-option': SLOT_STUB, 'el-input': SLOT_STUB, 'el-select': SLOT_STUB,
  'el-checkbox': SLOT_STUB, 'el-image': SLOT_STUB, 'el-empty': SLOT_STUB, 'el-icon': SLOT_STUB,
  'el-dropdown': DROPDOWN_STUB, 'el-dropdown-menu': SLOT_STUB, 'el-dropdown-item': SLOT_STUB,
  'el-table': BLANK_STUB, 'el-table-column': BLANK_STUB, 'el-pagination': PagerStub,
}

const post = (n, extra) => ({ id: n, userName: '同学', scopeType: 'suishi', content: `内容 ${n}`, likeCount: 1, commentCount: 0, recitationCount: 0, attachments: [], ...extra })
const book = (n, extra) => ({ id: n, title: `书目 ${n}`, memberCount: 3, postCount: 2, ...extra })

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.route = { params: {}, query: {} }
  hoisted.permissions = ['*']
  // 条数: 三类各发一次 size=1 的请求; 列表本身按当前页签返回
  getFabushiPosts.mockImplementation((params) => {
    const rows = params.scopeType ? [post(11), post(12)] : [post(11), post(12), post(13)]
    return Promise.resolve({ data: { records: rows, total: rows.length } })
  })
  getFabushiBooks.mockResolvedValue({ data: { records: [book(1), book(2)], total: 2 } })
  getFabushiAwakenStatus.mockResolvedValue({ data: { todayRecitations: 0, todayGifts: 1, currentBook: null } })
  getFabushiSettings.mockResolvedValue({ data: { reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00' } })
  toggleFabushiLike.mockResolvedValue({ data: { liked: true, likeCount: 4 } })
})

async function mountQuietly() {
  const wrapper = mount(FeedView, {
    global: {
      stubs,
      directives: { loading: {} },
      config: { warnHandler: (msg) => warnings.push(msg) },
    },
  })
  await flushPromises()
  return wrapper
}

describe('认知广场按 PC 原型 v8', () => {
  it('骨架：页面根节点是 .fx-page，没有页头标题块；四个类型页签带条数；三个发布入口在筛选条里', async () => {
    const wrapper = await mountQuietly()
    expect(warnings, 'Vue 警告').toEqual([])
    const root = wrapper.find('.fx-page')
    expect(root.exists()).toBe(true)
    expect(root.find('.page-head').exists()).toBe(false)
    // 条数：三类各 2 条相加，“全部”就是 6（每条内容只有一个类型）
    expect(root.findAll('.cat-tab').map((tab) => tab.text())).toEqual(['全部6', '每日诵读2', '每日随施2', '再读一本2'])
    expect(root.findAll('.moments-card')).toHaveLength(3)
    // 右栏三块（原型 P1）：我的觉醒 / 今日推荐书目 / 每日提醒
    expect(root.findAll('.side-title').map((t) => t.text())).toEqual(['我的觉醒 · 测试员（今日 1/3）', '今日推荐书目', '每日提醒 · 闹钟'])
    expect(root.findAll('.filter-card .btn-stub').map((b) => b.text())).toEqual(['重置', '＋每日诵读', '＋每日随施', '＋再读一本'])
  })

  it('没有发布权限的人看不到三个发布入口', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await mountQuietly()
    expect(wrapper.findAll('.filter-card .btn-stub').map((b) => b.text())).toEqual(['重置'])
  })

  it('进到“再读一本”页签就是按书浏览：页签文案不带条数，左栏书目、右栏表格', async () => {
    const wrapper = await mountQuietly()
    vm(wrapper).switchTab('zaidu')
    await flushPromises()
    expect(wrapper.findAll('.cat-tab').map((tab) => tab.text())).toEqual(['全部6', '每日诵读2', '每日随施2', '再读一本 · 按书浏览'])
    expect(wrapper.findAll('.moments-card')).toHaveLength(0)
    expect(wrapper.findAll('.sub-row')).toHaveLength(2)
    expect(wrapper.find('.sub-foot').text()).toContain('书名+作者+版本三级确定')
    expect(wrapper.find('.table-card').exists()).toBe(true)
    // 默认选中参与人数最多的那本书，右栏不空着
    expect(vm(wrapper).bookFilter).toBe(1)
    expect(getFabushiPosts).toHaveBeenLastCalledWith(expect.objectContaining({ scopeType: 'zaidu', bookId: 1 }))
  })

  it('带书目编号进来：落到这本书的按书浏览；带 publish=0（详情页“本书动态”）只看不弹发布器', async () => {
    hoisted.route = { params: {}, query: { scope: 'zaidu', bookId: '2', publish: '0' } }
    let wrapper = await mountQuietly()
    expect(warnings, 'Vue 警告').toEqual([])
    expect(vm(wrapper).active).toBe('zaidu')
    expect(vm(wrapper).bookFilter).toBe(2)
    expect(vm(wrapper).publishVisible).toBe(false)
    expect(getFabushiPosts).toHaveBeenLastCalledWith(expect.objectContaining({ scopeType: 'zaidu', bookId: 2 }))

    // 不带 publish=0：有发布权限的人直达发布器（觉醒三板块进来的原有行为），书已经预选好
    hoisted.route = { params: {}, query: { scope: 'zaidu', bookId: '2' } }
    wrapper = await mountQuietly()
    expect(vm(wrapper).publishVisible).toBe(true)
    expect(vm(wrapper).publishScope).toBe('zaidu')
    expect(vm(wrapper).presetBookId).toBe(2)
    expect(vm(wrapper).bookFilter).toBe(2)

    // 没有发布权限的人：只看，不弹
    hoisted.permissions = ['fabushi:view']
    wrapper = await mountQuietly()
    expect(vm(wrapper).publishVisible).toBe(false)
    expect(vm(wrapper).bookFilter).toBe(2)
  })

  it('带 scope 进来（不带书目编号）：切到那一类并按那一类重新取内容，有发布权限的直达发布器', async () => {
    hoisted.route = { params: {}, query: { scope: 'songdu' } }
    const wrapper = await mountQuietly()
    expect(vm(wrapper).active).toBe('songdu')
    // 此前切了页签却不重新取内容，页签下面摆的还是“全部”的列表
    expect(getFabushiPosts).toHaveBeenLastCalledWith(expect.objectContaining({ scopeType: 'songdu' }))
    expect(vm(wrapper).publishVisible).toBe(true)
  })

  it('不带参数进来：停在“全部”，不弹发布器；认不出的 scope 不理会', async () => {
    let wrapper = await mountQuietly()
    expect(vm(wrapper).active).toBe('all')
    expect(vm(wrapper).publishVisible).toBe(false)
    hoisted.route = { params: {}, query: { scope: 'nope', publish: '0' } }
    wrapper = await mountQuietly()
    expect(vm(wrapper).active).toBe('all')
    expect(vm(wrapper).publishVisible).toBe(false)
  })

  it('操作菜单“敲木鱼”：走详情页同一个接口；请求没回来之前不能重复点；回来后木鱼数与“我点过”跟着变', async () => {
    let resolveLike = /** @type {(value: unknown) => void} */ (() => {})
    toggleFabushiLike.mockImplementation(() => new Promise((resolve) => { resolveLike = resolve }))
    const wrapper = await mountQuietly()
    const target = vm(wrapper).posts[0]
    expect(target.likeCount).toBe(1)
    expect(target.likedByMe).toBeUndefined()

    vm(wrapper).onMore('like', target)
    await flushPromises()
    expect(toggleFabushiLike).toHaveBeenCalledTimes(1)
    expect(toggleFabushiLike).toHaveBeenCalledWith(11)
    vm(wrapper).onMore('like', target)
    await flushPromises()
    expect(toggleFabushiLike, '请求没回来之前不重复点').toHaveBeenCalledTimes(1)

    resolveLike({ data: { liked: true, likeCount: 4 } })
    await flushPromises()
    expect(target.likedByMe).toBe(true)
    expect(target.likeCount).toBe(4)
    expect(vm(wrapper).likingIds).toEqual([])
  })

  it('没有互动权限时点“敲木鱼”：不发请求，提示没有权限', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await mountQuietly()
    vm(wrapper).onMore('like', vm(wrapper).posts[0])
    await flushPromises()
    expect(toggleFabushiLike).not.toHaveBeenCalled()
    expect(elMessage.warning).toHaveBeenLastCalledWith('当前账号没有互动权限')
  })

  it('操作菜单“查看评论 / 我要复诵 / 打开详情”：进详情，前两个带上落点', async () => {
    const wrapper = await mountQuietly()
    vm(wrapper).onMore('comment', vm(wrapper).posts[0])
    expect(hoisted.push).toHaveBeenLastCalledWith({ path: '/fabushi/detail/11', query: { focus: 'comment' } })
    vm(wrapper).onMore('recite', vm(wrapper).posts[0])
    expect(hoisted.push).toHaveBeenLastCalledWith({ path: '/fabushi/detail/11', query: { focus: 'recite' } })
    vm(wrapper).onMore('detail', vm(wrapper).posts[0])
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/detail/11')
  })

  it('卡片元信息：随施报图片张数；诵读 / 再读没有关联书目时照实写“未关联书目”', async () => {
    const wrapper = await mountQuietly()
    const note = vm(wrapper).sourceNote
    expect(note(post(1, { scopeType: 'suishi', attachments: [{ fileType: 'image' }, { fileType: 'image' }] }))).toBe('图片 2 张')
    expect(note(post(2, { scopeType: 'suishi' }))).toBe('')
    expect(note(post(3, { scopeType: 'songdu', attachments: [{ fileType: 'audio' }] }))).toBe('语音 · 未关联书目')
    expect(note(post(4, { scopeType: 'zaidu', bookId: 1, attachments: [{ fileType: 'image' }, { fileType: 'audio' }] }))).toBe('图片+语音')
    expect(note(post(5, { scopeType: 'songdu' }))).toBe('文字 · 未关联书目')
  })

  it('点卡片上的“⋯”只打开操作菜单，不会连带打开详情页；点卡片其它地方才进详情', async () => {
    const wrapper = await mountQuietly()
    await wrapper.find('.moments-card .more').trigger('click')
    expect(hoisted.push, '点“⋯”不进详情').not.toHaveBeenCalled()
    await wrapper.find('.moments-card .mc-text').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/detail/11')
    // 真实的 el-dropdown 不把组件上的点击监听接到包住触发元素的那一层（挂载测试里的桩接得到，所以再钉住源码）：
    // 拦截冒泡必须写在触发元素自己身上，否则点“⋯”会连带触发整张卡片的“打开详情”（本机预览里实测过）
    const template = readFileSync('src/views/fabushi/feed.vue', 'utf-8').split('<script')[0]
    const trigger = template.match(/<span class="more"[^>]*>/)[0]
    expect(trigger).toContain('@click.stop')
  })

  it('图标不用表情符号；公共样式 fabushi-v8.css 有引入', () => {
    const src = readFileSync('src/views/fabushi/feed.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).not.toMatch(/🐟|🎤|🎙/)
  })
})
