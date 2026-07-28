import request from '@/utils/request'

// 用户管理
export function getUserList(params) {
  return request.get('/system/users', { params })
}
export function createUser(data) {
  return request.post('/system/users', data)
}
export function updateUser(id, data) {
  return request.put(`/system/users/${id}`, data)
}
export function resetPassword(id) {
  return request.post(`/system/users/${id}/reset-password`)
}
export function changePassword(data) {
  return request.post('/system/users/change-password', data)
}
export function deleteUser(id) {
  return request.delete(`/system/users/${id}`)
}

// 角色管理
export function getRoleList() {
  return request.get('/system/roles')
}
export function getRole(id) {
  return request.get(`/system/roles/${id}`)
}
export function createRole(data) {
  return request.post('/system/roles', data)
}
export function updateRole(id, data) {
  return request.put(`/system/roles/${id}`, data)
}
export function deleteRole(id) {
  return request.delete(`/system/roles/${id}`)
}

// 部门管理
export function getDeptTree() {
  return request.get('/system/depts/tree')
}
export function getDeptList() {
  return request.get('/system/depts')
}
export function createDept(data) {
  return request.post('/system/depts', data)
}
export function updateDept(id, data) {
  return request.put(`/system/depts/${id}`, data)
}
export function deleteDept(id) {
  return request.delete(`/system/depts/${id}`)
}

// 菜单管理
export function getMenuTree() {
  return request.get('/system/menus/tree')
}

// 日志管理
export function getLogList(params) {
  return request.get('/system/logs', { params })
}

// 系统配置
export function getConfigList() {
  return request.get('/system/configs')
}
export function getPublicConfig() {
  return request.get('/system/configs/public')
}
export function updateConfigs(data) {
  return request.put('/system/configs', data)
}

// 字典管理
export function getDictTypes() {
  return request.get('/system/dict/types')
}
export function createDictType(data) {
  return request.post('/system/dict/types', data)
}
export function updateDictType(id, data) {
  return request.put(`/system/dict/types/${id}`, data)
}
export function deleteDictType(id) {
  return request.delete(`/system/dict/types/${id}`)
}
export function getDictData(dictCode) {
  return request.get(`/system/dict/data/${dictCode}`)
}
export function getDictDataByTypeId(typeId) {
  return request.get(`/system/dict/data/type/${typeId}`)
}
export function createDictData(data) {
  return request.post('/system/dict/data', data)
}
export function updateDictData(id, data) {
  return request.put(`/system/dict/data/${id}`, data)
}
export function deleteDictData(id) {
  return request.delete(`/system/dict/data/${id}`)
}
