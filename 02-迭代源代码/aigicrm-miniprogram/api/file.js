const request = require('../utils/request')
// 【E7】getGlobalData/buildUrl 收敛至 utils/app-context 单一出处(原两份复制移除, 行为不变)
const { getGlobalData, buildUrl } = require('../utils/app-context')

function encodeDownloadPath(path) {
  return String(path || '').split('/').filter(Boolean).map(encodeURIComponent).join('/')
}

function safeDecode(value) {
  try {
    return decodeURIComponent(value)
  } catch (_) {
    return value
  }
}

function getDownloadUrl(file, thumbnail) {
  const globalData = getGlobalData()
  if (file && file.id) {
    return buildUrl(globalData.apiBaseUrl, '/api/file/download/' + file.id + (thumbnail ? '?thumbnail=1' : ''))
  }
  const rawPath = file && (file.ossKey || file.fileKey || file.path || file.fileUrl || file.url)
  if (!rawPath) return ''
  const marker = '/api/file/download/'
  const source = String(rawPath)
  const markerIndex = source.indexOf(marker)
  const downloadPath = markerIndex >= 0 ? source.slice(markerIndex + marker.length) : source.replace(/^https?:\/\/[^/]+\//, '')
  return buildUrl(globalData.apiBaseUrl, '/api/file/download/' + encodeDownloadPath(safeDecode(downloadPath)))
}

function getFileList(params) {
  return request({ url: '/api/file/list', data: params || {} })
}

function getStorageInfo() {
  return request({ url: '/api/file/storage' })
}

function getDownloadToken() {
  return request({ url: '/api/file/download-token' })
}

// 【体检 P1 2026-10-12】带 scope 请求文件夹树: 后端 @RequestParam(defaultValue="public")——
// 不带参恒返回公共树, 私人 Tab 再按 folderType 过滤必为空(PC 端已带 scope, MP 此前漏传)
function getFolders(scope) {
  return request({ url: '/api/file/folders', data: scope ? { scope } : {} })
}
function createFolder(data) { return request({ url: '/api/file/folders', method: 'POST', data }) }
function renameFolder(id, folderName) { return request({ url: '/api/file/folders/' + id, method: 'PUT', data: { folderName } }) }
function deleteFolder(id) { return request({ url: '/api/file/folders/' + id, method: 'DELETE' }) }
function deleteFile(id) { return request({ url: '/api/file/' + id, method: 'DELETE' }) }
function getFolderAcl(id) { return request({ url: '/api/file/folders/' + id + '/acl' }) }
function getSharedToMe(params) { return request({ url: '/api/file/shared-to-me', data: params || {} }) }
function getSharedToMeCount() { return request({ url: '/api/file/shared-to-me/count' }) }
function shareFile(id, userIds) { return request({ url: '/api/file/' + id + '/share', method: 'POST', data: { userIds } }) }
function replaceFile(id, filePath) {
  const globalData = getGlobalData()
  const token = require('../utils/auth').getToken()
  return new Promise((resolve, reject) => wx.uploadFile({
    url: buildUrl(globalData.apiBaseUrl, '/api/file/' + id + '/replace'), filePath, name: 'file',
    header: token ? { Authorization: 'Bearer ' + token } : {},
    // 【M21】响应判定与 401 踢登录复用 utils/request 统一封装, 不再各自复制口径
    success(res) { request.settleUploadResponse(res, '替换失败').then(resolve, reject) },
    fail: reject
  }))
}

function uploadFile(filePath, folderId, scope, options = {}) {
  const globalData = getGlobalData()
  const token = require('../utils/auth').getToken()
  const formData = { scope: scope || 'private' }
  if (folderId != null && folderId !== '' && folderId !== 'undefined' && folderId !== 'null') formData.folderId = String(folderId)
  return new Promise((resolve, reject) => {
    const task = wx.uploadFile({
      url: buildUrl(globalData.apiBaseUrl, '/api/file/upload'),
      filePath,
      name: 'file',
      formData,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      // 【M21】响应判定与 401 踢登录复用 utils/request 统一封装, 不再各自复制口径
      success(res) { request.settleUploadResponse(res, '上传失败').then(resolve, reject) },
      fail: reject
    })
    if (options && typeof options.onProgress === 'function' && task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate(options.onProgress)
    }
  })
}

module.exports = {
  getFileList,
  getDownloadUrl,
  getStorageInfo,
  getDownloadToken,
  getFolders,
  createFolder,
  renameFolder,
  deleteFolder,
  deleteFile,
  getFolderAcl,
  getSharedToMe,
  getSharedToMeCount,
  shareFile,
  replaceFile,
  uploadFile
}
