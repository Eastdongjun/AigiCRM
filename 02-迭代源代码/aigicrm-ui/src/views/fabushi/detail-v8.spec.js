import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】内容详情（detail.vue = 原型 P3）。用户裁决“PC 端按最新的原型来做”。这里钉住六件事：
//   ① 骨架跟原型走：页面根节点是 .fx-page，上面是没有底色的返回条（带“书名 章节 · 标题”），下面是“正文卡 + 右栏”；
//   ② 正文卡里的顺序：类型标签 + 标题 + NEW → 作者行 →（本人的）修改 / 删除 → 正文 → 附件 → 操作行 → 复诵 → 评论 → 输入框；
//   ③ 原有的能操作的东西一个不少：返回、敲木鱼（小程序那只）、我要复诵、修改 / 删除（仅本人）、复诵与评论的翻页（复诵在前）、
//      发评论、在线阅读 / 去书目浏览、修改弹窗；
//   ④ 右栏只放真数据：作者统计只有“作者 = 当前登录人”时才去取（接口只给当前登录人的数字），别人发的内容不拿它去顶；
//   ⑤ 图标不用表情符号，公共样式 fabushi-v8.css 有引入；
//   ⑥ 从认知广场操作菜单进来时带的落点 ?focus=comment|recite。
// 口径与 paged-list-mount.spec.js 相同：真实视图组件挂载；接口、路由、状态仓库、消息是替身；Element Plus 组件用桩
// （ElIcon 是模板里显式引入的真实组件，不桩）。抓不到：真实布局与配色（靠截图和原型并排核对）。

const hoisted = vi.hoisted(() => ({
  route: { params: {}, query: {} },
  push: vi.fn(),
  back: vi.fn(),
  permissions: ['*'],
  userId: 7,
  deptName: '',
  officeName: '',
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...await importOriginal(),
  useRoute: () => hoisted.route,
  useRouter: () => ({ push: hoisted.push, back: hoisted.back }),
}))
vi.mock('@/api/fabushi', () => ({
  getFabushiPostDetail: vi.fn(), getFabushiComments: vi.fn(), addFabushiComment: vi.fn(), toggleFabushiLike: vi.fn(),
  markFabushiRead: vi.fn(), getFabushiBooks: vi.fn(), updateFabushiPost: vi.fn(), deleteFabushiPost: vi.fn(),
  getFabushiRecitations: vi.fn(), addFabushiRecitation: vi.fn(), getFabushiSettings: vi.fn(),
  getFabushiPersonalStats: vi.fn(), getMyFabushiBookmarks: vi.fn(),
}))
vi.mock('@/utils/token', () => ({
  refreshDownloadToken: vi.fn(() => Promise.resolve('token-for-test')),
  getDownloadToken: vi.fn(() => 'token-for-test'),
}))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    hasPermission: (code) => hoisted.permissions.includes('*') || hoisted.permissions.includes(code),
    userId: hoisted.userId,
    deptName: hoisted.deptName,
    officeName: hoisted.officeName,
  }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), alert: vi.fn(), prompt: vi.fn() },
}))

import * as fabushiApiModule from '@/api/fabushi'
import { ElMessage, ElMessageBox } from 'element-plus'
import DetailView from './detail.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const fabushiApi = /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const {
  getFabushiPostDetail, getFabushiComments, addFabushiComment, markFabushiRead, getFabushiBooks, updateFabushiPost,
  deleteFabushiPost, getFabushiSettings, getFabushiPersonalStats, getMyFabushiBookmarks,
} = fabushiApi
const elMessageBox = /** @type {{ confirm: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessageBox))
const elMessage = /** @type {{ warning: import('vitest').Mock }} */ (/** @type {unknown} */ (ElMessage))
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

// Element Plus 组件的桩: 按钮单独一个类名, 好在正文卡里按文字找; 输入框的桩渲染一个真的 <textarea>
// (要验证“落到评论输入框并把光标放进去”); 图片的桩渲染 <img>; 分页组件声明属性, 好读它绑的总数
const BUTTON_STUB = { template: '<button class="btn-stub"><slot /></button>' }
const DIALOG_STUB = { template: '<div class="el-stub el-dialog-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const INPUT_STUB = {
  props: { modelValue: String, placeholder: String },
  emits: ['update:modelValue'],
  template: `<div class="el-stub"><textarea class="el-input-stub" :value="modelValue" :placeholder="placeholder" @input="$emit('update:modelValue', $event.target.value)" /></div>`,
}
const IMAGE_STUB = {
  props: { src: String, alt: String, fit: String, previewSrcList: Array },
  template: '<img class="el-stub el-image-stub" :src="src" :alt="alt" />',
}
const PagerStub = {
  props: { currentPage: Number, pageSize: Number, total: Number, layout: String },
  emits: ['update:currentPage', 'current-change'],
  template: '<div class="el-stub el-pager" />',
}
const stubs = {
  'el-button': BUTTON_STUB, 'el-dialog': DIALOG_STUB, 'el-input': INPUT_STUB, 'el-image': IMAGE_STUB, 'el-pagination': PagerStub,
}

