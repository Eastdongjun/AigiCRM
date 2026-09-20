const config = require('./config/env')
const { getToken, purgeLegacyToken } = require('./utils/auth')

App({
  globalData: {
    appName: config.appName || 'AIGICRM',
    apiBaseUrl: config.apiBaseUrl,
    demoMode: config.demoMode,
    currentUser: null,
    permissions: [],
    groupIds: [],
    currentGroupId: null,
    permissionRefreshPromise: null
  },

  onLaunch() {
    // 【PR-04 令牌缓存隔离】清除旧版单键令牌（不迁移，当前环境重新登录）
    purgeLegacyToken()
  },

  onShow() {
    if (this.globalData.demoMode || !getToken()) return
    if (this.globalData.permissionRefreshPromise) return
    const { loadCurrentUser } = require('./api/auth')
    this.globalData.permissionRefreshPromise = loadCurrentUser().catch(() => {
      // request.js handles expired tokens; transient failures keep the last known session.
    }).finally(() => {
      this.globalData.permissionRefreshPromise = null
    })
  }
})
