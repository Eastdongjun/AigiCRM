import request from '@/utils/request'

export function getApprovalList(params) {
  return request.get('/approval/list', { params })
}

export function getApprovalDetail(id) {
  return request.get(`/approval/${id}`)
}

export function submitApproval(data) {
  return request.post('/approval/submit', data)
}

export function approve(id, data) {
  return request.post(`/approval/${id}/approve`, data)
}

export function reject(id, data) {
  return request.post(`/approval/${id}/reject`, data)
}

export function getApprovalStatistics() {
  return request.get('/approval/statistics')
}