const range = (count, make) => Array.from({ length: count }, (_, i) => make(i + 1))
const comment = (n, extra) => ({ id: n, content: `评论 ${n}`, userName: '同学', createdAt: '2026-11-20T09:00:00', ...extra })
const recitation = (n, extra) => ({ id: n, audioFileId: 900 + n, durationSeconds: 12, userName: '同学', createdAt: '2026-11-20T09:30:00', ...extra })
const book = (n, extra) => ({
  id: n, title: `书目 ${n}`, author: `作者 ${n}`, publisher: `出版社 ${n}`, categoryName: '国学', status: 'approved', pages: 100 + n, ...extra,
})
const attachment = (n, fileType, extra) => ({ id: n, postId: 501, fileId: 80 + n, fileType, fileName: `附件-${n}`, ...extra })
const ownPost = (extra) => ({
  id: 501, title: '今日诵读', content: '第一行\n第二行', userId: 7, userName: '张三', scopeType: 'songdu',
  bookId: 1, bookTitle: '书目 1', sectionTitle: '第一章', createdAt: '2026-11-20T08:00:00', readByMe: false, ...extra,
})
const detail = (extra) => ({
  post: ownPost(), attachments: [], likeCount: 2, likedByMe: false, recitationCount: 0, recitations: [], ...extra,
})
const fileUrl = (fileId) => `/api/file/download/${fileId}?token=token-for-test`

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  hoisted.route = { params: { id: '501' }, query: {} }
  hoisted.permissions = ['*']
  hoisted.userId = 7
  hoisted.deptName = ''
  hoisted.officeName = ''
  getFabushiPostDetail.mockResolvedValue({ data: detail() })
  getFabushiComments.mockResolvedValue({ data: [] })
  getFabushiBooks.mockResolvedValue({ data: { records: [book(1)], total: 1 } })
  getFabushiSettings.mockResolvedValue({ data: { soundEnabled: 1 } })
  markFabushiRead.mockResolvedValue({ data: true })
  getFabushiPersonalStats.mockResolvedValue({ data: { monthSongdu: 12, monthSuishi: 14, monthZaidu: 2, myLikesReceived: 47 } })
  getMyFabushiBookmarks.mockResolvedValue({ data: range(3, (n) => ({ id: n, bookId: n })) })
})
afterEach(() => {
  // 浏览器历史里“上一页”的记号是各条测试自己设的，不让它带到下一条
  window.history.replaceState(null, '')
})

async function openDetail(query = {}, attach = false) {
  hoisted.route = { params: { id: '501' }, query }
  const wrapper = mount(DetailView, {
    attachTo: attach ? document.body : undefined,
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
  // 挂载全程一条 Vue 警告都没有: 缺导入会直接抛错; 模板引用了不存在的名字、组件解析不了, 都会以警告的形式出现
  expect(warnings, `${label}: Vue 警告`).toEqual([])
}

/** 某个节点直接子节点的类名（按顺序）。用 Array.from 而不是展开：类型库里 HTMLCollection 没有声明成可迭代 */
const childClasses = (wrapper) => Array.from(wrapper.element.children).map((node) => /** @type {any} */ (node).className)
/** 这些类名在子节点里各排第几；认不出的是 -1 */
const orderOf = (classes, ...wanted) => wanted.map((name) => classes.findIndex((item) => item.split(' ').includes(name)))
const sideCards = (wrapper) => wrapper.findAll('.side-col .side-card')
const sideRowTexts = (wrapper) => wrapper.findAll('.side-row').map((row) => row.text())
const pagerTotals = (wrapper) => wrapper.findAllComponents(PagerStub).map((pager) => pager.props('total'))

describe('内容详情按 PC 原型 v8：骨架与返回条', () => {
  it('页面根节点是 .fx-page（保留 fabushi-detail 类，截图工具靠它找页面）：返回条在上，“正文卡 + 右栏”在下', async () => {
    const wrapper = await openDetail()
    expectQuiet('骨架')
    const root = wrapper.find('.fx-page.fabushi-detail')
    expect(root.exists()).toBe(true)
    // 根节点下：返回条、正文布局，最后是修改弹窗（桩始终渲染）
    expect(childClasses(root).slice(0, 2)).toEqual(['back-bar', 'dtl-layout'])
    expect(childClasses(root)[2]).toContain('el-dialog')
    const layout = root.find('.dtl-layout')
    expect(childClasses(layout)).toEqual(['art-card', 'side-col'])
    // 右栏是原型的那套通用卡片（共享样式 .side-col / .side-card / .side-title / .side-row）
    expect(layout.find('.side-col .side-card').exists()).toBe(true)
  })

  it('返回条：一行文字，没有底色；“返回认知广场 ·《书名》章节 · 标题”；点它回上一页，没有上一页就去认知广场', async () => {
    const wrapper = await openDetail()
    expectQuiet('返回条')
    const bar = wrapper.find('.back-bar')
    expect(bar.exists()).toBe(true)
    // 不再是原来那个按钮（“← 返回认知广场”）
    expect(wrapper.find('.back').exists()).toBe(false)
    expect(bar.text()).toContain('返回认知广场')
    expect(bar.find('.crumb2').text()).toBe('· 《书目 1》第一章 · 今日诵读')
    expect(bar.attributes('aria-label')).toBe('返回认知广场')
    expect(bar.element.tagName).toBe('BUTTON')
    // 从认知广场点进来的：历史里有上一页，原路退回（页签、筛选都还在）
    window.history.replaceState({ back: '/fabushi/feed?scope=songdu' }, '')
    await bar.trigger('click')
    expect(hoisted.back).toHaveBeenCalledTimes(1)
    expect(hoisted.push).not.toHaveBeenCalled()
    // 收藏、分享链接直接打开的：没有上一页可退（退回去只是空白页），就直接去认知广场
    window.history.replaceState(null, '')
    await bar.trigger('click')
    expect(hoisted.back).toHaveBeenCalledTimes(1)
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/feed')
  })

  it('返回条后半段按实际情况：没有书只到标题；没有标题只到书；都没有就不显示', async () => {
    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ bookId: null, bookTitle: null, sectionTitle: null }) }) })
    let wrapper = await openDetail()
    expect(wrapper.find('.back-bar .crumb2').text()).toBe('· 今日诵读')

    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ title: null }) }) })
    wrapper = await openDetail()
    expect(wrapper.find('.back-bar .crumb2').text()).toBe('· 《书目 1》第一章')

    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ title: null, bookId: null, bookTitle: null, sectionTitle: null }) }) })
    wrapper = await openDetail()
    expect(wrapper.find('.back-bar').text()).toBe('返回认知广场')
    expect(wrapper.find('.back-bar .crumb2').exists()).toBe(false)
  })

  it('内容自己没有标题、但有书和章节时：大标题用“《书名》章节”，返回条不把它再写一遍；书和标题都没有才是“无标题”', async () => {
    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ title: null }) }) })
    let wrapper = await openDetail()
    expect(wrapper.find('.art-heading').text()).toBe('《书目 1》第一章')
    expect(wrapper.find('.back-bar .crumb2').text()).toBe('· 《书目 1》第一章')

    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ title: null, bookId: null, bookTitle: null, sectionTitle: null }) }) })
    wrapper = await openDetail()
    expect(wrapper.find('.art-heading').text()).toBe('无标题')
  })
})

