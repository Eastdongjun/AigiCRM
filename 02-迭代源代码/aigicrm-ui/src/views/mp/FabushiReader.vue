<template>
  <div class="mfr" :class="'theme-' + settings.backdrop" :style="pageVars">
    <!-- 仅微信小程序 web-view 内可用: 短时阅读票由小程序 URL 带入(F3a-2 起不携带主 JWT), 不依赖 PC 登录态 -->
    <div v-if="!isWechat" class="mfr-block">
      <ElIcon class="mfr-block-icon"><Lock /></ElIcon>
      <div class="mfr-block-title">请从微信小程序进入</div>
      <div class="mfr-block-desc">本页面在小程序「书目详情 → 在线阅读」中使用</div>
    </div>

    <div v-else-if="!token || !pdfFileId" class="mfr-block">
      <ElIcon class="mfr-block-icon"><Warning /></ElIcon>
      <div class="mfr-block-title">阅读票缺失或已过期</div>
      <div class="mfr-block-desc">请返回小程序重新进入（票有效期 60 分钟）</div>
    </div>

    <template v-else>
      <!-- 【2026-11-26 按小程序原型 v7】B1 在线阅读：深色阅读壳。顶栏 = 返回 · 书名与页码 · 版式 -->
      <div class="mfr-head">
        <button type="button" class="mfr-back" aria-label="返回" @click="goBack"><ElIcon><ArrowLeft /></ElIcon></button>
        <div class="mfr-title-row">
          <b class="mfr-book">{{ title }}</b>
          <span class="mfr-sub">{{ currentPage }} / {{ totalPages }} 页 · {{ layoutText }}</span>
        </div>
        <button type="button" class="mfr-btn" :class="{ on: sheetOpen }" @click="sheetOpen = !sheetOpen">版式</button>
      </div>

      <div class="mfr-body">
        <div v-if="state === 'loading'" class="mfr-block">
          <div class="mfr-loading">正在加载电子书…</div>
          <div class="mfr-pct">{{ loadPct ? loadPct + '%' : '' }}</div>
        </div>
        <div v-else-if="state === 'error'" class="mfr-block">
          <ElIcon class="mfr-block-icon"><Document /></ElIcon>
          <div class="mfr-block-title">加载失败</div>
          <div class="mfr-block-desc">{{ errorMsg }}</div>
          <button type="button" class="mfr-retry" @click="retry"><ElIcon><RefreshRight /></ElIcon>重试</button>
        </div>
        <template v-else>
          <!-- 书页区（原型 .reader-page + .paper）：原样（PDF）是 canvas，左右滑动翻页；重排模式下隐藏但保持挂载（页码进度照旧更新） -->
          <div class="mfr-page">
            <div
              v-show="!reflowOn" class="mfr-stage"
              @touchstart="onTouchStart" @touchend="onTouchEnd"
            >
              <canvas ref="canvasEl" class="mfr-canvas"></canvas>
            </div>

            <!-- 重排（文字）：连续滚动，滚到底自动续页，顶部页变化即更新进度 -->
            <div
              v-if="reflowOn" ref="reflowEl" class="mfr-reflow"
              @scroll.passive="onReflowScroll"
            >
              <div class="mfr-paper">
                <div v-for="blk in reflowBlocks" :key="blk.page" class="mfr-rblock" :data-page="blk.page">
                  <div class="mfr-rmark">第 {{ blk.page }} 页</div>
                  <p v-if="blk.text" class="mfr-rtext">{{ blk.text }}</p>
                  <p v-else class="mfr-rempty">本页没有文字（可能是插图页），切回「原样」查看。</p>
                </div>
                <p v-if="reflowLoading" class="mfr-rfoot">正在加载后续页…</p>
                <p v-else-if="reflowDone" class="mfr-rfoot">— 已到最后一页 —</p>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="mfr-bottom">
        <!-- 版式设置（原型 B1 底部那张卡片，浮在翻页条上方，不挤占书页区）：阅读模式 / 背景与增强 / 书内搜索。
             点顶栏“版式”才展开 -->
        <div v-if="state === 'ready' && sheetOpen" class="mfr-sheet">
            <div class="mfr-sheet-title">
              <b>版式设置</b>
              <span class="chip2">本书 · {{ layoutText }}</span>
            </div>

            <div class="mfr-sec">
              <div class="mfr-sec-t">阅读模式</div>
              <div class="mfr-chips">
                <button
                  type="button" class="mfr-chip" :class="{ active: reflowOn }"
                  :disabled="textLayerState !== 'yes'"
                  @click="setReflow(true)"
                >重排</button>
                <button type="button" class="mfr-chip" :class="{ active: !reflowOn }" @click="setReflow(false)">原样</button>
              </div>
              <p v-if="textLayerState === 'probing'" class="mfr-note">正在检测文本层…</p>
              <p v-else-if="textLayerState === 'none'" class="mfr-note warn">这本 PDF 没有文本层（影印件），不能重排，也不能书内搜索。</p>
              <template v-if="reflowOn">
                <div class="mfr-stepper">
                  <span>字号</span>
                  <button type="button" class="mfr-step" aria-label="字号减小" :disabled="fontSize <= LIMITS.fontSize[0]" @click="bumpFontSize(-1)">A－</button>
                  <b>{{ fontSize }}px</b>
                  <button type="button" class="mfr-step" aria-label="字号加大" :disabled="fontSize >= LIMITS.fontSize[1]" @click="bumpFontSize(1)">A＋</button>
                </div>
                <div class="mfr-stepper">
                  <span>行距</span>
                  <button type="button" class="mfr-step" aria-label="行距减小" :disabled="lineHeight <= LIMITS.lineHeight[0]" @click="bumpLineHeight(-1)">－</button>
                  <b>{{ lineHeightRatio(lineHeight) }}</b>
                  <button type="button" class="mfr-step" aria-label="行距加大" :disabled="lineHeight >= LIMITS.lineHeight[1]" @click="bumpLineHeight(1)">＋</button>
                </div>
              </template>
            </div>

            <div class="mfr-sec">
              <div class="mfr-sec-t">背景</div>
              <div class="mfr-chips">
                <button
                  v-for="item in backdrops" :key="item.value" type="button" class="mfr-chip"
                  :class="{ active: settings.backdrop === item.value }" @click="patchSettings({ backdrop: item.value })"
                >{{ item.label }}</button>
                <button type="button" class="mfr-chip" :class="{ active: settings.contrast > 100 }" @click="toggleContrast">对比度增强</button>
                <button type="button" class="mfr-chip" :class="{ active: settings.crop > 0 }" @click="toggleCrop">裁掉页边白边</button>
              </div>
            </div>

            <!-- 【D24 2026-11-23 MP 对齐 PC】书内搜索：与 PC read.vue 同一套逻辑，只读 PDF 自带文本层，不做 OCR。 -->
            <div class="mfr-sec">
              <div class="mfr-sec-t">书内搜索</div>
              <div class="mfr-search">
                <input v-model="searchKeyword" type="search" placeholder="输入关键词（全文检索）" :disabled="searchState === 'indexing' || textLayerState !== 'yes'" @keyup.enter="runSearch" />
                <button type="button" :disabled="searchState === 'indexing' || !searchKeyword.trim()" @click="runSearch">搜</button>
              </div>
              <p v-if="searchState === 'indexing'" class="mfr-note">正在建立全文索引 {{ indexDone }}/{{ totalPages }} 页…</p>
              <p v-else-if="searchState === 'done'" class="mfr-note">{{ searchHits.length ? `命中 ${searchHits.length} 处` : '没有找到匹配内容' }}</p>
              <div v-if="searchHits.length" class="mfr-hits">
                <button v-for="(hit, i) in searchHits.slice(0, 40)" :key="i" type="button" class="mfr-hit" @click="jumpToHit(hit)">
                  <span class="mfr-hit-p">第 {{ hit.page }} 页</span>
                  <span class="mfr-hit-t">{{ hit.snippet }}</span>
                </button>
                <p v-if="searchHits.length > 40" class="mfr-note">仅显示前 40 处，请输入更具体的关键词。</p>
              </div>
            </div>

            <p class="mfr-sheet-note">文本版可以重排（字号、行距、背景随调）；影印版只能原样显示，可以调对比度、裁掉页边。</p>
        </div>

        <!-- 底部（原型 .reader-foot）：上一页 / 进度条 / 页码 / 下一页 -->
        <div v-if="state === 'ready'" class="mfr-foot">
        <div class="mfr-ctl">
          <button type="button" class="pg-btn pg-btn-prev" :disabled="currentPage <= 1" aria-label="上一页" @click="goPage(currentPage - 1)">‹</button>
          <div class="rprog"><i :style="{ width: percent + '%' }" /></div>
          <span class="r-page">{{ currentPage }} / {{ totalPages }} · {{ percent }}%</span>
          <button type="button" class="pg-btn pg-btn-next" :disabled="currentPage >= totalPages" aria-label="下一页" @click="goPage(currentPage + 1)">›</button>
        </div>
        <div class="mfr-tip">{{ reflowOn ? '上下滑动阅读，页码随滚动自动记录' : '左右滑动翻页 · 页码进度自动记录' }}</div>
        </div>
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
// 【2026-11-26 按小程序原型 v7】外观改成原型 B1 的深色阅读壳 + 底部版式卡片；版式里新接上背景、对比度增强、
// 裁掉页边白边三项（与 PC 同一份设置、同一个纯函数 readerCssVars）。渲染、进度回传、搜索的逻辑都没有变。
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElIcon } from 'element-plus'
import { ArrowLeft, Document, Lock, RefreshRight, Warning } from '@element-plus/icons-vue'
import { useFabushiPdfReader } from '@/composables/useFabushiPdfReader'
import {
  READER_BACKDROPS, READER_LIMITS, layoutLabel, lineHeightRatio, normalizeReaderSettings, readingPercent, readerCssVars, storageKey
} from '@/utils/fabushi-reader-settings'

