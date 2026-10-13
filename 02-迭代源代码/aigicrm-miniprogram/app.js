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
    permissionRefreshPromise: null,
    // 【PR-12 契约修复 2026-10-07】写操作成功的强制刷新标记: 写页(激励上传/详情删除、文件夹重命名)
    // 成功回调按域置位(incentive/files), 对应列表页 onShow 消费后清零并把 _lastShowTs 归零,
    // 绕过 30s 节流一次 —— 保证"写完回来立刻见结果"(PR-12 契约);节流只拦纯回流的流量放大
    forceRefresh: { incentive: false, files: false }
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