describe('内容详情按 PC 原型 v8：正文卡的上半部分', () => {
  it('标题行：类型标签 + 标题 + NEW；NEW 只在未读且 30 天内才挂', async () => {
    // NEW 看“离现在几天”：把“现在”定在 2026-11-26 09:00(本机时间), 不跟着门禁哪天跑、跑到几点变。只换 Date, 定时器照旧
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 10, 26, 9, 0, 0))
    try {
      markFabushiRead.mockRejectedValue(new Error('记已读失败'))
      let wrapper = await openDetail()
      expectQuiet('标题行')
      const title = wrapper.find('.art-title')
      expect(childClasses(title)).toEqual(['tag songdu', 'art-heading', 'badge-new'])
      expect(title.find('.tag').text()).toBe('每日诵读')
      expect(title.find('.art-heading').text()).toBe('今日诵读')
      expect(title.find('.badge-new').text()).toBe('NEW')

      // 打开详情页会记已读：记完之后 NEW 摘掉
      markFabushiRead.mockResolvedValue({ data: true })
      wrapper = await openDetail()
      expect(wrapper.find('.art-title .badge-new').exists()).toBe(false)
      // 没读、但已经超过 30 天的内容不挂 NEW（夹具里的内容发于 2026-11-20，到“现在”是第 6 天）
      getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ createdAt: '2026-10-26T09:00:00' }) }) })
      wrapper = await openDetail()
      expect(wrapper.find('.art-title .badge-new').exists()).toBe(false)
      expect(wrapper.find('.art-title .art-heading').text()).toBe('今日诵读')
      // 三种内容类型各自的标签（类名照原型：songdu / suishi / zaidu）
      for (const [scopeType, label] of [['suishi', '每日随施'], ['zaidu', '再读一本']]) {
        getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ scopeType }) }) })
        wrapper = await openDetail()
        expect(wrapper.find('.art-title .tag').classes()).toContain(scopeType)
        expect(wrapper.find('.art-title .tag').text()).toBe(label)
      }
    } finally {
      vi.useRealTimers()
    }
  })

  it('作者行：圆形头像字 + 姓名 +（本人时）“作者 = 当前登录人”+ 时间 +《书名》… › 章节 + 阅读状态', async () => {
    const wrapper = await openDetail()
    expectQuiet('作者行')
    const meta = wrapper.find('.art-meta')
    expect(meta.find('.who .mini-ava').text()).toBe('张')
    expect(meta.find('.who').text()).toBe('张张三（作者 = 当前登录人）')
    expect(meta.text()).toContain('2026-11-20 08:00')
    expect(meta.text()).toContain('《书目 1》作者 1 · 出版社 1 › 第一章')
    expect(meta.find('.art-read').text()).toBe('阅读状态：已读')
  })

  it('别人发的内容：作者行不写“当前登录人”', async () => {
    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ userId: 9, userName: '李四' }) }) })
    const wrapper = await openDetail()
    expectQuiet('作者行（别人）')
    expect(wrapper.find('.art-meta .who').text()).toBe('李李四')
    expect(wrapper.text()).not.toContain('当前登录人')
    expect(wrapper.text()).not.toContain('（本人）')
  })

  it('书名那一项：书目表里查不到这本书时，只写内容自己带的书名和章节', async () => {
    getFabushiPostDetail.mockResolvedValue({ data: detail() })
    getFabushiBooks.mockResolvedValue({ data: { records: [], total: 0 } })
    const wrapper = await openDetail()
    expect(wrapper.find('.art-meta').text()).toContain('《书目 1》 › 第一章')
  })

  it('修改 / 删除：只有本人看得到，放在作者行下面那一行；点修改开弹窗，点删除先二次确认', async () => {
    const wrapper = await openDetail()
    expectQuiet('修改 / 删除')
    const acts = wrapper.find('.art-card .owner-acts')
    expect(acts.exists()).toBe(true)
    expect(acts.findAll('.btn-stub').map((b) => b.text())).toEqual(['修改', '删除'])
    // 位置：作者行与正文之间（原来放在操作行里）
    expect(childClasses(wrapper.find('.art-card'))).toEqual([
      'art-title', 'art-meta', 'art-acts owner-acts', 'art-body', 'art-acts main-acts', 'cmt-list', 'cmt-input',
    ])
    // 原型里给评审看的标注（权限码、“仅作者可见”）不做成界面文案
    expect(wrapper.text()).not.toContain('仅作者可见')
    expect(wrapper.text()).not.toContain('fabushi:create')

    await acts.findAll('.btn-stub')[0].trigger('click')
    expect(vm(wrapper).editVisible).toBe(true)
    // 删除先二次确认：在确认框里点了取消，不删
    elMessageBox.confirm.mockRejectedValue('cancel')
    await acts.findAll('.btn-stub')[1].trigger('click')
    await flushPromises()
    expect(elMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(deleteFabushiPost).not.toHaveBeenCalled()
    // 确认通过才真删，删完回上一页
    elMessageBox.confirm.mockResolvedValue('confirm')
    window.history.replaceState({ back: '/fabushi/feed' }, '')
    await acts.findAll('.btn-stub')[1].trigger('click')
    await flushPromises()
    expect(deleteFabushiPost).toHaveBeenCalledWith('501')
    expect(hoisted.back).toHaveBeenCalledTimes(1)
  })

  it('不是本人：没有修改 / 删除', async () => {
    hoisted.userId = 8
    const wrapper = await openDetail()
    expectQuiet('不是本人')
    expect(wrapper.find('.art-card').exists()).toBe(true)
    expect(wrapper.find('.owner-acts').exists()).toBe(false)
    expect(wrapper.findAll('.art-card .btn-stub').map((b) => b.text()).filter((t) => t === '修改' || t === '删除')).toEqual([])
  })

  it('正文：按行分段（原型的正文是带首行缩进的段落）', async () => {
    const wrapper = await openDetail()
    expect(wrapper.find('.art-body').findAll('p').map((p) => p.text())).toEqual(['第一行', '第二行'])
  })

  it('附件：图片是一排缩略图、点击放大；语音 / 视频保留播放器；附件里没有标注信息，就不放“已标注”的章', async () => {
    getFabushiPostDetail.mockResolvedValue({
      data: detail({ attachments: [
        attachment(1, 'image'), attachment(2, 'image'), attachment(3, 'audio'), attachment(4, 'video'), attachment(5, 'file'),
      ] }),
    })
    const wrapper = await openDetail()
    expectQuiet('附件')
    const thumbs = wrapper.findAll('.art-imgs .art-img')
    expect(thumbs).toHaveLength(2)
    const pics = wrapper.findAllComponents(IMAGE_STUB)
    expect(pics.map((pic) => pic.props('src'))).toEqual([fileUrl(81), fileUrl(82)])
    // 点开任意一张，都能在同一组里翻看全部图片
    for (const pic of pics) expect(pic.props('previewSrcList')).toEqual([fileUrl(81), fileUrl(82)])
    expect(wrapper.find('.art-imgs').text()).toContain('图片 2 张 · 点击放大查看')
    // 语音、视频还是能播的播放器；其它文件只列文件名
    const audio = wrapper.find('.art-media audio[controls]')
    expect(audio.exists()).toBe(true)
    expect(audio.attributes('src')).toBe(fileUrl(83))
    expect(wrapper.find('.art-media video[controls]').attributes('src')).toBe(fileUrl(84))
    expect(wrapper.find('.art-media').text()).toContain('附件-5')
    expect(wrapper.text()).not.toContain('已标注')
    // 顺序：正文 → 图片 → 其它附件 → 操作行
    expect(orderOf(childClasses(wrapper.find('.art-card')), 'art-body', 'art-imgs', 'art-media', 'main-acts')).toEqual([3, 4, 5, 6])
    // 没有附件时不留空壳
    getFabushiPostDetail.mockResolvedValue({ data: detail() })
    const plain = await openDetail()
    expect(plain.find('.art-imgs').exists()).toBe(false)
    expect(plain.find('.art-media').exists()).toBe(false)
  })

  it('操作行：木鱼（小程序那只）· 评论数 · 我要复诵，按这个顺序；图标不用表情符号', async () => {
    getFabushiComments.mockResolvedValue({ data: range(3, (n) => comment(n)) })
    getFabushiPostDetail.mockResolvedValue({ data: detail({ likeCount: 21, recitationCount: 2 }) })
    const wrapper = await openDetail()
    expectQuiet('操作行')
    const row = wrapper.find('.main-acts')
    // 按下标取(不对 HTMLCollection 做解构: 类型库里它没有声明成可迭代)
    const names = row.element.children[0]
    const count = row.element.children[1]
    const recite = row.element.children[2]
    expect(/** @type {any} */ (names).className).toContain('muyu')
    expect(/** @type {any} */ (count).className).toContain('art-count')
    expect(/** @type {any} */ (recite).className).toContain('recite-btn')

    // 木鱼：数字在上、“木鱼”小字在下（原型 .muyu-meta 的摆法）；点过的数字变主色
    const muyu = wrapper.find('button.muyu')
    expect(muyu.find('.muyu-meta b').text()).toBe('21')
    expect(muyu.find('.muyu-meta span').text()).toContain('木鱼')
    expect(muyu.classes()).not.toContain('on')
    getFabushiPostDetail.mockResolvedValue({ data: detail({ likeCount: 21, likedByMe: true }) })
    const liked = await openDetail()
    expect(liked.find('button.muyu').classes()).toContain('on')

    expect(row.find('.art-count').text()).toBe('评论 3')
    expect(row.find('.recite-btn').text()).toBe('我要复诵 · 已被复诵 2 次')
    expect(wrapper.text()).not.toMatch(/🎤|🎙|⏹|⏺|🐟/)
    // 录音中按钮换成“停止并上传”（原来的 ⏹ 换成图标）
    vm(wrapper).reciting = true
    await flushPromises()
    expect(wrapper.find('.recite-btn').text()).toBe('停止并上传 · 已被复诵 2 次')
    expect(wrapper.find('.recite-btn').classes()).toContain('is-reciting')
    vm(wrapper).reciting = false
    await flushPromises()
    expect(wrapper.find('.recite-btn').classes()).not.toContain('is-reciting')
    // 点“我要复诵”走原来的入口（录音环境的检查在 paged-list-mount.spec.js 里；测试环境录不了音，走到原来那句提示）
    await wrapper.find('.recite-btn').trigger('click')
    await flushPromises()
    expect(elMessage.warning).toHaveBeenLastCalledWith(expect.stringContaining('微信小程序端复诵'))
  })
})

