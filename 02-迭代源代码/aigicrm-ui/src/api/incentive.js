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

// 更新记录（追加/编辑文件、开关分享），后端按 本人∨admin∨专项授权 校验归属
export function updateRecord(id, data) {
  return request.put(`/incentive/records/${id}`, data)
}

// 【用户裁决 2026-10-10】移除记录槽位中的单个已有文件（上传错了需要修改）：
// 后端原子完成 剔除槽位文件ID→重算学分→盖修改人章；物理文件保留只解除引用
export function removeRecordFile(recordId, data) {
  return request.post(`/incentive/records/${recordId}/remove-file`, data)
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

export function getCustomerRecords(params) {
  return request.get('/incentive/customer-records', { params })
}

// 激励查询阅读状态：PC 与微信小程序共用
export function markIncentiveRead(recordId) {
  return request.post(`/incentive/records/${recordId}/read`)
}

export function getIncentiveReadStats(params) {
  return request.get('/incentive/read-stats', { params })
}

/**
 * 本月激励查询阅读排行榜（2026-09-13 新增页面数据源）
 * 需要 incentive:read-ranking:view（默认只给系统管理员，管理员勾选后其它角色可见）
 */
export function getIncentiveReadRanking(params) {
  return request.get('/incentive/read-ranking', { params })
}

// 四项服务权限
export function checkIncentivePermission() {
  return request.get('/incentive/permission/check')
}
