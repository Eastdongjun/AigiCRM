const request = require('../utils/request')

function getApprovalList(params) {
  return request({ url: '/api/approval/list', data: params || {} })
}

function approveApproval(id, data) {
  return request({ url: '/api/approval/' + id + '/approve', method: 'POST', data: data || {} })
}

function rejectApproval(id, data) {
  return request({ url: '/api/approval/' + id + '/reject', method: 'POST', data: data || {} })
}

function getApprovalStatistics() {
  return request({ url: '/api/approval/statistics' })
}

function getApprovalDetail(id) {
  return request({ url: '/api/approval/' + id })
}
function submitApproval(data) { return request({ url: '/api/approval/submit', method: 'POST', data: data || {} }) }

module.exports = {
  getApprovalList,
  approveApproval,
  rejectApproval,
  getApprovalStatistics,
  getApprovalDetail,
  submitApproval
}
