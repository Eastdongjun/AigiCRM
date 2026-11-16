<template>
  <div class="mfbr">
    <!-- 仅微信小程序 web-view 内可用: token 由小程序 URL 带入, 不依赖 PC 登录态 -->
    <div v-if="!isWechat" class="mfbr-block">
      <div class="mfbr-icon">🔒</div>
      <div class="mfbr-title">请从微信小程序进入</div>
      <div class="mfbr-desc">本页面在小程序「书目详情 → 在线阅读」中使用</div>
    </div>

    <div v-else-if="!token || !pdfFileId" class="mfbr-block">
      <div class="mfbr-icon">⚠️</div>
      <div class="mfbr-title">参数缺失</div>
      <div class="mfbr-desc">请返回小程序重新进入</div>
    </div>

    <template v-else>
      <div class="mfbr-head">
        <button class="mfbr-back" @click="goBack">‹ 返回</button>
        <div class="mfbr-title-row">
          <b class="mfbr-book">{{ title }}</b>
          <span class="mfbr-page" v-if="totalPages">第 {{ currentPage }} / {{ totalPages }} 页</span>
        </div>
      </div>

      <div class="mfbr-body">
        <div v-if="state === 'loading'" class="mfbr-block">
          <div class="mfbr-loading">正在加载电子书…</div>
          <div class="mfbr-pct">{{ loadPct ? loadPct + '%' : '' }}</div>
        </div>
        <div v-else-if="state === 'error'" class="mfbr-block">
          <div class="mfbr-icon">📖</div>
          <div class="mfbr-title">加载失败</div>
          <div class="mfbr-desc">{{ errorMsg }}</div>
          <button class="mfbr-retry" @click="loadPdf">重试</button>
        </div>
        <template v-else>
          <div class="mfbr-stage" @touchstart="onTouchStart" @touchend="onTouchEnd">
            <canvas ref="canvasEl" class="mfbr-canvas"></canvas>
          </div>
          <div class="mfbr-nav">
            <button class="mfbr-nav-btn" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">上一页</button>
            <span class="mfbr-nav-page">{{ currentPage }} / {{ totalPages }}</span>
            <button class="mfbr-nav-btn" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">下一页</button>
          </div>
          <div class="mfbr-foot">翻页自动保存进度，返回后可从本次页码继续</div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
// 【2026-11-16 P2 方案A】MP PDF 页码级进度: H5 PDF.js 阅读器(单页模式+滑动翻页),
// 翻页 debounce 自动回传 /api/fabushi/progress/{bookId}(lastPage/totalPages)。
// openDocument 不支持页码回传/定位——本页替代 MP openDocument 阅读路径(交接单 P2 红线)。
// 鉴权与 /mp-file-picker 先例同构: token 由小程序 URL 带入, API 直传 Authorization header。
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const token = ref('')
const bookId = ref('')
const pdfFileId = ref('')
const title = ref('')
const isWechat = /MicroMessenger/i.test(navigator.userAgent)

const state = ref('loading') // loading | ready | error
const loadPct = ref(0)
const errorMsg = ref('')
const currentPage = ref(1)
const totalPages = ref(0)
const canvasEl = ref(null)

let pdfDoc = null
let renderTask = null
let saveTimer = null
let lastSavedPage = 0
let touchX = 0

// web-view 内跳回小程序需微信 JSSDK(动态加载 + CSP 定向放行, 同 FilePicker.vue 方案)
const wxReady = ref(false)
onMounted(() => {
  token.value = String(route.query.token || '')
  bookId.value = String(route.query.bookId || '')
  pdfFileId.value = String(route.query.pdfFileId || '')
  title.value = decodeURIComponent(String(route.query.title || '在线阅读'))
  const s = document.createElement('script')
  s.src = 'https://res.wx.qq.com/open/js/jweixin-1.3.2.js'
  s.onload = () => { wxReady.value = true }
  s.onerror = () => { console.warn('JSSDK 加载失败(CSP/网络)——请用左上角返回') }
  document.head.appendChild(s)
  if (token.value && pdfFileId.value) loadPdf()
})
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  if (renderTask) { try { renderTask.cancel() } catch (_) { /* 已完成任务 cancel 抛错可忽略 */ } }
  if (pdfDoc) { try { pdfDoc.destroy() } catch (_) { /* destroy 幂等 */ } }
})

function goBack() {
  setTimeout(() => {
    // 微信 JSSDK 动态注入的全局(仅 web-view 环境存在), JSDoc cast 绕开 Window 类型无 wx 声明
    const wxApi = /** @type {any} */ (window).wx
    if (wxReady.value && wxApi && wxApi.miniProgram) {
      wxApi.miniProgram.navigateBack()
    }
    // JSSDK 未就绪兜底: 小程序自带左上角返回键可用
  }, 200)
}

