import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { readFileSync } from 'node:fs'

// 【2026-11-26 按 PC 原型 v8】发布器弹窗（FabushiPublishDialog = 原型 P6 每日诵读 / P7 每日随施 / P8 再读一本）。
// 这次只重做外观：props / 事件 / 校验 / 提交载荷 / 录音与上传链路都没动，那些仍由 FabushiPublishDialog.spec.js 钉着。
// 这里钉住跟原型走的骨架事实：
//   ① 外形：弹窗带 fx-dialog，宽度照原型（诵读 660 / 随施 560 / 再读 660），标题 + 副标题，底部 [取消][发布…]；
//   ② 三个弹窗各自的区块与先后顺序；
//   ③ 类型切换是现有功能，保留（随施弹窗本来就不显示，维持不变）；
//   ④ 原有控件一个不少：三级选书、章节两种方式、录音 / 上传录音 / 试听 / 逐段删除、图片增删与上限、裁剪 / 标注 / 放大、工具入口；
//   ⑤ 图标不用表情符号；评审标注（D / Q 编号、权限码、“演示”）不进界面；原型里后端没有的工具（位置 / 谁可以看）不做。
// 口径与 FabushiPublishDialog.spec.js 相同：接口、request、消息是替身；Element Plus 组件用会渲染插槽的桩。
// 抓不到：真实布局与配色（靠截图和原型并排核对）。

