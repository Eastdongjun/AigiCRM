const config = require('../config/env')

const LEGACY_TOKEN_KEY = 'AIGICRM_TOKEN'

// 【PR-04 令牌缓存隔离】令牌按环境(接口地址)分键存储：
// 本地调试令牌不得被带到正式/测试接口（反之亦然）——同一个小程序存储，
// 换档(env.local.js 有无/指向变化、trial/release)后旧令牌残留会造成 401 循环
// 或跨环境身份串用。key 后缀取运行时 apiBaseUrl，demo 档固定 'demo'。
function tokenStorageKey() {
  return LEGACY_TOKEN_KEY + '::' + (config.apiBaseUrl || 'demo')
}

function getGlobalData() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return {}
  if (!app.globalData) app.globalData = {}
  return app.globalData
}

function getToken() {
  return wx.getStorageSync(tokenStorageKey()) || ''
}

function setToken(token) {
  wx.setStorageSync(tokenStorageKey(), token || '')
}

function clearToken() {
  wx.removeStorageSync(tokenStorageKey())
}

// 旧版单键令牌一次性清理：不做跨环境迁移（无法证明旧令牌属于哪个环境，
// 跨环境复用即风险），清除后要求当前环境重新登录。
function purgeLegacyToken() {
  try {
    wx.removeStorageSync(LEGACY_TOKEN_KEY)
  } catch (e) {
    // 存储异常按"无令牌"处理，不阻断启动
  }
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
  tokenStorageKey,
  purgeLegacyToken,
  getToken,
  setToken,
  clearToken,
  hasPermission,
  isAdmin,
  requireLogin,
  logout
}
