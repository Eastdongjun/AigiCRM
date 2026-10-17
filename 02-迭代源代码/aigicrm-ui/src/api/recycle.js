import request from '@/utils/request'

// 【回收站 2026-10-18】已删除客户/人员查看与恢复（独立页面，不影响现有列表口径）

export function getDeletedCustomers(params) {
  return request.get('/system/recycle/customers', { params })
}

export function getDeletedUsers(params) {
  return request.get('/system/recycle/users', { params })
}

export function restoreDeletedCustomer(id) {
  return request.post(`/system/recycle/customers/${id}/restore`)
}

export function restoreDeletedUser(id) {
  return request.post(`/system/recycle/users/${id}/restore`)
}

export function getDeletedFiles(params) {
  return request.get('/system/recycle/files', { params })
}

export function restoreDeletedFile(id) {
  return request.post(`/system/recycle/files/${id}/restore`)
}

export function purgeDeletedFiles() {
  return request.post('/system/recycle/files/purge')
}
