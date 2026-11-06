import request from '@/utils/request'

export function addScoreItem(data) {
  return request.post('/score/items', data)
}

export function updateScoreItem(id, data) {
  return request.put(`/score/items/${id}`, data)
}

export function deleteScoreItem(id) {
  return request.delete(`/score/items/${id}`)
}

export function getScoreGrid(period) {
  return request.get('/score/grid', { params: { period } })
}

export function batchSaveEntries(data) {
  return request.post('/score/entries/batch', data)
}

export function getScoreRanking(params) {
  return request.get('/score/ranking', { params })
}

export function getScoreRankingStats(params) {
  return request.get('/score/ranking/stats', { params })
}

// 【2026-11-07 用户令"部门排行做成明细, PC端也一样"】部门成员明细(部门行点开弹窗)
export function getScoreDeptMembers(params) {
  return request.get('/score/ranking/dept-members', { params })
}
