import request from '@/utils/request'

export function getReminderList(params) {
  return request.get('/reminder/list', { params })
}

export function getExpiringList(params) {
  return request.get('/reminder/expiring-list', { params })
}

// S8: 物化今日到期提醒（GET 分页不再写库，进入提醒页前显式触发一次）
export function ensureExpiring() {
  return request.post('/reminder/ensure-expiring')
}

export function processReminder(id) {
  return request.post(`/reminder/${id}/process`)
}

export function getPendingCount() {
  return request.get('/reminder/pending-count')
}
