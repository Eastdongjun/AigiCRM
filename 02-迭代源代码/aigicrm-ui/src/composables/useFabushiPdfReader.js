// 【2026-11-16 P3】法布施 PDF 页码级阅读器共用逻辑（PC /fabushi/read 与 MP web-view 同一份）
//
// 为什么抽公共：MP 阅读器(2026-11-16 P2)已验证「PDF.js 单页渲染 + 翻页自动回传 + 续读定位」，
// PC 端升级不能复制一份逻辑（AGENTS.md 口径单源），两边只保留各自的壳（PC 书卷头部侧栏、
// MP 全屏触摸壳），渲染/进度/生命周期逻辑收敛到本文件。
//
// 约定：
// - PDF 用主 JWT 走 Authorization 头拉取（token 不进 URL，避免落到访问日志/Referer）；
// - 进度读写 = /api/fabushi/progress/{bookId}，与书架页/统计同口径；
// - 翻页 debounce 保存，连续翻页只落最终页码；加载完成回读 lastPage 续读定位；
// - 卸载/切书释放渲染任务与 PDF 文档，防内存泄漏与晚到渲染。
//
// 【K-ENG-FE-001】小程序 web-view 内核缺 Map.prototype.getOrInsertComputed
// （TC39 Upsert，Chrome 145+；pdfjs-dist 6.x 主线程 22 处 + worker 线程 19 处调用，
// 真机报 `getOrInsertComputed is not a function`）。此处静态 import 给主线程补齐，
// worker 线程由 pdfjs-worker-shim.worker.js 经 Vite `?worker&url` 打包注入。
// 该 import 必须位于本文件（pdfjs 动态 import 发生在 load() 运行期，
// 静态 import 先于全部运行时求值，时序安全）。
import '@/utils/map-upsert-polyfill'
// worker 资源 URL：Vite 把「polyfill + pdf.worker」打包成独立 worker 产物，
// 运行期仍走 GlobalWorkerOptions.workerSrc（每次加载新建 Worker，
// 与原 `?url` 静态资源行为一致，无 workerPort 的跨加载共享状态）。
import pdfjsWorkerUrl from './pdfjs-worker-shim.worker.js?worker&url'
import { nextTick, onBeforeUnmount, ref } from 'vue'

export const SAVE_DEBOUNCE_MS = 1200
const RESIZE_DEBOUNCE_MS = 150
const MIN_CANVAS_WIDTH = 280
const MAX_CANVAS_WIDTH = 860

/** 页码夹取：非数字/小于 1 归 1；已知总页数时不超过总页数。 */
export function clampPage(page, totalPages) {
  const n = Math.floor(Number(page))
  if (!Number.isFinite(n) || n < 1) return 1
  const total = Math.floor(Number(totalPages))
  if (!Number.isFinite(total) || total < 1) return n
  return Math.min(n, total)
}

/** 续读页：进度 lastPage 按真实页数夹取；进度缺失/异常（如换了挂载文件）回第 1 页。 */
export function resolveResumePage(progress, pageCount) {
  const last = progress && Number(progress.lastPage)
  if (!Number.isFinite(last) || last < 1) return 1
  return clampPage(last, pageCount)
}

/**
 * @param {{
 *   bookId: import('vue').Ref<string|number>,
 *   pdfFileId: import('vue').Ref<string|number>,
 *   token: import('vue').Ref<string>,
 *   saveDelay?: number,
 *   canvasMaxWidth?: number,
 *   progress?: boolean,
 *   initialPage?: number
 * }} opts
 * 【F3a-2】票据模式(MP 壳): progress=false 关内建进度读写(由壳经 postMessage/容器代传);
 * initialPage>0 跳过进度回读直接从该页续读(URL 带 lastPage)。PC 端不传=行为不变。
 */