async function loadPdf() {
  state.value = 'loading'
  loadPct.value = 0
  errorMsg.value = ''
  try {
    // 动态 import: pdfjs-dist 仅本页加载, 不影响 PC 主包
    const [pdfjsLib, workerMod] = await Promise.all([
      import('pdfjs-dist'),
      import('pdfjs-dist/build/pdf.worker.min.mjs?url')
    ])
    pdfjsLib.GlobalWorkerOptions.workerSrc = workerMod.default
    // 1) 回读进度(H5 自取, 不依赖 MP 传参, 与 PC read.vue 同一接口同一口径)
    let lastPage = 1
    try {
      const r = await fetch(`/api/fabushi/progress/${bookId.value}`, {
        headers: { Authorization: 'Bearer ' + token.value }
      })
      const body = await r.json()
      if (body && body.data && body.data.lastPage) lastPage = Math.max(1, Number(body.data.lastPage) || 1)
    } catch (_) { /* 进度回读失败不阻断阅读, 从第 1 页开始 */ }
    // 2) 加载 PDF 流(主 JWT header 通道, 与 MP openDocument 同一 file:download 权限要求)
    const task = pdfjsLib.getDocument({
      url: `/api/file/download/${pdfFileId.value}`,
      httpHeaders: { Authorization: 'Bearer ' + token.value },
      withCredentials: false
    })
    task.onProgress = (p) => { if (p && p.total) loadPct.value = Math.min(99, Math.round((p.loaded / p.total) * 100)) }
    pdfDoc = await task.promise
    totalPages.value = pdfDoc.numPages
    currentPage.value = Math.min(lastPage, pdfDoc.numPages)
    state.value = 'ready'
    await renderPage(currentPage.value)
    lastSavedPage = currentPage.value
    saveProgress() // totalPages 补记(首次阅读时回填总页数)
  } catch (e) {
    state.value = 'error'
    errorMsg.value = (e && e.message) || '网络异常或文件不可读'
  }
}

async function renderPage(num) {
  if (!pdfDoc) return
  const page = await pdfDoc.getPage(num)
  const canvas = canvasEl.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  const base = page.getViewport({ scale: 1 })
  const cssWidth = Math.min(canvas.parentElement.clientWidth - 16, 800)
  const scale = cssWidth / base.width
  const viewport = page.getViewport({ scale: scale * dpr })
  canvas.width = viewport.width
  canvas.height = viewport.height
  canvas.style.width = cssWidth + 'px'
  canvas.style.height = (viewport.height / dpr) + 'px'
  if (renderTask) { try { renderTask.cancel() } catch (_) { /* 同上 */ } }
  const ctx = canvas.getContext('2d')
  renderTask = page.render({ canvasContext: ctx, viewport })
  await renderTask.promise
}

async function goPage(num) {
  const n = Math.max(1, Math.min(totalPages.value || 1, Number(num) || 1))
  if (!pdfDoc) return
  currentPage.value = n
  await renderPage(n)
  if (n !== lastSavedPage) {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(saveProgress, 1200) // debounce: 连续翻页只保存最终页码
  }
}

function onTouchStart(e) {
  touchX = (e.touches && e.touches[0] && e.touches[0].clientX) || 0
}
function onTouchEnd(e) {
  const endX = (e.changedTouches && e.changedTouches[0] && e.changedTouches[0].clientX) || 0
  const dx = endX - touchX
  if (Math.abs(dx) < 48) return // 过短滑动视为点击, 不翻页
  if (dx < 0 && currentPage.value < totalPages.value) goPage(currentPage.value + 1)
  if (dx > 0 && currentPage.value > 1) goPage(currentPage.value - 1)
}

async function saveProgress() {
  if (!bookId.value || !totalPages.value) return
  lastSavedPage = currentPage.value
  try {
    await fetch(`/api/fabushi/progress/${bookId.value}`, {
      method: 'PUT',
      headers: { Authorization: 'Bearer ' + token.value, 'Content-Type': 'application/json' },
      body: JSON.stringify({ lastPage: currentPage.value, totalPages: totalPages.value })
    })
  } catch (_) { /* 静默: 下次翻页再试, 不打断阅读 */ }
}
</script>

<style scoped>
.mfbr { min-height: 100vh; background: #f5f7fa; font-family: -apple-system, 'PingFang SC', sans-serif; display: flex; flex-direction: column; }
.mfbr-block { padding-top: 38vh; text-align: center; }
.mfbr-icon { font-size: 44px; margin-bottom: 12px; }
.mfbr-title { font-size: 17px; font-weight: 600; color: #1f2d3d; }
.mfbr-desc { font-size: 13px; color: #8a97a6; margin-top: 6px; line-height: 1.5; padding: 0 24px; }
.mfbr-loading { font-size: 15px; color: #1f2d3d; }
.mfbr-pct { font-size: 13px; color: #8a97a6; margin-top: 8px; }
.mfbr-retry { margin: 16px auto 0; padding: 0 28px; height: 40px; border: none; border-radius: 10px; background: #3b6ff2; color: #fff; font-size: 14px; }
.mfbr-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: #fff; border-bottom: 1px solid #e8edf3; }
.mfbr-back { border: none; background: none; font-size: 15px; color: #3b6ff2; padding: 4px 6px; }
.mfbr-title-row { display: flex; flex-direction: column; min-width: 0; }
.mfbr-book { font-size: 15px; color: #1f2d3d; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mfbr-page { font-size: 12px; color: #8a97a6; margin-top: 2px; }
.mfbr-body { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.mfbr-stage { flex: 1; display: flex; justify-content: center; align-items: flex-start; overflow: auto; padding: 8px; }
.mfbr-canvas { background: #fff; box-shadow: 0 1px 6px rgba(31, 45, 61, .12); }
.mfbr-nav { display: flex; align-items: center; justify-content: space-between; padding: 8px 16px; background: #fff; border-top: 1px solid #e8edf3; }
.mfbr-nav-btn { border: 1px solid #b8c4d0; background: #fff; border-radius: 8px; padding: 8px 16px; font-size: 14px; color: #1f2d3d; }
.mfbr-nav-btn:disabled { opacity: .4; }
.mfbr-nav-page { font-size: 13px; color: #8a97a6; }
.mfbr-foot { text-align: center; font-size: 12px; color: #aab4c0; padding: 8px 0 12px; background: #fff; }
</style>
