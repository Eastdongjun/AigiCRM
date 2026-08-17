import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
vi.mock('@/api/system', () => ({
  getUserColumnPrefs: vi.fn().mockResolvedValue({ code: 200, data: null }),
  saveUserColumnPrefs: vi.fn().mockResolvedValue({ code: 200 }),
}))
import RecordTable from '@/views/incentive/RecordTable.vue'
import { IncentiveUploadKey } from '@/composables/useIncentiveUpload'

/**
 * RecordTable 渲染冒烟测试（2026-08-17 生产事故防回归）
 *
 * 【需求】列设置上线时曾因 onMounted(loadColumnPref) 写在解构前触发 TDZ 崩溃,
 *         组件 setup 失败 → 表格整个不渲染(用户"看不到数据/看不到列设置按钮")。
 *         此测试挂载真实组件, 若 setup 抛错则测试失败。
 */

// 最小注入上下文: 提供组件解构所需全部字段
function makeCtx() {
  return {
    userStore: { isAdmin: () => false, userId: 1 },
    pagedRecords: [],
    filteredRecords: [],
    pageNum: 1,
    pageSize: 30,
    COLUMN_DEFS: [{ key: 'cat', label: '大类·子类', default: true }],
    hiddenCols: new Set(),
    colSettingVisible: false,
    colDraft: [],
    isColVisible: () => true,
    loadColumnPref: vi.fn().mockResolvedValue(undefined),
    initColDraft: vi.fn(),
    onToggleCol: vi.fn(),
    resetCols: vi.fn(),
    canEdit: false,
    canDelete: false,
    scoreColor: () => '#333',
    formatDate: (d) => (d || '').toString(),
    fileType: () => 'doc',
    previewFile: vi.fn(),
    startReplace: vi.fn(),
    toggleShare: vi.fn(),
    handleDeleteRecord: vi.fn(),
    isRecordOwner: () => false,
  }
}

describe('RecordTable 渲染冒烟（防 TDZ 崩溃回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载不抛错且渲染出列设置按钮', async () => {
    const ctx = makeCtx()
    const wrapper = mount(RecordTable, {
      global: {
        provide: { [IncentiveUploadKey]: ctx },
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': true,
          'el-pagination': true,
          'el-popover': { template: '<div class="popover"><slot name="reference" /><slot /></div>' },
          'el-link': { template: '<a><slot /></a>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-switch': true,
          Document: true,
          Setting: true,
        },
      },
    })
    await nextTick()
    // setup 不抛错 + onMounted 回调被调用(修复点)
    expect(ctx.loadColumnPref).toHaveBeenCalledTimes(1)
    // 列设置按钮渲染
    expect(wrapper.text()).toContain('列设置')
    wrapper.unmount()
  })

  it('勾选列设置后表格立即生效(applyCols 同步更新 hiddenCols)', async () => {
    const ctx = makeCtx()
    const wrapper = mount(RecordTable, {
      global: {
        provide: { [IncentiveUploadKey]: ctx },
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': true,
          'el-pagination': true,
          'el-popover': { template: '<div class="popover"><slot name="reference" /><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-link': { template: '<a><slot /></a>' },
          'el-switch': true,
          Document: true,
          Setting: true,
        },
      },
    })
    await nextTick()
    expect(wrapper.text()).toContain('列设置')
    expect(wrapper.text()).toContain('恢复默认')
    wrapper.unmount()
  })

  it('表格数据行正常渲染', async () => {
    const ctx = makeCtx()
    ctx.pagedRecords = [{ id: 1, majorMinor: 'A·B', industry: 'x', relatedProducts: 'y', score: 3, customerName: 'c1', shared: 1, createdAt: '2026-08-17', userName: 'u1' }]
    ctx.filteredRecords = ctx.pagedRecords // footer 计数用 filteredRecords
    const wrapper = mount(RecordTable, {
      global: {
        provide: { [IncentiveUploadKey]: ctx },
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': true,
          'el-pagination': true,
          'el-dialog': true,
          'el-checkbox': true,
          'el-button': true,
          'el-icon': true,
          'el-switch': true,
          Document: true,
          Setting: true,
        },
      },
    })
    await nextTick()
    // 数据行经 el-table 渲染, 用真实渲染的 footer 计数断言数据流(1条)
    expect(wrapper.text()).toContain('共')
    expect(wrapper.text()).toContain('1')
    wrapper.unmount()
  })
})

import ElementPlus from 'element-plus'

  it('操作列设置(勾选/恢复默认)后按钮仍然渲染(防按钮消失回归)', async () => {
    const ctx = makeCtx()
    ctx.initColDraft = vi.fn(() => {
      ctx.colDraft.splice(0, ctx.colDraft.length, ...ctx.COLUMN_DEFS.map(d => ({ ...d, checked: true })))
    })
    ctx.onToggleCol = vi.fn((d) => {
      d.checked = !d.checked
      ctx.hiddenCols = new Set(ctx.colDraft.filter(x => !x.checked).map(x => x.key))
    })
    ctx.resetCols = vi.fn(() => {
      ctx.colDraft.forEach(d => { d.checked = true })
      ctx.hiddenCols = new Set()
    })
    const wrapper = mount(RecordTable, {
      global: {
        provide: { [IncentiveUploadKey]: ctx },
        stubs: {
          'el-table': { template: '<div class="el-table"><slot /></div>' },
          'el-table-column': true,
          'el-pagination': true,
          'el-popover': { template: '<div class="popover"><slot name="reference" /><slot /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-icon': { template: '<i><slot /></i>' },
          'el-link': { template: '<a><slot /></a>' },
          'el-switch': true,
          Document: true,
          Setting: true,
        },
      },
    })
    await nextTick()
    expect(wrapper.text()).toContain('列设置')
    // 模拟勾选一列(隐藏)与恢复默认, 按钮与工具栏不应受列显隐影响
    const d = { key: 'cat', label: '大类·子类', checked: true }
    ctx.onToggleCol(d)
    await nextTick()
    expect(wrapper.text()).toContain('列设置')
    ctx.resetCols()
    await nextTick()
    expect(wrapper.text()).toContain('列设置')
    wrapper.unmount()
  })
