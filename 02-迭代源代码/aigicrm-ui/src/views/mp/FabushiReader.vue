<template>
  <div class="mfbr">
    <!-- 仅微信小程序 web-view 内可用: 短时阅读票由小程序 URL 带入(F3a-2 起不携带主 JWT), 不依赖 PC 登录态 -->
    <div v-if="!isWechat" class="mfbr-block">
      <div class="mfbr-icon">🔒</div>
      <div class="mfbr-title">请从微信小程序进入</div>
      <div class="mfbr-desc">本页面在小程序「书目详情 → 在线阅读」中使用</div>
    </div>

    <div v-else-if="!token || !pdfFileId" class="mfbr-block">
      <div class="mfbr-icon">⚠️</div>
      <div class="mfbr-title">阅读票缺失或已过期</div>
      <div class="mfbr-desc">请返回小程序重新进入（票有效期 60 分钟）</div>
    </div>

    <template v-else>
      <div class="mfbr-head">
        <button class="mfbr-back" @click="goBack">‹ 返回</button>
        <div class="mfbr-title-row">
          <b class="mfbr-book">{{ title }}</b>
          <span class="mfbr-page" v-if="totalPages">
            第 {{ currentPage }} / {{ totalPages }} 页<template v-if="reflowOn"> · 重排</template>
          </span>
        </div>
        <button class="mfbr-aa" :class="{ on: panelOpen }" @click="panelOpen = !panelOpen">Aa</button>
      </div>

      <!-- 【D24 2026-11-23 MP 对齐 PC】版式面板：阅读模式（原样/重排）+ 字号行距 + 书内搜索。
           与 PC read.vue 同一套逻辑，均只读 PDF 自带文本层，不做 OCR。 -->
      <div v-if="panelOpen" class="mfbr-panel">
        <div class="mfbr-sec">
          <div class="mfbr-sec-t">阅读模式</div>
          <div class="mfbr-chips">
            <button type="button" class="mfbr-chip" :class="{ on: !reflowOn }" @click="setReflow(false)">原样（PDF）</button>
            <button
              type="button" class="mfbr-chip" :class="{ on: reflowOn }"
              :disabled="textLayerState !== 'yes'"
              :title="textLayerState === 'none' ? '该 PDF 无文本层（影印件），不支持重排' : ''"
              @click="setReflow(true)"
            >重排（文字）</button>
          </div>
          <p v-if="textLayerState === 'probing'" class="mfbr-note">正在检测文本层…</p>
          <p v-else-if="textLayerState === 'none'" class="mfbr-note warn">影印版 PDF 没有文本层，重排与书内搜索不可用（本系统不做 OCR）。</p>
          <template v-else-if="reflowOn">
            <div class="mfbr-slider">
              <span>字号</span>
              <input type="range" :min="LIMITS.fontSize[0]" :max="LIMITS.fontSize[1]" v-model.number="fontSize" @change="saveSettings" />
              <b>{{ fontSize }}px</b>
            </div>
            <div class="mfbr-slider">
              <span>行距</span>
              <input type="range" :min="LIMITS.lineHeight[0]" :max="LIMITS.lineHeight[1]" v-model.number="lineHeight" @change="saveSettings" />
              <b>{{ lineHeightRatio(lineHeight) }}</b>
            </div>
          </template>
        </div>

        <div class="mfbr-sec">
          <div class="mfbr-sec-t">书内搜索</div>
          <div class="mfbr-search">
            <input v-model="searchKeyword" type="search" placeholder="输入关键词（全文检索）" :disabled="searchState === 'indexing'" @keyup.enter="runSearch" />
            <button type="button" :disabled="searchState === 'indexing' || !searchKeyword.trim()" @click="runSearch">搜</button>
          </div>
          <p v-if="searchState === 'indexing'" class="mfbr-note">正在建立全文索引 {{ indexDone }}/{{ totalPages }} 页…</p>
          <p v-else-if="searchState === 'notext'" class="mfbr-note warn">该书 PDF 无文本层（影印件），书内搜索不可用；请用页码翻页。</p>
          <p v-else-if="searchState === 'done'" class="mfbr-note">
            {{ searchHits.length ? `命中 ${searchHits.length} 处` : '没有找到匹配内容' }}
          </p>
          <div v-if="searchHits.length" class="mfbr-hits">
            <button v-for="(hit, i) in searchHits.slice(0, 40)" :key="i" type="button" class="mfbr-hit" @click="jumpToHit(hit)">
              <span class="mfbr-hit-p">第 {{ hit.page }} 页</span>
              <span class="mfbr-hit-t">{{ hit.snippet }}</span>
            </button>
            <p v-if="searchHits.length > 40" class="mfbr-note">仅显示前 40 处，请输入更具体的关键词。</p>
          </div>
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
          <button class="mfbr-retry" @click="retry">重试</button>
        </div>
        <template v-else>
          <!-- 原样（PDF）：canvas 翻页；重排模式下隐藏但保持挂载（页码进度照旧更新） -->
          <div
            v-show="!reflowOn" class="mfbr-stage"
            @touchstart="onTouchStart" @touchend="onTouchEnd"
          >
            <canvas ref="canvasEl" class="mfbr-canvas"></canvas>
          </div>

          <!-- 重排（文字）：连续滚动，滚到底自动续页，顶部页变化即更新进度 -->
          <div
            v-if="reflowOn" ref="reflowEl" class="mfbr-reflow"
            :style="reflowStyle" @scroll.passive="onReflowScroll"
          >
            <div v-for="blk in reflowBlocks" :key="blk.page" class="mfbr-rblock" :data-page="blk.page">
              <div class="mfbr-rmark">第 {{ blk.page }} 页</div>
              <p v-if="blk.text" class="mfbr-rtext">{{ blk.text }}</p>
              <p v-else class="mfbr-rempty">本页无文本层（可能是插图页），切回「原样（PDF）」查看。</p>
            </div>
            <p v-if="reflowLoading" class="mfbr-rfoot">正在加载后续页…</p>
            <p v-else-if="reflowDone" class="mfbr-rfoot">— 已到最后一页 —</p>
          </div>

          <div v-if="!reflowOn" class="mfbr-nav">
            <button class="mfbr-nav-btn" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">上一页</button>
            <span class="mfbr-nav-page">{{ currentPage }} / {{ totalPages }}</span>
            <button class="mfbr-nav-btn" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">下一页</button>
          </div>
          <div class="mfbr-foot">
            {{ reflowOn ? '上下滑动阅读，页码随滚动自动记录' : '翻页自动保存进度，返回后可从本次页码继续' }}
          </div>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup>
