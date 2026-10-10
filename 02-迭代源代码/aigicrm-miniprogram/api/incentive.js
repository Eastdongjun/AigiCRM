const request = require('../utils/request')

function getCustomerRecords(params) {
  return request({ url: '/api/incentive/customer-records', data: params || {} })
}

function getRecords(params) {
  return request({ url: '/api/incentive/records', data: params || {} })
}

function getCategoryTree() {
  return request({ url: '/api/incentive/categories' })
}

function addRecord(data) {
  return request({ url: '/api/incentive/records', method: 'POST', data })
}

function markIncentiveRead(recordId) {
  return request({ url: '/api/incentive/records/' + recordId + '/read', method: 'POST' })
}

function getReadStats(params) {
  return request({ url: '/api/incentive/read-stats', data: params || {} })
}

function getCategoryStats(params) {
  return request({ url: '/api/incentive/category-stats', data: params || {} })
}

function getReadRanking(params) {
  return request({ url: '/api/incentive/read-ranking', data: params || {} })
}

function getScoreboard(params) {
  return request({ url: '/api/incentive/scoreboard', data: params || {} })
}

function getScoreByCustomer(customerId) {
  return request({ url: '/api/incentive/score/customer/' + customerId })
}

function getRanking(params) {
  return request({ url: '/api/incentive/ranking', data: params || {} })
}

function getUsers() {
  return request({ url: '/api/incentive/users' })
}

function getGrantCheck() {
  return request({ url: '/api/incentive/permission/check' })
}

function updateRecord(id, data) {
  return request({ url: '/api/incentive/records/' + id, method: 'PUT', data })
}

// 【用户裁决 2026-10-10】移除记录槽位中的单个已有文件（上传错了需要修改）：
// 后端原子完成 剔除槽位文件ID→重算积分→盖修改人章；物理文件保留只解除引用
function removeRecordFile(recordId, slot, fileId) {
  return request({ url: '/api/incentive/records/' + recordId + '/remove-file', method: 'POST', data: { slot, fileId } })
}

function deleteRecord(id) {
  return request({ url: '/api/incentive/records/' + id, method: 'DELETE' })
}

module.exports = {
  getCategoryTree,
  addRecord,
  getRecords,
  getCustomerRecords,
  markIncentiveRead,
  getReadStats,
  getCategoryStats,
  getReadRanking,
  getScoreboard,
  getScoreByCustomer,
  getRanking,
  getUsers,
  getGrantCheck,
  updateRecord,
  removeRecordFile,
  deleteRecord
}
