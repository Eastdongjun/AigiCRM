const TOKEN_KEY = 'AIGICRM_TOKEN'

function getGlobalData() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return {}
  if (!app.globalData) app.globalData = {}
  return app.globalData
}

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  wx.setStorageSync(TOKEN_KEY, token || '')
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY)
}

function hasPermission(permission) {
  const permissions = getGlobalData().permissions || []
  return permissions.includes(permission)
}

function isAdmin() {
  const user = getGlobalData().currentUser
  const roles = user && Array.isArray(user.roles) ? user.roles : []
  return Boolean(user && (user.admin || user.username === 'admin' || roles.includes('admin')))
}

function requireLogin() {
  const globalData = getGlobalData()
  if (globalData.demoMode || getToken()) return true
  wx.redirectTo({ url: '/pages/login/index' })
  return false
}

function logout() {
  const globalData = getGlobalData()
  clearToken()
  globalData.currentUser = null
  globalData.permissions = []
  globalData.groupIds = []
  globalData.currentGroupId = null
  wx.redirectTo({ url: '/pages/login/index' })
}

module.exports = {
  getToken,
  setToken,
  clearToken,
  hasPermission,
  isAdmin,
  requireLogin,
  logout
}
