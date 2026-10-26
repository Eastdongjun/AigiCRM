// 客户详情激励Tab预览/文件能力回归测试（2026-08-12）
// 覆盖: detectType 三态、getFiles 四槽、previewIncentiveFile(预览打开)、downloadIncentiveFile
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' }, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/api/customer', () => ({
  getCustomer: vi.fn(), getCustomerLogs: vi.fn(), addCustomerLog: vi.fn(), updateCustomer: vi.fn(),
  getInvoices: vi.fn(), getInvoiceSummary: vi.fn(), addInvoice: vi.fn(), deleteInvoice: vi.fn(),
  getPayments: vi.fn(), getPaymentSummary: vi.fn(), addPayment: vi.fn(), deletePayment: vi.fn(),
  getContacts: vi.fn(), addContact: vi.fn(), updateContact: vi.fn(), deleteContact: vi.fn(),
  getIndicator: vi.fn(), saveIndicator: vi.fn(), saveRelationship: vi.fn(),
  getShares: vi.fn(), addShares: vi.fn(), removeShare: vi.fn(), getSimpleUserList: vi.fn(),
  getStageAmounts: vi.fn(() => Promise.resolve({ data: null })),
}))
vi.mock('@/api/system', () => ({ getDictData: vi.fn() }))
vi.mock('@/api/approval', () => ({ submitApproval: vi.fn() }))
vi.mock('@/api/incentive', () => ({ getScoreByCustomer: vi.fn(), getCategoryTree: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))
vi.mock('@/utils/token', () => ({
  getDownloadToken: vi.fn(() => 't'),
  refreshDownloadToken: vi.fn(() => Promise.resolve('t')),
}))
vi.mock('@/utils/download', () => ({ downloadFile: vi.fn() }))

import { useCustomerDetail } from './useCustomerDetail'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { getCustomer, getCustomerLogs, getIndicator, getShares, saveIndicator } from '@/api/customer'
import { getScoreByCustomer } from '@/api/incentive'
import { submitApproval } from '@/api/approval'

describe('useCustomerDetail 激励Tab文件能力', () => {
  let detail
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 【2026-10-27 深度检查修复】激励分区补了 incentive:view 门控(与财务/联系人同款 B5 模式):
    // 桩权限补齐三码, 否则激励积分 task 不发请求、incentiveScore 恒 0
    useUserStore().permissions = new Set(['finance:view', 'contact:view', 'incentive:view'])
    detail = useCustomerDetail()
  })

  it('detectType 判定 image/video/doc', () => {
    expect(detail.detectType('a.mp4')).toBe('video')
    expect(detail.detectType('a.jpg')).toBe('image')
    expect(detail.detectType('a.pdf')).toBe('doc')
    expect(detail.detectType(null)).toBe('doc')
  })

  it('getFiles 解析 pre/mid/post/file 四槽', () => {
    const r = {
      filePreId: '[50]', filePreName: 'pre.jpg',
      fileMidId: '[51]', fileMidName: 'mid.mp4',
      filePostId: '[52]', filePostName: 'post.pdf',
      fileId: '[53]', fileName: 'file.png',
    }
    const files = detail.getFiles(r)
    expect(files).toHaveLength(4)
    expect(files[0]).toMatchObject({ id: 50, type: 'image', tag: '安装前' })
    expect(files[1]).toMatchObject({ id: 51, type: 'video', tag: '安装中' })
    expect(files[2]).toMatchObject({ id: 52, type: 'doc', tag: '安装后' })
    expect(files[3]).toMatchObject({ id: 53, type: 'image', tag: '' })
  })

  it('previewIncentiveFile 打开预览(远端 url + 类型)', async () => {
    await detail.previewIncentiveFile({ id: 5, label: 'a.mp4', type: 'video' })
    expect(detail.filePreviewVisible.value).toBe(true)
    expect(detail.filePreviewUrl.value).toBe('/api/file/download/5?token=t')
    expect(detail.filePreviewType.value).toBe('video')
    expect(detail.filePreviewName.value).toBe('a.mp4')
  })

  it('previewIncentiveFile 无 id no-op', async () => {
    await detail.previewIncentiveFile({ id: null, label: 'x.mp4' })
    expect(detail.filePreviewVisible.value).toBe(false)
  })

  it('核心指标保存失败时显示后端错误', async () => {
    saveIndicator.mockRejectedValueOnce({ response: { data: { message: '没有访问权限' } } })
    await detail.handleSaveIndicator()
    expect(ElMessage.error).toHaveBeenCalledWith('没有访问权限')
  })

  // 【B5 2026-10-21】首屏 getCustomer 失败：页面级错误态(loadError)承载信息，不再额外弹 toast
  //（响应拦截器对 HTTP 错误已弹过一次，页面 catch 再弹即审计指出的双 toast）
  it('首屏加载失败：解除 loading 锁、置 loadError、不再弹 ElMessage', async () => {
    getCustomer.mockRejectedValueOnce(new Error('network down'))
    await detail.loadAll()
    expect(detail.loading.value).toBe(false)
    expect(detail.customer.value).toBe(null)
    expect(detail.loadError.value).toBe('network down')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('首屏失败后重试成功：loadError 清空、客户主体恢复', async () => {
    getCustomer.mockRejectedValueOnce(new Error('network down'))
    await detail.loadAll()
    expect(detail.loadError.value).toBe('network down')
    getCustomer.mockResolvedValueOnce({ data: { id: 1, customerName: '恢复客户' } })
    await detail.loadAll()
    expect(detail.customer.value?.customerName).toBe('恢复客户')
    expect(detail.loadError.value).toBe(null)
    expect(detail.loading.value).toBe(false)
  })

  // 【B5 2026-10-21】9 路分区 Promise.all → allSettled：单路失败只丢该分区并记录，其余照常写回
  it('分区单路失败（核心指标）不影响其他分区数据写回', async () => {
    getCustomer.mockResolvedValueOnce({ data: { id: 1, customerName: '客户甲' } })
    getCustomerLogs.mockResolvedValueOnce({ data: [{ id: 11, content: '日志A' }] })
    getShares.mockResolvedValueOnce({ data: [{ id: 21, sharedToUserName: '张三' }] })
    getScoreByCustomer.mockResolvedValueOnce({ data: 8 })
    getIndicator.mockRejectedValueOnce(new Error('indicator boom'))
    await detail.loadAll()
    // 客户主体与其余分区正常
    expect(detail.customer.value?.customerName).toBe('客户甲')
    expect(detail.logs.value).toHaveLength(1)
    expect(detail.shares.value).toHaveLength(1)
    expect(detail.incentiveScore.value).toBe(8)
    // 失败分区：保持缺省并记录到 sectionErrors，页面无整体错误
    expect(detail.indicator.value).toBe(null)
    expect(detail.loadError.value).toBe(null)
    expect(detail.loading.value).toBe(false)
    const failed = detail.sectionErrors.value.find((s) => s.label === '核心指标')
    expect(failed).toBeTruthy()
    expect(failed.message).toBe('indicator boom')
  })

  it('分区全部成功：sectionErrors 为空', async () => {
    getCustomer.mockResolvedValueOnce({ data: { id: 1, customerName: '客户乙' } })
    getCustomerLogs.mockResolvedValueOnce({ data: [] })
    getShares.mockResolvedValueOnce({ data: [] })
    getScoreByCustomer.mockResolvedValueOnce({ data: 0 })
    getIndicator.mockResolvedValueOnce({ data: null })
    await detail.loadAll()
    expect(detail.sectionErrors.value).toHaveLength(0)
    expect(detail.loading.value).toBe(false)
  })

  // 【PR-09】客户转移: 提交 transfer 审批, extraData 携带 newOwnerId; 未选/选当前负责人拦截
  it('confirmTransfer 提交转移审批且载荷含 newOwnerId', async () => {
    detail.customer.value = { id: 1, customerId: 1, customerName: '测试客户', ownerId: 7 }
    // customerId 是 computed(route.params.id 派生, mock 已置 '1'), 旧代码硬写 .value 触发
    // "Write operation failed: computed value is readonly" Vue warn 且写入静默失败——删除该无效写
    detail.transferUserId.value = 9
    submitApproval.mockResolvedValueOnce({ data: null })
    await detail.confirmTransfer()
    expect(submitApproval).toHaveBeenCalledWith(expect.objectContaining({
      type: 'transfer',
      extraData: JSON.stringify({ newOwnerId: 9 })
    }))
    expect(detail.showTransferDialog.value).toBe(false)
  })

  it('confirmTransfer 未选接收人/选当前负责人时不提交', async () => {
    detail.customer.value = { id: 1, ownerId: 7 }
    detail.transferUserId.value = null
    await detail.confirmTransfer()
    detail.transferUserId.value = 7
    await detail.confirmTransfer()
    expect(submitApproval).not.toHaveBeenCalled()
  })

})