vi.mock('@/api/fabushi', () => ({
  addFabushiAttachment: vi.fn(),
  createFabushiPost: vi.fn(),
  getFabushiBooks: vi.fn(),
  getFabushiSections: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import * as fabushiApiModule from '@/api/fabushi'
import { ElMessage } from 'element-plus'
import requestModule from '@/utils/request'
import FabushiPublishDialog from './FabushiPublishDialog.vue'

const { getFabushiBooks, getFabushiSections } =
  /** @type {Record<string, import('vitest').Mock>} */ (/** @type {unknown} */ (fabushiApiModule))
const request = /** @type {any} */ (requestModule)
const message = /** @type {any} */ (ElMessage)
// <script setup> 组件默认不对外暴露内部绑定: 测试里按 any 读取
const vm = (wrapper) => /** @type {any} */ (wrapper.vm)

const SLOT = { template: '<div><slot /></div>' }
// 弹窗桩: 渲染标题插槽、正文、底部(现有那个测试的桩只渲染正文, 这里要看标题与底部按钮)
const stubs = {
  'el-dialog': { props: ['modelValue', 'title', 'width'], template: '<section class="dialog" :data-width="width"><slot name="header" /><slot /><slot name="footer" /></section>' },
  'el-button': { props: ['disabled', 'loading', 'type', 'size'], template: '<button class="btn-stub" :disabled="disabled || loading" :data-type="type"><slot /></button>' },
  'el-select': SLOT, 'el-option': SLOT, 'el-input': SLOT, 'el-image-viewer': true,
}
const BOOKS = [
  { id: 1, title: '了凡四训', categoryCode: 'liaofan', categoryName: '了凡四训类', publisher: '中华书局', editionType: '精装', pages: 120 },
  { id: 2, title: '了凡四训', categoryCode: 'liaofan', categoryName: '了凡四训类', publisher: '古籍出版社', editionType: '平装', pages: 100 },
  { id: 3, title: '菜根谭', categoryCode: 'caigeng', categoryName: '菜根谭类', publisher: '中华书局', editionType: '精装', pages: 80 },
]
const SECTION_TREE = [{ id: 11, title: '第一篇', levelCode: 'part', children: [{ id: 12, title: '立命之学', levelCode: 'chapter' }] }]

/** 正文里按先后顺序数得出来的区块(小标题带上它自己的名字) */
const LANDMARKS = [
  ['.pub-body .pub-type', '类型切换'],
  ['.pub-body .frm-label', '小标题'],
  ['.pub-body > .seg', '分段开关'],
  ['.pub-body .triple', '三级选书'],
  ['.pub-body .bcard-tags', '版本切换'],
  ['.pub-body .frm-title', '标题输入'],
  ['.pub-body .frm-textarea', '正文'],
  ['.pub-body .att-row', '图片行'],
  ['.pub-body .edit-tools', '图片编辑'],
  ['.pub-body .audio-seg', '语音行'],
  ['.pub-body .audio-block', '语音列表'],
  ['.pub-body .composer-grid', '图片格子'],
  ['.pub-body .pub-tools', '工具格子'],
  ['.pub-body .file-chips', '文件'],
  ['.pub-body .frm-foot', '说明'],
  ['.pub-body .pub-archive', '归档提示'],
]
const texts = (wrapper, selector) => wrapper.findAll(selector).map((node) => node.text())
/** 下拉桩里的选项文字(el-option 的文字在 label 属性上, 不在插槽里) */
const optionLabels = (wrapper, selector) => wrapper.findAll(selector).map((node) => node.findAll('[label]').map((opt) => opt.attributes('label')))
const TOOLS = '.pub-body .edit-tools .edit-tool'
const outline = (wrapper) => {
  const body = wrapper.find('.pub-body')
  return body.findAll(LANDMARKS.map(([selector]) => selector).join(', ')).map((node) => {
    const found = LANDMARKS.find(([selector]) => node.element.matches(selector))
    return found && found[1] === '小标题' ? node.find('.frm-name').text() : (found ? found[1] : '')
  })
}
/** 弹窗里那个隐藏的文件选择框(按 HTMLInputElement 取用) */
const fileInput = (wrapper) => /** @type {HTMLInputElement} */ (wrapper.find('input[type=file]').element)
const imageFiles = (count) => Array.from({ length: count }, (_, i) => new File(['x'.repeat(i + 1)], `photo-${i + 1}.jpg`, { type: 'image/jpeg' }))
async function chooseFiles(wrapper, files) {
  const input = wrapper.find('input[type=file]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}
const buttonByText = (wrapper, selector, label) => wrapper.findAll(selector).find((node) => node.text() === label)

let warnings
beforeEach(() => {
  vi.clearAllMocks()
  warnings = []
  getFabushiBooks.mockResolvedValue({ data: { records: BOOKS, total: BOOKS.length } })
  getFabushiSections.mockResolvedValue({ data: { sections: SECTION_TREE } })
  request.post.mockResolvedValue({ data: { id: 88 } })
  // jsdom 不会真的播声音: play 只记一笔, pause 补发一个 pause 事件(界面靠它把“停止”改回“试听”)
  vi.spyOn(window.HTMLMediaElement.prototype, 'play').mockImplementation(function () { return Promise.resolve() })
  vi.spyOn(window.HTMLMediaElement.prototype, 'pause').mockImplementation(function () { this.dispatchEvent(new Event('pause')) })
  vi.stubGlobal('URL', { ...globalThis.URL, createObjectURL: vi.fn(() => 'blob:preview'), revokeObjectURL: vi.fn() })
})
afterEach(() => {
  vi.restoreAllMocks()
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
const dialog = (wrapper) => wrapper.find('section.dialog')
/** 发布器自己那一排底部按钮（“编辑图片”弹窗嵌在正文里, 它的底部按钮排在前面, 所以取最后一个） */
const mainFooter = (wrapper) => {
  const all = wrapper.findAll('section.dialog > .dialog-footer')
  return all[all.length - 1]
}
const footerButtons = (wrapper) => texts(mainFooter(wrapper), '.btn-stub')
const head = (wrapper) => [wrapper.find('.pub-head .el-dialog__title').text(), wrapper.find('.pub-head-sub').text()]

describe('发布器弹窗按 PC 原型 v8', () => {
  it('Vue 没有警告(桩里没有 el-icon: 图标容器是从 element-plus 显式引入的 ElIcon)', async () => {
    await open()
    expect(warnings).toEqual([])
  })

  describe('外形', () => {
    it('弹窗带 fx-dialog；宽度照原型：每日诵读 660px、每日随施 560px、再读一本 660px', async () => {
      const wrapper = await open()
      expect(dialog(wrapper).classes()).toContain('fx-dialog')
      expect(dialog(wrapper).attributes('data-width')).toBe('660px')
      vm(wrapper).switchType('suishi')
      await nextTick()
      expect(dialog(wrapper).attributes('data-width')).toBe('560px')
      vm(wrapper).switchType('zaidu')
      await nextTick()
      expect(dialog(wrapper).attributes('data-width')).toBe('660px')
    })

    it('标题 + 副标题、底部按钮照原型；再读一本的发布键是绿的', async () => {
      const wrapper = await open()
      expect(head(wrapper)).toEqual(['发布 · 每日诵读', '人人 · 天天 · 小而短地读经典（固定时点推荐 晨 08:00）'])
      expect(footerButtons(wrapper)).toEqual(['取消', '发布每日诵读'])
      expect(mainFooter(wrapper).find('.btn-stub:last-child').attributes('data-type')).toBe('primary')

      vm(wrapper).switchType('suishi')
      await nextTick()
      expect(head(wrapper)).toEqual(['发布 · 每日随施', '随手施 · 像发朋友圈一样简单'])
      expect(footerButtons(wrapper)).toEqual(['取消', '发表'])

      vm(wrapper).switchType('zaidu')
      await nextTick()
      expect(head(wrapper)).toEqual(['发布 · 再读一本', '按章节日日上传 · 整本精读记录'])
      expect(footerButtons(wrapper)).toEqual(['取消', '发布再读一本'])
      expect(mainFooter(wrapper).find('.btn-stub:last-child').attributes('data-type')).toBe('success')
    })

    it('对外的 props / 事件没变：取消仍是发出 update:modelValue = false', async () => {
      const wrapper = await open()
      expect(Object.keys(wrapper.props()).sort()).toEqual(['defaultScope', 'modelValue', 'presetBookId'])
      await buttonByText(mainFooter(wrapper), '.btn-stub', '取消').trigger('click')
      expect(wrapper.emitted('update:modelValue')[wrapper.emitted('update:modelValue').length - 1]).toEqual([false])
    })

    it('类型切换还在：诵读 / 再读两个弹窗标题下面有一行分段开关；随施弹窗本来就不显示(维持不变)', async () => {
      const wrapper = await open()
      const items = () => texts(wrapper, '.pub-type .seg-item')
      const active = () => wrapper.find('.pub-type .seg-item.active').text()
      expect(items()).toEqual(['每日诵读', '每日随施', '再读一本'])
      expect(active()).toBe('每日诵读')
      await wrapper.findAll('.pub-type .seg-item')[2].trigger('click')
      await nextTick()
      expect(active()).toBe('再读一本')
      expect(head(wrapper)[0]).toBe('发布 · 再读一本')
      await wrapper.findAll('.pub-type .seg-item')[1].trigger('click')
      await nextTick()
      expect(vm(wrapper).form.scopeType).toBe('suishi')
      expect(wrapper.find('.pub-type').exists()).toBe(false)
    })
  })

  describe('P6 每日诵读', () => {
    // 【2026-11-26 审后修正】A 通道也要选书：后端对每日诵读一律要求关联已审核书目（没有书会 400），
    // 小程序端的 A 通道也是先选书；改版前 PC 的 A 通道界面里没有选书入口，提示还写着“不必选书”，
    // 从“＋每日诵读”直接打开、停在 A 通道的人根本发不出去。原型的 A 通道没画选书，这里以能发出去为准。
    it('A 通道的区块与顺序：诵读方式 → 选书 → 标题与素材 → 图片 → 语音 → 说明；底部说明写当前通道', async () => {
      const wrapper = await open()
      expect(outline(wrapper)).toEqual([
        '类型切换', '诵读方式', '分段开关', '选择具体书（类别 → 书目 → 版本）', '三级选书',
        '诵读素材（文字或图片）', '标题输入', '正文',
        '或上传图片（拍纸页）', '图片行', '图片编辑', '诵读语音（朗读 / 背诵上面的内容）', '语音行', '说明',
      ])
      // 小标题旁的轻说明与必填星号(照校验来: 方式、选书必选; 素材是“至少填一项”，不挂必填星号)
      expect(wrapper.find('.pub-body .frm-label .frm-name').exists()).toBe(true)
      expect(wrapper.findAll('.pub-body .frm-label')[0].find('.frm-req').exists()).toBe(true)
      expect(wrapper.findAll('.pub-body .frm-label')[1].find('.frm-req').exists()).toBe(true)
      expect(wrapper.findAll('.pub-body .frm-label')[2].find('.frm-req').exists()).toBe(false)
      expect(wrapper.findAll('.pub-body .frm-label')[2].find('.frm-note').text()).toBe('标题、正文、图片、语音至少填一项')
      expect(wrapper.findAll('.pub-body .frm-label')[3].find('.frm-note').text()).toBe('1-5 张 · 可裁剪标注')
      expect(wrapper.findAll('.pub-body .frm-label')[4].find('.frm-note').text()).toBe('1-10 段 × 每段 ≤60 秒')
      const foot = () => texts(wrapper, '.frm-foot p')
      expect(foot()).toEqual([
        '录完先点“保存语音”；保存后可逐段试听，不满意删掉重录。',
        'A 通道：先选书 → 再写 / 传今天要读的文字或图片 → 对着它朗读背诵；内容挂在这本书下，不指定章节。',
      ])
      // 原有的字数上限还在
      expect(wrapper.find('.frm-title').attributes('maxlength')).toBe('120')
      expect(wrapper.find('.frm-title').attributes('placeholder')).toBe('给这段诵读起个标题（可选）')
      expect(wrapper.find('.frm-textarea').attributes('maxlength')).toBe('2000')
      expect(wrapper.find('.frm-textarea').attributes('placeholder')).toBe('今天诵读的原文 / 你的批注…')
    })

    it('两通道分段开关：文案照原型；两个通道都要选书，切到 B 多出“具体段落”，切回 A 只收起段落', async () => {
      const wrapper = await open()
      const items = () => texts(wrapper, '.pub-body > .seg .seg-item')
      expect(items()).toEqual(['A · 直接传（文字 / 图片 + 语音）', 'B · 选书选段（从《再读一本》内容中选）'])
      expect(wrapper.find('.pub-body > .seg .seg-item.active').text()).toContain('A')
      // A 通道也有三级选书
      expect(wrapper.find('.triple').exists()).toBe(true)
      const labelNames = () => wrapper.findAll('.pub-body .frm-label .frm-name').map((n) => n.text())
      expect(labelNames()).toContain('选择具体书（类别 → 书目 → 版本）')
      expect(labelNames()).not.toContain('具体段落（章节）')
      expect(wrapper.findAll('.pub-body .frm-hint.warn')).toHaveLength(0)

      await wrapper.findAll('.pub-body > .seg .seg-item')[1].trigger('click')
      await nextTick()
      expect(vm(wrapper).form.channel).toBe('B')
      expect(wrapper.find('.pub-body > .seg .seg-item.active').text()).toContain('B')
      expect(outline(wrapper)).toEqual([
        '类型切换', '诵读方式', '分段开关', '选择具体书（类别 → 书目 → 版本）', '三级选书',
        '具体段落（章节）', '诵读素材（文字或图片）', '标题输入', '正文', '或上传图片（拍纸页）', '图片行', '图片编辑',
        '诵读语音（朗读 / 背诵上面的内容）', '语音行', '说明',
      ])
      expect(optionLabels(wrapper, '.triple .pub-select').slice(0, 2)).toEqual([['了凡四训类', '菜根谭类'], ['了凡四训', '菜根谭']])
      expect(optionLabels(wrapper, '.triple .pub-select')[2][0]).toContain('《了凡四训》')
      expect(texts(wrapper, '.frm-foot p')[1]).toBe('B 通道：三级选书 → 具体段落 → 诵读，内容会挂到该书的章节下。')

      // 切回 A：选书还在，段落收起
      await wrapper.findAll('.pub-body > .seg .seg-item')[0].trigger('click')
      await nextTick()
      expect(wrapper.find('.triple').exists()).toBe(true)
      expect(labelNames()).not.toContain('具体段落（章节）')
    })

    it('B 通道选了书却没有章节：提示还在', async () => {
      getFabushiSections.mockResolvedValue({ data: { sections: [] } })
      const wrapper = await open({ presetBookId: 1 })
      vm(wrapper).form.channel = 'B'
      await nextTick()
      await flushPromises()
      expect(wrapper.find('.frm-hint.warn').text()).toBe('该书还没有章节结构，先切到「再读一本」用自录章节建立，或改选其他书。')
    })

    it('图片：已选的是带“已选 N 张”字样的小块，旁边是“＋ 添加图片”；满 5 张不再出现；小块上有删除', async () => {
      const wrapper = await open()
      const addTile = () => wrapper.find('.att-row .att-tile.add')
      expect(addTile().text()).toBe('＋ 添加图片')
      const click = vi.spyOn(fileInput(wrapper), 'click')
      await addTile().trigger('click')
      expect(click).toHaveBeenCalledTimes(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('image/*')
      expect(fileInput(wrapper).hasAttribute('capture')).toBe(false)

      await chooseFiles(wrapper, imageFiles(2))
      expect(texts(wrapper, '.att-row .att-tile.picked .att-cap')).toEqual(['已选 2 张', '第 2 张'])
      expect(wrapper.findAll('.att-row .att-tile.picked img')).toHaveLength(2)
      expect(addTile().exists()).toBe(true)

      await chooseFiles(wrapper, imageFiles(4))
      expect(vm(wrapper).images).toHaveLength(5)
      expect(wrapper.findAll('.att-row .att-tile.picked')).toHaveLength(5)
      expect(addTile().exists()).toBe(false)

      // 第二次选的 4 张里只放得下 3 张(上限 5): 文件名是 photo-1…photo-3
      expect(vm(wrapper).images.map((a) => a.name)).toEqual(['photo-1.jpg', 'photo-2.jpg', 'photo-1.jpg', 'photo-2.jpg', 'photo-3.jpg'])
      await wrapper.findAll('.att-row .att-tile.picked .cell-x')[0].trigger('click')
      expect(vm(wrapper).images.map((a) => a.name)).toEqual(['photo-2.jpg', 'photo-1.jpg', 'photo-2.jpg', 'photo-3.jpg'])
      expect(addTile().exists()).toBe(true)
    })

    it('图片编辑只放现有功能真支持的：裁剪与框选标注(没有划线、圈)；多张时点哪张就对哪张做', async () => {
      const wrapper = await open()
      expect(texts(wrapper, TOOLS)).toEqual(['裁剪', '框选标注'])
      expect(wrapper.findAll(TOOLS).every((tool) => tool.attributes('disabled') !== undefined)).toBe(true)

      await chooseFiles(wrapper, imageFiles(2))
      expect(wrapper.findAll(TOOLS).every((tool) => tool.attributes('disabled') === undefined)).toBe(true)
      const openEditor = vi.fn()
      vm(wrapper).openEditor = openEditor
      const picks = () => wrapper.findAll('.att-row .att-tile.picked .cell-pick')
      await wrapper.findAll(TOOLS)[1].trigger('click')
      expect(openEditor).toHaveBeenLastCalledWith(0, 'mark') // 默认第一张
      const tiles = () => wrapper.findAll('.att-row .att-tile.picked')
      expect(tiles()[0].classes()).toContain('current') // 默认对第一张
      expect(tiles()[1].classes()).not.toContain('current')
      await picks()[1].trigger('click')
      expect(vm(wrapper).editIndex).toBe(1)
      expect(tiles()[1].classes()).toContain('current') // 点选的那一块描出来
      expect(tiles()[0].classes()).not.toContain('current')
      await wrapper.findAll(TOOLS)[0].trigger('click')
      expect(openEditor).toHaveBeenLastCalledWith(1, 'crop')
    })

    it('语音行：波形 + 真实状态 + [＋ 录一段][试听][上传录音]；试听 = 播放最新一段(刚录的优先)，再点一下暂停', async () => {
      const wrapper = await open()
      expect(wrapper.findAll('.audio-seg .recite-wave i')).toHaveLength(7)
      expect(wrapper.find('.audio-seg-text').text()).toBe('还没有语音（最多 10 段）')
      expect(texts(wrapper, '.audio-seg .btn-stub')).toEqual(['＋ 录一段', '试听', '上传录音'])
      const listen = () => wrapper.findAll('.audio-seg .btn-stub')[1]
      const listenLabel = () => listen().text()
      expect(listen().attributes('disabled')).toBeDefined()
      // 语音怎么用: 两行说明里的第一行(第二行是当前通道的意思, 上面已经钉过)
      expect(texts(wrapper, '.frm-foot p')[0]).toBe('录完先点“保存语音”；保存后可逐段试听，不满意删掉重录。')

      vm(wrapper).attachments.push({ key: 'a1', fileId: 1, name: 'rec-1.webm', fileType: 'audio', previewUrl: 'blob:1' })
      vm(wrapper).attachments.push({ key: 'a2', fileId: 2, name: 'rec-2.webm', fileType: 'audio', previewUrl: 'blob:2' })
      await nextTick()
      expect(wrapper.find('.audio-seg-text').text()).toBe('已录 2 段')
      // 逐段: 每一段有自己的播放器与删除
      expect(wrapper.findAll('.audio-list .audio-item audio[controls]')).toHaveLength(2)
      const players = wrapper.findAll('.audio-list audio')
      const play = /** @type {import('vitest').Mock} */ (window.HTMLMediaElement.prototype.play)
      const pause = /** @type {import('vitest').Mock} */ (window.HTMLMediaElement.prototype.pause)
      await listen().trigger('click')
      expect(play).toHaveBeenCalledTimes(1)
      expect(play.mock.instances[0]).toBe(players[1].element) // 最新的一段
      expect(listenLabel()).toBe('停止')
      await listen().trigger('click')
      expect(pause.mock.instances).toContain(players[1].element)
      expect(listenLabel()).toBe('试听')

      await wrapper.findAll('.audio-list .audio-x')[0].trigger('click')
      expect(vm(wrapper).audioAttachments.map((a) => a.name)).toEqual(['rec-2.webm'])

      // 刚录的一段(还没保存): 试听放它; 这一行上仍有保存 / 重录
      vm(wrapper).rec.url = 'blob:new'
      vm(wrapper).rec.seconds = 12
      await nextTick()
      expect(wrapper.find('.audio-pending audio[controls]').exists()).toBe(true)
      expect(texts(wrapper, '.audio-pending .btn-stub')).toEqual(['保存语音', '重录'])
      expect(wrapper.find('.audio-seg-text').text()).toBe('已录 1 段，另有一段待保存（12 秒）')
      await listen().trigger('click')
      expect(play.mock.instances[1]).toBe(wrapper.find('.audio-pending audio').element)
    })

    it('试听放不了的时候(格式不支持): 键复原成“试听”并提示, 不会停在“停止”上', async () => {
      const wrapper = await open()
      vm(wrapper).attachments.push({ key: 'a1', fileId: 1, name: 'rec-1.webm', fileType: 'audio', previewUrl: 'blob:1' })
      await nextTick()
      const listen = () => wrapper.findAll('.audio-seg .btn-stub')[1]
      const play = /** @type {import('vitest').Mock} */ (window.HTMLMediaElement.prototype.play)
      play.mockImplementationOnce(() => Promise.reject(new DOMException('no supported source', 'NotSupportedError')))
      await listen().trigger('click')
      await flushPromises()
      expect(listen().text()).toBe('试听')
      expect(message.warning).toHaveBeenLastCalledWith('这段语音当前浏览器放不了，可以删掉重录或换一段')
      // 不是格式问题的失败(比如放了一半被打断): 只复原键, 不提示
      play.mockImplementationOnce(() => Promise.reject(new DOMException('interrupted', 'AbortError')))
      await listen().trigger('click')
      await flushPromises()
      expect(listen().text()).toBe('试听')
      expect(message.warning).toHaveBeenCalledTimes(1)
    })

    it('“＋ 录一段”仍是开始 / 停止录音；录制中显示秒数；HTTP 下自动改走选文件(上传录音入口也还在)', async () => {
      const wrapper = await open()
      const record = () => buttonByText(wrapper, '.audio-seg .btn-stub', '＋ 录一段') || buttonByText(wrapper, '.audio-seg .btn-stub', '停止录制')
      vm(wrapper).rec.active = true
      vm(wrapper).rec.seconds = 7
      await nextTick()
      expect(record().text()).toBe('停止录制')
      expect(wrapper.find('.audio-seg-text').text()).toBe('录制中 7 秒 / 60 秒')
      vm(wrapper).rec.active = false
      vm(wrapper).rec.seconds = 0
      await nextTick()

      const click = vi.spyOn(fileInput(wrapper), 'click')
      await buttonByText(wrapper, '.audio-seg .btn-stub', '＋ 录一段').trigger('click')
      await flushPromises()
      expect(click).toHaveBeenCalledTimes(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('audio/*')
      await buttonByText(wrapper, '.audio-seg .btn-stub', '上传录音').trigger('click')
      expect(click).toHaveBeenCalledTimes(2)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('audio/*')
      expect(vm(wrapper).pendingPick).toEqual({ type: 'audio', capture: false })
    })
  })

  describe('P7 每日随施', () => {
    it('区块与顺序：正文 → 九宫格 → 图片编辑 → 工具 → 说明 → 归档提示；没有类型切换', async () => {
      const wrapper = await open({ defaultScope: 'suishi' })
      expect(outline(wrapper)).toEqual(['正文', '图片格子', '图片编辑', '工具格子', '说明', '归档提示'])
      expect(wrapper.find('.pub-type').exists()).toBe(false)
      expect(wrapper.find('.frm-textarea').attributes('maxlength')).toBe('2000')
      expect(wrapper.find('.frm-textarea').attributes('placeholder')).toBe('写点什么…（纯文字也能发，不设标题、不关联书目）')
      expect(wrapper.find('.frm-foot').text()).toBe('不关联书目 · 不设标题 · 纯文字也能发；正能量、书的 1-5 页、心得、照片都行。')
      // 已有的事实写进提示(不做成可点的工具): 附件保存在文件中心的法布施空间、社区内容全员可见。
      // 原型那句“下架或书目停用后可见范围回落到上传人”与实现不符：下架后信息流和详情都不再给任何人看(发布人自己也看不到)，
      // 记录只留在内容管理里；书目停用不影响已发内容。提示只写后端真做的那样。
      const archive = wrapper.find('.pub-archive').text()
      expect(archive).toContain('文件中心 › 法布施')
      expect(archive).toContain('全员')
      expect(archive).not.toContain('回落到上传人')
      expect(archive).not.toContain('书目停用')
      expect(archive).toContain('发布人自己也看不到')
      expect(archive).toContain('内容管理')
    })

    it('九宫格：没选图时是 6 个“＋”格子，点哪个都是选图；满了 9 张不再出现“＋”', async () => {
      const wrapper = await open({ defaultScope: 'suishi' })
      const cells = () => wrapper.findAll('.composer-grid .composer-cell')
      expect(cells()).toHaveLength(6)
      expect(wrapper.findAll('.composer-grid .composer-cell.add').every((cell) => cell.element.tagName === 'BUTTON')).toBe(true)
      expect(wrapper.find('.composer-grid .add-count').text()).toBe('0/9')
      const click = vi.spyOn(fileInput(wrapper), 'click')
      await wrapper.findAll('.composer-grid .composer-cell.add')[4].trigger('click')
      expect(click).toHaveBeenCalledTimes(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('image/*')
      expect(fileInput(wrapper).hasAttribute('capture')).toBe(false)

      await chooseFiles(wrapper, imageFiles(5))
      expect(wrapper.findAll('.composer-grid .composer-cell.img')).toHaveLength(5)
      expect(wrapper.findAll('.composer-grid .composer-cell.add')).toHaveLength(1) // 至少两行: 5 张 + 1 个“＋”
      expect(wrapper.find('.composer-grid .add-count').text()).toBe('5/9')

      await chooseFiles(wrapper, imageFiles(5))
      expect(wrapper.findAll('.composer-grid .composer-cell.img')).toHaveLength(9)
      expect(wrapper.findAll('.composer-grid .composer-cell.add')).toHaveLength(0)

      await wrapper.findAll('.composer-grid .composer-cell.img .cell-x')[8].trigger('click')
      expect(wrapper.findAll('.composer-grid .composer-cell.add')).toHaveLength(1)
    })

    it('“已选图片可：裁剪 / 标注 / 放大”按现有能力来；多张时点哪张就对哪张做；每张图上的放大也还在', async () => {
      const wrapper = await open({ defaultScope: 'suishi' })
      expect(wrapper.find('.pub-body .edit-tools-label').text()).toBe('已选图片可：')
      expect(texts(wrapper, TOOLS)).toEqual(['裁剪', '标注', '放大'])
      expect(wrapper.findAll(TOOLS).every((tool) => tool.attributes('disabled') !== undefined)).toBe(true)

      await chooseFiles(wrapper, imageFiles(3))
      expect(wrapper.findAll(TOOLS).every((tool) => tool.attributes('disabled') === undefined)).toBe(true)
      const openEditor = vi.fn()
      vm(wrapper).openEditor = openEditor
      await wrapper.findAll(TOOLS)[0].trigger('click')
      expect(openEditor).toHaveBeenLastCalledWith(0, 'crop')
      await wrapper.findAll('.composer-grid .composer-cell.img .cell-pick')[2].trigger('click')
      expect(vm(wrapper).editIndex).toBe(2)
      await wrapper.findAll(TOOLS)[1].trigger('click')
      expect(openEditor).toHaveBeenLastCalledWith(2, 'mark')
      await wrapper.findAll(TOOLS)[2].trigger('click')
      expect(vm(wrapper).viewer).toMatchObject({ visible: true, index: 2 })
      vm(wrapper).viewer.visible = false
      // 图上的放大键: 放大的是它自己那一张
      await wrapper.findAll('.composer-grid .composer-cell.img .cell-zoom')[0].trigger('click')
      expect(vm(wrapper).viewer).toMatchObject({ visible: true, index: 0 })
    })

    it('工具格子只放现有功能真有的：图片 / 拍摄 / 视频 / 说话 / 文件(没有位置、谁可以看、文件归档)', async () => {
      const wrapper = await open({ defaultScope: 'suishi' })
      expect(texts(wrapper, '.pub-tools .pub-tool')).toEqual(['图片', '拍摄', '视频', '说话', '文件'])
      const click = vi.spyOn(fileInput(wrapper), 'click')
      const pick = async (label) => {
        const before = click.mock.calls.length
        await buttonByText(wrapper, '.pub-tools .pub-tool', label).trigger('click')
        await flushPromises()
        return click.mock.calls.length - before
      }
      expect(await pick('图片')).toBe(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('image/*')
      expect(fileInput(wrapper).hasAttribute('capture')).toBe(false)
      expect(await pick('拍摄')).toBe(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('image/*')
      expect(fileInput(wrapper).hasAttribute('capture')).toBe(true)
      expect(await pick('视频')).toBe(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('video/*')
      expect(fileInput(wrapper).hasAttribute('capture')).toBe(false)
      // “说话”是录语音: HTTP 页面上自动改走选音频文件(现有兜底)
      expect(await pick('说话')).toBe(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('audio/*')
      expect(await pick('文件')).toBe(1)
      expect(fileInput(wrapper).getAttribute('accept')).toBe('')
    })

    it('录完的一段(保存 / 重录)、已存语音的逐段删除、视频与文件的胶囊都还在', async () => {
      const wrapper = await open({ defaultScope: 'suishi' })
      vm(wrapper).rec.url = 'blob:new'
      await nextTick()
      expect(wrapper.find('.audio-pending audio[controls]').exists()).toBe(true)
      expect(texts(wrapper, '.audio-pending .btn-stub')).toEqual(['保存语音', '重录'])
      await buttonByText(wrapper, '.audio-pending .btn-stub', '重录').trigger('click')
      expect(wrapper.find('.audio-pending').exists()).toBe(false)

      vm(wrapper).attachments.push({ key: 'a1', fileId: 1, name: 'rec-1.webm', fileType: 'audio', previewUrl: 'blob:1' })
      vm(wrapper).attachments.push({ key: 'v1', fileId: 2, name: '车间工装.mp4', fileType: 'video', previewUrl: '' })
      vm(wrapper).attachments.push({ key: 'f1', fileId: 3, name: '笔记.pdf', fileType: 'file', previewUrl: '' })
      await nextTick()
      expect(wrapper.findAll('.audio-list .audio-item audio[controls]')).toHaveLength(1)
      expect(texts(wrapper, '.file-chips .file-chip')).toEqual(['车间工装.mp4', '笔记.pdf'])
      await wrapper.findAll('.file-chips .chip-x')[0].trigger('click')
      expect(vm(wrapper).nonImages.map((a) => a.name)).toEqual(['笔记.pdf'])
      await wrapper.find('.audio-list .audio-x').trigger('click')
      expect(vm(wrapper).audioAttachments).toHaveLength(0)
    })
  })

  describe('P8 再读一本', () => {
    it('区块与顺序：❶ 选书 → ❷ 章节 → ❸ 上传内容 → 三个上传入口 → 说明', async () => {
      const wrapper = await open({ defaultScope: 'zaidu' })
      expect(outline(wrapper)).toEqual([
        '类型切换', '① 选择具体书（类别 → 书目 → 书名 / 作者 / 版本）', '三级选书',
        '② 确定章节', '分段开关', '③ 上传内容', '正文', '图片行', '说明',
      ])
      const required = wrapper.findAll('.pub-body .frm-label').filter((label) => label.find('.frm-req').exists())
      expect(required.map((label) => label.find('.frm-name').text())).toEqual([
        '① 选择具体书（类别 → 书目 → 书名 / 作者 / 版本）', '② 确定章节', '③ 上传内容',
      ])
      expect(wrapper.find('.frm-foot').text()).toBe('同一本书的章节日日积累 → 「按书浏览」按书聚合展示；精读完成后整本记录沉淀在我的觉醒。')
      expect(wrapper.find('.frm-textarea').attributes('maxlength')).toBe('2000')
    })

    it('“版本切换”一行只列同一本书的各个版本：没选书目时不出，选了有两版的书才出，点一下切过去', async () => {
      const fresh = await open({ defaultScope: 'zaidu' })
      expect(vm(fresh).editionOptions).toHaveLength(3)
      expect(fresh.find('.bcard-tags').exists()).toBe(false)

      vm(fresh).form.categoryCode = 'liaofan'
      vm(fresh).form.bookTitle = '了凡四训'
      await nextTick()
      expect(vm(fresh).versionChips.map((b) => b.id)).toEqual([1, 2])
      expect(texts(fresh, '.bcard-tags .ver-chip')).toEqual(['中华书局', '古籍出版社'])
      await fresh.findAll('.bcard-tags .ver-chip')[1].trigger('click')
      expect(vm(fresh).form.bookId).toBe(2)
      expect(fresh.findAll('.bcard-tags .ver-chip')[1].classes()).toContain('active')
      expect(fresh.findAll('.bcard-tags .ver-chip')[0].classes()).not.toContain('active')
      await flushPromises()
      expect(getFabushiSections).toHaveBeenCalledWith(2)

      // 带着预设书目打开: 按这本书的书名列版本; 只有一版的书不出这一行
      const preset = await open({ defaultScope: 'zaidu', presetBookId: 1 })
      expect(vm(preset).versionChips.map((b) => b.id)).toEqual([1, 2])
      const single = await open({ defaultScope: 'zaidu', presetBookId: 3 })
      expect(vm(single).versionChips).toEqual([])
      expect(single.find('.bcard-tags').exists()).toBe(false)
    })

    it('第三个下拉(版本)：还没选书目时带上书名，选了书目后只写版本信息', async () => {
      const wrapper = await open({ defaultScope: 'zaidu' })
      const third = () => optionLabels(wrapper, '.triple .pub-select')[2]
      expect(third()).toEqual(['《了凡四训》 · 中华书局 · 精装 · 120 页', '《了凡四训》 · 古籍出版社 · 平装 · 100 页', '《菜根谭》 · 中华书局 · 精装 · 80 页'])
      vm(wrapper).form.bookTitle = '了凡四训'
      await nextTick()
      expect(third()).toEqual(['中华书局 · 精装 · 120 页', '古籍出版社 · 平装 · 100 页'])
    })

    it('❷ 章节：选择已有章节 / 自我录入章节 两条路都在；选了书却没有章节时提示', async () => {
      const wrapper = await open({ defaultScope: 'zaidu' })
      const seg = () => texts(wrapper, '.pub-body > .seg .seg-item')
      expect(seg()).toEqual(['选择已有章节', '自我录入章节'])
      expect(wrapper.find('.pub-body > .seg .seg-item.active').text()).toBe('选择已有章节')
      expect(wrapper.find('.pub-select--full').attributes('placeholder')).toBe('选择章节（按 部 / 章 / 节 层级）')

      await wrapper.findAll('.pub-body > .seg .seg-item')[1].trigger('click')
      await nextTick()
      expect(vm(wrapper).form.sectionMode).toBe('manual')
      expect(wrapper.find('.pub-body > .seg .seg-item.active').text()).toBe('自我录入章节')
      expect(wrapper.find('.pub-select--full').attributes('placeholder')).toBe('例如：第八章 · 上善若水')
      expect(wrapper.find('.pub-select--full').attributes('maxlength')).toBe('100')

      await wrapper.findAll('.pub-body > .seg .seg-item')[0].trigger('click')
      await nextTick()
      expect(wrapper.find('.pub-select--full').attributes('placeholder')).toBe('选择章节（按 部 / 章 / 节 层级）')
      getFabushiSections.mockResolvedValue({ data: { sections: [] } })
      vm(wrapper).form.bookId = 2
      await flushPromises()
      expect(wrapper.find('.frm-hint.warn').text()).toBe('该书还没有章节结构，请切到「自我录入章节」。')
    })

    it('❸ 三个上传入口：书页图片 / 诵读语音 / 文件 · 视频；选了的东西列在下面，带数量与删除', async () => {
      const wrapper = await open({ defaultScope: 'zaidu' })
      const tiles = () => texts(wrapper, '.att-row .att-tile')
      expect(tiles()).toEqual(['书页图片0/5', '诵读语音0/10 段', '文件 / 视频0 个'])
      const input = fileInput(wrapper)
      await wrapper.findAll('.att-row .att-tile')[0].trigger('click')
      expect(input.getAttribute('accept')).toBe('image/*')
      // “诵读语音”是录语音: HTTP 页面上自动改走选音频文件(现有兜底)
      await wrapper.findAll('.att-row .att-tile')[1].trigger('click')
      await flushPromises()
      expect(input.getAttribute('accept')).toBe('audio/*')
      await wrapper.findAll('.att-row .att-tile')[2].trigger('click')
      expect(input.getAttribute('accept')).toBe('')

      await chooseFiles(wrapper, imageFiles(2))
      vm(wrapper).attachments.push({ key: 'a1', fileId: 1, name: 'rec-1.webm', fileType: 'audio', previewUrl: 'blob:1' })
      vm(wrapper).attachments.push({ key: 'f1', fileId: 2, name: '读书笔记.pdf', fileType: 'file', previewUrl: '' })
      await nextTick()
      expect(tiles()).toEqual(['书页图片2/5', '诵读语音1/10 段', '文件 / 视频1 个'])
      expect(outline(wrapper)).toEqual([
        '类型切换', '① 选择具体书（类别 → 书目 → 书名 / 作者 / 版本）', '三级选书',
        '② 确定章节', '分段开关', '③ 上传内容', '正文', '图片行', '图片格子', '语音列表', '文件', '说明',
      ])
      expect(wrapper.findAll('.att-thumbs .composer-cell.img')).toHaveLength(2)
      expect(wrapper.find('.att-thumbs .add-count').text()).toBe('2/5')
      await wrapper.findAll('.att-thumbs .composer-cell.img .cell-x')[0].trigger('click')
      expect(vm(wrapper).images).toHaveLength(1)
      await wrapper.findAll('.file-chips .chip-x')[0].trigger('click')
      expect(vm(wrapper).nonImages).toHaveLength(0)
      await wrapper.find('.audio-list .audio-x').trigger('click')
      expect(vm(wrapper).audioAttachments).toHaveLength(0)
    })
  })

  it('评审标注不进界面：没有 D / Q 编号、权限码、“演示”、文档路径', async () => {
    const wrapper = await open()
    const seen = []
    for (const scope of ['songdu', 'suishi', 'zaidu']) {
      vm(wrapper).switchType(scope)
      vm(wrapper).form.channel = 'B'
      await nextTick()
      seen.push(wrapper.find('.pub-body').text())
    }
    const text = seen.join('\n')
    expect(text).not.toMatch(/\b[DGQ]\d{1,2}\b/)
    expect(text).not.toContain('fabushi:')
    expect(text).not.toContain('演示')
    expect(text).not.toContain('docs/prototypes')
  })

  it('图标不用表情符号：图标来自 @element-plus/icons-vue、放在显式引入的 ElIcon 里；公共样式 fabushi-v8.css 有引入', () => {
    const src = readFileSync('src/components/fabushi/FabushiPublishDialog.vue', 'utf-8')
    expect(src).toContain("import '@/assets/fabushi-v8.css'")
    expect(src).toMatch(/import \{[^}]*\bElIcon\b[^}]*\} from 'element-plus'/)
    expect(src).toContain("from '@element-plus/icons-vue'")
    // 现有的 FabushiPublishDialog.spec.js 没有给 el-icon 准备桩, 所以模板里不写自动引入的 <el-icon>(注释里提到不算)
    const code = src.replace(/<!--[\s\S]*?-->|\/\*[\s\S]*?\*\/|^[ \t]*\/\/.*$/gm, '')
    expect(code).not.toMatch(/<el-icon[\s>]/)
    expect(src).not.toMatch(/\p{Extended_Pictographic}/u)
  })
})
