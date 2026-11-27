const request = require('../utils/request')
const { setToken } = require('../utils/auth')
// 【E7】getGlobalData 收敛至 utils/app-context 单一出处(原复制移除, 行为不变)
const { getGlobalData } = require('../utils/app-context')

function login(data) {
  return request({ url: '/api/auth/login', method: 'POST', data })
}

function loadCurrentUser() {
  const globalData = getGlobalData()
  const before = JSON.stringify({
    permissions: globalData.permissions || [],
    dataScope: (globalData.currentUser || {}).dataScope || '',
    roles: (globalData.currentUser || {}).roles || []
  })
  return request({ url: '/api/auth/info' }).then((user) => {
    globalData.currentUser = user || null
    globalData.permissions = user && Array.isArray(user.permissions) ? user.permissions : []
    globalData.groupIds = user && Array.isArray(user.groupIds) ? user.groupIds : []
    globalData.currentGroupId = user ? user.currentGroupId : null
    // 【BUG-MP-TABBAR-PERM-RACE-20261127】回填成功即置位(权限为空也算: 那是真的无码)。
    // 必须在 markChanged 之前置位——通知链里的 tabBar 重算要能区分"未回填"与"无权限"。
    globalData.permissionsLoaded = true
    const after = JSON.stringify({
      permissions: globalData.permissions,
      dataScope: (user || {}).dataScope || '',
      roles: (user || {}).roles || []
    })
    if (before !== after) {
      // 权限集/角色/基础范围发生变化：清理文件缓存并通知当前页刷新
      require('../utils/permission-refresh').markChanged('auth-info')
    }
    return user
  })
}

// 【2026-11-15】并发去重的权限刷新：epoch 变化、403、app.onShow 共用，避免请求风暴
let refreshPromise = null
let lastRefreshAt = 0
function refreshPermissions(force = false) {
  if (refreshPromise) return refreshPromise
  // 403 可能连续发生，3 秒内非强制刷新直接复用上次结果，避免请求风暴；
  // epoch 变化传 force=true，必须真实拉取。
  if (!force && Date.now() - lastRefreshAt < 3000) return Promise.resolve()
  refreshPromise = loadCurrentUser().then(() => true).catch(() => {
    // 失败保留旧 epoch/旧权限，下次请求或 onShow 再试
    return false
  }).finally(() => {
    refreshPromise = null
    lastRefreshAt = Date.now()
  })
  return refreshPromise
}

function switchGroup(groupId) {
  return request({ url: '/api/auth/switch-group', method: 'POST', data: { groupId } }).then((response) => {
    const globalData = getGlobalData()
    if (response && response.token) setToken(response.token)
    globalData.groupIds = response.groupIds || []
    globalData.currentGroupId = response.currentGroupId || null
    return response
  })
}

// 【体检 P1 2026-10-12】服务端注销: 后端按 jti 拉黑当前 token(防被盗 token 复用, 有效期内不再可用);
// PC 已接(store/user.js 先调 apiLogout), MP 此前只清本地未同步——两端行为分裂
function logout() {
  return request({ url: '/api/auth/logout', method: 'POST' })
}

function forgotPasswordLookup(data) { return request({ url: '/api/auth/forgot-password/lookup', method: 'POST', data }) }
function forgotPasswordSendCode(data) { return request({ url: '/api/auth/forgot-password/send-code', method: 'POST', data }) }
function forgotPasswordReset(data) { return request({ url: '/api/auth/forgot-password/reset', method: 'POST', data }) }

module.exports = {
  refreshPermissions,
  login,
  loadCurrentUser,
  switchGroup,
  logout,
  forgotPasswordLookup,
  forgotPasswordSendCode,
  forgotPasswordReset
}
