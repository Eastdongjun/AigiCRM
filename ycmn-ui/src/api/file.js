import request from '@/utils/request'

export function getFolderTree(scope) {
  return request.get('/file/folders', { params: { scope } })
}

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

export function shareFile(fileId, userIds) {
  return request.post(`/file/${fileId}/share`, { userIds })
}

export function getSharedToMe(params) {
  return request.get('/file/shared-to-me', { params })
}

export function getSharedToMeCount() {
  return request.get('/file/shared-to-me/count')
}