describe('内容详情按 PC 原型 v8：复诵、评论、输入框', () => {
  it('复诵区：标题照原型；每条是波形 + 姓名 + 时长 · 时间，右侧是能播的播放器', async () => {
    getFabushiPostDetail.mockResolvedValue({
      data: detail({ recitationCount: 7, recitations: range(7, (n) => recitation(n)) }),
    })
    const wrapper = await openDetail()
    expectQuiet('复诵区')
    const sec = wrapper.find('.recite-sec')
    expect(sec.find('.recite-title').text()).toBe('复诵（他人对本段也进行朗读/背诵，不必自建诵读）')
    const rows = sec.findAll('.recite-row')
    expect(rows).toHaveLength(5)
    expect(rows[0].findAll('.recite-wave i')).toHaveLength(7)
    expect(rows[0].find('.recite-name').text()).toBe('同学')
    expect(rows[0].find('.recite-sub').text()).toBe('复诵 12 秒 · 2026-11-20 09:30')
    // 播放器靠右，在最后
    const audio = rows[0].find('audio.recite-audio[controls]')
    expect(audio.exists()).toBe(true)
    expect(audio.attributes('src')).toBe(fileUrl(901))
    expect(rows[0].element.lastElementChild).toBe(audio.element)
    // 没有时长就只写时间；没有复诵就不出这一块
    getFabushiPostDetail.mockResolvedValue({
      data: detail({ recitationCount: 1, recitations: [recitation(1, { durationSeconds: null })] }),
    })
    const one = await openDetail()
    expect(one.find('.recite-sub').text()).toBe('2026-11-20 09:30')
    getFabushiPostDetail.mockResolvedValue({ data: detail() })
    expect((await openDetail()).find('.recite-sec').exists()).toBe(false)
  })

  it('评论：头像字 + 姓名 + 时间 + 内容；没有评论时照实说“还没有评论”', async () => {
    const wrapper = await openDetail()
    expectQuiet('评论（空）')
    expect(wrapper.find('.cmt-list .comment-empty').text()).toBe('还没有评论')
    expect(wrapper.findAll('.cmt')).toHaveLength(0)

    getFabushiComments.mockResolvedValue({
      data: [comment(1, { userName: '陈慧', content: '“不争”这段重点。' }), comment(2, { userName: null })],
    })
    const two = await openDetail()
    const items = two.findAll('.cmt')
    expect(items).toHaveLength(2)
    expect(items[0].find('.cmt-ava').text()).toBe('陈')
    expect(items[0].find('.cmt-name').text()).toContain('陈慧')
    expect(items[0].find('.cmt-time').text()).toBe('2026-11-20 09:00')
    expect(items[0].find('.cmt-text').text()).toBe('“不争”这段重点。')
    expect(items[1].find('.cmt-ava').text()).toBe('同')
    expect(two.find('.cmt-list .comment-empty').exists()).toBe(false)
  })

  it('分页条：复诵的在评论的之前；顺序是 复诵区 → 复诵翻页 → 评论 → 评论翻页 → 输入框', async () => {
    getFabushiPostDetail.mockResolvedValue({
      data: detail({ recitationCount: 7, recitations: range(7, (n) => recitation(n)) }),
    })
    getFabushiComments.mockResolvedValue({ data: range(12, (n) => comment(n)) })
    const wrapper = await openDetail()
    expectQuiet('分页条')
    expect(pagerTotals(wrapper)).toEqual([7, 12])
    // 正文卡从操作行起：操作行 → 复诵区 → 复诵翻页 → 评论 → 评论翻页 → 输入框
    expect(childClasses(wrapper.find('.art-card')).slice(4)).toEqual([
      'art-acts main-acts', 'recite-sec', 'inline-pager', 'cmt-list', 'inline-pager', 'cmt-input',
    ])
  })

  it('底部输入框一行 + 右侧“发布评论”：填了再发，发完清空并重新拉评论', async () => {
    getFabushiComments.mockResolvedValue({ data: [] })
    addFabushiComment.mockResolvedValue({ data: true })
    const wrapper = await openDetail()
    expectQuiet('输入框')
    const form = wrapper.find('.cmt-input')
    expect(form.exists()).toBe(true)
    expect(form.find('textarea').attributes('placeholder')).toBe('写评论…')
    const submit = form.find('.btn-stub')
    expect(submit.text()).toBe('发布评论')
    expect(form.element.lastElementChild).toBe(submit.element)
    vm(wrapper).commentText = '  写得好  '
    await flushPromises()
    await submit.trigger('click')
    await flushPromises()
    expect(addFabushiComment).toHaveBeenCalledWith('501', '写得好')
    expect(vm(wrapper).commentText).toBe('')
    expect(getFabushiComments).toHaveBeenCalledTimes(2)
  })

  it('没有评论权限的人：没有输入框，照实提示', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await openDetail()
    expectQuiet('没有评论权限')
    expect(wrapper.find('.cmt-list').exists()).toBe(true)
    expect(wrapper.find('.cmt-input').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前账号没有评论权限')
  })
})

