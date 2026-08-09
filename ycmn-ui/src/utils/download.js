// 文件下载公共工具（F4 2026-08-09）：fetch blob + a.download 强制保存（任何浏览器/状态可靠触发），
// 统一 Authorization header、AbortController 超时、失败/超时 toast；替换 8 处逐行重复实现。
// 用法：downloadFile(url, { name: '文件名.xlsx', headers: { Authorization: 'Bearer ' + t } })
import { ElMessage } from 'element-plus'

const DEFAULT_TIMEOUT = 60000

export async function downloadFile(url, { name = '下载', headers = {}, timeout = DEFAULT_TIMEOUT } = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const res = await fetch(url, { headers, signal: controller.signal })
    if (!res.ok) {
      ElMessage.error(`下载失败(${res.status})`)
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
      ElMessage.error((e && e.message) || '下载失败')
    }
    return false
  } finally {
    clearTimeout(timer)
  }
}
