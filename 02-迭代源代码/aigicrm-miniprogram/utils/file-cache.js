// 【2026-10-26 用户令"文件管理默认下载缓存"】文件预览本地缓存:
// 预览下载落到 USER_DATA_PATH/file-cache, 同版本文件再次打开秒开(不重新请求), LRU 淘汰。
// 【2026-10-27 深度检查修复批】
//   · 目录路径改为首次使用时求值——模块顶层读 wx.env 会让 Node 契约测试(桩无 wx.env) require 即崩
//   · 缓存键加入 用户+环境 维度, 并提供 clear(): 登出/换号即清——否则同设备换账号可离线打开
//     上一账号缓存的无权文件(绕过服务端下载鉴权)
//   · 键的版本段优先取 version(后端替换文件只 +version 不刷新 updatedAt), 换版即失效
//   · 上限 200→150MB: 给 fs.saveFile/临时文件等共享 USER_DATA_PATH 额度的写入留余量
// 所有操作 fail-open: 缓存目录不可写/索引损坏时静默退回直下, 绝不阻塞预览主链路。
const MAX_TOTAL_BYTES = 150 * 1024 * 1024

let indexCache = null
let dirPath = ''
// 【性能 2026-10-27】索引落盘节流: lookup 命中只刷内存 lastUsed, 至多 60s 落一次盘
// (此前每次命中都同步重写整个 index.json); store 写入新内容仍立即落盘
let lastIndexSaveAt = 0

function fs() {
  return wx.getFileSystemManager()
}

/** 目录惰性求值(勿在模块顶层读 wx.env——Node 测试环境无此全局) */
function dir() {
  if (!dirPath) dirPath = wx.env.USER_DATA_PATH + '/file-cache'
  return dirPath
}

function ensureDir() {
  const d = dir()
  try {
    fs().accessSync(d)
  } catch (e) {
    try { fs().mkdirSync(d, true) } catch (e2) { /* 不可写则放弃缓存 */ }
  }
}

function loadIndex() {
  if (indexCache) return indexCache
  ensureDir()
  try {
    indexCache = JSON.parse(fs().readFileSync(dir() + '/index.json', 'utf8') || '{}') || {}
  } catch (e) {
    indexCache = {}
  }
  return indexCache
}

function saveIndex() {
  try {
    ensureDir()
    fs().writeFileSync(dir() + '/index.json', JSON.stringify(indexCache || {}))
    lastIndexSaveAt = Date.now()
  } catch (e) { /* 索引持久化失败只影响下次冷启动的命中率 */ }
}

function suffixOf(name) {
  const s = String(name || '').split('.').pop()
  return /^[a-zA-Z0-9]{1,6}$/.test(s) ? s.toLowerCase() : 'bin'
}

/** 当前缓存归属(用户+接口环境); 换号/换环境后键自然错开, 旧条目等 LRU 淘汰或 clear() 清掉 */
function scopeSlug() {
  let userId = ''
  let apiBase = ''
  try {
    const app = typeof getApp === 'function' ? getApp() : null
    const user = app && app.globalData && app.globalData.currentUser
    userId = String((user && (user.id != null ? user.id : user.username)) || '')
    apiBase = String((app && app.globalData && app.globalData.apiBaseUrl) || '')
  } catch (e) { /* 取不到归属时按空串, 与历史键兼容 */ }
  return (userId + '~' + apiBase).replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 40)
}

function cacheKey(item) {
  const scope = scopeSlug()
  const id = String(item.id || item.ossKey || item.fileUrl || 'f').replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 40)
  // 版本段优先 version(后端替换文件 setVersion+1 而不刷新 updatedAt), 没有再退 updatedAt
  const ver = String(item.version || item.updatedAt || '').replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 32)
  return (scope + '.' + id + '.' + ver + '.' + suffixOf(item.name)).slice(0, 120)
}

/** 命中返回本地路径并刷新 LRU; 未命中/文件已被系统清理返回 '' */
function lookup(item) {
  try {
    const entry = loadIndex()[cacheKey(item)]
    if (!entry || !entry.path) return ''
    fs().accessSync(entry.path)
    entry.lastUsed = Date.now()
    if (Date.now() - lastIndexSaveAt >= 60000) saveIndex()
    return entry.path
  } catch (e) {
    return ''
  }
}

/** 把下载的临时文件落缓存(同 key 覆盖); 超上限按 lastUsed 淘汰最旧; 返回缓存路径, 失败返回 '' */
function store(item, tempFilePath) {
  try {
    ensureDir()
    const key = cacheKey(item)
    const dest = dir() + '/' + key
    let size = 0
    try {
      try { fs().unlinkSync(dest) } catch (e) {}
      fs().copyFileSync(tempFilePath, dest)
      size = fs().statSync(dest).size || 0
    } catch (e) {
      return ''
    }
    const list = loadIndex()
    list[key] = { path: dest, size, lastUsed: Date.now() }
    // LRU 淘汰: 总量超上限时删最久未用, 当前刚写入的永不淘汰
    const entries = Object.keys(list).map((k) => ({ k, ...list[k] })).sort((a, b) => a.lastUsed - b.lastUsed)
    let total = entries.reduce((sum, entry) => sum + (entry.size || 0), 0)
    for (const entry of entries) {
      if (total <= MAX_TOTAL_BYTES) break
      if (entry.k === key) continue
      try { fs().unlinkSync(entry.path) } catch (e) { /* 已被清理 */ }
      delete list[entry.k]
      total -= entry.size || 0
    }
    saveIndex()
    return dest
  } catch (e) {
    return ''
  }
}

/** 清空整个缓存目录(登出/换号调用): 删目录下全部文件并复位索引; 失败静默(fail-open) */
function clear() {
  try {
    indexCache = null
    const d = dir()
    try {
      const names = fs().readdirSync(d) || []
      names.forEach((name) => {
        try { fs().unlinkSync(d + '/' + name) } catch (e) { /* 单个失败不阻断 */ }
      })
    } catch (e) { /* 目录不存在=本来就空 */ }
  } catch (e) { /* 清缓存永不抛错 */ }
}

module.exports = { lookup, store, clear }
