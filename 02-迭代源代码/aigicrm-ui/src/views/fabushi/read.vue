<template>
  <div class="fabushi-reader" :class="'theme-' + settings.backdrop" :style="cssVars">
    <div class="reader-head">
      <el-button link class="back" @click="router.back()">‹ 返回书目</el-button>
      <b>{{ book.title || '在线阅读' }}</b>
      <span class="reader-sub">{{ book.author || '' }}<template v-if="totalPages"> · {{ totalPages }} 页</template></span>
      <span class="layout-chip" :title="layoutHint">{{ layoutLabel(layoutType) }}</span>
      <span class="spacer" />
      <span class="page-control" v-if="state === 'ready'">
        第 <el-input-number v-model="pageInput" :min="1" :max="totalPages || 1" size="small" controls-position="right" @change="onPageInput" /> 页<template v-if="totalPages"> / {{ totalPages }} · {{ percent }}%</template>
      </span>
      <el-button link class="settings-btn" :class="{ active: settingsOpen }" @click="settingsOpen = !settingsOpen">⚙ 版式</el-button>
      <el-button v-if="book.pdfFileId && canDownload" link class="download" @click="downloadPdf">下载</el-button>
    </div>

    <div v-if="!bookError" class="reader-body">
      <aside class="reader-rail">
        <template v-if="!settingsOpen">
          <div class="rail-title">阅读说明</div>
          <p>单页阅读，翻页自动记录进度；下次从书目进入，可接着上次页码继续。</p>
          <p class="rail-tip">← → 方向键翻页 · ⚙ 版式可换背景/缩放/对比度/重排</p>
          <div class="rail-meta">
            <span>书目</span><b>{{ book.title || '—' }}</b>
            <span>作者</span><b>{{ book.author || '—' }}</b>
            <span>页数</span><b>{{ totalPages || book.pages || '—' }}</b>
            <span>版式</span><b>{{ layoutLabel(layoutType) }}</b>
          </div>

          <!-- 【D24 2026-11-23】书内搜索：只读 PDF 自带文本层，不做 OCR -->
          <div class="rail-search">
            <div class="rail-subtitle">书内搜索</div>
            <div class="rs-input">
              <input
                v-model="searchKeyword"
                type="search"
                placeholder="搜本书正文…"
                :disabled="searchState === 'indexing'"
                @keyup.enter="runSearch"
              />
              <button type="button" :disabled="searchState === 'indexing' || !searchKeyword.trim()" @click="runSearch">搜</button>
            </div>
            <p v-if="searchState === 'indexing'" class="rs-note">正在建立全文索引 {{ indexDone }}/{{ totalPages }} 页…</p>
            <p v-else-if="searchState === 'notext'" class="rs-note warn">该书 PDF 无文本层（影印件），书内搜索不可用；请用页码翻页或下载原件检索。</p>
            <p v-else-if="searchState === 'done'" class="rs-note">
              {{ searchHits.length ? `命中 ${searchHits.length} 处` : '没有找到匹配内容' }}
            </p>
            <div v-if="searchHits.length" class="rs-hits">
              <button v-for="(hit, i) in pagedSearchHits" :key="i" type="button" class="rs-hit" @click="jumpToHit(hit)">
                <span class="rs-page">第 {{ hit.page }} 页</span>
                <span class="rs-snippet">{{ hit.snippet }}</span>
              </button>
              <div v-if="searchTotalPages > 1" class="rail-pager">
                <el-pagination v-model:current-page="searchPage" :page-size="searchSize" :total="searchTotal" layout="prev, pager, next" small />
              </div>
            </div>
          </div>

          <div class="rail-bookmarks">
            <div class="rail-subtitle">我的书签 <span>{{ bookmarks.length }}</span></div>
            <div v-if="!bookmarks.length" class="rail-empty">还没有书签，翻到重要页可点「加书签」</div>
            <div v-if="bookmarkTotalPages > 1" class="rail-pager">
              <el-pagination v-model:current-page="bookmarkPage" :page-size="bookmarkSize" :total="bookmarks.length" layout="prev, pager, next" small />
            </div>
            <div v-for="item in pagedBookmarks" :key="item.id" class="bookmark-row">
              <button type="button" class="bm-jump" @click="turnTo(item.page)">第 {{ item.page }} 页<template v-if="item.note"> · {{ item.note }}</template></button>
              <button type="button" class="bm-del" :aria-label="'删除第 ' + item.page + ' 页书签'" @click="removeBookmark(item.id)">×</button>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="rail-title">版式设置 · 按本书记忆</div>
          <div class="set-group">
            <span class="set-label">底色</span>
            <div class="set-chips">
              <button v-for="item in backdrops" :key="item.value" type="button" class="set-chip"
                :class="{ active: settings.backdrop === item.value }" @click="patch({ backdrop: item.value })">{{ item.label }}</button>
            </div>
          </div>
          <div class="set-group">
            <span class="set-label">缩放 <b>{{ settings.zoom }}%</b></span>
            <input type="range" min="70" max="160" step="5" :value="settings.zoom" aria-label="缩放" @input="setZoom" />
          </div>
          <div class="set-group">
            <span class="set-label">对比度 <b>{{ settings.contrast }}%</b></span>
            <input type="range" min="90" max="160" step="5" :value="settings.contrast" aria-label="对比度" @input="setContrast" />
            <p class="set-tip">扫描件灰底可调高，不修改源文件</p>
          </div>
          <div class="set-group">
            <span class="set-label">裁页边 <b>{{ settings.crop }}%</b></span>
            <input type="range" min="0" max="14" step="1" :value="settings.crop" aria-label="裁页边" @input="setCrop" />
          </div>
          <div class="set-group">
            <span class="set-label">翻页</span>
            <div class="set-chips">
              <button type="button" class="set-chip" :class="{ active: !settings.continuous }" @click="patch({ continuous: false })">单页</button>
              <button type="button" class="set-chip" :class="{ active: settings.continuous }" @click="patch({ continuous: true })">连续滚动</button>
            </div>
            <p class="set-tip">连续模式：滚到页底自动翻下一页</p>
          </div>

          <!-- 【D24 2026-11-23】重排 / 字号行距：文本层可读时可用，影印件如实禁用 -->
          <div class="set-group">
            <span class="set-label">阅读模式</span>
            <div class="set-chips">
              <button type="button" class="set-chip" :class="{ active: !settings.reflow }" @click="patch({ reflow: false })">原样（PDF）</button>
              <button
                type="button" class="set-chip" :class="{ active: settings.reflow }"
                :disabled="textLayerState === 'none' || textLayerState === 'probing'"
                :title="textLayerState === 'none' ? '该书 PDF 无文本层，无法重排' : ''"
                @click="enableReflow"
              >重排（文字）</button>
            </div>
            <p class="set-tip">
              <template v-if="textLayerState === 'probing'">正在检测文本层…</template>
              <template v-else-if="textLayerState === 'none'">影印版无文本层：重排与书内搜索不可用（不做 OCR）</template>
              <template v-else>重排只读取 PDF 自带文本层，不改源文件；不做 OCR</template>
            </p>
          </div>
          <div v-if="settings.reflow && textLayerState === 'yes'" class="set-group">
            <span class="set-label">字号 <b>{{ settings.fontSize }}px</b></span>
            <input type="range" min="14" max="30" step="1" :value="settings.fontSize" aria-label="字号" @input="setFontSize" />
          </div>
          <div v-if="settings.reflow && textLayerState === 'yes'" class="set-group">
            <span class="set-label">行距 <b>{{ lineHeightRatio(settings.lineHeight) }}</b></span>
            <input type="range" min="14" max="26" step="1" :value="settings.lineHeight" aria-label="行距" @input="setLineHeight" />
          </div>
          <button type="button" class="set-reset" @click="resetSettings">恢复本版式默认</button>
        </template>
      </aside>

      <section class="reader-stage">
        <!-- 【D24 2026-11-23】重排视图：连续滚动读取 PDF 自带文本层，字号/行距走 CSS 变量；
             影印件（无文本层）不进入本分支，改由版式面板如实提示不可用。 -->
        <div v-if="settings.reflow && textLayerState === 'yes'" ref="reflowEl" class="reflow-pane" @scroll.passive="onReflowScroll">
          <div class="reflow-paper">
            <div v-for="blk in reflowBlocks" :key="blk.page" class="reflow-block" :data-page="blk.page">
              <div class="reflow-mark">第 {{ blk.page }} 页</div>
              <p v-if="blk.text" class="reflow-text">{{ blk.text }}</p>
              <p v-else class="reflow-empty">本页无文本层（可能是插图页），切回「原样（PDF）」查看。</p>
            </div>
            <p v-if="reflowLoading" class="reflow-loading">正在加载后续页…</p>
            <p v-else-if="reflowDone" class="reflow-end">— 已到最后一页 —</p>
          </div>
        </div>

        <div v-show="!(settings.reflow && textLayerState === 'yes')" ref="wrapEl" class="stage-canvas-wrap" @scroll.passive="onStageScroll">
          <canvas ref="canvasEl" class="stage-canvas" />
        </div>
        <div v-if="state === 'loading'" class="stage-mask">
          <div class="mask-title">正在加载电子书…</div>
          <div class="mask-sub">{{ loadPct ? loadPct + '%' : '' }}</div>
        </div>
        <div v-else-if="state === 'error'" class="stage-mask">
          <div class="mask-title">加载失败</div>
          <div class="mask-sub">{{ errorMsg }}</div>
          <el-button class="mask-retry" @click="retry">重试</el-button>
        </div>
        <div v-if="state === 'ready'" class="stage-nav">
          <el-button :disabled="currentPage <= 1" @click="prevPage">‹ 上一页</el-button>
          <button type="button" class="bookmark-toggle" :class="{ on: currentBookmarked }" :disabled="bookmarkBusy" @click="toggleBookmark">
            {{ currentBookmarked ? '★ 已加书签' : '☆ 加书签' }}
          </button>
          <span class="stage-page">{{ currentPage }} / {{ totalPages }} · {{ percent }}%</span>
          <el-button :disabled="currentPage >= totalPages" @click="nextPage">下一页 ›</el-button>
        </div>
      </section>
    </div>

    <div v-else class="reader-empty">
      <el-empty :description="bookError" :image-size="80" />
    </div>
  </div>
