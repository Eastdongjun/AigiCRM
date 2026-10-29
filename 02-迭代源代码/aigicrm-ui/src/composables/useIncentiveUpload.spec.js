// C4 拆组件后为激励上传/管理页共享态 composable 补回归测试（2026-08-09）
// 覆盖: 品类判定(categoryCodeOf/isSingleSlot)、canSubmit、confirmUpload 槽位 ID 序列化(单槽/三槽)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/incentive', () => ({
  getCategoryTree: vi.fn(),
  getRecords: vi.fn(),
  addRecord: vi.fn(),
  deleteRecord: vi.fn(),
  updateRecord: vi.fn(),
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
vi.mock('@/utils/request', () => ({ default: { post: vi.fn() } }))

import { useIncentiveUpload } from './useIncentiveUpload'
import { addRecord, deleteRecord, updateRecord, getRecords } from '@/api/incentive'
import request from '@/utils/request'

const cats = [
  { id: 1, parentId: 0, categoryCode: 'visit', categoryName: '交流拜访', score: 1 },
  { id: 2, parentId: 1, categoryCode: 'visit_factory', categoryName: '工厂参观', score: 2 },
  { id: 3, parentId: 0, categoryCode: 'trial', categoryName: '产品试点', score: 1 },
  { id: 4, parentId: 3, categoryCode: 'trial_seal', categoryName: '密封试点', score: 3 },
]

