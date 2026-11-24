import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, provide } from 'vue'

// 【GL-14 2026-11-24】激励上传弹窗的挂载测试（此前只有共享状态 useIncentiveUpload 有单测，弹窗本身没有）。
// 缘起: 静态检查发现模板把 :files="trackFile" 传给“跟踪记录”那一格, 但 script 里没有从共享状态解构 trackFile
//       ——翻页组件一渲染就读 files.length, 拿到 undefined 直接抛错, 这一格整个渲染不出来:
//       已有的跟踪文件看不到, 也没有“选择文件”的入口(添加跟踪文件的唯一入口)。自 2026-10-30 起就在生产上。
// 口径: 弹窗与翻页组件用真实的; 共享状态用真实的 useIncentiveUpload(接口替身沿用 useIncentiveUpload.spec.js);
//       Element Plus 组件与图标用桩。判定: 全程没有 Vue 警告和错误, 各格收到的文件数组正确, 该显示的都显示出来。

vi.mock('@/api/incentive', () => ({
  getCategoryTree: vi.fn(),
  getRecords: vi.fn(),
  addRecord: vi.fn(),
  deleteRecord: vi.fn(),
  updateRecord: vi.fn(),
  removeRecordFile: vi.fn(),
  checkIncentivePermission: vi.fn(),
}))
vi.mock('@/api/customer', () => ({ getCustomerList: vi.fn() }))
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/utils/layout-cache', () => ({ cachedGet: vi.fn() }))
vi.mock('@/utils/token', () => ({
  getDownloadToken: vi.fn(() => 't'),
  refreshDownloadToken: vi.fn(() => Promise.resolve('t')),
}))
vi.mock('@/utils/confirm', () => ({ confirmAction: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { post: vi.fn(), get: vi.fn() } }))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal(),
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
}))

import { useIncentiveUpload, IncentiveUploadKey } from '@/composables/useIncentiveUpload'
import { getCustomerList as getCustomerListMock } from '@/api/customer'
import UploadDialog from './UploadDialog.vue'
import FileSlotPager from './FileSlotPager.vue'

// vi.mock 的工厂返回的是 vi.fn(): 这里按 Mock 取用, 以便设应答(类型检查基线只认声明的类型)
const getCustomerList = /** @type {import('vitest').Mock} */ (getCustomerListMock)

const cats = [
  { id: 1, parentId: 0, categoryCode: 'visit', categoryName: '交流拜访', score: 1 },
  { id: 2, parentId: 1, categoryCode: 'visit_factory', categoryName: '工厂参观', score: 2 },
  { id: 3, parentId: 0, categoryCode: 'trial', categoryName: '产品试点', score: 1 },
  { id: 4, parentId: 3, categoryCode: 'trial_seal', categoryName: '密封试点', score: 3 },
]

// 全局注册的图标组件在测试里没人注册, 统一给桩
const ICONS = ['ArrowLeft', 'ArrowRight', 'Close', 'Document', 'Edit', 'InfoFilled', 'Notebook', 'Picture', 'Plus', 'User', 'VideoPlay']
const SLOT = { template: '<div class="el-stub"><slot name="header" /><slot /><slot name="footer" /></div>' }
const stubs = Object.assign(
  { 'el-dialog': SLOT, 'el-icon': SLOT, 'el-button': SLOT, 'el-input': SLOT, 'el-select': SLOT, 'el-option': SLOT, 'el-switch': SLOT, 'el-tag': SLOT },
  Object.fromEntries(ICONS.map((name) => [name, { template: '<i />' }])),
)

describe('incentive/UploadDialog.vue 挂载', () => {
  let ctx
  let warnings
  let errors
  const Host = defineComponent({
    setup() {
      ctx = useIncentiveUpload()
      provide(IncentiveUploadKey, ctx)
      return () => h(UploadDialog)
    },
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getCustomerList.mockResolvedValue({ data: { records: [], total: 0 } })
    vi.stubGlobal('URL', { ...globalThis.URL, createObjectURL: vi.fn(() => 'blob:fake'), revokeObjectURL: vi.fn() })
  })

  async function openFor(child) {
    warnings = []
    errors = []
    const wrapper = mount(Host, {
      global: {
        stubs,
        config: {
          warnHandler: (msg) => warnings.push(msg),
          errorHandler: (err) => errors.push(err),
        },
      },
    })
    ctx.categories.value = cats
    ctx.startUpload(child)
    await flushPromises()
    return wrapper
  }

  it('三槽模式：跟踪记录那一格收得到文件数组，已有的跟踪文件和“选择文件”入口都显示出来', async () => {
    const wrapper = await openFor(cats[4 - 1]) // trial 子类 → 三槽
    // 追加/编辑时已有的跟踪文件由共享状态解析进来
    ctx.trackFile.value = [{ id: 77, name: '跟踪记录.pdf', _existing: true }]
    await flushPromises()

    expect(errors).toEqual([])
    expect(warnings).toEqual([])
    // 安装前 / 安装中 / 安装后 / 跟踪记录 四格
    const pagers = wrapper.findAllComponents(FileSlotPager)
    expect(pagers.map((pager) => pager.props('slotKey'))).toEqual(['pre', 'mid', 'post', 'track'])
    expect(pagers[3].props('files')).toEqual([{ id: 77, name: '跟踪记录.pdf', _existing: true }])
    expect(pagers[3].text()).toContain('跟踪记录.pdf')
    expect(pagers[3].text()).toContain('选择文件')
  })

  it('三槽模式：点“选择文件”走的是挑跟踪文件的入口', async () => {
    const wrapper = await openFor(cats[3])
    const click = vi.fn()
    ctx.fileInputRef.value = { click }

    const addCard = wrapper.findAllComponents(FileSlotPager)[3].find('.file-card.add')
    expect(addCard.exists()).toBe(true)
    await addCard.trigger('click')

    expect(click).toHaveBeenCalledTimes(1)
    expect(errors).toEqual([])
    expect(warnings).toEqual([])
  })

  it('单槽模式：没有跟踪记录那一格，其余照常渲染', async () => {
    const wrapper = await openFor(cats[1]) // visit 子类 → 单槽

    expect(errors).toEqual([])
    expect(warnings).toEqual([])
    const pagers = wrapper.findAllComponents(FileSlotPager)
    expect(pagers.map((pager) => pager.props('slotKey'))).toEqual(['pre'])
    expect(wrapper.text()).not.toContain('选择文件')
    expect(pagers[0].props('files')).toEqual([])
  })
})
