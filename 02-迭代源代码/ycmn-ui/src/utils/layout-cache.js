// 布局级公共数据缓存：分类树/用户列表等跨页面复用，10 分钟 TTL
// 【性能 2026-08-08】激励查询/统计/上传等多页面重复请求公共数据，缓存后二次进入免网络请求
const TTL = 10 * 60 * 1000
const store = new Map()

/**
 * 带 TTL 的缓存包装：hit 直接返回缓存的 Promise；miss 调用 fetcher 后缓存其返回值。
 * @param {string} key 缓存键（同数据跨页面复用需同 key）
 * @param {() => Promise<any>} fetcher 返回 axios 响应（缓存整个响应，调用方自行取 .data）
 */
export function cachedGet(key, fetcher) {
  const hit = store.get(key)
  const now = Date.now()
  if (hit && now - hit.at < TTL) return Promise.resolve(hit.data)
  return fetcher().then(res => {
    store.set(key, { at: now, data: res })
    return res
  }).catch(err => {
    store.delete(key) // 失败不缓存，下次重试
    throw err
  })
}

export function clearLayoutCache() {
  store.clear()
}