describe('useIncentiveUpload 品类判定与提交序列化', () => {
  let list
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // jsdom 无 URL.createObjectURL，stub 供 previewCardFile 用
    vi.stubGlobal('URL', { ...globalThis.URL, createObjectURL: vi.fn(() => 'blob:fake'), revokeObjectURL: vi.fn() })
    list = useIncentiveUpload()
    list.categories.value = cats
  })

  it('categoryCodeOf 子分类上溯到顶层品类 code', () => {
    expect(list.categoryCodeOf(cats[1])).toBe('visit')   // visit_factory → visit
    expect(list.categoryCodeOf(cats[3])).toBe('trial')   // trial_seal → trial
    expect(list.categoryCodeOf(cats[0])).toBe('visit')   // 顶层自身
  })

  it('isSingleSlot: visit/exchange/plan 单槽, trial 三槽', () => {
    list.pendingChild.value = cats[1]   // visit 子类
    expect(list.isSingleSlot.value).toBe(true)
    list.pendingChild.value = cats[3]   // trial 子类
    expect(list.isSingleSlot.value).toBe(false)
  })

  it('canSubmit: 无客户不可提交; 单槽需至少 1 个文件', () => {
    list.pendingChild.value = cats[1]   // visit 单槽
    list.selectedCustomerId.value = null
    expect(list.canSubmit.value).toBe(false)
    list.selectedCustomerId.value = 5
    expect(list.canSubmit.value).toBe(false)   // 单槽无文件
    list.installFiles.pre = [{ name: 'a.jpg' }]
    expect(list.canSubmit.value).toBe(true)
  })

  it('confirmUpload 新上传(单槽 visit): filePreId 序列化 JSON, 中/后/跟踪槽置 null', async () => {
    list.pendingChild.value = cats[1]           // visit 单槽
    list.selectedCustomerId.value = 5
    list.installFiles.pre = [{ name: 'a.jpg' }] // 新文件
    request.post.mockResolvedValue({ code: 200, data: { id: 100 } })
    addRecord.mockResolvedValue({})

    await list.confirmUpload()

    expect(addRecord).toHaveBeenCalledTimes(1)
    const payload = addRecord.mock.calls[0][0]
    expect(payload.categoryId).toBe(2)   // cats[1].id = 2 (visit_factory)
    expect(payload.customerId).toBe(5)
    expect(payload.filePreId).toBe('[100]')     // 新文件 id 序列化为 JSON 数组
    expect(payload.fileMidId).toBeNull()        // 单槽: 中槽不提交
    expect(payload.filePostId).toBeNull()
    expect(payload.fileTrackId).toBeNull()
    expect(payload.shared).toBe(1)              // 默认共享
    // 弹窗关闭 + 记录刷新
    expect(list.showCustomerDialog.value).toBe(false)
  })

  it('confirmUpload 新上传(三槽 trial): pre/mid/post/track 各自序列化', async () => {
    list.pendingChild.value = cats[3]           // trial 三槽
    list.selectedCustomerId.value = 6
    list.installFiles.pre = [{ name: 'a.jpg' }]
    list.installFiles.mid = [{ name: 'b.jpg' }, { name: 'c.jpg' }]
    list.installFiles.post = [{ name: 'd.jpg' }]
    list.trackFile.value = [{ name: 'e.jpg' }]
    let seq = 0
    request.post.mockImplementation(() => Promise.resolve({ code: 200, data: { id: 200 + (seq++) } }))
    addRecord.mockResolvedValue({})

    await list.confirmUpload()

    const payload = addRecord.mock.calls[0][0]
    expect(payload.filePreId).toBe('[200]')
    expect(payload.fileMidId).toBe('[201,202]')   // 两个中槽文件
    expect(payload.filePostId).toBe('[203]')
    expect(payload.fileTrackId).toBe('[204]')
  })

  it('confirmUpload 追加模式: 保留已有文件 id + 新文件 id, 改走 updateRecord 保留原记录', async () => {
    list.pendingChild.value = cats[1]
    list.pendingReplace.value = { id: 9, filePreId: '[50]', fileMidId: null, filePostId: null, fileTrackId: null }
    list.selectedCustomerId.value = 5
    // 已有文件(_existing, id=50) + 一个新文件
    list.installFiles.pre = [{ name: 'old.jpg', id: 50, _existing: true }, { name: 'new.jpg' }]
    request.post.mockResolvedValue({ code: 200, data: { id: 100 } })
    addRecord.mockResolvedValue({})
    deleteRecord.mockResolvedValue({})
    updateRecord.mockResolvedValue({})

    await list.confirmUpload()

    // 追加走 updateRecord(PUT)：保留原记录 id/userId/createdAt，初始上传人不被改写
    expect(deleteRecord).not.toHaveBeenCalled()
    expect(addRecord).not.toHaveBeenCalled()
    expect(updateRecord).toHaveBeenCalledWith(9, expect.any(Object))
    const payload = updateRecord.mock.calls[0][1]
    expect(payload.filePreId).toBe('[50,100]')     // 保留旧 + 新
    expect(payload.fileMidId).toBeNull()
    // 编辑 payload 不应携带 categoryId/period（保留原记录值）
    expect(payload).not.toHaveProperty('categoryId')
    expect(payload).not.toHaveProperty('period')
  })

  it('toggleShare 非本人拒绝（admin/can_edit 也不能改分享）', async () => {
    list.userStore.userId = 2
    const row = { id: 9, userId: 1, shared: 1 }
    updateRecord.mockResolvedValue({})

    await list.toggleShare(row, false)

    expect(updateRecord).not.toHaveBeenCalled()
    expect(row.shared).toBe(1)  // 乐观更新未发生
  })

  it('confirmUpload 编辑本人记录携带 shared', async () => {
    list.pendingChild.value = cats[1]
    list.pendingReplace.value = { id: 9, userId: 1, filePreId: '[50]', fileMidId: null, filePostId: null, fileTrackId: null }
    list.userStore.userId = 1
    list.shareToTeam.value = true
    list.selectedCustomerId.value = 5
    list.installFiles.pre = [{ name: 'old.jpg', id: 50, _existing: true }]
    request.post.mockResolvedValue({ code: 200, data: { id: 100 } })
    addRecord.mockResolvedValue({})
    deleteRecord.mockResolvedValue({})
    updateRecord.mockResolvedValue({})

    await list.confirmUpload()

    expect(updateRecord).toHaveBeenCalled()
    const payload = updateRecord.mock.calls[0][1]
    expect(payload.shared).toBe(1)  // 本人编辑携带 shared
  })

  it('confirmUpload 编辑他人记录不携带 shared', async () => {
    list.pendingChild.value = cats[1]
    list.pendingReplace.value = { id: 9, userId: 2, filePreId: '[50]', fileMidId: null, filePostId: null, fileTrackId: null }
    list.userStore.userId = 1
    list.shareToTeam.value = true
    list.selectedCustomerId.value = 5
    list.installFiles.pre = [{ name: 'old.jpg', id: 50, _existing: true }]
    request.post.mockResolvedValue({ code: 200, data: { id: 100 } })
    addRecord.mockResolvedValue({})
    deleteRecord.mockResolvedValue({})
    updateRecord.mockResolvedValue({})

    await list.confirmUpload()

    expect(updateRecord).toHaveBeenCalled()
    const payload = updateRecord.mock.calls[0][1]
    expect(payload).not.toHaveProperty('shared')  // 非本人编辑不携带 shared
  })

  it('fileType 判定 image/video/doc', () => {
    expect(list.fileType('a.mp4')).toBe('video')
    expect(list.fileType('a.jpg')).toBe('image')
    expect(list.fileType('a.pdf')).toBe('doc')
  })

  it('previewCardFile 新文件(视频/图片)用本地 objectURL', async () => {
    const f = { name: 'a.mp4' }
    await list.previewCardFile(f)
    expect(list.previewVisible.value).toBe(true)
    expect(list.previewType.value).toBe('video')
    expect(list.previewUrl.value).toBe('blob:fake')
    expect(list.previewName.value).toBe('a.mp4')
    expect(f._url).toBe('blob:fake')  // 缓存，二次调用不新建

    const img = { name: 'b.jpg' }
    await list.previewCardFile(img)
    expect(list.previewType.value).toBe('image')
  })

  it('previewCardFile 已提交文件(_existing)走远端 fileUrl', async () => {
    await list.previewCardFile({ name: 'old.mp4', id: 50, _existing: true })
    expect(list.previewVisible.value).toBe(true)
    expect(list.previewType.value).toBe('video')
    expect(list.previewUrl.value).toContain('/api/file/download/50?token=')
  })

  it('previewCardFile null/无id no-op', async () => {
    await list.previewCardFile(null)
    expect(list.previewVisible.value).toBe(false)
    await list.previewCardFile({ name: 'x.mp4', id: null, _existing: true })
    expect(list.previewVisible.value).toBe(false)
  })

  // 2026-09-12 回归：默认周期 '2026上半年' + 每次都带该参数 ⇒ 打开页面即 0 条（库内数据是 '2026全年'）
  it('周期口径: 默认「全部」、选项含「全年」、请求不携带空周期', async () => {
    const currentYear = new Date().getFullYear()
    expect(list.selectedPeriod.value).toBe('')
    expect(list.periodOptions.map((o) => o.value)).toContain(`${currentYear}全年`)
    expect(list.periodOptions[0]).toEqual({ label: '全部', value: '' })

    getRecords.mockResolvedValue({ data: [] })
    await list.loadRecords()
    expect(getRecords).toHaveBeenCalledWith(expect.objectContaining({ period: undefined }))
  })

  // 2026-10-29 左侧子分类联动 + 筛选栏新增(行业/时间范围)
  it('左侧分类联动: 选中只留该 categoryId 记录, 再点取消恢复, selectedCategoryName 供标签展示', () => {
    list.records.value = [
      { id: 11, categoryId: 4, customerName: 'A', createdAt: '2026-10-20 10:00:00' },
      { id: 12, categoryId: 2, customerName: 'B', createdAt: '2026-10-21 10:00:00' },
    ]
    expect(list.filteredRecords.value.length).toBe(2)

    list.toggleCategoryFilter(4)
    expect(list.selectedCategoryId.value).toBe(4)
    expect(list.selectedCategoryName.value).toBe('密封试点')
    expect(list.filteredRecords.value.map((r) => r.id)).toEqual([11])

    list.toggleCategoryFilter(4)
    expect(list.selectedCategoryId.value).toBe(null)
    expect(list.selectedCategoryName.value).toBe('')
    expect(list.filteredRecords.value.length).toBe(2)
  })

  it('行业与时间范围筛选: 行业等值过滤, 时间按上传日期闭区间过滤', () => {
    list.records.value = [
      { id: 21, categoryId: 4, industry: '化工', createdAt: '2026-10-01 09:00:00' },
      { id: 22, categoryId: 4, industry: '电力', createdAt: '2026-10-15 09:00:00' },
      { id: 23, categoryId: 4, industry: '化工', createdAt: '2026-11-01 09:00:00' },
    ]
    list.filterIndustry.value = '化工'
    // 同品类按时间倒序: 23 在前
    expect(list.filteredRecords.value.map((r) => r.id)).toEqual([23, 21])

    list.filterIndustry.value = ''
    list.filterDateRange.value = ['2026-10-01', '2026-10-31']
    expect(list.filteredRecords.value.map((r) => r.id)).toEqual([22, 21])

    // 清空区间恢复全量
    list.filterDateRange.value = []
    expect(list.filteredRecords.value.length).toBe(3)
  })
})
