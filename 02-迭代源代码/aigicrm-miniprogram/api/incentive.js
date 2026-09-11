const request = require('../utils/request')

function getCustomerRecords(params) {
  return request({ url: '/api/incentive/customer-records', data: params || {} })
}

function getCategoryTree() {
  return request({ url: '/api/incentive/categories' })
}

function addRecord(data) {
  return request({ url: '/api/incentive/records', method: 'POST', data })
}

function createRecord(data) {
  return request({ url: '/api/incentive/records', method: 'POST', data })
}

function markIncentiveRead(recordId) {
  return request({ url: '/api/incentive/records/' + recordId + '/read', method: 'POST' })
}

function getReadStats(params) {
  return request({ url: '/api/incentive/read-stats', data: params || {} })
}

function updateRecord(id, data) {
  return request({ url: '/api/incentive/records/' + id, method: 'PUT', data })
}

module.exports = {
  getCategoryTree,
  addRecord,
  getCustomerRecords,
  createRecord,
  markIncentiveRead,
  getReadStats,
  updateRecord
}
