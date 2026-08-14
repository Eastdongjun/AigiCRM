// token 工具：本地解析 JWT，判断是否有效（存在且未过期）
import { ref } from 'vue'

export function getToken() {
  return localStorage.getItem('token')
}

export function clearToken() {
  localStorage.removeItem('token')
  // 【2026-08-14 评审 P2】登出时重置下载 token 模块态——防失败防抖跨登录污染：
  // 旧会话的 dlFailedAt/dlRetryTimer 残留会把新会话的下载/预览压制 30s 甚至无限重试
  dlToken.value = null
  dlFetchedAt = 0
  dlFailedAt = 0
  if (dlRetryTimer) { clearTimeout(dlRetryTimer); dlRetryTimer = null }
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
// dlToken 用 Vue ref: 模板渲染期调用 getDownloadToken() 会在 token 就绪后自动重渲染
let dlToken = ref(null)
let dlFetching = null
let dlFetchedAt = 0
let dlFailedAt = 0      // 最近一次拉取失败时间（防抖重试用）
let dlRetryTimer = null // 失败后自动重试定时器（单例，防重复调度）
let dlLastMain = null   // 最近一次见到的主 JWT（变化=会话切换，重置模块态）
const DL_TTL_MS = 50 * 60 * 1000
const DL_RETRY_MS = 30 * 1000

// 获取/刷新下载专用短时 token。
// 失败（网络/后端异常）后：30s 内不再重复请求（防抖），30s 后自动重试一次；
// 仍失败则下个 30s 再试（防抖循环，成功或未登录后自动停止），避免一次瞬时失败导致缩略图永久裂图。
export function refreshDownloadToken() {
  const main = getToken()
  if (!main) return Promise.resolve(null)
  // 【2026-08-14 评审 P2】主 JWT 变化（重新登录/换号）时重置模块态，
  // 防旧会话的防抖窗口/失败状态压制新会话的下载 token 获取
  if (main !== dlLastMain) {
    dlLastMain = main
    dlToken.value = null
    dlFetchedAt = 0
    dlFailedAt = 0
    if (dlRetryTimer) { clearTimeout(dlRetryTimer); dlRetryTimer = null }
  }
  if (dlToken.value && Date.now() - dlFetchedAt < DL_TTL_MS) return Promise.resolve(dlToken.value)
  if (dlFetching) return dlFetching
  if (!dlToken.value && dlFailedAt && Date.now() - dlFailedAt < DL_RETRY_MS) return Promise.resolve(null)
  dlFetching = fetch('/api/file/download-token', { headers: { Authorization: 'Bearer ' + main } })
    .then(r => r.json())
    .then(d => {
      const t = d?.data?.token || null
      if (t) {
        if (dlRetryTimer) { clearTimeout(dlRetryTimer); dlRetryTimer = null }
        dlToken.value = t
        dlFetchedAt = Date.now()
        dlFailedAt = 0
      } else {
        // 响应正常但无 token（异常）：按失败处理，进入防抖重试
        dlToken.value = null
        dlFailedAt = Date.now()
        scheduleDlRetry()
      }
      return dlToken.value
    })
    .catch(() => {
      dlToken.value = null
      dlFailedAt = Date.now()
      scheduleDlRetry()
      return null
    })
    .finally(() => { dlFetching = null })
  return dlFetching
}

function scheduleDlRetry() {
  if (dlRetryTimer) return
  dlRetryTimer = setTimeout(() => {
    dlRetryTimer = null
    refreshDownloadToken()
  }, DL_RETRY_MS)
}

export function getDownloadToken() {
  refreshDownloadToken() // 后台预热
  // 安全修复 2026-08-10: 不再回退主 JWT(防主 token 进 URL/访问日志/Referer);
  // 未就绪时返回 null——渲染期调用因 dlToken 是 ref 会自动重渲染, 显式下载(点击时)此时已就绪
  return dlToken.value
}