// 【2026-11-16 P3 → F3a-2 2026-11-19 换票】渲染逻辑仍收敛在 useFabushiPdfReader（PC /fabushi/read 同源）。
// 【MP-TOKEN-001 法布施侧闭环】URL 从"携带主 JWT"改为短时阅读票(scope=download∧scene=fabushi-reader,
// 60min)——票仅能拉 PDF(/api/file/download/** 下载路径), 进度读写不再由本页直连(票对 progress 路径
// 无效): 改 postMessage 页码给小程序容器代传(QC 批准方案 A), localStorage 按 bookId 缓存页码供崩溃恢复。
// 【MP-PARITY 2026-11-23】按 PC v8 P5 补齐：重排（文本层）/字号/行距/书内搜索；影印件如实禁用（不 OCR）。
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useFabushiPdfReader } from '@/composables/useFabushiPdfReader'
import {
  READER_LIMITS, normalizeReaderSettings, lineHeightRatio, storageKey
} from '@/utils/fabushi-reader-settings'

const route = useRoute()
const token = ref('')
const bookId = ref('')
const pdfFileId = ref('')
const title = ref('')
const isWechat = /MicroMessenger/i.test(navigator.userAgent)

const canvasEl = ref(null)
const wxReady = ref(false)

// URL lastPage(服务端续读页) vs localStorage 缓存(崩溃恢复, QC 建议采纳)——取较大者
function resolveInitialPage() {
  const fromUrl = Math.floor(Number(route.query.lastPage || 0)) || 0
  let cached = 0
  try {
    const raw = localStorage.getItem('fabushi-reader:' + route.query.bookId)
    const obj = raw ? JSON.parse(raw) : null
    if (obj && Number.isFinite(Number(obj.p))) cached = Math.floor(Number(obj.p))
  } catch (_) { /* localStorage 不可用不阻断 */ }
  return Math.max(fromUrl, cached)
}

const {
  state, loadPct, errorMsg, currentPage, totalPages,
  load, goPage, markPage, retry, pageText, detectTextLayer, extractAllText, searchInPages
} = useFabushiPdfReader({
  bookId,
  pdfFileId,
  token,
  canvasMaxWidth: 800,
  progress: false, // 票据模式: 进度由容器代传, 本页不直连 progress API
  initialPage: resolveInitialPage()
})