describe('内容详情按 PC 原型 v8：右栏', () => {
  it('三块卡：作者 / 关联书目 / 阅读状态；没有关联书目时不出中间那块', async () => {
    let wrapper = await openDetail()
    expectQuiet('右栏')
    expect(wrapper.findAll('.side-col .side-title').map((t) => t.text())).toEqual(['作者', '关联书目', '阅读状态'])
    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ bookId: null, bookTitle: null, sectionTitle: null }) }) })
    wrapper = await openDetail()
    expect(wrapper.findAll('.side-col .side-title').map((t) => t.text())).toEqual(['作者', '阅读状态'])
  })

  it('作者卡（作者 = 当前登录人）：头像、姓名、身份说明；下面是本月诵读 / 随施 / 再读、获木鱼、我的书摘', async () => {
    hoisted.deptName = '销售一部'
    hoisted.officeName = '销售员'
    const wrapper = await openDetail()
    expectQuiet('作者卡（本人）')
    const card = sideCards(wrapper)[0]
    expect(card.find('.avatar').text()).toBe('张')
    expect(card.find('.author-name').text()).toBe('张三')
    // “（本人）”前面是不换行符 U+2060：部门名很长要折行时，不让它被单独挤到最后一行
    expect(card.find('.author-sub').text()).toBe('销售一部 · 销售员\u2060（本人）')
    expect(sideRowTexts(card)).toEqual(['本月诵读 / 随施 / 再读12 / 14 / 2', '获木鱼47', '我的书摘 3 条 ›'])
    // 个人统计接口只给“当前登录人”的数字：这里作者是本人，才去取
    expect(getFabushiPersonalStats).toHaveBeenCalledTimes(1)
    expect(getMyFabushiBookmarks).toHaveBeenCalledTimes(1)
    // “被复诵”这个数接口里没有：不放，不编
    expect(card.text()).not.toContain('被复诵')
    await card.find('.hl-link').trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/highlights')
  })

  it('作者卡：本人但没填部门 / 岗位时，身份说明退回“法布施内容发布人（本人）”；部门与岗位是同一串时只写一遍', async () => {
    let wrapper = await openDetail()
    expect(sideCards(wrapper)[0].find('.author-sub').text()).toBe('法布施内容发布人\u2060（本人）')
    hoisted.deptName = '某某股份有限公司'
    hoisted.officeName = '某某股份有限公司'
    wrapper = await openDetail()
    expect(sideCards(wrapper)[0].find('.author-sub').text()).toBe('某某股份有限公司\u2060（本人）')
  })

  it('作者卡（别人发的内容）：只有头像、姓名、身份说明；不去取当前登录人的统计来顶', async () => {
    getFabushiPostDetail.mockResolvedValue({ data: detail({ post: ownPost({ userId: 9, userName: '李四' }) }) })
    const wrapper = await openDetail()
    expectQuiet('作者卡（别人）')
    const card = sideCards(wrapper)[0]
    expect(card.find('.author-name').text()).toBe('李四')
    expect(card.find('.author-sub').text()).toBe('法布施内容发布人')
    expect(card.findAll('.side-row')).toHaveLength(0)
    expect(getFabushiPersonalStats).not.toHaveBeenCalled()
    expect(getMyFabushiBookmarks).not.toHaveBeenCalled()
  })

  it('作者卡：统计接口失败或应答不是对象 → 那几行不放；书摘条数取不到 → 那一行不放', async () => {
    getFabushiPersonalStats.mockRejectedValue(new Error('统计失败'))
    let wrapper = await openDetail()
    expectQuiet('统计失败')
    let card = sideCards(wrapper)[0]
    expect(card.text()).not.toContain('本月诵读')
    expect(card.text()).not.toContain('获木鱼')
    expect(card.find('.hl-link').text()).toBe('我的书摘 3 条 ›')

    // 应答是数组 / 空的：当没有数据
    getFabushiPersonalStats.mockResolvedValue({ data: [] })
    wrapper = await openDetail()
    expect(sideCards(wrapper)[0].text()).not.toContain('本月诵读')

    getFabushiPersonalStats.mockResolvedValue({ data: { monthSongdu: 1, monthSuishi: 0, monthZaidu: 0, myLikesReceived: 0 } })
    getMyFabushiBookmarks.mockRejectedValue(new Error('书摘失败'))
    wrapper = await openDetail()
    card = sideCards(wrapper)[0]
    expect(sideRowTexts(card)).toEqual(['本月诵读 / 随施 / 再读1 / 0 / 0', '获木鱼0'])
    expect(wrapper.findAll('.side-col .btn-stub').map((b) => b.text()).filter((t) => t.includes('我的书摘'))).toEqual([])
  })

  it('关联书目：书脊 +《书名》+ 作者 · 出版社 · 页数 + 电子书标签 + “另有 N 个版本”（同名书的条数）+ 在线阅读 / 本书动态', async () => {
    getFabushiBooks.mockResolvedValue({ data: { records: [book(1, { pdfFileId: 77 }), book(9, { title: '书目 1' })], total: 2 } })
    const wrapper = await openDetail()
    expectQuiet('关联书目')
    const card = sideCards(wrapper)[1]
    expect(card.find('.book-spine').text()).toBe('书目 1')
    expect(card.find('.rec-title').text()).toBe('《书目 1》')
    expect(card.findAll('.rec-meta').map((m) => m.text())).toEqual(['作者 1 · 出版社 1 · 101 页', '电子书另有 1 个版本'])
    expect(card.find('.rec-tags .tag').classes()).toContain('blue')
    expect(card.find('.rec-tags .tag').text()).toBe('电子书')
    const buttons = card.findAll('.rec-acts .btn-stub')
    expect(buttons.map((b) => b.text())).toEqual(['在线阅读', '本书动态'])
    await buttons[0].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/read/1')
    // “本书动态”去认知广场的按书浏览；带 publish=0 = 只看这本书的内容，不直达发布器
    //（认知广场对带 scope / bookId 的地址默认“直达发布器”，不加这个参数，有发布权限的人一点就弹出“发布 · 再读一本”）
    await buttons[1].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith({ path: '/fabushi/feed', query: { scope: 'zaidu', bookId: 1, publish: '0' } })
  })

  it('关联书目：没有电子书 → 不给“在线阅读”，原来的“去书目浏览”还在；没有别的版本就不写“另有”；作者 出版社 页数全缺时退回分类名', async () => {
    getFabushiBooks.mockResolvedValue({ data: { records: [book(1, { pdfFileId: null, author: null, publisher: null, pages: null })], total: 1 } })
    const wrapper = await openDetail()
    expectQuiet('关联书目（没有电子书）')
    const card = sideCards(wrapper)[1]
    expect(card.find('.rec-meta').text()).toBe('国学')
    expect(card.find('.tag').exists()).toBe(false)
    expect(card.text()).not.toContain('另有')
    const buttons = card.findAll('.rec-acts .btn-stub')
    expect(buttons.map((b) => b.text())).toEqual(['去书目浏览', '本书动态'])
    await buttons[0].trigger('click')
    expect(hoisted.push).toHaveBeenLastCalledWith('/fabushi/library')
  })

  it('关联书目：书目列表没取全（超过一页）时数不准，就不写“另有 N 个版本”', async () => {
    getFabushiBooks.mockResolvedValue({ data: { records: [book(1), book(9, { title: '书目 1' })], total: 300 } })
    const wrapper = await openDetail()
    expect(sideCards(wrapper)[1].text()).not.toContain('另有')
  })

  it('阅读状态：阅读状态 + 木鱼 / 评论 / 复诵三个数（一行）+ 一句说明', async () => {
    getFabushiComments.mockResolvedValue({ data: range(3, (n) => comment(n)) })
    getFabushiPostDetail.mockResolvedValue({ data: detail({ likeCount: 21, recitationCount: 2 }) })
    let wrapper = await openDetail()
    expectQuiet('阅读状态')
    const card = sideCards(wrapper)[2]
    expect(sideRowTexts(card)).toEqual(['阅读状态已读', '木鱼 / 评论 / 复诵21 / 3 / 2'])
    expect(card.findAll('.side-row b')[0].classes()).toContain('on')
    expect(card.find('.side-note').text()).toContain('木鱼即点赞')
    // 记不上已读时就照实写未读
    markFabushiRead.mockRejectedValue(new Error('记已读失败'))
    wrapper = await openDetail()
    expect(sideRowTexts(sideCards(wrapper)[2])[0]).toBe('阅读状态未读')
  })
})

