// token 工具：本地解析 JWT，判断是否有效（存在且未过期）

export function getToken() {
  return localStorage.getItem('token')
}

export function clearToken() {
  localStorage.removeItem('token')
}

// 解析 JWT payload，失败返回 null
function decodeJwt(token) {
  try {
    const payload = token.split('.')[1]
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

// token 是否有效：存在、可解析、且未过期。
// 无法解析或缺 exp 时，保守视为「无效」→ 走重新登录，避免带坏 token 打接口。
export function isTokenValid() {
  const token = getToken()
  if (!token) return false
  const payload = decodeJwt(token)
  if (!payload || !payload.exp) return false
  // exp 为秒级时间戳
  return payload.exp * 1000 > Date.now()
}

// ---- 下载专用短时 token（F2 2026-08-09）----
// <img> 缩略图/导出等无法带 header，用短时下载 token（scope=download, 1h）替代主 JWT 进 URL，
// 避免主 token 泄漏到访问日志/浏览器历史/Referer。
// 懒加载 + 内存缓存 ~50min（略小于后端 1h），失效后自动刷新。
let dlToken = null
let dlFetching = null
let dlFetchedAt = 0
const DL_TTL_MS = 50 * 60 * 1000

function refreshDownloadToken() {
  const main = getToken()
  if (!main) return Promise.resolve(null)
  if (dlToken && Date.now() - dlFetchedAt < DL_TTL_MS) return Promise.resolve(dlToken)
  if (dlFetching) return dlFetching
  dlFetching = fetch('/api/file/download-token', { headers: { Authorization: 'Bearer ' + main } })
    .then(r => r.json())
    .then(d => {
      dlToken = d?.data?.token || null
      dlFetchedAt = Date.now()
      return dlToken
    })
    .catch(() => { dlToken = null; return null })
    .finally(() => { dlFetching = null })
  return dlFetching
}

export function getDownloadToken() {
  refreshDownloadToken() // 后台预热
  return dlToken || getToken() // token 就绪前回退主 token（首次渲染窗口极小）
}