const LIMITS = READER_LIMITS
const panelOpen = ref(false)

// ===== 版式（重排/字号/行距）：与 PC 共用 normalizeReaderSettings，越界值一律回落 =====
const settings = ref(normalizeReaderSettings(null, route.query.layoutType))
const reflowOn = computed(() => settings.value.reflow === true)
const fontSize = computed({
  get: () => settings.value.fontSize,
  set: (v) => { settings.value = { ...settings.value, fontSize: v } }
})
const lineHeight = computed({
  get: () => settings.value.lineHeight,
  set: (v) => { settings.value = { ...settings.value, lineHeight: v } }
})
const reflowStyle = computed(() => ({
  '--reader-font-size': settings.value.fontSize + 'px',
  '--reader-line-height': String(lineHeightRatio(settings.value.lineHeight))
}))

function loadSettings() {
  let raw = null
  try { raw = JSON.parse(localStorage.getItem(storageKey(bookId.value)) || 'null') } catch (_) { raw = null }
  settings.value = normalizeReaderSettings(raw, route.query.layoutType)
}
function saveSettings() {
  try { localStorage.setItem(storageKey(bookId.value), JSON.stringify(settings.value)) } catch (_) { /* 存不了不阻断 */ }
}

// ===== 文本层探测 / 重排视图 =====
const textLayerState = ref('probing') // probing | yes | none
const reflowEl = ref(null)
const reflowBlocks = ref([])
const reflowLoading = ref(false)
const reflowDone = ref(false)

async function probeTextLayer() {
  textLayerState.value = 'probing'
  try {
    textLayerState.value = (await detectTextLayer()) ? 'yes' : 'none'
  } catch (e) {
    textLayerState.value = 'none'
  }
  // 影印件不许留在重排模式（否则是一片空白，用户会以为坏了）
  if (textLayerState.value === 'none' && settings.value.reflow) {
    settings.value = { ...settings.value, reflow: false }
    saveSettings()
  }
}

function setReflow(on) {
  if (on && textLayerState.value !== 'yes') return
  settings.value = { ...settings.value, reflow: !!on }
  saveSettings()
  if (on) nextTick(() => resetReflowTo(currentPage.value))
}

async function resetReflowTo(page) {
  if (!reflowOn.value || textLayerState.value !== 'yes') return
  reflowDone.value = false
  reflowLoading.value = true
  const first = Math.max(1, Number(page) || 1)
  const text = await pageText(first)
  reflowBlocks.value = [{ page: first, text }]
  reflowLoading.value = false
  await nextTick()
  if (reflowEl.value) reflowEl.value.scrollTop = 0
}

async function appendReflowPage() {
  if (reflowLoading.value || reflowDone.value) return
  const last = reflowBlocks.value[reflowBlocks.value.length - 1]
  const next = (last ? last.page : 0) + 1
  if (next > totalPages.value) { reflowDone.value = true; return }
  reflowLoading.value = true
  const text = await pageText(next)
  reflowBlocks.value = [...reflowBlocks.value, { page: next, text }]
  reflowLoading.value = false
}

function onReflowScroll() {
  const el = reflowEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) appendReflowPage()
  const marks = el.querySelectorAll('.mfbr-rblock')
  const top = el.getBoundingClientRect().top
  for (const node of marks) {
    if (node.getBoundingClientRect().bottom > top + 20) {
      const n = Number(node.dataset.page)
      if (Number.isFinite(n)) markPage(n)
      break
    }
  }
}

// ===== 书内搜索 =====
const searchKeyword = ref('')
const searchState = ref('idle') // idle | indexing | done | notext
const indexDone = ref(0)
const searchHits = ref([])
let allTextPages = null

async function runSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) { searchHits.value = []; return }
  if (textLayerState.value === 'none') { searchState.value = 'notext'; return }
  if (textLayerState.value === 'probing') return
  if (!allTextPages) {
    searchState.value = 'indexing'
    indexDone.value = 0
    allTextPages = await extractAllText((done) => { indexDone.value = done })
  }
  searchHits.value = searchInPages(allTextPages, kw)
  searchState.value = 'done'
}

function jumpToHit(hit) {
  if (!hit) return
  if (reflowOn.value) markPage(hit.page)
  else goPage(hit.page)
  panelOpen.value = false
}

