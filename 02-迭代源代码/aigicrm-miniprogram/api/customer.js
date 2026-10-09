const request = require('../utils/request')

function getCustomerList(params) {
  return request({ url: '/api/customer/list', data: params || {} })
}

function getCustomer(id) {
  return request({ url: '/api/customer/' + id })
}

function getCustomerCounts() {
  return request({ url: '/api/customer/list/counts' })
}

function addCustomer(data) { return request({ url: '/api/customer/add', method: 'POST', data }) }
function updateCustomer(id, data) { return request({ url: '/api/customer/' + id, method: 'PUT', data }) }
/** 【PR-05】客户+客情指标同事务组合提交:任一步失败整体回滚,消除"部分成功界面报失败"
 * 【PR-05 残余 20260921】强幂等:同一次提交(重试/重复发包)携带同一 Idempotency-Key,
 * 后端 Redis 兜底,重复请求拿到同一客户 id 而不是重复建客 */
function saveCustomerWithIndicator(data, idempotencyKey) {
  return request({ url: '/api/customer/full', method: 'POST', data, header: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined })
}
function updateCustomerWithIndicator(id, data) { return request({ url: '/api/customer/' + id + '/full', method: 'PUT', data }) }
function deleteCustomer(id) { return request({ url: '/api/customer/' + id, method: 'DELETE' }) }
function checkCustomerName(name) { return request({ url: '/api/customer/check-name', data: { name } }) }
function getCustomerContacts(id) { return request({ url: '/api/customer/' + id + '/contacts' }) }
function addCustomerContact(id, data) { return request({ url: '/api/customer/' + id + '/contacts', method: 'POST', data }) }
function updateCustomerContact(id, contactId, data) { return request({ url: '/api/customer/' + id + '/contacts/' + contactId, method: 'PUT', data }) }
function deleteCustomerContact(id, contactId) { return request({ url: '/api/customer/' + id + '/contacts/' + contactId, method: 'DELETE' }) }
function getCustomerLogs(id) { return request({ url: '/api/customer/' + id + '/logs' }) }
function addCustomerLog(id, data) { return request({ url: '/api/customer/' + id + '/logs', method: 'POST', data }) }
function getInvoiceSummary(id) { return request({ url: '/api/customer/' + id + '/invoices/summary' }) }
function getPaymentSummary(id) { return request({ url: '/api/customer/' + id + '/payments/summary' }) }
function getCustomerInvoices(id) { return request({ url: '/api/customer/' + id + '/invoices' }) }
function getCustomerPayments(id) { return request({ url: '/api/customer/' + id + '/payments' }) }
function getCustomerShares(id) { return request({ url: '/api/customer/' + id + '/shares' }) }
function getCustomerSimpleUsers() { return request({ url: '/api/customer/users/simple' }) }
/** 历史集团名去重候选（后端按数据范围返回）——"所属集团"可选可新建下拉用，与 PC 同源 */
function getCustomerGroupNames() { return request({ url: '/api/customer/group-names' }) }
function addCustomerShares(id, userIds) { return request({ url: '/api/customer/' + id + '/shares', method: 'POST', data: { userIds } }) }
function removeCustomerShare(id, userId) { return request({ url: '/api/customer/' + id + '/shares/' + userId, method: 'DELETE' }) }
function addInvoice(id, data) { return request({ url: '/api/customer/' + id + '/invoices', method: 'POST', data }) }
function deleteInvoice(id, invoiceId, reason) { return request({ url: '/api/customer/' + id + '/invoices/' + invoiceId + '?reason=' + encodeURIComponent(reason || ''), method: 'DELETE' }) }
function addPayment(id, data) { return request({ url: '/api/customer/' + id + '/payments', method: 'POST', data }) }
function deletePayment(id, paymentId, reason) { return request({ url: '/api/customer/' + id + '/payments/' + paymentId + '?reason=' + encodeURIComponent(reason || ''), method: 'DELETE' }) }
function getCustomerIndicator(id) { return request({ url: '/api/customer/' + id + '/indicator' }) }
function saveCustomerIndicator(id, data) { return request({ url: '/api/customer/' + id + '/indicator', method: 'POST', data }) }
function saveCustomerRelationship(id, score) { return request({ url: '/api/customer/' + id + '/relationship', method: 'PUT', data: { score } }) }
function releaseCustomer(id, reason) { return request({ url: '/api/customer/' + id + '/release', method: 'POST', data: { reason: reason || '移动端释放' } }) }
// 【闭环 2026-10-09 用户反馈】删除拒删指引原指向不存在的"停用";归档=停止跟进的真实入口
// (后端 POST /customer/{id}/archive, 默认隐藏+不参与统计提醒, PC 端可恢复)
function archiveCustomer(id) { return request({ url: '/api/customer/' + id + '/archive', method: 'POST' }) }

module.exports = {
  getCustomerList,
  getCustomer,
  getCustomerCounts,
  addCustomer,
  updateCustomer,
  saveCustomerWithIndicator,
  updateCustomerWithIndicator,
  deleteCustomer,
  checkCustomerName,
  getCustomerContacts,
  addCustomerContact,
  updateCustomerContact,
  deleteCustomerContact,
  getCustomerLogs,
  addCustomerLog,
  getInvoiceSummary,
  getPaymentSummary,
  getCustomerInvoices,
  getCustomerPayments,
  getCustomerShares,
  getCustomerSimpleUsers,
  getCustomerGroupNames,
  addCustomerShares,
  removeCustomerShare,
  addInvoice,
  deleteInvoice,
  addPayment,
  deletePayment,
  getCustomerIndicator,
  saveCustomerIndicator,
  saveCustomerRelationship,
  releaseCustomer,
  archiveCustomer
}
