import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

const back = vi.fn()
const route = {
  params: { childId: '30' },
  query: {
    parentId: '4',
    parentName: '产品试点',
    childName: '项目方案',
    score: '2',
    period: '2026全年',
  },
}

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, useRoute: () => route, useRouter: () => ({ back }) }
})

vi.mock('@/api/incentive', () => ({
  getRecords: vi.fn(),
}))

vi.mock('@/utils/token', () => ({
  getDownloadToken: vi.fn(() => 'download-token'),
  getToken: vi.fn(() => 'access-token'),
  refreshDownloadToken: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/utils/download', () => ({
  downloadFile: vi.fn(() => Promise.resolve()),
}))

import StatsDetail from './StatsDetail.vue'
import { getRecords } from '@/api/incentive'
import { downloadFile } from '@/utils/download'

const stubs = {
  'el-button': { template: '<button @click="$emit(`click`)"><slot /></button>' },
  'el-table': {
    props: ['data'],
    template: `
      <div class="table">
        <div v-for="row in data" :key="row.id" class="row">
          {{ row.customerName }} {{ row.userName }} {{ row.filePreName }} {{ row.remark }}
        </div>
      </div>
    `,
  },
  'el-table-column': true,
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function rows() {
  return [
    {
      id: 1,
      categoryId: 30,
      customerName: '山西平阳重工',
      userName: '张销售',
      createdAt: '2026-09-13T11:20:38',
      score: 2,
      filePreIds: [11],
      filePreName: '安装前方案.pdf',
      remark: '已上传',
    },
    {
      id: 2,
      categoryId: 31,
      customerName: '不应展示客户',
      score: 8,
    },
  ]
}

describe('incentive/StatsDetail.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.params.childId = '30'
    route.query = {
      parentId: '4',
      parentName: '产品试点',
      childName: '项目方案',
      score: '2',
      period: '2026全年',
    }
    getRecords.mockResolvedValue({ data: rows() })
  })

  it('loads parent category records and renders only the selected child category details', async () => {
    const wrapper = mount(StatsDetail, { global: { stubs } })
    await flushPromises()

    expect(getRecords).toHaveBeenCalledWith({ period: '2026全年', parentCategoryId: 4 })
    const text = wrapper.text()
    expect(text).toContain('项目方案 明细')
    expect(text).toContain('产品试点')
    expect(text).toContain('记录：1 条')
    expect(text).toContain('合计：2 分')
    expect(text).toContain('山西平阳重工')
    expect(text).toContain('安装前方案.pdf')
    expect(text).not.toContain('不应展示客户')
  })

  it('downloads slot attachments through the tokenized file endpoint', async () => {
    const wrapper = mount(StatsDetail, { global: { stubs } })
    await flushPromises()

    await wrapper.vm.download({ id: 11, name: '安装前方案.pdf' })

    expect(downloadFile).toHaveBeenCalledWith('/api/file/download/11?token=download-token', {
      name: '安装前方案.pdf',
      headers: { Authorization: 'Bearer access-token' },
    })
  })
})
