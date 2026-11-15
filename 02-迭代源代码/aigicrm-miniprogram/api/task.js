const request = require('../utils/request')

// 【PR-10 2026-09-19】任务模块 API
function getTaskStatistics() { return request({ url: '/api/task/statistics' }) }
function getTaskList(params) { return request({ url: '/api/task/list', data: params || {} }) }
// 【2026-11-16 MP 我的任务】完成/取消/转交与 PC 同端点同权限(task:manage)
function completeTask(id, completionNote) {
  return request({ url: `/api/task/${id}/complete`, method: 'POST', data: { completionNote } })
}
function cancelTask(id, reason) {
  return request({ url: `/api/task/${id}/cancel`, method: 'POST', data: { reason } })
}
function transferTask(id, newAssigneeId) {
  return request({ url: `/api/task/${id}/transfer`, method: 'POST', data: { newAssigneeId } })
}

module.exports = { getTaskStatistics, getTaskList, completeTask, cancelTask, transferTask }
