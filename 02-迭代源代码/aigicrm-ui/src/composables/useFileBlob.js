// 在线预览的文件字节获取（文件阅读器 2026-10-16）
//
// pdf/docx/sheet/text 四种渲染器都要先把文件拿到内存再渲染，共用这一份：
// - 同源 fetch + Authorization: Bearer 主 JWT（CSP connect-src 'self' 放行；主 JWT 永不进 URL）
// - AbortController：切换文件/关闭对话框即中止在途请求，避免晚到响应渲染到错的文件上
// - 体积阈值：超过 PREVIEW_SIZE_LIMIT 不拉取，由调用方展示"仅支持下载"——浏览器端解析
//   几十 MB 的 docx/xlsx 会卡死标签页，这是产品边界不是故障
// - 失败文案复用下载工具的状态码口径
import { ref, onBeforeUnmount } from 'vue'
import { getToken } from '@/utils/token'

export const PREVIEW_SIZE_LIMIT = 30 * 1024 * 1024

const STATUS_MSG = {
  401: '登录已过期，请重新登录',
  403: '没有查看权限',
  404: '文件不存在或已被删除',
  500: '服务器繁忙，请稍后再试',
}

export function useFileBlob() {
  const loading = ref(false)
  const error = ref('')
  const tooLarge = ref(false)
  let controller = null

  function abort() {
    if (controller) {
      controller.abort()
      controller = null
    }
  }

  /**
   * 拉取文件内容。
   * @param {string} url 下载地址（可带 ?token=，header 优先）
   * @param {{ expect?: 'blob'|'arrayBuffer'|'text', sizeHint?: number, timeout?: number }} opts
   * @returns {Promise<Blob|ArrayBuffer|string|null>} 失败/超限返回 null，状态写入 error/tooLarge
   */
  async function load(url, { expect = 'blob', sizeHint = 0, timeout = 120000 } = {}) {
    abort()
    error.value = ''
    tooLarge.value = false
    if (!url) {
      error.value = '文件地址缺失'
      return null
    }
    if (sizeHint && sizeHint > PREVIEW_SIZE_LIMIT) {
      tooLarge.value = true
      return null
    }
    const own = new AbortController()
    controller = own
    const timer = setTimeout(() => own.abort(), timeout)
    loading.value = true
    try {
      const res = await fetch(url, {
        headers: { Authorization: 'Bearer ' + (getToken() || '') },
        signal: own.signal,
      })
      if (!res.ok) {
        error.value = STATUS_MSG[res.status] || `文件加载失败（${res.status}）`
        return null
      }
      const length = Number(res.headers.get('content-length') || 0)
      if (length > PREVIEW_SIZE_LIMIT) {
        tooLarge.value = true
        return null
      }
      if (expect === 'text') return await res.text()
      if (expect === 'arrayBuffer') return await res.arrayBuffer()
      return await res.blob()
    } catch (e) {
      // 被更新的请求取代（abort）不算错误；超时与网络失败给出可读原因
      if (e && e.name === 'AbortError') {
        if (controller === own) error.value = '加载超时，请重试或下载查看'
      } else {
        error.value = '网络错误，文件加载失败'
      }
      return null
    } finally {
      clearTimeout(timer)
      if (controller === own) {
        controller = null
        loading.value = false
      }
    }
  }

  onBeforeUnmount(abort)

  return { loading, error, tooLarge, load, abort }
}

/** 人类可读的体积阈值文案，供"文件过大"提示使用 */
export function previewSizeLimitText() {
  return `${Math.round(PREVIEW_SIZE_LIMIT / 1024 / 1024)}MB`
}
