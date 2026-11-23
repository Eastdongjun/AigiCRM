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

/** 单页命中上限：防某页高频词（如"的"）刷爆结果列表，也让用户不至于滚不完。 */
const MAX_HITS_PER_PAGE = 50

/**
 * 书内搜索（纯函数，不依赖 PDF/DOM，便于单测）。
 * 大小写不敏感；中文直接子串匹配，不做分词；snippet 取命中前后各 ~30 字，命中词用 ⟪⟫ 标出。
 * @param {{page:number, text:string}[]} pages
 * @param {string} keyword
 * @returns {{page:number, snippet:string}[]}
 */
export function searchInPagesPure(pages, keyword) {
  const kw = String(keyword || '').trim()
  if (!kw || !Array.isArray(pages)) return []
  const lower = kw.toLowerCase()
  const hits = []
  for (const item of pages) {
    const text = String((item && item.text) || '')
    if (!text) continue
    const hay = text.toLowerCase()
    let from = 0
    let guard = 0
    while (guard < MAX_HITS_PER_PAGE) {
      const at = hay.indexOf(lower, from)
      if (at < 0) break
      const start = Math.max(0, at - 30)
      const end = Math.min(text.length, at + kw.length + 30)
      hits.push({
        page: item.page,
        snippet: `${start > 0 ? '…' : ''}${text.slice(start, at)}⟪${text.slice(at, at + kw.length)}⟫${text.slice(at + kw.length, end)}${end < text.length ? '…' : ''}`,
      })
      from = at + kw.length
      guard += 1
    }
  }
  return hits
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

  /**
   * 【D24 2026-11-23】只更新页码与进度，不重绘画布。
   * 重排视图是连续滚动，页码随滚动变化，若走 goPage 会白白重绘一张看不见的画布。
   */
  function markPage(num) {
    const n = clampPage(num, totalPages.value)
    if (n === currentPage.value) return
    currentPage.value = n
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

  /* ==================== 文本层（D24：重排 / 字号行距 / 书内搜索） ====================
   * 只读 PDF 自带的文本层，不做 OCR（原型明确「在线 OCR 重排」不做）。
   * 影印件没有文本层 ⇒ pageText 返回空串，由壳层如实提示「重排与书内搜索不可用」，
   * 不允许拿图片假装文字，也不允许在无文本时给出"搜索无结果"的假结论。
   */
  const textCache = new Map()
  let textLayerProbe = null

  /** 单页文本：按 item.str 拼接，item.hasEOL 断行（PDF 文本层无段落语义，不臆造段落）。 */
  async function pageText(num) {
    const n = Math.floor(Number(num))
    if (!Number.isFinite(n) || n < 1) return ''
    if (textCache.has(n)) return textCache.get(n)
    if (!pdfDoc) return ''
    const page = await pdfDoc.getPage(n)
    if (disposed) return ''
    const content = await page.getTextContent()
    let out = ''
    for (const item of content.items) {
      if (!item || typeof item.str !== 'string') continue
      out += item.str
      if (item.hasEOL) out += '\n'
    }
    const text = out.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim()
    textCache.set(n, text)
    return text
  }

  /**
   * 是否有文本层：探前 min(3, 总页数) 页，任一处有实质文字即视为有。
   * 结果缓存，重复调用不再触发 PDF 解析。
   */
  async function detectTextLayer() {
    if (textLayerProbe !== null) return textLayerProbe
    if (!pdfDoc) return false
    const probe = Math.min(3, totalPages.value || 1)
    for (let i = 1; i <= probe; i += 1) {
      const t = await pageText(i)
      if (t && t.replace(/\s/g, '').length >= 10) {
        textLayerProbe = true
        return true
      }
    }
    textLayerProbe = false
    return false
  }

  /**
   * 全书文本（书内搜索用）：逐页提取并回调进度；已提取过的页走缓存。
   * @param {(done:number, total:number) => void} [onProgress]
   */
  async function extractAllText(onProgress) {
    const total = totalPages.value
    if (!pdfDoc || !total) return []
    const pages = []
    for (let i = 1; i <= total; i += 1) {
      if (disposed) break
      pages.push({ page: i, text: await pageText(i) })
      if (typeof onProgress === 'function') onProgress(i, total)
    }
    return pages
  }

  /**
   * 书内搜索：大小写不敏感、支持中文（不做分词，直接子串），返回 { page, snippet } 列表。
   * 纯函数在模块级（searchInPages），这里只做转发，便于单测不依赖 PDF/DOM。
   */
  function searchInPages(pages, keyword) {
    return searchInPagesPure(pages, keyword)
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

  return {
    state, loadPct, errorMsg, currentPage, totalPages, load, goPage, markPage, retry, dispose,
    // 【D24 2026-11-23】文本层能力：重排视图与书内搜索共用，不做 OCR
    pageText, detectTextLayer, extractAllText, searchInPages,
  }
}
