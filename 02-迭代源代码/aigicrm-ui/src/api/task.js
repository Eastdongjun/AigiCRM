import request from '@/utils/request'

// 【PR-10 2026-09-19】任务模块 API
export function getTaskList(params) {
  return request.get('/task/list', { params })
}

export function getTaskStatistics() {
  return request.get('/task/statistics')
}

export function createTask(data) {
  return request.post('/task', data)
}

export function completeTask(id, completionNote) {
  return request.post(`/task/${id}/complete`, { completionNote })
}

export function cancelTask(id, reason) {
  return request.post(`/task/${id}/cancel`, { reason })
}

export function transferTask(id, newAssigneeId) {
  return request.post(`/task/${id}/transfer`, { newAssigneeId })
}