// 重排模式下翻页控件/搜索跳页后，重排视图跟着换页
watch(currentPage, (v) => {
  if (!reflowOn.value || textLayerState.value !== 'yes') return
  const first = reflowBlocks.value[0]
  const last = reflowBlocks.value[reflowBlocks.value.length - 1]
  if (!first || v < first.page || (last && v > last.page)) resetReflowTo(v)
})

let touchX = 0
let postTimer = null

// 翻页 → debounce 双写: localStorage(同设备崩溃恢复) + postMessage(容器代传, 平台回退/销毁时送达)
function persistProgress() {
  if (!bookId.value || !currentPage.value) return
  try {
    localStorage.setItem('fabushi-reader:' + bookId.value, JSON.stringify({ p: currentPage.value, t: totalPages.value }))
  } catch (_) { /* 同上不阻断 */ }
  const wxApi = /** @type {any} */ (window).wx
  if (wxReady.value && wxApi && wxApi.miniProgram) {
    wxApi.miniProgram.postMessage({ data: { bookId: bookId.value, lastPage: currentPage.value, totalPages: totalPages.value } })
  }
}

watch([currentPage, totalPages], () => {
  if (postTimer) clearTimeout(postTimer)
  postTimer = setTimeout(persistProgress, 800)
})

// web-view 内跳回小程序需微信 JSSDK(动态加载 + CSP 定向放行, 同 FilePicker.vue 方案)
onMounted(() => {
  token.value = String(route.query.ticket || '')
  bookId.value = String(route.query.bookId || '')
  pdfFileId.value = String(route.query.pdfFileId || '')
  title.value = decodeURIComponent(String(route.query.title || '在线阅读'))
  loadSettings()
  const s = document.createElement('script')
  s.src = 'https://res.wx.qq.com/open/js/jweixin-1.3.2.js'
  s.onload = () => { wxReady.value = true }
  s.onerror = () => { console.warn('JSSDK 加载失败(CSP/网络)——请用左上角返回') }
  document.head.appendChild(s)
  if (token.value && pdfFileId.value) {
    // 文本层探测必须在 PDF doc 就绪后（先注册 watcher 再 load，避免 immediate 时 stop 还在 TDZ）
    const stopProbe = watch(state, (v) => {
      if (v === 'ready') { stopProbe(); probeTextLayer() }
    })
    load(() => canvasEl.value)
  }
})

