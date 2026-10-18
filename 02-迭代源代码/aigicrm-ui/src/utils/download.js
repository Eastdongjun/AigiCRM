// 文件下载公共工具（F4 2026-08-09）：fetch blob + a.download 强制保存（任何浏览器/状态可靠触发），
// 统一 Authorization header、AbortController 超时、失败/超时 toast；替换 8 处逐行重复实现。
// 用法：downloadFile(url, { name: '文件名.xlsx', headers: { Authorization: 'Bearer ' + t } })
//
// 【2026-10-17 大文件下载修复】原实现用 60s 固定总超时（timeout=60000），超过 60s 的大文件
// （导出 xlsx / 回收站文件等）会在仍有数据到达时被中止。现改为：
//   1. 默认不限制总时长（timeout=0 表示不设总超时；调用方仍可按需传 timeout 毫秒数）；
//   2. 增加“空闲超时”：默认连续 60s 没读到任何数据才中止（idleTimeout=60000），
//      每次读取到数据块都会重置计时器，因此慢但持续传输的大文件不会被误杀；
//   3. 优先用 res.body.getReader() 流式读取以感知数据到达；无流式 body 的旧环境回退
//      res.blob()，该路径无法感知进度，空闲计时不生效，仅受可选总时长约束（已在下方注释）。
// Authorization header、失败提示、blob 保存行为保持不变。
import { ElMessage } from 'element-plus'

// 空闲超时：连续 N 毫秒未读到数据则中止（0 表示禁用空闲超时）
const DEFAULT_IDLE_TIMEOUT = 60000
// 总超时：0 表示不限制总时长（大文件不再被 60s 固定总时长误杀）
const DEFAULT_TOTAL_TIMEOUT = 0

// 【2026-08-13 全局中文化】下载失败的 HTTP 状态码 → 中文提示
const STATUS_MSG = {
  401: '登录已过期，请重新登录',
  403: '没有下载权限',
  404: '文件不存在或已被删除',
  413: '文件过大，超出下载限制',
  500: '服务器繁忙，请稍后再试'
}

/** 触发浏览器保存（保持原有 blob + a.download 行为不变） */
function saveBlob(blob, name) {
  const a = document.createElement('a')
  const blobUrl = URL.createObjectURL(blob)
  a.href = blobUrl
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(blobUrl)
}

export async function downloadFile(url, {
  name = '下载',
  headers = {},
  // timeout：总时长上限（毫秒），默认 0 = 不限制；调用方需要总时限时显式传入
  timeout = DEFAULT_TOTAL_TIMEOUT,
  // idleTimeout：无数据空闲上限（毫秒），默认 60s；0 = 禁用
  idleTimeout = DEFAULT_IDLE_TIMEOUT,
} = {}) {
  const controller = new AbortController()
  let idleTimer = null
  let totalTimer = null

  const clearTimers = () => {
    if (idleTimer) { clearTimeout(idleTimer); idleTimer = null }
    if (totalTimer) { clearTimeout(totalTimer); totalTimer = null }
  }
  // 每读到一块数据就重置空闲计时器；idleTimeout<=0 时不做空闲中止
  const resetIdleTimer = () => {
    if (!(idleTimeout > 0)) return
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(() => controller.abort(), idleTimeout)
  }

  try {
    // 可选总超时：默认不启用，避免大文件在数据持续传输时被固定时长中止
    if (timeout > 0) totalTimer = setTimeout(() => controller.abort(), timeout)
    // 等待响应头阶段同样受空闲超时保护（迟迟拿不到响应也算“无数据”）
    resetIdleTimer()

    const res = await fetch(url, { headers, signal: controller.signal })
    if (!res.ok) {
      ElMessage.error(STATUS_MSG[res.status] || '下载失败，请稍后重试')
      return false
    }

    let blob
    if (res.body && typeof res.body.getReader === 'function') {
      // 流式读取：逐块感知数据到达，持续传输时不断重置空闲计时器
      const reader = res.body.getReader()
      const chunks = []
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        // 读到数据即重置空闲计时器（大文件慢速传输不会被 60s 误杀）
        resetIdleTimer()
        if (value && value.byteLength) chunks.push(value)
      }
      const contentType = res.headers.get('content-type') || ''
      blob = new Blob(chunks, { type: contentType.split(';')[0].trim() })
    } else {
      // 旧环境无流式 body：blob() 期间无法感知数据到达，若继续跑空闲计时会把
      // “正在下载但无进度事件”的大文件误判为卡死，故此处清除空闲计时（不启用总超时的
      // 默认配置下等同于不限时；显式传 timeout 时仍受其约束）。
      if (idleTimer) { clearTimeout(idleTimer); idleTimer = null }
      blob = await res.blob()
    }

    saveBlob(blob, name)
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
    clearTimers()
  }
}
