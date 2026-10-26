// 【2026-10-26 用户令"文件管理默认下载缓存"】文件预览本地缓存:
// 预览下载落到 USER_DATA_PATH/file-cache, 同版本文件再次打开秒开(不重新请求);
// key = 文件id + updatedAt(文件被替换/更新后自动失效), LRU 淘汰, 默认上限 200MB。
// 所有操作 fail-open: 缓存目录不可写/索引损坏时静默退回直下, 绝不阻塞预览主链路。
const DIR = wx.env.USER_DATA_PATH + '/file-cache'
const INDEX_PATH = DIR + '/index.json'
const MAX_TOTAL_BYTES = 200 * 1024 * 1024

let indexCache = null

function fs() {
  return wx.getFileSystemManager()
}

function ensureDir() {
  try {
    fs().accessSync(DIR)
  } catch (e) {
    try { fs().mkdirSync(DIR, true) } catch (e2) { /* 不可写则放弃缓存 */ }
  }
}

function loadIndex() {
  if (indexCache) return indexCache
  ensureDir()
  try {
    indexCache = JSON.parse(fs().readFileSync(INDEX_PATH, 'utf8') || '{}') || {}
  } catch (e) {
    indexCache = {}
  }
  return indexCache
}

function saveIndex() {
  try {
    ensureDir()
    fs().writeFileSync(INDEX_PATH, JSON.stringify(indexCache || {}))
  } catch (e) { /* 索引持久化失败只影响下次冷启动的命中率 */ }
}

function suffixOf(name) {
  const s = String(name || '').split('.').pop()
  return /^[a-zA-Z0-9]{1,6}$/.test(s) ? s.toLowerCase() : 'bin'
}

function cacheKey(item) {
  const raw = String(item.id || item.ossKey || item.fileUrl || 'f') + '_' + String(item.updatedAt || '')
  return raw.replace(/[^a-zA-Z0-9_-]/g, '').slice(-72) + '.' + suffixOf(item.name)
}

/** 命中返回本地路径并刷新 LRU; 未命中/文件已被系统清理返回 '' */
function lookup(item) {
  try {
    const entry = loadIndex()[cacheKey(item)]
    if (!entry || !entry.path) return ''
    fs().accessSync(entry.path)
    entry.lastUsed = Date.now()
    saveIndex()
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
    const dest = DIR + '/' + key
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

module.exports = { lookup, store }