</template>

<script setup>
// 【2026-11-16 P3】PC 阅读器升级为 H5 PDF.js 页码级（对齐 MP）：
// iframe+#page 只能"跳到"某页、无法知道读者停在哪页；改为 canvas 单页渲染 + 翻页自动回传
// 进度 + 打开时按 lastPage 续读。渲染/进度逻辑与 MP 共用 useFabushiPdfReader（口径单源）。
// 鉴权：PDF 走主 JWT 的 Authorization 头，token 不再拼进 URL（防落到访问日志/Referer）。
// 【D19/D24 切片 7 批1】新增：版式面板（背景/缩放/对比度/裁边/单连续页/百分比）+ 页码书签存库；
// 【2026-11-23 批2 已落地】重排、字号行距、书内搜索三项已实现（影印件无文本层时如实禁用，
// 不做 OCR）；此注释原写「留批 2、UI 已如实标注」，与现状不符，已更正。
// 仍属批 2：D12 图片裁剪/标注（双端都还没有编辑器，只有放大查看）。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getFabushiBook, getFabushiBookmarks, addFabushiBookmark, removeFabushiBookmark
} from '@/api/fabushi'
import { getToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { useUserStore } from '@/store/user'
import { useFabushiPdfReader } from '@/composables/useFabushiPdfReader'
import { useFabushiReaderSettings } from '@/composables/useFabushiReaderSettings'
import { usePagedList } from '@/composables/usePagedList'
import {
  FABUSHI_LAYOUT_TYPES, READER_BACKDROPS, backdropOf, layoutLabel, lineHeightRatio,
  normalizeReaderSettings, readingPercent, readerCssVars
} from '@/utils/fabushi-reader-settings'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const book = ref({ id: null, title: '', author: '', pages: null, pdfFileId: null, layoutType: 'scan' })
const bookError = ref('')
const pageInput = ref(1)
const canvasEl = ref(null)
const wrapEl = ref(null)
const token = ref(getToken() || '')
const canDownload = computed(() => userStore.hasPermission('fabushi:view'))
const settingsOpen = ref(false)
const bookmarks = ref([])
const { current: bookmarkPage, size: bookmarkSize, totalPages: bookmarkTotalPages, paged: pagedBookmarks } = usePagedList(bookmarks, { pageSize: 8 })
const bookmarkBusy = ref(false)

const layoutType = computed(() => book.value.layoutType === 'text' ? 'text' : 'scan')
const layoutHint = computed(() =>
  (FABUSHI_LAYOUT_TYPES.find((item) => item.value === layoutType.value) || {}).hint || '')
const bookIdRef = computed(() => String(route.params.bookId || ''))
const { settings, patch } = useFabushiReaderSettings(bookIdRef, layoutType)
const backdrops = READER_BACKDROPS
const cssVars = computed(() => readerCssVars(settings.value))
const percent = computed(() => readingPercent(currentPage.value, totalPages.value))
const currentBookmarked = computed(() => bookmarks.value.some((item) => item.page === currentPage.value))

const { state, loadPct, errorMsg, currentPage, totalPages, load, goPage, markPage, retry,
  pageText, detectTextLayer, extractAllText, searchInPages } = useFabushiPdfReader({
  bookId: bookIdRef,
  pdfFileId: computed(() => book.value.pdfFileId),
  token
})

watch(currentPage, (v) => { pageInput.value = v })

const setZoom = (e) => patch({ zoom: Number(e && e.target && e.target.value) })
const setContrast = (e) => patch({ contrast: Number(e && e.target && e.target.value) })
const setCrop = (e) => patch({ crop: Number(e && e.target && e.target.value) })
const setFontSize = (e) => patch({ fontSize: Number(e && e.target && e.target.value) })
const setLineHeight = (e) => patch({ lineHeight: Number(e && e.target && e.target.value) })
const resetSettings = () => { patch(normalizeReaderSettings(null, layoutType.value)) }

/* ============ 【D24 2026-11-23】重排 / 字号行距 / 书内搜索 ============ */
// 文本层状态：probing=检测中 / yes=有文本层 / none=影印件无文本层（重排与搜索如实禁用）
const textLayerState = ref('probing')
const reflowEl = ref(null)
const reflowBlocks = ref([])
const reflowLoading = ref(false)
const reflowDone = ref(false)

const searchKeyword = ref('')
const searchState = ref('idle') // idle | indexing | done | notext
const indexDone = ref(0)
const searchHits = ref([])
const { current: searchPage, size: searchSize, total: searchTotal, totalPages: searchTotalPages, paged: pagedSearchHits, reset: resetSearchPage } = usePagedList(searchHits, { pageSize: 20 })
let allTextPages = null

/** 检测文本层：有则允许重排与搜索；无则明确告知不做 OCR。 */
async function probeTextLayer() {
  textLayerState.value = 'probing'
  try {
    textLayerState.value = (await detectTextLayer()) ? 'yes' : 'none'
  } catch (e) {
    textLayerState.value = 'none'
  }
  // 记录里的版式标记说「文本版」但实际没文本层时，重排自动退回原样，不留空白屏
  if (textLayerState.value === 'none' && settings.value.reflow) patch({ reflow: false })
}

/** 开启重排：把当前页灌进重排视图。 */
async function enableReflow() {
  if (textLayerState.value !== 'yes') return
  patch({ reflow: true })
  await nextTick()
  await resetReflowTo(currentPage.value)
}

/** 重排视图从指定页起重新铺（页码输入 / 方向键 / 搜索命中 / 首开都会走这里）。 */
async function resetReflowTo(page) {
  if (!settings.value.reflow || textLayerState.value !== 'yes') return
  reflowDone.value = false
  reflowLoading.value = true
  const first = Math.max(1, Number(page) || 1)
  const text = await pageText(first)
  reflowBlocks.value = [{ page: first, text }]
  reflowLoading.value = false
  await nextTick()
  if (reflowEl.value) reflowEl.value.scrollTop = 0
}

/** 追加下一页文本（滚动到底时）。 */
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

/** 重排视图滚动：到底追加下一页；同时把当前页码对齐到视口顶部那一页（进度随之保存）。 */
function onReflowScroll() {
  const el = reflowEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) appendReflowPage()
  const marks = el.querySelectorAll('.reflow-block')
  const top = el.getBoundingClientRect().top
  let visible = null
  for (const node of marks) {
    if (node.getBoundingClientRect().bottom > top + 24) { visible = node; break }
  }
  if (visible) {
    const n = Number(visible.dataset.page)
    if (Number.isFinite(n)) markPage(n)
  }
}

