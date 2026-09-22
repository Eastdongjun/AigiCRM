const { clearToken, getToken } = require('./auth')

let redirectingToLogin = false

function getGlobalData() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return {}
  if (!app.globalData) app.globalData = {}
  return app.globalData
}

function requestError(message, details) {
  const error = new Error(message || '请求失败')
  Object.assign(error, details)
  return error
}

function redirectToLoginOnce() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  clearToken()
  wx.redirectTo({
    url: '/pages/login/index',
    complete() { redirectingToLogin = false }
  })
}

function sanitizeData(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data || {}
  return Object.keys(data).reduce((result, key) => {
    const value = data[key]
    if (value !== undefined && value !== null && value !== '' && value !== 'undefined' && value !== 'null') result[key] = value
    return result
  }, {})
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

function request(options) {
  const globalData = getGlobalData()
  const baseUrl = globalData.apiBaseUrl || ''
  const token = getToken()

  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(baseUrl, options.url),
      method: options.method || 'GET',
      data: sanitizeData(options.data),
      header: {
        'content-type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        // 调用方自定义头透传(如 PR-05 幂等键),同名时以调用方为准
        ...(options.header || {})
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode >= 200 && res.statusCode < 300 && (body.code === undefined || body.code === 200)) {
          resolve(body.data !== undefined ? body.data : body)
          return
        }
        // 【2026-09-22 语义修正】只有 401(未认证/失效)才踢登录; 403=已认证但无权,
        // 此前 403 也触发 redirectToLoginOnce, 会把"无权限动作"放大成"被登出"(reminder 页实测)
        if (res.statusCode === 401 || body.code === 401) {
          redirectToLoginOnce()
        }
        reject(requestError(body.message || '请求失败', {
          statusCode: res.statusCode,
          code: body.code,
          response: res
        }))
      },
      fail(error) {
        reject(requestError(error.errMsg || '网络请求失败', {
          statusCode: 0,
          code: 'NETWORK_ERROR',
          cause: error
        }))
      }
    })
  })
}

module.exports = request
