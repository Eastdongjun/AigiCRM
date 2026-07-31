import request from '@/utils/request'

export function getReminderList(params) {
  return request.get('/reminder/list', { params })
}

export function getExpiringList(params) {
  return request.get('/reminder/expiring-list', { params })
}

export function processReminder(id) {
  return request.post(`/reminder/${id}/process`)
}

export function getPendingCount() {
  return request.get('/reminder/pending-count')
}
