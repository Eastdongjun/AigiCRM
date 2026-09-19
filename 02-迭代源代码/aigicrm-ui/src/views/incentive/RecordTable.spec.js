import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

import RecordTable from './RecordTable.vue'
import { IncentiveUploadKey } from '@/composables/useIncentiveUpload'

// 用户诉求（2026-09-13）：列表底部缺「每页 30/50/100」的选择器
const pageNum = ref(1)
const pageSize = ref(10)
const records = [{ id: 1, majorMinor: '交流拜访 · 高层拜访', score: 2, customerName: '客户A', createdAt: '2026-09-01T10:00:00' }]

const ctx = {
  userStore: { isAdmin: () => true, userId: 1, hasPermission: () => true },
  pagedRecords: ref(records),
  filteredRecords: ref(records),
  pageNum,
  pageSize,
  canEdit: ref(true),
  canDelete: ref(true),
  scoreColor: () => '#67c23a',
  formatDate: (v) => String(v || '').slice(0, 10),
  fileType: () => 'doc',
  previewFile: vi.fn(),
  startReplace: vi.fn(),
  toggleShare: vi.fn(),
  handleDeleteRecord: vi.fn(),
  isRecordOwner: () => true,
}

// el-pagination stub 用组件定义引用查找（按 name 查 stub 不稳）
const ElPaginationStub = {
  name: 'ElPagination',
  props: ['pageSizes', 'layout', 'total', 'pageSize'],
  template: '<div class="pager" />',
}

const stubs = {
  'el-table': { template: '<div class="table"><slot /></div>' },
  'el-table-column': { template: '<div />' },
  'el-switch': { template: '<div class="switch" />' },
  'el-icon': { template: '<i><slot /></i>' },
  Document: { template: '<i />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-pagination': ElPaginationStub,
}

function mountTable() {
  return mount(RecordTable, {
    global: { provide: { [IncentiveUploadKey]: ctx }, stubs },
  })
}

describe('激励上传「已上传文件」分页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pageNum.value = 1
    pageSize.value = 10
  })

  it('分页带「每页条数」下拉，选项含 30/50/100', () => {
    const wrapper = mountTable()
    const pager = wrapper.findComponent(ElPaginationStub)

    expect(pager.props('layout')).toContain('sizes')
    expect(pager.props('pageSizes')).toEqual(expect.arrayContaining([30, 50, 100]))
    // 当前默认 10/页 必须仍在选项内，否则下拉会显示空值
    expect(pager.props('pageSizes')).toContain(10)
  })

  it('切换每页条数后回到第 1 页（避免停在已不存在的页码看到空表）', async () => {
    const wrapper = mountTable()
    pageNum.value = 3

    wrapper.vm.onSizeChange(50)

    expect(pageSize.value).toBe(50)
    expect(pageNum.value).toBe(1)
  })
})
