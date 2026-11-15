// 【2026-11-15 权限生效修复】权限变化统一入口：
// 1) 版本号递增，页面可在 onShow 比对后主动重建权限相关 data；
// 2) 清理文件本地缓存，避免权限收窄/撤销后仍能离线打开已缓存文件；
// 3) 通知当前页面（实现 onPermissionChanged 的页面）立即刷新。
const fileCache = require('./file-cache')

let version = 0

function getVersion() {
  return version
}

function markChanged(reason) {
  version += 1
  try {
    fileCache.clear()
  } catch (e) { /* 清缓存失败不阻断权限刷新 */ }
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  pages.forEach((page) => {
    try {
      const tabBar = page && typeof page.getTabBar === 'function' ? page.getTabBar() : null
      if (tabBar && typeof tabBar.refreshTabs === 'function') tabBar.refreshTabs()
    } catch (e) { /* tabBar 刷新失败不影响权限通知 */ }
    if (page && typeof page.onPermissionChanged === 'function') {
      try {
        page.onPermissionChanged(version, reason || 'permission-changed')
      } catch (e) { /* 单页刷新失败不影响其他页面 */ }
    }
  })
}

module.exports = { getVersion, markChanged }