/** 书内搜索：首次触发时建全文索引（逐页提取，带进度），之后走内存缓存。 */
async function runSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) { searchHits.value = []; resetSearchPage(); return }
  if (textLayerState.value === 'none') { searchState.value = 'notext'; return }
  if (textLayerState.value === 'probing') return
  if (!allTextPages) {
    searchState.value = 'indexing'
    indexDone.value = 0
    allTextPages = await extractAllText((done) => { indexDone.value = done })
  }
  searchHits.value = searchInPages(allTextPages, kw)
  resetSearchPage()
  searchState.value = 'done'
}

/** 点命中项：跳到该页（重排模式下由 currentPage 的 watch 在重排视图里重新铺页）。 */
async function jumpToHit(hit) {
  if (!hit) return
  turnTo(hit.page)
  if (window.innerWidth < 1100) settingsOpen.value = false
}

watch(currentPage, (v) => {
  // 重排模式下用页码控件/方向键翻页时，重排视图要跟着换页
  if (!settings.value.reflow || textLayerState.value !== 'yes') return
  const last = reflowBlocks.value[reflowBlocks.value.length - 1]
  const first = reflowBlocks.value[0]
  if (!first || v < first.page || (last && v > last.page)) resetReflowTo(v)
})
watch(() => settings.value.reflow, (on) => {
  if (on) nextTick(() => resetReflowTo(currentPage.value))
})

