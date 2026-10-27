const config = require('./config/env')
const { getToken, purgeLegacyToken } = require('./utils/auth')

// 【全局转发 2026-10-27】微信规则: 页面未定义 onShareAppMessage 时, 右上角「…」菜单不出现
// 「转发」入口 —— 1.0.26 前全项目零定义, 手机上无从转发。这里在 app.js(先于所有页面加载)
// 包一层 Page, 给未自定义的页面注入默认转发; 落地页=登录页(已登录者由 login onShow 自动
// switchTab 回首页, 见 pages/login/index.js)。有意不注入 onShareTimeline: 朋友圈单页模式
// 无登录态, CRM 页面在单页模式下不可用。
;(function enableGlobalForward() {
  const originalPage = Page
  Page = function (options) {
    if (options && typeof options.onShareAppMessage !== 'function') {
      options.onShareAppMessage = function () {
        return {
          title: config.appName || 'AIGICRM',
          path: '/pages/login/index'
        }
      }
    }
    return originalPage(options)
  }
})()

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