const route = useRoute()
const token = ref('')
const bookId = ref('')
const pdfFileId = ref('')
const title = ref('')
const isWechat = /MicroMessenger/i.test(navigator.userAgent)
const layoutType = computed(() => (route.query.layoutType === 'text' ? 'text' : 'scan'))
const layoutText = computed(() => layoutLabel(layoutType.value))

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
const backdrops = READER_BACKDROPS
// 底部版式卡片是否展开
const sheetOpen = ref(false)
const percent = computed(() => readingPercent(currentPage.value, totalPages.value))

// ===== 版式：与 PC 共用 normalizeReaderSettings，越界值一律回落 =====
const settings = ref(normalizeReaderSettings(null, layoutType.value))
const reflowOn = computed(() => settings.value.reflow === true)
const fontSize = computed(() => settings.value.fontSize)
const lineHeight = computed(() => settings.value.lineHeight)
// 书页的底色 / 墨色 / 对比度 / 裁边 / 重排字号行距，全部走 CSS 变量（与 PC 阅读器同一个纯函数）
const pageVars = computed(() => readerCssVars(settings.value))

function loadSettings() {
  let raw = null
  try { raw = JSON.parse(localStorage.getItem(storageKey(bookId.value)) || 'null') } catch (_) { raw = null }
  settings.value = normalizeReaderSettings(raw, layoutType.value)
}
function saveSettings() {
  try { localStorage.setItem(storageKey(bookId.value), JSON.stringify(settings.value)) } catch (_) { /* 存不了不阻断 */ }
}
function patchSettings(partial) {
  settings.value = normalizeReaderSettings({ ...settings.value, ...partial }, layoutType.value)
  saveSettings()
}
// “对比度增强”“裁掉页边白边”在原型里是两个开关：点一下开、再点一下关（开时的取值与 PC 影印版默认值同一个量级）
function toggleContrast() {
  patchSettings({ contrast: settings.value.contrast > 100 ? 100 : 125 })
}
function toggleCrop() {
  patchSettings({ crop: settings.value.crop > 0 ? 0 : 6 })
}
function bumpFontSize(delta) {
  const [min, max] = LIMITS.fontSize
  patchSettings({ fontSize: Math.min(max, Math.max(min, settings.value.fontSize + delta)) })
}
function bumpLineHeight(delta) {
  const [min, max] = LIMITS.lineHeight
  patchSettings({ lineHeight: Math.min(max, Math.max(min, settings.value.lineHeight + delta)) })
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
  if (textLayerState.value === 'none' && settings.value.reflow) patchSettings({ reflow: false })
}

