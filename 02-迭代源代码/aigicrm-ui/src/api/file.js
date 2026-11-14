import request from '@/utils/request'

export function getFolderTree(scope) {
  return request.get('/file/folders', { params: { scope } })
}
// 【T2 2026-11-12】原 getTypePerms(根目录类型授权页级接口)已退役——/file/list 行级 perms(row.perms)取代

export function getFileList(params) {
  return request.get('/file/list', { params })
}

export function createFolder(data) {
  return request.post('/file/folders', data)
}

export function renameFolder(id, folderName) {
  return request.put(`/file/folders/${id}`, { folderName })
}

export function deleteFolder(id) {
  return request.delete(`/file/folders/${id}`)
}

export function deleteFile(id) {
  return request.delete(`/file/${id}`)
}

export function getStorageInfo() {
  return request.get('/file/storage')
}

// 存储扩容申请信息：{used,quota,maxQuota,canApply,reason,enabled,allowDecrease}（quota 类字段均为 bytes）
export function getStorageApplyInfo() {
  return request.get('/file/storage/apply-info')
}

export function shareFile(fileId, userIds) {
  return request.post(`/file/${fileId}/share`, { userIds })
}

export function getSharedToMe(params) {
  return request.get('/file/shared-to-me', { params })
}

export function getSharedToMeCount() {
  return request.get('/file/shared-to-me/count')
}

// === 文件权限体系（Task 5/6 接口）===

// 【体检 P1 2026-10-12】ACL 授权面轻量角色字典 [{id, roleName}]——/system/roles 要求
// system:role:view(仅 admin/0), 05/06 等 file:acl:manage 持有者改走本端点(同权限放行)
export function getAclRoles() {
  return request.get('/file/folders/acl-roles')
}
// 当前用户对文件夹的权限视图 {perms: [], downloadable: 0|1}
export function getFolderPermissions(id) {
  return request.get(`/file/folders/${id}/permissions`)
}

// 【2026-11-09 文件权限 v2】文件空间类型授权(角色|人 × 空间 × 动作)——权限中心「文件权限」页签
// 读写；实时生效(判定层 typeGranted, 覆盖该空间全部存量+未来文件夹, 不再建夹物化)；
// 词表 = 8 细动作(edit 伞已拆 rename/replace) + create(可建该类型根夹)；均挂 file:acl:manage。
export function getFolderTypeDefaults() {
  return request.get('/file/folders/type-defaults')
}

// 【2026-11-10 终版】保存例外授权 {folderType, objectType: 'FOLDER'|'FILE', folderId?, scopeOrgId?,
// subjectType: 'ROLE'|'USER', subjectId, perms: [], expiresAt: 'yyyy-MM-dd HH:mm:ss'}
export function saveFolderTypeDefault(data) {
  return request.post('/file/folders/type-defaults', data)
}

// 续期(顺延至指定时刻)
export function renewFolderTypeDefault(id, expiresAt) {
  return request.put(`/file/folders/type-defaults/${id}/renew`, { expiresAt })
}

// 按 id 删除授权(立即收回)
export function removeFolderTypeDefault(id) {
  return request.delete(`/file/folders/type-defaults/${id}`)
}

// 文件覆盖替换（multipart "file"），返回更新后文件（含 version）
export function replaceFile(id, formData) {
  return request.post(`/file/${id}/replace`, formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// 【2026-11-08 PC 补文件改名入口】后端 FileController PUT /file/{id}/rename
// （全局权限码 file:edit，service 层 checkFile(P_EDIT) 细粒度同 replace），body 传新文件名
export function renameFile(id, fileName) {
  return request.put(`/file/${id}/rename`, { fileName })
}

// ==================== 【V2026.11.14.1 回收站/版本历史/批量操作】(用户拍板 2026-11-14) ====================

// —— 我的回收站(FileRecycleController, file:delete 码) ——
export function myRecycleFiles(params) {
  return request.get('/file/recycle/files', { params })
}

export function myRecycleFolders(params) {
  return request.get('/file/recycle/folders', { params })
}

export function restoreRecycleFile(id) {
  return request.post(`/file/recycle/files/${id}/restore`)
}

export function purgeRecycleFile(id) {
  return request.post(`/file/recycle/files/${id}/purge`)
}

export function restoreRecycleFolder(id) {
  return request.post(`/file/recycle/folders/${id}/restore`)
}

export function purgeRecycleFolder(id) {
  return request.post(`/file/recycle/folders/${id}/purge`)
}

export function batchRestoreRecycle(data) {
  return request.post('/file/recycle/batch-restore', data)
}

export function batchPurgeRecycle(data) {
  return request.post('/file/recycle/batch-purge', data)
}

// —— 版本历史 ——
export function listFileVersions(id) {
  return request.get(`/file/${id}/versions`)
}

// 版本内容下载(blob 流, 调用方 createObjectURL 触发保存)
export function downloadFileVersion(id, versionNo) {
  return request.get(`/file/${id}/versions/${versionNo}/download`, { responseType: 'blob' })
}

// 回滚到指定版本(当前内容先归档不丢)
export function rollbackFileVersion(id, versionNo) {
  return request.post(`/file/${id}/versions/${versionNo}/rollback`)
}

// —— 批量操作 ——
export function batchDeleteFiles(ids) {
  return request.post('/file/batch-delete', { ids })
}

// 批量下载(zip 流式打包, 逐个过下载权限, 无权限项静默剔除)
export function batchDownloadFiles(ids) {
  return request.post('/file/batch-download', { ids }, { responseType: 'blob' })
}
