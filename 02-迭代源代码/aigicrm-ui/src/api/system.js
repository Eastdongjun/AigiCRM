import request from '@/utils/request'

// 用户管理
export function getUserList(params) {
  return request.get('/system/users', { params })
}
export function getUserById(id) {
  return request.get(`/system/users/${id}`)
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
// 当前用户可见部门树（deptName/children/id），供"选择部门（含全部子部门）"筛选
export function getScopeDepts() {
  return request.get('/common/scope/depts')
}
// 当前用户数据范围可见人员（id/realName/deptId），供负责人"筛选"下拉——筛选用范围感知,
// 选人(转移/分享接收人)用 customer/users/simple(集团边界),两类下拉勿混用
export function getScopeUsers(params) {
  return request.get('/common/scope/users', { params })
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
  return request.get('/system/configs/public', { silent: true })
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

// 组织/部门管理员
export function getDeptAdmins(deptId) {
  return request.get(`/system/depts/${deptId}/admins`)
}
export function addDeptAdmin(deptId, userId) {
  return request.post(`/system/depts/${deptId}/admins`, null, { params: { userId } })
}
export function removeDeptAdmin(deptId, adminId) {
  return request.delete(`/system/depts/${deptId}/admins/${adminId}`)
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
/** 【2026-08-11 岗位绑定】查用户已绑岗位（编辑回显用） */
export function getUserPositions(userId) {
  return request.get(`/system/positions/users/${userId}`)
}
/** 【2026-08-11 岗位绑定】分配用户岗位（清空重建, 第一个为主岗位） */
export function assignUserPositions(userId, positionIds) {
  return request.put(`/system/positions/users/${userId}`, { positionIds })
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
export function createGroup(data) {
  return request.post('/system/groups', data)
}
export function updateGroup(id, data) {
  return request.put(`/system/groups/${id}`, data)
}
export function deleteGroup(id) {
  return request.delete(`/system/groups/${id}`)
}

// ---- 角色-模块-动作 数据范围(权限体系重构 2026-08-10; 批③ 主体三级+锚点) ----
export function getRolePermScopes(roleCode) {
  return request.get(`/system/perm-scopes/${roleCode}`)
}
export function updateRolePermScopes(roleCode, configs) {
  return request.put(`/system/perm-scopes/${roleCode}`, configs)
}
export function getAssigneePermScopes(type, key) {
  return request.get(`/system/perm-scopes/assignee/${type}/${key}`)
}
export function updateAssigneePermScopes(type, key, configs) {
  return request.put(`/system/perm-scopes/assignee/${type}/${key}`, configs)
}

// 【BS-R 2026-09-19】安全审计日志(append-only,只读):登录/匿名探测/403/权限与集团变更/用户管理
export function getAuditLogList(params) {
  return request.get('/system/audit-logs', { params })
}
