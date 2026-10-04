import request from '@/utils/request'

export function getSeaList(params) {
  return request.get('/sea/list', { params })
}

export function getSeaStatistics() {
  return request.get('/sea/statistics')
}

// 删除公海池客户(仅待捞取状态可删)
export function deleteSeaPool(id) {
  return request.delete(`/sea/pool/${id}`)
}

// 批量导入客户到公海池(模板同客户导入)
export function importSeaCustomers(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/sea/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 【M10 2026-10-04】大文件导入对齐 customer.js importCustomers 的 120s 超时：
    // 全局 30s 会让前端先报"请求超时"而服务端可能仍在写入完成，诱导用户重复导入
    timeout: 120000
  })
}

// 公海池配置
export function getSeaConfigs() {
  return request.get('/sea/configs')
}
// 当前用户可见的公海池(公海池页面用)
export function getVisibleSeaConfigs() {
  return request.get('/sea/configs/visible')
}
// 组织机构树(部门+人员可选),供"可见部门/人员"树选择器
export function getOrgTree() {
  return request.get('/sea/org-tree')
}
export function createSeaConfig(data) {
  return request.post('/sea/configs', data)
}
export function updateSeaConfig(id, data) {
  return request.put(`/sea/configs/${id}`, data)
}
export function deleteSeaConfig(id) {
  return request.delete(`/sea/configs/${id}`)
}

// 捞取记录
export function getClaimRecords(params) {
  return request.get('/sea/claim-records', { params })
}
