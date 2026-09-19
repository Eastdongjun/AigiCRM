const request = require('../utils/request')

function getReminderList(params) {
  return request({ url: '/api/reminder/list', data: params || {} })
}

function getExpiringList(params) {
  return request({ url: '/api/reminder/expiring-list', data: params || {} })
}

function ensureExpiring() {
  return request({ url: '/api/reminder/ensure-expiring', method: 'POST' })
}

function processReminder(id) {
  return request({ url: '/api/reminder/' + id + '/process', method: 'POST' })
}

function getPendingCount() {
  return request({ url: '/api/reminder/pending-count' })
}

module.exports = {
  getReminderList,
  getExpiringList,
  ensureExpiring,
  processReminder,
  getPendingCount
}
