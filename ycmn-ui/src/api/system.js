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

// 组织管理
export function getOrgTree() {
  return request.get('/org/tree')
}
export function createOrg(data) {
  return request.post('/org', data)
}
export function updateOrg(id, data) {
  return request.put(`/org/${id}`, data)
}
export function deleteOrg(id) {
  return request.delete(`/org/${id}`)
}
export function getOrgAdmins(orgId) {
  return request.get(`/org/${orgId}/admins`)
}
export function addOrgAdmin(orgId, userId) {
  return request.post(`/org/${orgId}/admins`, null, { params: { userId } })
}
export function removeOrgAdmin(orgId, adminId) {
  return request.delete(`/org/${orgId}/admins/${adminId}`)
}

// 岗位管理
export function getPositionList() {
  return request.get('/system/positions')
}
export function createPosition(data) {
  return request.post('/system/positions', data)
}
export function updatePosition(id, data) {
  return request.put(`/system/positions/${id}`, data)
}
export function deletePosition(id) {
  return request.delete(`/system/positions/${id}`)
}

// 权限规则管理
export function getRuleList() {
  return request.get('/system/rules')
}
export function createRule(data) {
  return request.post('/system/rules', data)
}
export function updateRule(id, data) {
  return request.put(`/system/rules/${id}`, data)
}
export function deleteRule(id) {
  return request.delete(`/system/rules/${id}`)
}

// 角色-组织关联
export function getRoleOrgs(roleId) {
  return request.get(`/system/roles/${roleId}/orgs`)
}
export function updateRoleOrgs(roleId, data) {
  return request.put(`/system/roles/${roleId}/orgs`, data)
}

// 岗位-规则关联
export function getPositionRules(positionId) {
  return request.get(`/system/positions/${positionId}/rules`)
}
export function updatePositionRules(positionId, data) {
  return request.put(`/system/positions/${positionId}/rules`, data)
}

// 集团管理
export function getGroupList() {
  return request.get('/system/groups')
}
export function getGroup(id) {
  return request.get(`/system/groups/${id}`)
}
export function createGroup(data) {
  return request.post('/system/groups', data)
}
export function updateGroup(id, data) {
  return request.put(`/system/groups/${id}`, data)
}
export function deleteGroup(id) {
  return request.delete(`/system/groups/${id}`)
}

// 法人公司管理
export function getLegalEntityList(groupId) {
  return request.get('/system/legal-entities', { params: groupId ? { groupId } : {} })
}
export function getLegalEntity(id) {
  return request.get(`/system/legal-entities/${id}`)
}
export function createLegalEntity(data) {
  return request.post('/system/legal-entities', data)
}
export function updateLegalEntity(id, data) {
  return request.put(`/system/legal-entities/${id}`, data)
}
export function deleteLegalEntity(id) {
  return request.delete(`/system/legal-entities/${id}`)
}

// 用户-岗位分配
export function getUserPositions(userId) {
  return request.get(`/system/positions/users/${userId}`)
}
export function assignUserPositions(userId, positionIds) {
  return request.put(`/system/positions/users/${userId}`, { positionIds })
}
