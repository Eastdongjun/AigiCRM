import request from '@/utils/request'

export function getCategoryTree() {
  return request.get('/incentive/categories')
}

export function createCategory(data) {
  return request.post('/incentive/categories', data)
}

export function updateCategory(id, data) {
  return request.put(`/incentive/categories/${id}`, data)
}

export function deleteCategory(id) {
  return request.delete(`/incentive/categories/${id}`)
}

export function getRecords(params) {
  return request.get('/incentive/records', { params })
}

export function addRecord(data) {
  return request.post('/incentive/records', data)
}

export function deleteRecord(id) {
  return request.delete(`/incentive/records/${id}`)
}

export function getScoreboard(params) {
  return request.get('/incentive/scoreboard', { params })
}

// 激励统计表(全员口径): 按子类聚合文件数/总学分 + 周期总计
export function getCategoryStats(params) {
  return request.get('/incentive/category-stats', { params })
}

export function getScoreByCustomer(customerId) {
  return request.get(`/incentive/score/customer/${customerId}`)
}

export function getIncentiveRanking(params) {
  return request.get('/incentive/ranking', { params })
}

export function getIncentiveRankingStats(params) {
  return request.get('/incentive/ranking/stats', { params })
}
