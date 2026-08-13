// 文件下载公共工具（F4 2026-08-09）：fetch blob + a.download 强制保存（任何浏览器/状态可靠触发），
// 统一 Authorization header、AbortController 超时、失败/超时 toast；替换 8 处逐行重复实现。
// 用法：downloadFile(url, { name: '文件名.xlsx', headers: { Authorization: 'Bearer ' + t } })
import { ElMessage } from 'element-plus'

const DEFAULT_TIMEOUT = 60000

// 【2026-08-13 全局中文化】下载失败的 HTTP 状态码 → 中文提示
const STATUS_MSG = {
  401: '登录已过期，请重新登录',
  403: '没有下载权限',
  404: '文件不存在或已被删除',
  413: '文件过大，超出下载限制',
  500: '服务器繁忙，请稍后再试'
}

export async function downloadFile(url, { name = '下载', headers = {}, timeout = DEFAULT_TIMEOUT } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const res = await fetch(url, { headers, signal: controller.signal })
    if (!res.ok) {
      ElMessage.error(STATUS_MSG[res.status] || '下载失败，请稍后重试')
      return false
    }
    const blob = await res.blob()
    const a = document.createElement('a')
    const blobUrl = URL.createObjectURL(blob)
    a.href = blobUrl
    a.download = name
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(blobUrl)
    return true
  } catch (e) {
    if (e && e.name === 'AbortError') {
      ElMessage.error('下载超时，请重试')
    } else {
      // 【2026-08-13 全局中文化】fetch 网络类异常(如 "Failed to fetch")统一中文兜底
      ElMessage.error('下载失败，请稍后重试')
    }
    return false
  } finally {
    clearTimeout(timer)
  }
}