function goBack() {
  persistProgress()
  setTimeout(() => {
    // 微信 JSSDK 动态注入的全局(仅 web-view 环境存在), JSDoc cast 绕开 Window 类型无 wx 声明
    const wxApi = /** @type {any} */ (window).wx
    if (wxReady.value && wxApi && wxApi.miniProgram) {
      wxApi.miniProgram.navigateBack()
    }
    // JSSDK 未就绪兜底: 小程序自带左上角返回键可用
  }, 200)
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
</script>

<style scoped>
/* 【书卷改版 2026-11-16】纸色底/墨绿导航/朱砂强调, 与 MP 书卷语言同源 */
.mfbr { min-height: 100vh; background: #f1ebdf; font-family: -apple-system, 'PingFang SC', sans-serif; display: flex; flex-direction: column; }
.mfbr-block { padding-top: 38vh; text-align: center; }
.mfbr-icon { font-size: 44px; margin-bottom: 12px; }
.mfbr-title { font-size: 17px; font-weight: 600; color: #2e2a24; }
.mfbr-desc { font-size: 13px; color: #9c9184; margin-top: 6px; line-height: 1.5; padding: 0 24px; }
.mfbr-loading { font-size: 15px; color: #6b6257; }
.mfbr-pct { font-size: 13px; color: #9c9184; margin-top: 8px; }
.mfbr-retry { margin: 16px auto 0; padding: 0 28px; height: 40px; border: none; border-radius: 999px; background: #b5543a; color: #fff7ee; font-size: 14px; }
.mfbr-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: #fffdf8; border-bottom: 1px solid #e8e0d3; }
.mfbr-back { border: 1px solid #d8cdbb; background: transparent; border-radius: 999px; font-size: 13px; color: #2d5a4a; padding: 5px 12px; }
.mfbr-title-row { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.mfbr-book { font-family: 'Songti SC', 'STSong', serif; font-size: 16px; font-weight: 700; color: #2e2a24; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mfbr-page { font-size: 12px; color: #a67c52; margin-top: 2px; }
.mfbr-aa { flex-shrink: 0; width: 38px; height: 32px; border: 1px solid #d8cdbb; border-radius: 8px; background: #fffdf8; color: #2d5a4a; font-size: 14px; font-weight: 700; }
.mfbr-aa.on { background: #2d5a4a; border-color: #2d5a4a; color: #fff7ee; }
.mfbr-panel { background: #fffdf8; border-bottom: 1px solid #e8e0d3; padding: 10px 12px 12px; }
.mfbr-sec + .mfbr-sec { margin-top: 12px; padding-top: 10px; border-top: 1px dashed #e8e0d3; }
.mfbr-sec-t { font-size: 12px; font-weight: 700; color: #6b6257; margin-bottom: 6px; }
.mfbr-chips { display: flex; gap: 8px; }
.mfbr-chip { padding: 6px 12px; border: 1px solid #d8cdbb; border-radius: 999px; background: #fffdf8; color: #6b6257; font-size: 12.5px; }
.mfbr-chip.on { background: #eef3ee; border-color: #2d5a4a; color: #2d5a4a; font-weight: 700; }
.mfbr-chip:disabled { opacity: .45; }
.mfbr-note { margin: 6px 0 0; font-size: 11.5px; color: #9c9184; line-height: 1.5; }
.mfbr-note.warn { color: #b5543a; }
.mfbr-slider { display: flex; align-items: center; gap: 8px; margin-top: 8px; font-size: 12px; color: #6b6257; }
.mfbr-slider input { flex: 1; accent-color: #2d5a4a; }
.mfbr-slider b { min-width: 42px; text-align: right; color: #2d5a4a; }
.mfbr-search { display: flex; gap: 6px; }
.mfbr-search input { flex: 1; height: 34px; padding: 0 10px; border: 1px solid #d8cdbb; border-radius: 8px; background: #fff; font-size: 13px; color: #2e2a24; }
.mfbr-search button { width: 52px; border: none; border-radius: 8px; background: #2d5a4a; color: #fff7ee; font-size: 13px; }
.mfbr-search button:disabled { opacity: .45; }
.mfbr-hits { margin-top: 8px; max-height: 190px; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }
.mfbr-hit { display: flex; flex-direction: column; gap: 2px; padding: 6px 8px; border: 1px solid #e8e0d3; border-radius: 8px; background: #fffdf8; text-align: left; }
.mfbr-hit-p { font-size: 11px; color: #a67c52; }
.mfbr-hit-t { font-size: 12.5px; color: #2e2a24; line-height: 1.5; }
.mfbr-body { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.mfbr-stage { flex: 1; display: flex; justify-content: center; align-items: flex-start; overflow: auto; padding: 10px 8px; }
.mfbr-canvas { background: #fffdf8; box-shadow: 0 2px 10px rgba(90, 74, 51, .18); border-radius: 4px; }
.mfbr-reflow { flex: 1; overflow-y: auto; padding: 0 0 30px; }
.mfbr-rblock { padding: 0 18px; }
.mfbr-rmark { margin: 14px 0 6px; padding-bottom: 4px; border-bottom: 1px dashed #d8cdbb; font-size: 10.5px; color: #a67c52; }
.mfbr-rtext { margin: 0 0 4px; font-family: 'Songti SC', 'STSong', serif; font-size: var(--reader-font-size, 18px); line-height: var(--reader-line-height, 1.9); color: #2e2a24; white-space: pre-wrap; word-break: break-word; }
.mfbr-rempty { margin: 0; font-size: 11.5px; color: #a67c52; font-style: italic; }
.mfbr-rfoot { margin: 14px 0 0; text-align: center; font-size: 11px; color: #a67c52; }
.mfbr-nav { display: flex; align-items: center; justify-content: space-between; padding: 8px 16px; background: #fffdf8; border-top: 1px solid #e8e0d3; }
.mfbr-nav-btn { border: 1px solid #c8d8cc; background: #eef3ee; border-radius: 999px; padding: 8px 18px; font-size: 14px; color: #2d5a4a; font-weight: 600; }
.mfbr-nav-btn:disabled { opacity: .4; }
.mfbr-nav-page { font-size: 13px; color: #a67c52; font-family: 'Songti SC', 'STSong', serif; font-weight: 600; }
.mfbr-foot { text-align: center; font-size: 12px; color: #9c9184; padding: 8px 0 12px; background: #fffdf8; }
</style>
