const config = require('../config/env')
// 【E7】getGlobalData 收敛至 app-context 单一出处(原复制移除, 行为不变)
const { getGlobalData } = require('./app-context')

const LEGACY_TOKEN_KEY = 'AIGICRM_TOKEN'

// 【PR-04 令牌缓存隔离】令牌按环境(接口地址)分键存储：
// 本地调试令牌不得被带到正式/测试接口（反之亦然）——同一个小程序存储，
// 换档(env.local.js 有无/指向变化、trial/release)后旧令牌残留会造成 401 循环
// 或跨环境身份串用。key 后缀取运行时 apiBaseUrl，demo 档固定 'demo'。
function tokenStorageKey() {
  return LEGACY_TOKEN_KEY + '::' + (config.apiBaseUrl || 'demo')
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

// 【L18 口径对齐】与 PC 端 aigicrm-ui/src/store/user.js 的 isAdmin() 完全同口径:
// roles 含 'admin' 或用户名为 'admin'。后端 /api/auth/info(UserInfoResponse)并不返回
// admin 布尔字段, 此前的 user.admin 分支实际只有 demo 档登录会命中(且 demo 用户已带
// roles:['admin']), 故删除。username === 'admin' 魔法用户名分支按 PC 端口径保留:
// 历史内置管理员账号不依赖角色数据配置, 两端口径必须一致以防裁决漂移。
function isAdmin() {
  const user = getGlobalData().currentUser
  const roles = user && Array.isArray(user.roles) ? user.roles : []
  return Boolean(user && (roles.includes('admin') || user.username === 'admin'))
}

function requireLogin() {
  const globalData = getGlobalData()
  if (globalData.demoMode || getToken()) return true
  wx.redirectTo({ url: '/pages/login/index' })
  return false
}

function logout() {
  const globalData = getGlobalData()
  // 【体检 P1 2026-10-12】注销即拉黑: 先调服务端 /auth/logout(按 jti 拉黑当前 token)再清本地——
  // 此前只清本地, 退出后 token 在有效期内服务端仍可用; PC 已同款修复, 此处对齐。
  // 函数内惰性 require 避免 utils/auth ↔ api/auth 循环依赖; 尽力而为, 失败不阻断本地登出
  try { require('../api/auth').logout().catch(() => {}) } catch (e) { /* 网络异常不阻断登出 */ }
  // 【2026-10-27 深度检查修复】登出同时清文件预览本地缓存——缓存键虽已带用户/环境维度,
  // 但旧条目仍在盘上, 换号后无权用户不应能离线打开上一账号缓存过的文件(fail-open, 失败不阻断)
  try { require('./file-cache').clear() } catch (e) { /* 清缓存失败不阻断登出 */ }
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