export function useFabushiPdfReader({ bookId, pdfFileId, token, saveDelay = SAVE_DEBOUNCE_MS, canvasMaxWidth = MAX_CANVAS_WIDTH,
    progress = true, initialPage = 0 }) {
  const state = ref('loading') // loading | ready | error
  const loadPct = ref(0)
  const errorMsg = ref('')
  const currentPage = ref(1)
  const totalPages = ref(0)

  let pdfDoc = null
  let renderTask = null
  let saveTimer = null
  let resizeTimer = null
  let lastSavedPage = 0
  // 画布来源：接受元素或 getter——MP 的画布在 ready 后才挂载，load 时取不到元素
  let canvasSource = null
  let disposed = false

  const authHeader = () => 'Bearer ' + String((token && token.value) || '')
  // 【F3a-2】progress=false 时关内建进度读写(票据模式由壳层经 postMessage/容器代传)
  const progressEnabled = () => progress !== false
  const resolveCanvas = () => {
    if (typeof canvasSource === 'function') return canvasSource()
    return canvasSource
  }

  /** 回读进度；失败不阻断阅读（从第 1 页开始）。 */
  async function readProgress() {
    try {
      const res = await fetch(`/api/fabushi/progress/${bookId.value}`, {
        headers: { Authorization: authHeader() },
      })
      if (!res.ok) return null
      const body = await res.json()
      return (body && body.data) || null
    } catch (_) {
      return null // 进度回读失败不阻断阅读
    }
  }

  /**
   * 加载 PDF 并渲染续读页。
   * @param {HTMLCanvasElement|(() => HTMLCanvasElement|null)} source 画布元素或取画布的 getter
   */
  async function load(source = null) {
    if (source) canvasSource = source
    state.value = 'loading'
    loadPct.value = 0
    errorMsg.value = ''
    try {
      // 动态 import：pdfjs-dist 仅阅读页加载，不进 PC 主包
      const pdfjsLib = await import('pdfjs-dist')
      if (disposed) return
      // 【K-ENG-FE-001】worker 换成含 Map.upsert polyfill 的打包产物（见文件顶部说明）
      pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorkerUrl

      const progress = progressEnabled() ? await readProgress() : null
      if (disposed) return

      const task = pdfjsLib.getDocument({
        url: `/api/file/download/${pdfFileId.value}`,
        httpHeaders: { Authorization: authHeader() },
        withCredentials: false,
      })
      task.onProgress = (p) => {
        if (p && p.total) loadPct.value = Math.min(99, Math.round((p.loaded / p.total) * 100))
      }
      pdfDoc = await task.promise
      if (disposed) return

      totalPages.value = pdfDoc.numPages
      // 【F3a-2】initialPage(URL lastPage)优先于内建进度回读; 两路都缺失回第 1 页
      currentPage.value = initialPage > 0
              ? clampPage(initialPage, pdfDoc.numPages)
              : resolveResumePage(progress, pdfDoc.numPages)
      state.value = 'ready'
      await nextTick() // 等画布挂载后再量宽度
      await renderPage(currentPage.value)
      lastSavedPage = currentPage.value
      saveProgress() // totalPages 补记（首次阅读回填总页数）
    } catch (e) {
      if (disposed) return
      state.value = 'error'
      errorMsg.value = (e && e.message) || '网络异常或文件不可读'
    }
  }

  /** 按画布容器宽度渲染指定页（devicePixelRatio 适配高清屏）。 */
  async function renderPage(num) {
    if (!pdfDoc) return
    const canvas = resolveCanvas()
    if (!canvas) return
    const page = await pdfDoc.getPage(num)
    if (disposed) return
    const dpr = window.devicePixelRatio || 1
    const base = page.getViewport({ scale: 1 })
    const parentWidth = canvas.parentElement ? canvas.parentElement.clientWidth : 0
    const maxWidth = Math.max(MIN_CANVAS_WIDTH, Math.min(parentWidth - 32, canvasMaxWidth))
    const viewport = page.getViewport({ scale: (maxWidth / base.width) * dpr })
    canvas.width = viewport.width
    canvas.height = viewport.height
    canvas.style.width = maxWidth + 'px'
    canvas.style.height = viewport.height / dpr + 'px'
    if (renderTask) {
      try { renderTask.cancel() } catch (_) { /* 已完成任务 cancel 抛错可忽略 */ }
    }
    renderTask = page.render({ canvasContext: canvas.getContext('2d'), viewport })
    await renderTask.promise
  }

  /** 翻页（夹取边界），渲染并 debounce 回传进度。 */
  async function goPage(num) {
    if (!pdfDoc) return
    const n = clampPage(num, totalPages.value)
    if (n === currentPage.value && state.value === 'ready') return
    currentPage.value = n
    await renderPage(n)
    if (n !== lastSavedPage) scheduleSave()
  }

  function scheduleSave() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(saveProgress, saveDelay) // debounce: 连续翻页只保存最终页码
  }

  /** 保存进度；静默失败（下次翻页再试，不打断阅读）。F3a-2 票据模式下为 no-op(壳层接管)。 */
  async function saveProgress() {
    if (!progressEnabled()) return
    if (!bookId.value || !totalPages.value) return
    lastSavedPage = currentPage.value
    try {
      await fetch(`/api/fabushi/progress/${bookId.value}`, {
        method: 'PUT',
        headers: { Authorization: authHeader(), 'Content-Type': 'application/json' },
        body: JSON.stringify({ lastPage: currentPage.value, totalPages: totalPages.value }),
      })
    } catch (_) { /* 静默: 网络抖动不打断阅读，翻页会再触发 */ }
  }

  function onResize() {
    if (disposed || state.value !== 'ready') return
    if (resizeTimer) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => { renderPage(currentPage.value) }, RESIZE_DEBOUNCE_MS)
  }

  /** 重试（错误态按钮）：复用已挂载画布重新拉取。 */
  function retry() {
    return load()
  }

  function dispose() {
    disposed = true
    if (saveTimer) clearTimeout(saveTimer)
    if (resizeTimer) clearTimeout(resizeTimer)
    window.removeEventListener('resize', onResize)
    if (renderTask) { try { renderTask.cancel() } catch (_) { /* 同上 */ } }
    if (pdfDoc) { try { pdfDoc.destroy() } catch (_) { /* destroy 幂等 */ } }
    pdfDoc = null
    renderTask = null
  }

  if (typeof window !== 'undefined') window.addEventListener('resize', onResize)
  onBeforeUnmount(dispose)

  return { state, loadPct, errorMsg, currentPage, totalPages, load, goPage, retry, dispose }
}
