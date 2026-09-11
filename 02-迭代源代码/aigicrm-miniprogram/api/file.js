const request = require('../utils/request')

function getGlobalData() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return {}
  if (!app.globalData) app.globalData = {}
  return app.globalData
}

function buildUrl(baseUrl, path) {
  const normalizedBase = String(baseUrl || '').replace(/\/+$/, '')
  const normalizedPath = String(path || '').replace(/^\/+/, '')
  if (!normalizedBase) return '/' + normalizedPath
  if (normalizedBase.endsWith('/api') && normalizedPath.startsWith('api/')) {
    return normalizedBase + '/' + normalizedPath.slice(4)
  }
  return normalizedBase + '/' + normalizedPath
}

function getFileList(params) {
  return request({ url: '/api/file/list', data: params || {} })
}

function getDownloadToken() {
  return request({ url: '/api/file/download-token' })
}

function getFolders() { return request({ url: '/api/file/folders' }) }
function createFolder(data) { return request({ url: '/api/file/folders', method: 'POST', data }) }
function renameFolder(id, folderName) { return request({ url: '/api/file/folders/' + id, method: 'PUT', data: { folderName } }) }
function deleteFolder(id) { return request({ url: '/api/file/folders/' + id, method: 'DELETE' }) }
function getSharedToMe(params) { return request({ url: '/api/file/shared-to-me', data: params || {} }) }
function shareFile(id, userIds) { return request({ url: '/api/file/' + id + '/share', method: 'POST', data: { userIds } }) }
function replaceFile(id, filePath) {
  const globalData = getGlobalData()
  const token = require('../utils/auth').getToken()
  return new Promise((resolve, reject) => wx.uploadFile({
    url: buildUrl(globalData.apiBaseUrl, '/api/file/' + id + '/replace'), filePath, name: 'file',
    header: token ? { Authorization: 'Bearer ' + token } : {},
    success(res) {
      try { const body = JSON.parse(res.data || '{}'); if (res.statusCode >= 200 && res.statusCode < 300 && (body.code === undefined || body.code === 200)) resolve(body.data !== undefined ? body.data : body); else reject(new Error(body.message || '替换失败')) } catch (error) { reject(error) }
    },
    fail: reject
  }))
}

function uploadFile(filePath, folderId, scope) {
  const globalData = getGlobalData()
  const token = require('../utils/auth').getToken()
  const formData = { scope: scope || 'private' }
  if (folderId != null && folderId !== '' && folderId !== 'undefined' && folderId !== 'null') formData.folderId = String(folderId)
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: buildUrl(globalData.apiBaseUrl, '/api/file/upload'),
      filePath,
      name: 'file',
      formData,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success(res) {
        try {
          const body = JSON.parse(res.data || '{}')
          if (res.statusCode >= 200 && res.statusCode < 300 && (body.code === undefined || body.code === 200)) {
            resolve(body.data !== undefined ? body.data : body)
          } else reject(new Error(body.message || '上传失败'))
        } catch (error) { reject(error) }
      },
      fail: reject
    })
  })
}

module.exports = {
  getFileList,
  getDownloadToken,
  getFolders,
  createFolder,
  renameFolder,
  deleteFolder,
  getSharedToMe,
  shareFile,
  replaceFile,
  uploadFile
}
