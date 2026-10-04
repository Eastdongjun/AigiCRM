// 【E7 去重】getGlobalData/buildUrl 单一出处：此前 getGlobalData 在 utils/request.js、
// utils/auth.js、api/auth.js、api/file.js 各有一份复制(buildUrl 两份)，任一副本被单独
// 改动都会造成取 app 上下文/拼接口地址的口径漂移，故收敛到本模块统一引用。
// 取 app.globalData（无 app 时回退空对象并懒初始化，保证 demo 档/单测环境可用）。
function getGlobalData() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (!app) return {}
  if (!app.globalData) app.globalData = {}
  return app.globalData
}

// 拼「baseUrl + 接口路径」：剔除尾部/头部多余斜杠；baseUrl 为空时退化为根相对路径；
// baseUrl 以 /api 结尾且 path 以 api/ 开头时去掉重复的一段，避免 .../api/api/...。
function buildUrl(baseUrl, path) {
  const normalizedBase = String(baseUrl || '').replace(/\/+$/, '')
  const normalizedPath = String(path || '').replace(/^\/+/, '')
  if (!normalizedBase) return '/' + normalizedPath
  if (normalizedBase.endsWith('/api') && normalizedPath.startsWith('api/')) {
    return normalizedBase + '/' + normalizedPath.slice(4)
  }
  return normalizedBase + '/' + normalizedPath
}

module.exports = { getGlobalData, buildUrl }