async function loadBookmarks() {
  try {
    const { data } = await getFabushiBookmarks(route.params.bookId)
    bookmarks.value = Array.isArray(data) ? data : []
  } catch (e) {
    bookmarks.value = [] // 书签拉取失败不阻断阅读
  }
}

async function toggleBookmark() {
  if (bookmarkBusy.value || state.value !== 'ready') return
  bookmarkBusy.value = true
  const hit = bookmarks.value.find((item) => item.page === currentPage.value)
  try {
    if (hit) {
      await removeFabushiBookmark(hit.id)
      ElMessage.success('已删除书签')
    } else {
      await addFabushiBookmark({ bookId: route.params.bookId, page: currentPage.value })
      ElMessage.success(`已标记第 ${currentPage.value} 页`)
    }
    await loadBookmarks()
  } catch (e) {
    // 全局拦截器已提示
  } finally {
    bookmarkBusy.value = false
  }
}

async function removeBookmark(id) {
  try {
    await removeFabushiBookmark(id)
    await loadBookmarks()
  } catch (e) {
    // 全局拦截器已提示
  }
}

async function loadBook() {
  bookError.value = ''
  try {
    const { data } = await getFabushiBook(route.params.bookId)
    const b = (data && data.book) || null
    if (!b) {
      bookError.value = '书目不存在或未通过审核'
      return
    }
    book.value = b
    loadBookmarks()
    if (!b.pdfFileId) {
      bookError.value = '该书目暂未挂载电子书'
      return
    }
    await load(() => canvasEl.value)
    // 【P9B 2026-11-23】支持从「我的书摘」带页码跳转：/fabushi/read/:bookId?page=N。
    // 必须在 load() 之后——load 会按书记忆恢复上次进度，跳转要覆盖它。
    const jumpPage = Number(route.query.page)
    if (state.value === 'ready' && Number.isFinite(jumpPage) && jumpPage > 0) {
      turnTo(Math.min(jumpPage, totalPages.value || jumpPage))
    }
    // 【D24】PDF 就绪后探测文本层：决定「重排 / 书内搜索」是否可用（影印件如实禁用）
    if (state.value === 'ready') probeTextLayer()
  } catch (e) {
    // 后端 404「书目不存在」/403 已由请求拦截器转成中文 message
    bookError.value = (e && e.message) || '书目录入失败，请稍后再试'
  }
}

