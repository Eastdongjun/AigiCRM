import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

// 【GL-14 2026-11-24】法布施发布器(FabushiPublishDialog, P6/P7/P8 三屏合一)的组件测试。此前没有测试。
// 钉住:
//   提交——三类发布各自的必填校验与提示文案; 提交载荷; 附件逐个挂到新帖; 成功后发出 published / 关闭;
//         接口失败时不重复提示、不发出 published;
//   上传——图片上限随施 9 张、其余 5 张, 超出的不再上传; 选文件入口的 accept / capture;
//   录音——HTTP 或不支持麦克风的浏览器: 明确提示并切到“上传录音”(修复 a93eed71, 不再静默失败);
//         语音满了 10 段不再录; 麦克风被拒时提示; 录完按实际格式命名(m4a / ogg / webm); 到 60 秒自动停;
//   切换类型——切到随施清掉书目选择。
// 接口与 request 用替身; MediaRecorder / getUserMedia / isSecureContext 用可控的替身; Element Plus 组件用会渲染插槽的桩。

vi.mock('@/api/fabushi', () => ({
  addFabushiAttachment: vi.fn(),
  createFabushiPost: vi.fn(),
  getFabushiBooks: vi.fn(),
  getFabushiSections: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
// 【FABUSHI-DELTA-20261128 R3/S4】发布器新增「打开这本书」用 useRouter：这里给固定替身
vi.mock('vue-router', () => ({
  useRouter: () => ({ resolve: () => ({ href: '/fabushi/read/501' }) }),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import { ElMessage } from 'element-plus'
import requestModule from '@/utils/request'
import * as fabushiApiModule from '@/api/fabushi'
import FabushiPublishDialog from './FabushiPublishDialog.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const mocked = (fn) => /** @type {import('vitest').Mock} */ (fn)
const request = /** @type {any} */ (requestModule)
const { addFabushiAttachment, createFabushiPost, getFabushiBooks, getFabushiSections } =
  /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const message = /** @type {any} */ (ElMessage)
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)
/** 弹窗里那个隐藏的文件选择框(按 HTMLInputElement 取用: 泛型推断在这里认不出 input 的事件) */
const fileInput = (wrapper) => /** @type {HTMLInputElement} */ (wrapper.find('input[type=file]').element)

const SLOT = { template: '<div><slot /></div>' }
const stubs = {
  'el-dialog': { props: ['modelValue', 'title', 'width'], template: '<section class="dialog"><slot /></section>' },
  'el-button': SLOT, 'el-select': SLOT, 'el-option': SLOT, 'el-input': SLOT, 'el-image-viewer': true,
}
const BOOKS = [
  { id: 1, title: '了凡四训', categoryCode: 'liaofan', categoryName: '了凡四训类', publisher: '中华书局', editionType: '精装', pages: 120 },
  { id: 2, title: '了凡四训', categoryCode: 'liaofan', categoryName: '了凡四训类', publisher: '古籍出版社', editionType: '平装', pages: 100 },
  { id: 3, title: '菜根谭', categoryCode: 'caigeng', categoryName: '菜根谭类', publisher: '中华书局', editionType: '精装', pages: 80 },
]
const SECTION_TREE = [{ id: 11, title: '第一篇', levelCode: 'part', children: [{ id: 12, title: '立命之学', levelCode: 'chapter' }] }]

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  getFabushiBooks.mockResolvedValue({ data: { records: BOOKS, total: BOOKS.length } })
  getFabushiSections.mockResolvedValue({ data: { sections: SECTION_TREE } })
  createFabushiPost.mockResolvedValue({ data: { id: 900 } })
  addFabushiAttachment.mockResolvedValue({ data: true })
  request.post.mockResolvedValue({ data: { id: 88 } })
  vi.stubGlobal('URL', { ...globalThis.URL, createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() })
})
afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  if ('mediaDevices' in navigator) delete /** @type {any} */ (navigator).mediaDevices
})

async function open(props = {}) {
  // 真实使用里弹窗总是先关着、再被打开: 初始化(重置表单、拉书目)发生在“由关变开”那一刻
  const wrapper = mount(FabushiPublishDialog, {
    props: { modelValue: false, defaultScope: 'songdu', ...props },
    global: { stubs, config: { warnHandler: (msg) => warnings.push(msg) } },
  })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  return wrapper
}
const lastWarning = () => message.warning.mock.calls.at(-1)?.[0]

/** 能录音的浏览器: 安全上下文 + getUserMedia + MediaRecorder(固定产出给定的 mime) */
function recordingBrowser(mimeType) {
  const instances = []
  const track = { stop: vi.fn() } // 同一条音轨贯穿始终(录完要停掉它)
  class FakeMediaRecorder {
    constructor(stream) { this.stream = stream; this.mimeType = mimeType; this.state = 'inactive'; this.onstop = null; this.ondataavailable = null; instances.push(this) }
    start() { this.state = 'recording' }
    stop() { this.state = 'inactive'; if (this.onstop) this.onstop() }
  }
  vi.stubGlobal('isSecureContext', true)
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  Object.defineProperty(navigator, 'mediaDevices', {
    value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [track] })) },
    configurable: true,
  })
  return { instances, track }
}
/** 录一段: 开始 → 来了一块音频数据 → 用户点停止 */
async function recordOnce(wrapper, browser, mimeType) {
  await vm(wrapper).startRec()
  const recorder = browser.instances.at(-1)
  recorder.ondataavailable({ data: new Blob(['audio-bytes'], { type: mimeType }) })
  vm(wrapper).stopRec()
  return recorder
}
const imageFiles = (count) => Array.from({ length: count }, (_, i) => new File(['x'.repeat(i + 1)], `photo-${i + 1}.jpg`, { type: 'image/jpeg' }))
async function chooseFiles(wrapper, files) {
  const input = wrapper.find('input[type=file]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}

describe('FabushiPublishDialog 发布器', () => {
  it('打开: 拉取已通过审核的书目, 标题随类型', async () => {
    const wrapper = await open()
    expect(warnings).toEqual([])
    expect(getFabushiBooks).toHaveBeenCalledWith({ status: 'approved', current: 1, size: 500 })
    expect(wrapper.findComponent({ name: 'ElDialog' }).exists() || wrapper.props('modelValue')).toBe(true)
    expect(vm(wrapper).dialogTitle).toBe('发布 · 每日诵读')
    expect(vm(wrapper).submitLabel).toBe('发布每日诵读')
    // 三级选书: 类别去重, 书名按类别过滤, 版本按类别+书名过滤
    expect(vm(wrapper).categoryOptions).toEqual([{ value: 'liaofan', label: '了凡四训类' }, { value: 'caigeng', label: '菜根谭类' }])
    vm(wrapper).form.categoryCode = 'liaofan'
    await nextTick()
    expect(vm(wrapper).titleOptions).toEqual(['了凡四训'])
    vm(wrapper).form.bookTitle = '了凡四训'
    await nextTick()
    expect(vm(wrapper).editionOptions.map((b) => b.id)).toEqual([1, 2])
  })

  describe('提交校验与载荷', () => {
    it('每日诵读: 没选书 / 三项全空 / B 通道没选段落 各自有提示; 合格时按载荷发布', async () => {
      const wrapper = await open()

      await vm(wrapper).submit()
      expect(lastWarning()).toBe('请先选择具体书目')
      vm(wrapper).form.bookId = 1
      await vm(wrapper).submit()
      expect(lastWarning()).toBe('标题、正文、附件至少填一项')
      vm(wrapper).form.channel = 'B'
      vm(wrapper).submit()
      await flushPromises()
      expect(lastWarning()).toBe('B 通道请选择具体段落')
      expect(createFabushiPost).not.toHaveBeenCalled()

      vm(wrapper).form.channel = 'A'
      vm(wrapper).form.title = '  今日诵读  '
      await vm(wrapper).submit()
      expect(lastWarning()).toBe('B 通道请选择具体段落') // 这次没有新的提示
      expect(createFabushiPost).toHaveBeenCalledTimes(1)
      expect(createFabushiPost).toHaveBeenCalledWith({ scopeType: 'songdu', bookId: 1, title: '今日诵读', content: null, sectionId: null })
      expect(wrapper.emitted('published')).toEqual([[900]])
      expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
      expect(message.success).toHaveBeenCalledWith('发布成功')
      expect(vm(wrapper).submitting).toBe(false)
    })

    it('每日随施: 不选书; 内容和附件至少有一样', async () => {
      // 从某本书的页面进来、以随施打开: 表单里有预设书目, 但随施的载荷不能带书目(后端要求为空)
      const wrapper = await open({ defaultScope: 'suishi', presetBookId: 2 })
      expect(vm(wrapper).dialogTitle).toBe('发布 · 每日随施')
      expect(vm(wrapper).submitLabel).toBe('发表')
      expect(vm(wrapper).form.bookId).toBe(2)

      await vm(wrapper).submit()
      expect(lastWarning()).toBe('随施写点内容吧（一句话也行）')
      vm(wrapper).form.content = '  一句话  '
      await vm(wrapper).submit()
      expect(createFabushiPost).toHaveBeenCalledTimes(1)
      expect(createFabushiPost).toHaveBeenCalledWith({ scopeType: 'suishi', bookId: null, title: null, content: '一句话', sectionId: null })
    })

    it('再读一本: 选已有章节 / 自我录入章节 两条路; 内容与附件至少有一样', async () => {
      const wrapper = await open()
      vm(wrapper).switchType('zaidu')
      vm(wrapper).form.bookId = 1
      await flushPromises()
      // 章节树拍平成带层级的路径
      expect(vm(wrapper).sectionOptions).toEqual([{ id: 11, path: '部第一篇' }, { id: 12, path: '部第一篇 / 章立命之学' }])

      await vm(wrapper).submit()
      expect(lastWarning()).toBe('请选择章节')
      vm(wrapper).form.sectionId = 12
      await vm(wrapper).submit()
      expect(lastWarning()).toBe('再读一本请写下这一章的所得，或上传书页/语音')
      vm(wrapper).form.content = '心得'
      await vm(wrapper).submit()
      expect(createFabushiPost).toHaveBeenLastCalledWith({ scopeType: 'zaidu', bookId: 1, title: null, content: '心得', sectionId: 12 })

      vm(wrapper).switchType('zaidu')
      vm(wrapper).form.bookId = 2
      vm(wrapper).form.sectionMode = 'manual'
      vm(wrapper).form.content = '心得'
      await flushPromises()
      await vm(wrapper).submit()
      expect(lastWarning()).toBe('请填写章节名称')
      vm(wrapper).form.manualSection = '  第八章  '
      // 自我录入章节走另一条接口: 按地址区分应答
      request.post.mockImplementation((url) => (String(url).includes('/sections/self')
        ? Promise.resolve({ data: { id: 4242 } })
        : Promise.resolve({ data: { id: 88 } })))
      await vm(wrapper).submit()
      expect(request.post).toHaveBeenLastCalledWith('/fabushi/books/2/sections/self', { title: '第八章' })
      expect(createFabushiPost).toHaveBeenLastCalledWith({ scopeType: 'zaidu', bookId: 2, title: null, content: '心得', sectionId: 4242 })
    })

    it('附件逐个挂到新帖; 接口失败时不重复提示、不发出 published、按钮恢复', async () => {
      request.post.mockResolvedValue({ data: { id: 88 } })
      const wrapper = await open({ defaultScope: 'suishi' })
      await chooseFiles(wrapper, imageFiles(2))
      expect(vm(wrapper).attachments.map((a) => a.fileType)).toEqual(['image', 'image'])
      vm(wrapper).form.content = '带图'
      await vm(wrapper).submit()
      expect(createFabushiPost).toHaveBeenCalledTimes(1)
      expect(addFabushiAttachment).toHaveBeenCalledTimes(2)
      expect(addFabushiAttachment).toHaveBeenNthCalledWith(1, 900, { fileId: 88, fileType: 'image', durationSeconds: null })

      const failing = await open({ defaultScope: 'suishi' })
      vm(failing).form.content = '会失败'
      createFabushiPost.mockRejectedValueOnce(new Error('500'))
      const successCalls = message.success.mock.calls.length
      await vm(failing).submit()
      expect(message.success.mock.calls.length).toBe(successCalls)
      expect(message.error).not.toHaveBeenCalled() // request 拦截器已经提示过
      expect(failing.emitted('published')).toBeUndefined()
      expect(vm(failing).submitting).toBe(false)
    })
  })

  describe('上传', () => {
    it('图片上限: 每日诵读 5 张、每日随施 9 张, 超出的不再上传', async () => {
      const wrapper = await open()
      expect(vm(wrapper).imageLimit).toBe(5)
      await chooseFiles(wrapper, imageFiles(6))
      expect(vm(wrapper).images).toHaveLength(5)

      const suishi = await open({ defaultScope: 'suishi' })
      expect(vm(suishi).imageLimit).toBe(9)
      await chooseFiles(suishi, imageFiles(10))
      expect(vm(suishi).images).toHaveLength(9)
    })

    it('已经满了再选: 不上传, 提示上限', async () => {
      const wrapper = await open()
      await chooseFiles(wrapper, imageFiles(5))
      await chooseFiles(wrapper, imageFiles(1))
      expect(request.post).toHaveBeenCalledTimes(5)
      expect(lastWarning()).toBe('图片最多 5 张')
    })

    it('选文件入口: 拍照时 accept 图片并带 capture, 选录音时 accept 音频且不带 capture', async () => {
      const wrapper = await open()
      const input = fileInput(wrapper)
      const click = vi.spyOn(input, 'click')

      vm(wrapper).pickFile('image', true)
      expect(click).toHaveBeenCalledTimes(1)
      expect(input.getAttribute('accept')).toBe('image/*')
      expect(input.hasAttribute('capture')).toBe(true)

      vm(wrapper).pickFile('audio', false)
      expect(input.getAttribute('accept')).toBe('audio/*')
      expect(input.hasAttribute('capture')).toBe(false)
      expect(vm(wrapper).pendingPick).toEqual({ type: 'audio', capture: false })
    })

    it('移除附件: 从列表里去掉', async () => {
      const wrapper = await open()
      await chooseFiles(wrapper, imageFiles(3))
      vm(wrapper).removeAttachment(1)
      await nextTick()
      expect(vm(wrapper).images.map((a) => a.name)).toEqual(['photo-1.jpg', 'photo-3.jpg'])
    })
  })

  describe('录音与语音兜底', () => {
    it('HTTP 环境: 明确提示, 并自动切到“上传录音”(不再静默失败)', async () => {
      // 浏览器本身能录音(MediaRecorder / getUserMedia 都在), 只是页面跑在 HTTP 上——浏览器禁止麦克风
      const browser = recordingBrowser('audio/webm')
      vi.stubGlobal('isSecureContext', false)
      const wrapper = await open()
      const input = fileInput(wrapper)
      const click = vi.spyOn(input, 'click')

      await vm(wrapper).startRec()

      expect(lastWarning()).toBe('当前是 HTTP 或浏览器不支持麦克风；已切换为上传录音，请用手机录音后选择文件，或改用 HTTPS 打开')
      expect(click).toHaveBeenCalledTimes(1)
      expect(input.getAttribute('accept')).toBe('audio/*')
      expect(vm(wrapper).rec.active).toBe(false)
      expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
      expect(browser.instances).toHaveLength(0)
    })

    it('安全上下文但浏览器不支持麦克风: 同样提示并切到上传录音', async () => {
      vi.stubGlobal('isSecureContext', true)
      const wrapper = await open()
      const click = vi.spyOn(fileInput(wrapper), 'click')

      await vm(wrapper).startRec()

      expect(lastWarning()).toContain('浏览器不支持麦克风')
      expect(click).toHaveBeenCalledTimes(1)
    })

    it('语音已满 10 段: 提示上限, 不录音也不弹选文件', async () => {
      const browser = recordingBrowser('audio/webm')
      const wrapper = await open()
      const click = vi.spyOn(fileInput(wrapper), 'click')
      vm(wrapper).attachments = Array.from({ length: 10 }, (_, i) => ({ key: `a${i}`, fileId: i, name: `rec-${i}.webm`, fileType: 'audio', previewUrl: '' }))

      await vm(wrapper).startRec()

      expect(lastWarning()).toBe('语音最多 10 段，请先删除不用的段')
      expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled()
      expect(click).not.toHaveBeenCalled()
      expect(browser.instances).toHaveLength(0)
    })

    it('麦克风被拒: 提示检查权限, 不进入录音态', async () => {
      vi.stubGlobal('isSecureContext', true)
      Object.defineProperty(navigator, 'mediaDevices', {
        value: { getUserMedia: vi.fn(async () => { throw new DOMException('Permission denied', 'NotAllowedError') }) },
        configurable: true,
      })
      vi.stubGlobal('MediaRecorder', class {})
      const wrapper = await open()

      await vm(wrapper).startRec()

      expect(lastWarning()).toBe('无法访问麦克风，请检查浏览器权限')
      expect(vm(wrapper).rec.active).toBe(false)
    })

    it.each([
      ['audio/mp4; codecs=mp4a.40.2', 'm4a'],
      ['audio/ogg;codecs=opus', 'ogg'],
      ['audio/webm;codecs=opus', 'webm'],
      ['audio/webm', 'webm'],
    ])('录完按实际格式命名: %s → .%s', async (mimeType, ext) => {
      const browser = recordingBrowser(mimeType)
      const wrapper = await open()

      await vm(wrapper).startRec()
      expect(vm(wrapper).rec.active).toBe(true)
      expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: true })
      const recorder = await recordOnce(wrapper, browser, mimeType)

      expect(browser.track.stop).toHaveBeenCalledTimes(1)
      expect(vm(wrapper).rec.file.name).toMatch(new RegExp(`^rec-\\d+\\.${ext}$`))
      expect(vm(wrapper).rec.file.type).toBe(mimeType)
      expect(vm(wrapper).rec.url).toBe('blob:preview')
    })

    it('到 60 秒自动停止并提示', async () => {
      const browser = recordingBrowser('audio/webm')
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
      const wrapper = await open()

      const starting = vm(wrapper).startRec()
      await Promise.resolve()
      await Promise.resolve()
      await starting
      await vi.advanceTimersByTimeAsync(59_000)
      expect(vm(wrapper).rec.seconds).toBe(59)
      expect(browser.instances[0].state).toBe('recording')
      await vi.advanceTimersByTimeAsync(1_000)

      expect(vm(wrapper).rec.seconds).toBe(60)
      expect(browser.instances[0].state).toBe('inactive')
      expect(vm(wrapper).rec.active).toBe(false)
      expect(message.info).toHaveBeenCalledWith('单段语音上限 60 秒，已自动停止')
    })

    it('保存语音: 上传后进入附件, 录音态清空', async () => {
      const browser = recordingBrowser('audio/mp4')
      const wrapper = await open()
      await vm(wrapper).startRec()
      await recordOnce(wrapper, browser, 'audio/mp4')

      await vm(wrapper).saveRec()

      expect(request.post).toHaveBeenCalledTimes(1)
      const [url, body] = request.post.mock.calls[0]
      expect(url).toBe('/file/upload')
      expect(body.get('scope')).toBe('fabushi')
      expect(body.get('file').name).toMatch(/^rec-\d+\.m4a$/)
      expect(vm(wrapper).audioAttachments).toHaveLength(1)
      expect(vm(wrapper).audioAttachments[0]).toMatchObject({ fileId: 88, fileType: 'audio' })
      expect(vm(wrapper).rec).toEqual({ active: false, seconds: 0, url: '', file: null })
      expect(message.success).toHaveBeenCalledWith('语音已上传')
    })
  })

  it('切换类型: 切到随施清掉书目与章节; 再切回诵读不带回书目', async () => {
    const wrapper = await open()
    vm(wrapper).form.bookId = 1
    vm(wrapper).form.title = '标题'
    await flushPromises()

    vm(wrapper).switchType('suishi')

    expect(vm(wrapper).form.scopeType).toBe('suishi')
    expect(vm(wrapper).form.bookId).toBeNull()
    expect(vm(wrapper).form.categoryCode).toBeNull()
    expect(vm(wrapper).form.bookTitle).toBeNull()
    expect(vm(wrapper).form.title).toBe('')
  })

  it('默认书目: 带预设书目打开时自动选中, 并拉取它的章节', async () => {
    const wrapper = await open({ presetBookId: 2 })
    expect(vm(wrapper).form.bookId).toBe(2)
    expect(getFabushiSections).toHaveBeenCalledWith(2)
    expect(vm(wrapper).sections).toEqual(SECTION_TREE)
  })
})