function setReflow(on) {
  if (on && textLayerState.value !== 'yes') return
  patchSettings({ reflow: !!on })
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
  const marks = el.querySelectorAll('.mfr-rblock')
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
  sheetOpen.value = false
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
  // 不在微信里（没有阅读票可用）就不去加载电子书，只显示提示
  if (isWechat && token.value && pdfFileId.value) {
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
/* 【2026-11-26 按小程序原型 v7】配色取自原型 B1 的“阅读器”那组变量：深蓝条、浅字、半透明按钮、米白书页 */
.mfr {
  --mf-bar: #1c2b45; --mf-text: #d8e1ef; --mf-muted: #8fa1bd;
  --mf-btn: rgba(255, 255, 255, .14); --mf-btn-line: rgba(255, 255, 255, .3); --mf-good: #9fd8bd;
  --mf-ink: #172033; --mf-ink2: #52627a; --mf-ink3: #7c8ba1; --mf-ink4: #94a3b8; --mf-line: #dbe4f0;
  --mf-accent: #2563eb;
  position: relative; display: flex; flex-direction: column; min-height: 100vh; background: var(--mf-bar);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif; color: var(--mf-ink);
}
.mfr-block { display: flex; flex-direction: column; align-items: center; padding-top: 30vh; text-align: center; background: #f5f7fb; min-height: 100vh; }
.mfr-block-icon { margin-bottom: 12px; color: var(--mf-ink4); font-size: 40px; }
.mfr-block-title { color: var(--mf-ink); font-size: 17px; font-weight: 600; }
.mfr-block-desc { margin-top: 6px; padding: 0 24px; color: var(--mf-ink3); font-size: 13px; line-height: 1.5; }
.mfr-loading { color: var(--mf-ink2); font-size: 15px; }
.mfr-pct { margin-top: 8px; color: var(--mf-ink3); font-size: 13px; }
.mfr-retry { display: inline-flex; align-items: center; gap: 6px; height: 40px; margin: 16px auto 0; padding: 0 26px; border: 0; border-radius: 999px; background: var(--mf-accent); color: #fff; font-size: 14px; }

/* 顶栏（原型 .reader-head） */
.mfr-head { display: flex; align-items: center; flex-shrink: 0; gap: 9px; padding: 10px 12px; background: var(--mf-bar); color: var(--mf-text); }
.mfr-back { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: 1px solid var(--mf-btn-line); border-radius: 999px; background: var(--mf-btn); color: var(--mf-text); font-size: 16px; }
.mfr-title-row { display: flex; flex: 1; flex-direction: column; align-items: center; min-width: 0; text-align: center; }
.mfr-book { overflow: hidden; max-width: 100%; color: #fff; font-size: 13px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.mfr-sub { margin-top: 2px; color: var(--mf-muted); font-size: 9.5px; font-variant-numeric: tabular-nums; }
.mfr-btn { flex-shrink: 0; height: 28px; padding: 0 12px; border: 1px solid var(--mf-btn-line); border-radius: 999px; background: var(--mf-btn); color: var(--mf-text); font-size: 11px; font-weight: 700; }
.mfr-btn.on { background: rgba(255, 255, 255, .32); color: #fff; }

/* 书页区（原型 .reader-page + .paper） */
.mfr-body { display: flex; flex: 1; flex-direction: column; min-height: 0; }
.mfr-page { display: flex; flex: 1; flex-direction: column; min-height: 0; padding: 10px 12px; overflow: hidden; background: #e9e4d8; }
.theme-night .mfr-page { background: #141818; }
.theme-sepia .mfr-page { background: #ddd2b8; }
.mfr-stage { display: flex; flex: 1; justify-content: center; align-items: flex-start; min-height: 0; overflow: auto; }
.mfr-canvas { flex-shrink: 0; border-radius: 4px; background: var(--reader-page-bg); box-shadow: 0 4px 12px rgba(60, 50, 20, .18); filter: contrast(var(--reader-contrast)); clip-path: inset(var(--reader-crop) round 4px); }
.theme-night .mfr-canvas { box-shadow: 0 6px 24px rgba(0, 0, 0, .65); }
.mfr-reflow { flex: 1; min-height: 0; overflow-y: auto; }
.mfr-paper { padding: 14px 16px 24px; border-radius: 4px; background: var(--reader-page-bg); box-shadow: 0 4px 12px rgba(60, 50, 20, .18); color: var(--reader-ink); font-family: 'Songti SC', 'STSong', serif; }
.theme-night .mfr-paper { box-shadow: 0 6px 24px rgba(0, 0, 0, .65); }
.mfr-rblock { position: relative; }
.mfr-rmark { margin: 14px 0 6px; padding-bottom: 4px; border-bottom: 1px dashed rgba(150, 140, 120, .35); color: var(--reader-ink); font-size: 10px; letter-spacing: .04em; opacity: .7; }
.mfr-rtext { margin: 0 0 7px; font-size: var(--reader-font-size, 18px); line-height: var(--reader-line-height, 1.9); text-indent: 2em; white-space: pre-wrap; word-break: break-word; }
.mfr-rempty { margin: 0; color: var(--mf-ink3); font-size: 11.5px; font-style: italic; }
.mfr-rfoot { margin: 14px 0 0; color: var(--mf-ink3); font-size: 11px; text-align: center; }

/* 版式卡片（原型 B1 底部那张白卡）：浮在翻页条上方，不挤占书页区 */
.mfr-bottom { position: relative; flex-shrink: 0; }
.mfr-sheet { position: absolute; z-index: 5; left: 12px; right: 12px; bottom: calc(100% + 8px); max-height: 52vh; overflow-y: auto; padding: 9px 11px 10px; border-radius: 12px; background: #fff; box-shadow: 0 -6px 18px rgba(13, 23, 51, .25); }
.mfr-sheet-title { display: flex; align-items: center; justify-content: space-between; }
.mfr-sheet-title b { color: var(--mf-ink); font-size: 11.5px; }
.chip2 { display: inline-flex; align-items: center; padding: 1px 6px; border: 1px solid transparent; border-radius: 4px; background: #eff6ff; color: var(--mf-accent); font-size: 9px; font-weight: 800; }
.mfr-sec + .mfr-sec { margin-top: 10px; padding-top: 9px; border-top: 1px solid #eef2f7; }
.mfr-sec-t { margin-bottom: 5px; color: var(--mf-ink); font-size: 11px; font-weight: 800; }
.mfr-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.mfr-chip { height: 25px; padding: 0 8px; border: 1px solid var(--mf-line); border-radius: 999px; background: #fff; color: var(--mf-ink2); font-size: 11px; font-weight: 700; font-family: inherit; }
.mfr-chip.active { border-color: var(--mf-accent); background: var(--mf-accent); color: #fff; }
.mfr-chip:disabled { opacity: .45; }
.mfr-note { margin-top: 6px; color: var(--mf-ink3); font-size: 11px; line-height: 1.5; }
.mfr-note.warn { color: #b45309; }
.mfr-sheet-note { margin-top: 8px; color: var(--mf-ink4); font-size: 9px; line-height: 1.6; }
.mfr-stepper { display: flex; align-items: center; gap: 8px; margin-top: 8px; color: var(--mf-ink2); font-size: 12px; }
.mfr-stepper span { width: 32px; }
.mfr-stepper b { min-width: 44px; color: var(--mf-ink); font-size: 12px; text-align: center; font-variant-numeric: tabular-nums; }
.mfr-step { min-width: 38px; height: 26px; padding: 0 8px; border: 1px solid var(--mf-line); border-radius: 999px; background: #fff; color: var(--mf-ink2); font-size: 11px; font-weight: 700; font-family: inherit; }
.mfr-step:disabled { opacity: .4; }
.mfr-search { display: flex; gap: 6px; }
.mfr-search input { flex: 1; min-width: 0; height: 34px; padding: 0 10px; border: 1px solid var(--mf-line); border-radius: 8px; background: #fff; color: var(--mf-ink); font-size: 13px; font-family: inherit; }
.mfr-search button { width: 52px; border: 0; border-radius: 8px; background: var(--mf-accent); color: #fff; font-size: 13px; font-family: inherit; }
.mfr-search button:disabled, .mfr-search input:disabled { opacity: .45; }
.mfr-hits { display: flex; flex-direction: column; gap: 4px; max-height: 150px; margin-top: 8px; overflow-y: auto; }
.mfr-hit { display: flex; flex-direction: column; gap: 2px; padding: 6px 8px; border: 1px solid #eef2f7; border-radius: 8px; background: #fff; text-align: left; font-family: inherit; }
.mfr-hit-p { color: var(--mf-accent); font-size: 11px; font-weight: 700; }
.mfr-hit-t { color: var(--mf-ink); font-size: 12.5px; line-height: 1.5; }

/* 底部（原型 .reader-foot） */
.mfr-foot { flex-shrink: 0; padding: 8px 14px 14px; background: var(--mf-bar); color: var(--mf-text); }
.mfr-ctl { display: flex; align-items: center; gap: 10px; }
.pg-btn { display: flex; align-items: center; justify-content: center; width: 34px; height: 30px; border: 1px solid var(--mf-btn-line); border-radius: 8px; background: var(--mf-btn); color: #fff; font-size: 15px; font-family: inherit; }
.pg-btn:disabled { opacity: .35; }
.rprog { position: relative; flex: 1; height: 5px; border-radius: 999px; background: rgba(255, 255, 255, .2); }
.rprog i { position: absolute; left: 0; top: 0; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #2364d8, #10b981); }
.r-page { color: var(--mf-text); font-size: 11px; font-weight: 700; font-variant-numeric: tabular-nums; white-space: nowrap; }
.mfr-tip { margin-top: 8px; color: var(--mf-good); font-size: 10px; text-align: center; }
</style>