describe('内容详情按 PC 原型 v8：修改弹窗', () => {
  it('弹窗用 fx-dialog，底部按钮包在 .dialog-footer 里：取消 / 保存；保存走原来的接口', async () => {
    const wrapper = await openDetail()
    expectQuiet('修改弹窗')
    const dialog = wrapper.find('.el-dialog-stub')
    expect(dialog.classes()).toContain('fx-dialog')
    expect(dialog.findAll('.dialog-footer .btn-stub').map((b) => b.text())).toEqual(['取消', '保存'])
    vm(wrapper).openEdit()
    await flushPromises()
    vm(wrapper).editTitle = '新标题'
    await flushPromises()
    await dialog.findAll('.dialog-footer .btn-stub')[1].trigger('click')
    await flushPromises()
    expect(updateFabushiPost).toHaveBeenCalledWith('501', { title: '新标题', content: '第一行\n第二行' })
    expect(vm(wrapper).editVisible).toBe(false)
  })
})

describe('从认知广场的操作菜单进来：按地址栏里的 ?focus= 落点', () => {
  /** @type {import('vitest').Mock} */ let scrollIntoView
  beforeEach(() => {
    // jsdom 没有实现 scrollIntoView: 换成一个记下“在哪个元素上调用”的替身
    // （上一行行尾的分号不能省: 下一行以括号开头, 少了分号两行会被连成一句）
    scrollIntoView = vi.fn();
    /** @type {any} */ (Element.prototype).scrollIntoView = scrollIntoView
  })
  afterEach(() => {
    delete /** @type {any} */ (Element.prototype).scrollIntoView
    document.body.innerHTML = ''
  })

  it('?focus=comment：滚到评论输入框，并把光标放进去', async () => {
    const wrapper = await openDetail({ focus: 'comment' }, true)
    expectQuiet('focus=comment')
    const form = wrapper.find('.cmt-input')
    expect(document.activeElement).toBe(form.find('textarea').element)
    expect(scrollIntoView).toHaveBeenCalledTimes(1)
    expect(scrollIntoView.mock.instances[0]).toBe(form.element)
    wrapper.unmount()
  })

  it('?focus=recite：滚到“我要复诵”，短暂高亮，约 1.6 秒后摘掉', async () => {
    // 只把 setTimeout/clearTimeout 换成假的: flushPromises 靠 setImmediate, 留成真的才不会卡住
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      const wrapper = await openDetail({ focus: 'recite' }, true)
      expectQuiet('focus=recite')
      const button = wrapper.find('.recite-btn')
      expect(button.classes()).toContain('flash')
      expect(scrollIntoView).toHaveBeenCalledTimes(1)
      expect(scrollIntoView.mock.instances[0]).toBe(button.element)
      expect(scrollIntoView).toHaveBeenLastCalledWith({ block: 'center', behavior: 'smooth' })
      vi.advanceTimersByTime(1599)
      await flushPromises()
      expect(wrapper.find('.recite-btn').classes()).toContain('flash')
      vi.advanceTimersByTime(1)
      await flushPromises()
      expect(wrapper.find('.recite-btn').classes()).not.toContain('flash')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('没有 focus、或者不认识的 focus：和现在一样，不滚动、不抢光标、不高亮', async () => {
    for (const query of [{}, { focus: 'like' }, { focus: '' }]) {
      const wrapper = await openDetail(query, true)
      expectQuiet(JSON.stringify(query))
      expect(scrollIntoView).not.toHaveBeenCalled()
      expect(document.activeElement).toBe(document.body)
      expect(wrapper.find('.recite-btn').classes()).not.toContain('flash')
      wrapper.unmount()
    }
  })

  it('没有评论权限时 ?focus=comment：没有输入框可落，就滚到评论区，不报错', async () => {
    hoisted.permissions = ['fabushi:view']
    const wrapper = await openDetail({ focus: 'comment' }, true)
    expectQuiet('focus=comment（没有权限）')
    expect(scrollIntoView.mock.instances[0]).toBe(wrapper.find('.cmt-list').element)
    expect(document.activeElement).toBe(document.body)
    wrapper.unmount()
  })
})

describe('内容详情按 PC 原型 v8：样式约定', () => {
  it('图标不用表情符号；不写书卷色；公共样式 fabushi-v8.css 有引入', () => {
    const src = readFileSync('src/views/fabushi/detail.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).not.toMatch(/🎤|🎙|⏹|⏺|🐟/)
    const style = src.slice(src.indexOf('<style'))
    // 米色 / 墨绿 / 朱砂那一套书卷色不再出现
    for (const color of ['#b5543a', '#6b4a2f', '#2d5a4a']) expect(style).not.toContain(color)
    // 应用里没有 --primary 这个令牌（那是原型的）；用了会让整条声明失效
    expect(style).not.toContain('var(--primary)')
    // 返回条没有底色
    const backRule = style.match(/\.back-bar\s*\{[^}]*\}/)
    expect(backRule).not.toBeNull()
    expect(backRule && backRule[0]).toMatch(/background:\s*(none|transparent)/)
  })
})
