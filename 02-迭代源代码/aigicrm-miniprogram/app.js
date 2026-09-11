const config = require('./config/env')

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

  onShow() {
    if (this.globalData.demoMode || !wx.getStorageSync('AIGICRM_TOKEN')) return
    if (this.globalData.permissionRefreshPromise) return
    const { loadCurrentUser } = require('./api/auth')
    this.globalData.permissionRefreshPromise = loadCurrentUser().catch(() => {
      // request.js handles expired tokens; transient failures keep the last known session.
    }).finally(() => {
      this.globalData.permissionRefreshPromise = null
    })
  }
})
