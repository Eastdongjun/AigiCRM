// 【BUG-MP-TABBAR-PERM-RACE-20261127】冷启动权限回填竞态的统一判定。
//
// 背景: app.onShow 才异步请求 /auth/info 回填 globalData.permissions(app.js:49-55),
// 回填完成前 globalData.permissions=[], hasPermission() 对空数组返回 false(utils/auth.js:37-43)。
// 自定义 tabBar 与页面守卫若此时直接判定, 会把"尚未回填"误当成"没有权限":
//   · tabBar 把四项服务/法布施置灰并吞掉点击(弹「暂无该模块权限」);
//   · 两个 tab 页 onLoad/onShow 把用户踢回首页。
// 这里给出三档判定, 供 tabBar 与页面守卫共用(文件页原 whenAuthReady 语义保留):
//   isAuthReady()            同步快路径: demo / admin / 权限已回填;
//   whenAuthReady()          只等"已有在途"的回填(与 pages/files 旧实现逐字同义);
//   ensurePermissionsReady() 无在途且持 token 时主动补拉一次(覆盖首次 /auth/info 失败、
//                            分享卡片直入等 app.onShow 没机会拉取的路径), 永不 reject。
//
// 口径: 回填失败(网络/401)时 ensure 返回 false, 调用方回落原有判定与提示, 不在这里放行。
const { getGlobalData } = require('./app-context')

function isDemoMode() {
  return Boolean(getGlobalData().demoMode)
}

// admin 前端恒有全部权限(utils/auth.isAdmin), 与 hasPermission 的旁路同口径
function isAdminSafe() {
  try {
    return require('./auth').isAdmin()
  } catch (e) {
    return false
  }
}

/** 权限是否已就绪(可安全调用 hasPermission)。demo/admin 视为就绪。 */
function isAuthReady() {
  const globalData = getGlobalData()
  return Boolean(globalData.demoMode) || isAdminSafe() || globalData.permissionsLoaded === true
}

function pendingRefresh() {
  const pending = getGlobalData().permissionRefreshPromise
  return pending && typeof pending.then === 'function' ? pending : null
}

/** 有在途回填就等它结束(失败也放行, 与原 pages/files 行为一致); 无在途立即返回。 */
function whenAuthReady() {
  const pending = pendingRefresh()
  if (!pending || isDemoMode()) return Promise.resolve()
  return pending.then(() => undefined, () => undefined)
}

/**
 * 入口守卫统一入口: 权限未回填时等回填再执行, 已就绪(demo/admin/已回填)同步执行。
 * 只用于"会踢人/拦住取数"的 onLoad/onShow; 按钮级 hasPermission 不包(由 markChanged 重算)。
 * 回填失败(网络/无 token)时 ensure 返回 false 后仍执行 fn, 由 fn 里的原判定回落原行为。
 */
function whenPermissionsReady(fn) {
  if (typeof fn !== 'function') return Promise.resolve()
  if (isAuthReady()) return fn()
  return ensurePermissionsReady().then(() => fn())
}

/**
 * 确保权限已回填, 返回 Promise<boolean>(true=已就绪, false=回填失败/无 token)。
 * 顺序: 已就绪 → true; 有在途 → 等它; 否则持 token 时 force 拉一次。
 * 与 app.onShow 共用 globalData.permissionRefreshPromise 槽位, 避免并发重复请求。
 */
function ensurePermissionsReady() {
  const globalData = getGlobalData()
  if (isAuthReady()) return Promise.resolve(true)
  const pending = pendingRefresh()
  if (pending) {
    return pending.then(
      () => Boolean(globalData.permissionsLoaded),
      () => Boolean(globalData.permissionsLoaded)
    )
  }
  let hasToken = false
  try {
    hasToken = Boolean(require('./auth').getToken())
  } catch (e) {
    hasToken = false // 单测/非小程序运行时无 wx 存储, 按无 token 处理
  }
  if (!hasToken) return Promise.resolve(false)
  let refresh
  try {
    refresh = require('../api/auth').refreshPermissions(true)
  } catch (e) {
    return Promise.resolve(false)
  }
  const boot = Promise.resolve(refresh)
    .catch(() => false)
    .then(() => Boolean(getGlobalData().permissionsLoaded))
  globalData.permissionRefreshPromise = boot.finally(() => {
    globalData.permissionRefreshPromise = null
  })
  return globalData.permissionRefreshPromise
}

module.exports = { isAuthReady, whenAuthReady, whenPermissionsReady, ensurePermissionsReady }