/** 翻页统一入口：重排模式只改页码（由 currentPage 的 watch 负责换页），原样模式走画布渲染。 */
function turnTo(page) {
  if (settings.value.reflow && textLayerState.value === 'yes') {
    markPage(page)
    return
  }
  goPage(page)
}

function onPageInput() {
  turnTo(pageInput.value)
}
function prevPage() {
  turnTo(currentPage.value - 1)
}
function nextPage() {
  turnTo(currentPage.value + 1)
}

/** 连续滚动模式：滚到页底自动翻下一页（单页渲染口径不变，不预渲染整本）。 */
function onStageScroll() {
  if (!settings.value.continuous || state.value !== 'ready' || currentPage.value >= totalPages.value) return
  const el = wrapEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24) {
    goPage(currentPage.value + 1)
    el.scrollTop = 0
  }
}

function onKeydown(e) {
  if (state.value !== 'ready') return
  const target = e.target
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    prevPage()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    nextPage()
  }
}

async function downloadPdf() {
  if (!book.value.pdfFileId) return
  await downloadFile(`/api/file/download/${book.value.pdfFileId}`, {
    name: `${book.value.title || '法布施书目'}.pdf`,
    headers: { Authorization: `Bearer ${getToken() || ''}` },
  })
}

onMounted(() => {
  loadBook()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
/* 【书卷化 2026-11-16 P3】纸色头/侧栏 + 深灰衬底衬托 PDF 书页；翻页导航落在纸色条上。
   【D19 切片 7】底色/缩放/对比度/裁边改为 CSS 变量驱动，按书目记忆，不触碰源 PDF。 */
.fabushi-reader { display: flex; flex-direction: column; height: calc(100vh - 150px); min-height: 520px; overflow: hidden; border: 1px solid #e8e0d3; border-radius: 12px; background: #fffdf8; }
.reader-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-bottom: 1px solid #e8e0d3; color: #2e2a24; background: var(--reader-page-bg); }
.reader-head b { color: var(--reader-ink); font-family: 'Songti SC', 'STSong', serif; font-size: 15px; }
.reader-sub { color: #a67c52; font-size: 11px; }
.reader-head .back, .reader-head .download, .reader-head .settings-btn { color: #2d5a4a; }
.reader-head .settings-btn.active { color: #b5543a; font-weight: 700; }
.layout-chip { padding: 2px 9px; border: 1px solid #d8cdbb; border-radius: 999px; color: #a67c52; font-size: 10px; white-space: nowrap; }
.page-control { display: inline-flex; align-items: center; gap: 4px; color: #a67c52; font-size: 11px; }
.page-control :deep(.el-input-number) { width: 86px; }
.reader-head .back:hover, .reader-head .download:hover { color: #b5543a; }
.spacer { flex: 1; }

.reader-body { display: grid; grid-template-columns: 220px minmax(0, 1fr); flex: 1; min-height: 0; }
.reader-rail { padding: 16px 14px; border-right: 1px solid #e8e0d3; color: #6b6257; background: #f4ede0; overflow-y: auto; }
.rail-title { margin-bottom: 10px; color: #2e2a24; font-family: 'Songti SC', 'STSong', serif; font-size: 12px; font-weight: 700; }
.reader-rail p { margin: 0; color: #9c9184; font-size: 11.5px; line-height: 1.8; }
.rail-tip { margin-top: 8px !important; color: #a67c52 !important; font-size: 11px !important; }
.rail-meta { display: flex; flex-direction: column; gap: 7px; margin-top: 16px; }
.rail-meta span { color: #a67c52; font-size: 10.5px; }
.rail-meta b { color: #2e2a24; font-size: 12px; font-weight: 500; }
.rail-pager { margin-top: 8px; display: flex; justify-content: center; }
.rail-bookmarks { margin-top: 18px; padding-top: 10px; border-top: 1px dashed #d8cdbb; }
.rail-subtitle { display: flex; justify-content: space-between; color: #2e2a24; font-size: 11.5px; font-weight: 700; }
.rail-subtitle span { color: #a67c52; }
.rail-empty { margin-top: 8px; color: #a99d8c; font-size: 10.5px; line-height: 1.6; }
.bookmark-row { display: flex; align-items: center; gap: 4px; margin-top: 6px; }
.bm-jump { flex: 1; overflow: hidden; padding: 5px 8px; border: 1px solid #d8cdbb; border-radius: 6px; color: #2d5a4a; background: #fffdf8; font-size: 10.5px; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.bm-jump:hover { border-color: #b5543a; color: #b5543a; }
.bm-del { width: 22px; height: 24px; border: 0; color: #a67c52; background: transparent; font-size: 14px; cursor: pointer; }

.set-group { margin-top: 14px; }
.set-label { display: flex; justify-content: space-between; color: #6b6257; font-size: 11px; }
.set-label b { color: #b5543a; font-variant-numeric: tabular-nums; }
.set-chips { display: flex; gap: 6px; margin-top: 7px; }
.set-chip { flex: 1; padding: 5px 2px; border: 1px solid #d8cdbb; border-radius: 6px; color: #6b6257; background: #fffdf8; font-size: 10.5px; cursor: pointer; }
.set-chip.active { border-color: #2d5a4a; color: #fff7ee; background: #2d5a4a; font-weight: 600; }
.set-chip:disabled { border-color: #e8e0d3; color: #c3b8a5; background: #f7f1e6; cursor: not-allowed; }
.set-group input[type='range'] { width: 100%; margin-top: 8px; accent-color: #2d5a4a; }
.set-tip { margin-top: 5px !important; color: #a99d8c !important; font-size: 10px !important; line-height: 1.5 !important; }
.set-reset { width: 100%; margin-top: 18px; padding: 7px; border: 1px solid #d8cdbb; border-radius: 6px; color: #2d5a4a; background: #fffdf8; font-size: 11px; cursor: pointer; }

/* 【D24 2026-11-23】书内搜索（侧栏） */
.rail-search { margin-top: 16px; padding-top: 10px; border-top: 1px dashed #d8cdbb; }
.rs-input { display: flex; gap: 4px; margin-top: 7px; }
.rs-input input { flex: 1; min-width: 0; padding: 5px 8px; border: 1px solid #d8cdbb; border-radius: 6px; color: #2e2a24; background: #fffdf8; font-size: 11px; }
.rs-input input:focus { border-color: #2d5a4a; outline: none; }
.rs-input button { flex-shrink: 0; padding: 5px 10px; border: 1px solid #2d5a4a; border-radius: 6px; color: #fff7ee; background: #2d5a4a; font-size: 11px; cursor: pointer; }
.rs-input button:disabled { border-color: #d8cdbb; color: #b3a893; background: #f4ede0; cursor: default; }
.rs-note { margin: 6px 0 0; color: #a99d8c; font-size: 10.5px; line-height: 1.6; }
.rs-note.warn { color: #b5543a; }
.rs-hits { display: flex; flex-direction: column; gap: 3px; margin-top: 6px; max-height: 220px; overflow-y: auto; }
.rs-hit { display: flex; flex-direction: column; gap: 2px; padding: 5px 7px; border: 1px solid #e8e0d3; border-radius: 6px; background: #fffdf8; text-align: left; cursor: pointer; }
.rs-hit:hover { border-color: #b5543a; }
.rs-page { color: #2d5a4a; font-size: 10px; font-weight: 700; }
.rs-snippet { color: #6b6257; font-size: 10.5px; line-height: 1.5; word-break: break-word; }

/* 【D24 2026-11-23】重排视图：连续滚动读文本层，字号/行距走 CSS 变量 */
.reflow-pane { flex: 1; min-height: 0; overflow-y: auto; background: var(--reader-stage-bg); }
.reflow-paper { max-width: 760px; margin: 22px auto 40px; padding: 22px 30px 60px; border-radius: 4px; background: var(--reader-page-bg); box-shadow: 0 6px 24px rgba(0, 0, 0, .35); }
.reflow-block { position: relative; }
.reflow-mark { margin: 18px 0 8px; padding-bottom: 4px; border-bottom: 1px dashed rgba(150, 140, 120, .35); color: #a67c52; font-size: 10.5px; letter-spacing: .04em; }
.reflow-text { margin: 0 0 4px; color: var(--reader-ink); font-family: 'Songti SC', 'STSong', serif; font-size: var(--reader-font-size); line-height: var(--reader-line-height); white-space: pre-wrap; word-break: break-word; }
.reflow-empty { margin: 0; color: #a67c52; font-size: 11.5px; font-style: italic; }
.reflow-loading, .reflow-end { margin: 14px 0 0; color: #a67c52; font-size: 11px; text-align: center; }

.reader-stage { position: relative; display: flex; flex-direction: column; min-height: 0; background: var(--reader-stage-bg); }
.stage-canvas-wrap { flex: 1; display: flex; justify-content: center; align-items: flex-start; overflow: auto; padding: 16px; }
/* 【D15 2026-11-23】缩放用 zoom：canvas 的 width/height 由 useFabushiPdfReader 以行内样式写死，
   只有 zoom 能在不改渲染器、不被行内样式覆盖的前提下同时做到"视觉放大 + 撑开滚动区"。
   transform: scale() 不重排，放大后底部不可滚，故不采用。
   兼容性：Chrome/Edge/Safari 全版本；Firefox ≥126。Firefox <126 下 zoom 被忽略 ⇒ 缩放滑杆无效果，
   版式不坏（仍可正常阅读），已在部署说明"已知限制"登记。 */
.stage-canvas { background: var(--reader-page-bg); border-radius: 4px; box-shadow: 0 6px 24px rgba(0, 0, 0, .35); zoom: var(--reader-zoom); filter: contrast(var(--reader-contrast)); clip-path: inset(var(--reader-crop) round 4px); }
.theme-night .stage-canvas { box-shadow: 0 6px 24px rgba(0, 0, 0, .65); }
.stage-mask { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: var(--reader-stage-bg); }
.mask-title { color: #e8e0d3; font-family: 'Songti SC', 'STSong', serif; font-size: 14px; }
.mask-sub { color: #a67c52; font-size: 12px; }
.mask-retry { margin-top: 6px; }

.stage-nav { display: flex; align-items: center; justify-content: center; gap: 18px; padding: 10px 16px; border-top: 1px solid #e8e0d3; background: var(--reader-page-bg); }
.stage-nav :deep(.el-button) { border-radius: 999px; border-color: #d8cdbb; color: #2d5a4a; background: var(--reader-page-bg); }
.stage-nav :deep(.el-button:hover:not(.is-disabled)) { color: #b5543a; border-color: #b5543a; }
.bookmark-toggle { padding: 5px 12px; border: 1px solid #d8cdbb; border-radius: 999px; color: #a67c52; background: transparent; font-size: 11px; cursor: pointer; }
.bookmark-toggle.on { color: #b5543a; border-color: #b5543a; background: #f8e9e0; }
.bookmark-toggle:disabled { opacity: .6; cursor: default; }
.stage-page { min-width: 110px; text-align: center; color: #a67c52; font-family: 'Songti SC', 'STSong', serif; font-size: 12px; }

.reader-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
</style>
