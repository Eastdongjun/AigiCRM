import request from '@/utils/request'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'

export function getCustomerList(params) {
  return request.get('/customer/list', { params })
}

export function getCustomer(id) {
  return request.get(`/customer/${id}`)
}

export function addCustomer(data) {
  return request.post('/customer/add', data)
}

export function updateCustomer(id, data) {
  return request.put(`/customer/${id}`, data)
}

// 【PR-05】客户+客情指标同事务组合提交:任一步失败整体回滚,消除"部分成功界面报失败"
// 【PR-05 残余 20260921】强幂等:同一次提交(含重试/双击重复发包)携带同一 Idempotency-Key,
// 后端 Redis PENDING/RESULT 兜底,重复请求拿到同一客户 id 而不是重复建客
export function saveCustomerWithIndicator(data, idempotencyKey) {
  const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
  return request.post('/customer/full', data, headers ? { headers } : undefined)
}

export function updateCustomerWithIndicator(id, data) {
  return request.put(`/customer/${id}/full`, data)
}

// 【裁决 2026-10-10】开票回款阶段总额(半年/全年录入制, 取代逐笔录入入口):
// GET 返回 { rows, baseYear, annualInvoiceAmount, annualPaymentAmount, coreType }——判定口径后端一处算好
export function getStageAmounts(id) {
  return request.get(`/customer/${id}/stage-amounts`)
}

export function saveStageAmount(id, data) {
  return request.post(`/customer/${id}/stage-amounts`, data)
}

export function deleteCustomer(id) {
  return request.delete(`/customer/${id}`)
}

export function getCustomerLogs(id) {
  return request.get(`/customer/${id}/logs`)
}

export function addCustomerLog(id, data) {
  return request.post(`/customer/${id}/logs`, data)
}

export function getCustomerStatistics() {
  return request.get('/customer/statistics')
}

export function getCustomerListCounts() {
  return request.get('/customer/list/counts')
}

// 【容量加固·服务端分页 2026-10-05】五个开发类型页签计数(与 /customer/list 同参数,不含分页)
export function getCustomerListCountsByFilter(params) {
  return request.get('/customer/list/counts-by-filter', { params })
}

export function getSimpleUserList() {
  return request.get('/customer/users/simple')
}

export function releaseCustomer(id, reason) {
  return request.post(`/customer/${id}/release`, { reason })
}

// 开票记录
export function getInvoices(id) {
  return request.get(`/customer/${id}/invoices`)
}
export function getInvoiceSummary(id) {
  return request.get(`/customer/${id}/invoices/summary`)
}
export function addInvoice(id, data) {
  return request.post(`/customer/${id}/invoices`, data)
}
// 【议题五.4】归档(默认隐藏, 统计/到期提醒不参与)与恢复 —— 守卫 customer:edit
export function archiveCustomer(id) {
  return request.post(`/customer/${id}/archive`)
}
export function restoreCustomer(id) {
  return request.post(`/customer/${id}/restore`)
}

// 【裁决 2026-09-24】发票可删但必留痕: 原因必填(query), 后端记审计快照+逻辑删
export function deleteInvoice(customerId, invoiceId, reason) {
  return request.delete(`/customer/${customerId}/invoices/${invoiceId}`, { params: { reason } })
}
// 【PR-18 台账状态机】确认(pending→confirmed) / 作废(留痕, 统计口径已排除)
export function confirmInvoice(customerId, invoiceId) {
  return request.post(`/customer/${customerId}/invoices/${invoiceId}/confirm`)
}
export function voidInvoice(customerId, invoiceId, reason) {
  return request.post(`/customer/${customerId}/invoices/${invoiceId}/void`, { reason })
}

/** 新增/编辑表单「所属集团」下拉候选：后端按当前用户数据范围返回客户历史集团名（去重） */
export function getCustomerGroupNames() {
  return request.get('/customer/group-names')
}

// 客户指标
export function getIndicator(id) {
  return request.get(`/customer/${id}/indicator`)
}
export function saveIndicator(id, data) {
  return request.post(`/customer/${id}/indicator`, data)
}
export function saveRelationship(id, score) {
  return request.put(`/customer/${id}/relationship`, { score })
}

// 客户分享
export function getShares(id) {
  return request.get(`/customer/${id}/shares`)
}
export function addShares(id, userIds) {
  return request.post(`/customer/${id}/shares`, { userIds })
}
export function removeShare(customerId, userId) {
  return request.delete(`/customer/${customerId}/shares/${userId}`)
}

// 联系人
export function getContacts(id) {
  return request.get(`/customer/${id}/contacts`)
}
export function addContact(id, data) {
  return request.post(`/customer/${id}/contacts`, data)
}
export function updateContact(customerId, contactId, data) {
  return request.put(`/customer/${customerId}/contacts/${contactId}`, data)
}
export function deleteContact(customerId, contactId) {
  return request.delete(`/customer/${customerId}/contacts/${contactId}`)
}

// 回款记录
export function getPayments(id) {
  return request.get(`/customer/${id}/payments`)
}
export function getPaymentSummary(id) {
  return request.get(`/customer/${id}/payments/summary`)
}
export function addPayment(id, data) {
  return request.post(`/customer/${id}/payments`, data)
}
// 【裁决 2026-09-24】回款同口径: 可删必留痕
export function deletePayment(customerId, paymentId, reason) {
  return request.delete(`/customer/${customerId}/payments/${paymentId}`, { params: { reason } })
}
export function confirmPayment(customerId, paymentId) {
  return request.post(`/customer/${customerId}/payments/${paymentId}/confirm`)
}
export function voidPayment(customerId, paymentId, reason) {
  return request.post(`/customer/${customerId}/payments/${paymentId}/void`, { reason })
}

// 批量导入
export function importCustomers(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/customer/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function downloadImportTemplate() {
  // 【D20 2026-10-04】token 统一走 token.js getToken() 唯一入口：
  // 此前裸读 localStorage，未登录时会拼出 'Bearer null' 发给后端
  return downloadFile('/api/customer/import/template', {
    name: '客户导入模板.xlsx',
    headers: { Authorization: `Bearer ${getToken() || ''}` }
  })
}

