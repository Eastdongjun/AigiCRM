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
    headers: { 'Content-Type': 'multipart/form-data' }
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

// 公海专项权限(按组织架构层级勾选 view/edit/delete/claim)
export function getSeaPermissionList() {
  return request.get('/sea/permission/list')
}
export function checkSeaPermission() {
  return request.get('/sea/permission/check')
}
export function grantSeaPermission(data) {
  return request.post('/sea/permission/grant', data)
}
export function revokeSeaPermission(permType) {
  return request.delete(`/sea/permission/${permType}`)
}
