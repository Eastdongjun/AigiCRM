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
function getCustomerContacts(id) { return request({ url: '/api/customer/' + id + '/contacts' }) }
function addCustomerContact(id, data) { return request({ url: '/api/customer/' + id + '/contacts', method: 'POST', data }) }
function getCustomerLogs(id) { return request({ url: '/api/customer/' + id + '/logs' }) }
function addCustomerLog(id, data) { return request({ url: '/api/customer/' + id + '/logs', method: 'POST', data }) }
function getInvoiceSummary(id) { return request({ url: '/api/customer/' + id + '/invoices/summary' }) }
function getPaymentSummary(id) { return request({ url: '/api/customer/' + id + '/payments/summary' }) }
function getCustomerInvoices(id) { return request({ url: '/api/customer/' + id + '/invoices' }) }
function getCustomerPayments(id) { return request({ url: '/api/customer/' + id + '/payments' }) }
function getCustomerShares(id) { return request({ url: '/api/customer/' + id + '/shares' }) }
function getCustomerSimpleUsers() { return request({ url: '/api/customer/users/simple' }) }
function addCustomerShares(id, userIds) { return request({ url: '/api/customer/' + id + '/shares', method: 'POST', data: { userIds } }) }
function removeCustomerShare(id, userId) { return request({ url: '/api/customer/' + id + '/shares/' + userId, method: 'DELETE' }) }
function addInvoice(id, data) { return request({ url: '/api/customer/' + id + '/invoices', method: 'POST', data }) }
function addPayment(id, data) { return request({ url: '/api/customer/' + id + '/payments', method: 'POST', data }) }
function getCustomerIndicator(id) { return request({ url: '/api/customer/' + id + '/indicator' }) }
function getCustomerFiles(id) { return request({ url: '/api/customer/' + id + '/files' }) }
function attachCustomerFile(id, fileId) { return request({ url: '/api/customer/' + id + '/files/' + fileId, method: 'POST' }) }
function detachCustomerFile(id, fileId) { return request({ url: '/api/customer/' + id + '/files/' + fileId, method: 'DELETE' }) }
function saveCustomerIndicator(id, data) { return request({ url: '/api/customer/' + id + '/indicator', method: 'POST', data }) }
function releaseCustomer(id, reason) { return request({ url: '/api/customer/' + id + '/release', method: 'POST', data: { reason: reason || '移动端释放' } }) }

module.exports = {
  getCustomerList,
  getCustomer,
  getCustomerCounts,
  addCustomer,
  updateCustomer,
  getCustomerContacts,
  addCustomerContact,
  getCustomerLogs,
  addCustomerLog,
  getInvoiceSummary,
  getPaymentSummary,
  getCustomerInvoices,
  getCustomerPayments,
  getCustomerShares,
  getCustomerSimpleUsers,
  addCustomerShares,
  removeCustomerShare,
  addInvoice,
  addPayment,
  getCustomerIndicator,
  getCustomerFiles,
  attachCustomerFile,
  detachCustomerFile,
  saveCustomerIndicator,
  releaseCustomer
}
