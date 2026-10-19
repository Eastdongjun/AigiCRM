// 【F5】冷启动权限竞态: app.onShow 才异步请求 /auth/info 回填 globalData.permissions,
// 页面 onLoad 若此时直接 hasPermission, 会把"尚未回填"误判成"没有权限"而误踢。
// 有在途回填请求时等它结束再判定; 演示模式/无在途请求时立即放行(行为与原来一致)。
function whenAuthReady() {
  const app = typeof getApp === 'function' ? getApp() : null
  const globalData = app && app.globalData
  const pending = globalData && !globalData.demoMode ? globalData.permissionRefreshPromise : null
  if (!pending || typeof pending.then !== 'function') return Promise.resolve()
  // 回填失败(网络/401)也放行到页面自己的权限判定: 与原逻辑一致, 不在此拦截
  return pending.then(() => undefined, () => undefined)
}

module.exports = { whenAuthReady }
