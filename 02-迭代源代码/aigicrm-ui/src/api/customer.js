import request from '@/utils/request'
import { downloadFile } from '@/utils/download'

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
export function deleteInvoice(customerId, invoiceId) {
  return request.delete(`/customer/${customerId}/invoices/${invoiceId}`)
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
export function deletePayment(customerId, paymentId) {
  return request.delete(`/customer/${customerId}/payments/${paymentId}`)
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
  const token = localStorage.getItem('token')
  return downloadFile('/api/customer/import/template', {
    name: '客户导入模板.xlsx',
    headers: { Authorization: `Bearer ${token}` }
  })
}

export function getCustomerFiles(customerId) { return request.get(`/customer/${customerId}/files`) }
export function attachCustomerFile(customerId, fileId) { return request.post(`/customer/${customerId}/files/${fileId}`) }
export function detachCustomerFile(customerId, fileId) { return request.delete(`/customer/${customerId}/files/${fileId}`) }
