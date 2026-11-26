<template>
  <div class="fx-page fabushi-reader" :class="'theme-' + settings.backdrop" :style="cssVars">
    <div class="reader-shell">
      <!-- 【2026-11-26 按 PC 原型 v8】P5 在线阅读：深色阅读壳。顶栏 = 返回书目 · 书名 · 版式标记 · 版式设置 / 书签 / 书内搜索 / 下载 -->
      <div class="r-top">
        <button type="button" class="back" @click="router.back()"><ElIcon><ArrowLeft /></ElIcon>返回书目</button>
        <b class="r-name">《{{ book.title || '在线阅读' }}》</b>
        <span class="r-sub">
          <template v-if="bookSub">{{ bookSub }} · </template><b v-if="!bookError" class="ok">{{ layoutLabel(layoutType) }}</b>
        </span>
        <span class="spacer" />
        <button type="button" class="r-btn" :class="{ active: settingsOpen }" @click="toggleSettings"><ElIcon><Setting /></ElIcon>版式设置</button>
        <button type="button" class="r-btn" :class="{ active: sidePanel === 'bookmarks' }" @click="toggleSide('bookmarks')">
          <ElIcon><CollectionTag /></ElIcon>书签<span v-if="bookmarks.length" class="r-badge">{{ bookmarks.length }}</span>
        </button>
        <button type="button" class="r-btn" :class="{ active: sidePanel === 'search' }" @click="toggleSide('search')"><ElIcon><Search /></ElIcon>书内搜索</button>
        <button v-if="book.pdfFileId && canDownload" type="button" class="r-btn" @click="downloadPdf"><ElIcon><Download /></ElIcon>下载</button>
      </div>

      <div v-if="!bookError" class="r-body">
        <!-- 左侧面板：目录（默认）/ 书内搜索 / 书签。三种内容都渲染在页面里，用 v-show 切换 -->
        <aside class="r-toc">
          <div v-show="sidePanel === 'toc'" class="toc-panel toc-panel-toc">
            <div class="r-toc-head">目录<span v-if="tocRows.length" class="chip2 blue">{{ tocRows.length }} 节</span></div>
            <div class="toc-list">
              <!-- 章节接口没有页码，目录行点不了，所以不做成按钮 -->
              <div v-for="row in tocRows" :key="row.id" class="toc-row" :class="['lv' + row.depth, { part: row.depth <= 1 }]">{{ row.title }}</div>
              <p v-if="!tocRows.length" class="r-toc-empty">这本书还没有章节目录。</p>
            </div>
            <p class="r-toc-foot">章节还没有关联页码，暂时不能点击跳转；可以用底部页码或键盘左右方向键翻页。</p>
          </div>

          <!-- 【D24 2026-11-23】书内搜索：只读 PDF 自带文本层，不做 OCR -->
          <div v-show="sidePanel === 'search'" class="toc-panel toc-panel-search">
            <div class="r-toc-head">书内搜索</div>
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
            <p v-if="textLayerState === 'none'" class="rs-note warn">这本 PDF 没有文本层（影印件），书内搜索不可用；请用页码翻页，或下载原件检索。</p>
            <p v-else-if="searchState === 'indexing'" class="rs-note">正在建立全文索引 {{ indexDone }}/{{ totalPages }} 页…</p>
            <p v-else-if="searchState === 'done'" class="rs-note">{{ searchHits.length ? `命中 ${searchHits.length} 处` : '没有找到匹配内容' }}</p>
            <div v-if="searchHits.length" class="toc-list">
              <button v-for="(hit, i) in pagedSearchHits" :key="i" type="button" class="rs-hit" @click="jumpToHit(hit)">
                <span class="rs-page">第 {{ hit.page }} 页</span>
                <span class="rs-snippet">{{ hit.snippet }}</span>
              </button>
              <div v-if="searchTotalPages > 1" class="r-pager">
                <el-pagination v-model:current-page="searchPage" :page-size="searchSize" :total="searchTotal" layout="prev, pager, next" small />
              </div>
            </div>
          </div>

          <div v-show="sidePanel === 'bookmarks'" class="toc-panel toc-panel-bookmarks">
            <div class="r-toc-head">我的书签<span class="chip2 blue">{{ bookmarks.length }}</span></div>
            <button
              type="button"
              class="r-action"
              :class="{ on: currentBookmarked }"
              :disabled="state !== 'ready' || bookmarkBusy"
              @click="toggleBookmark"
            >{{ currentBookmarked ? '★ 取消本页书签' : '☆ 给本页加书签' }}</button>
            <p v-if="!bookmarks.length" class="r-toc-empty">还没有书签。翻到重要的一页，点上面的按钮。</p>
            <div class="toc-list">
              <div v-if="bookmarkTotalPages > 1" class="r-pager">
                <el-pagination v-model:current-page="bookmarkPage" :page-size="bookmarkSize" :total="bookmarks.length" layout="prev, pager, next" small />
              </div>
              <div v-for="item in pagedBookmarks" :key="item.id" class="bm-row">
                <button type="button" class="bm-jump" @click="turnTo(item.page)">第 {{ item.page }} 页<template v-if="item.note"> · {{ item.note }}</template></button>
                <button type="button" class="bm-del" :aria-label="'删除第 ' + item.page + ' 页书签'" @click="removeBookmark(item.id)"><ElIcon><Close /></ElIcon></button>
              </div>
            </div>
          </div>
        </aside>

        <div class="paper-zone">
          <!-- 【D24 2026-11-23】重排视图：连续滚动读取 PDF 自带文本层，字号/行距走 CSS 变量；
               影印件（无文本层）不进入本分支，由版式面板如实提示不可用。 -->
          <div v-if="settings.reflow && textLayerState === 'yes'" ref="reflowEl" class="reflow-pane" @scroll.passive="onReflowScroll">
            <div class="reflow-paper">
              <div v-for="blk in reflowBlocks" :key="blk.page" class="reflow-block" :data-page="blk.page">
                <div class="reflow-mark">第 {{ blk.page }} 页</div>
                <p v-if="blk.text" class="reflow-text">{{ blk.text }}</p>
                <p v-else class="reflow-empty">本页没有文字（可能是插图页），切回“原样”查看。</p>
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
        </div>
      </div>

      <div v-else class="r-empty">
        <el-empty :description="bookError" :image-size="80" />
      </div>

      <!-- 版式设置：右上角浮层（原型 .r-set）。底色 / 缩放 / 对比度 / 裁边 / 单连续页 / 重排与字号行距，按本书记忆，不改源 PDF -->
      <div v-if="settingsOpen" class="r-set">
        <h5>版式设置<em>按本书记忆</em></h5>
        <div class="row">
          <div class="row-label"><span>模式</span><small>{{ reflowNote }}</small></div>
          <div class="seg">
            <button type="button" class="seg-item" :class="{ active: !settings.reflow }" @click="patch({ reflow: false })">原样</button>
            <button
              type="button" class="seg-item" :class="{ active: settings.reflow }"
              :disabled="textLayerState !== 'yes'"
              @click="enableReflow"
            >重排</button>
          </div>
        </div>
        <div class="row">
          <div class="row-label"><span>背景</span></div>
          <div class="seg">
            <button v-for="item in backdrops" :key="item.value" type="button" class="seg-item" :class="{ active: settings.backdrop === item.value }" @click="patch({ backdrop: item.value })">{{ item.label }}</button>
          </div>
        </div>
        <div class="row col">
          <div class="row-label"><span>字号 / 行距</span><small>重排模式生效</small></div>
          <template v-if="settings.reflow && textLayerState === 'yes'">
            <label class="r-range">字号 <b>{{ settings.fontSize }}px</b><input type="range" min="14" max="30" step="1" :value="settings.fontSize" aria-label="字号" @input="setFontSize" /></label>
            <label class="r-range">行距 <b>{{ lineHeightRatio(settings.lineHeight) }}</b><input type="range" min="14" max="26" step="1" :value="settings.lineHeight" aria-label="行距" @input="setLineHeight" /></label>
          </template>
        </div>
        <div class="row col">
          <div class="row-label"><span>对比度增强</span><small>扫描件的灰底可以调高，不修改源文件</small></div>
          <label class="r-range"><b>{{ settings.contrast }}%</b><input type="range" min="90" max="160" step="5" :value="settings.contrast" aria-label="对比度" @input="setContrast" /></label>
        </div>
        <div class="row col">
          <div class="row-label"><span>裁掉页边白边</span></div>
          <label class="r-range"><b>{{ settings.crop }}%</b><input type="range" min="0" max="14" step="1" :value="settings.crop" aria-label="裁页边" @input="setCrop" /></label>
        </div>
        <div class="row col">
          <div class="row-label"><span>缩放</span></div>
          <label class="r-range"><b>{{ settings.zoom }}%</b><input type="range" min="70" max="160" step="5" :value="settings.zoom" aria-label="缩放" @input="setZoom" /></label>
        </div>
        <div class="row">
          <div class="row-label"><span>翻页</span><small>{{ settings.continuous ? '滚到页底自动翻到下一页' : '用底部按钮或方向键翻页' }}</small></div>
          <div class="seg">
            <button type="button" class="seg-item" :class="{ active: !settings.continuous }" @click="patch({ continuous: false })">单页</button>
            <button type="button" class="seg-item" :class="{ active: settings.continuous }" @click="patch({ continuous: true })">连续滚动</button>
          </div>
        </div>
        <button type="button" class="set-reset" @click="resetSettings">恢复本书的默认版式</button>
      </div>

      <!-- 底部（原型 .r-foot）：上一页 / 进度条 / 下一页 / 页码 / 加书签 -->
      <div v-if="state === 'ready'" class="r-foot">
        <button type="button" class="pg-btn" :disabled="currentPage <= 1" aria-label="上一页" @click="prevPage"><ElIcon><ArrowLeft /></ElIcon></button>
        <div class="rprog"><i :style="{ width: percent + '%' }" /></div>
        <button type="button" class="pg-btn" :disabled="currentPage >= totalPages" aria-label="下一页" @click="nextPage"><ElIcon><ArrowRight /></ElIcon></button>
        <span class="r-page">{{ currentPage }} / {{ totalPages }} · {{ percent }}%</span>
        <label class="r-jump">跳至第 <el-input-number v-model="pageInput" :min="1" :max="totalPages || 1" size="small" controls-position="right" @change="onPageInput" /> 页</label>
        <button type="button" class="r-bm" :class="{ on: currentBookmarked }" :disabled="bookmarkBusy" @click="toggleBookmark">
          <ElIcon><StarFilled v-if="currentBookmarked" /><Star v-else /></ElIcon>{{ currentBookmarked ? '已加书签' : '加书签' }}
        </button>
        <span class="r-tip">进度自动记录 · 下次续读</span>
      </div>
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
// 不做 OCR）。
// 【2026-11-26 按 PC 原型 v8】外观改成原型的深色阅读壳：顶栏按钮、左侧目录 / 书内搜索 / 书签三种面板、
// 右上角版式浮层、底部进度条。渲染、进度、书签、搜索的逻辑都没有变。
// 图标容器写成 <ElIcon>（从 element-plus 显式引入）而不是靠自动引入的 <el-icon>：
// paged-list-mount.spec.js 挂载本视图时没有给 el-icon 准备桩，自动引入的标签会报“Failed to resolve component”。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElIcon, ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Close, CollectionTag, Download, Search, Setting, Star, StarFilled } from '@element-plus/icons-vue'
import {
  getFabushiBook, getFabushiSections, getFabushiBookmarks, addFabushiBookmark, removeFabushiBookmark
} from '@/api/fabushi'
import { getToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { useUserStore } from '@/store/user'
import { useFabushiPdfReader } from '@/composables/useFabushiPdfReader'
import { useFabushiReaderSettings } from '@/composables/useFabushiReaderSettings'
import { usePagedList } from '@/composables/usePagedList'
import {
  FABUSHI_LAYOUT_TYPES, READER_BACKDROPS, layoutLabel, lineHeightRatio,
  normalizeReaderSettings, readingPercent, readerCssVars
} from '@/utils/fabushi-reader-settings'
import '@/assets/fabushi-v8.css'

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
// 左侧面板显示哪一种：toc=目录（默认）/ search=书内搜索 / bookmarks=书签
const sidePanel = ref('toc')
const toggleSide = (name) => {
  sidePanel.value = sidePanel.value === name ? 'toc' : name
}
const toggleSettings = () => { settingsOpen.value = !settingsOpen.value }
const bookmarks = ref([])
const { current: bookmarkPage, size: bookmarkSize, totalPages: bookmarkTotalPages, paged: pagedBookmarks } = usePagedList(bookmarks, { pageSize: 8 })
const bookmarkBusy = ref(false)

const layoutType = computed(() => book.value.layoutType === 'text' ? 'text' : 'scan')
const bookSub = computed(() => {
  const pages = totalPages.value || book.value.pages
  return [book.value.author, pages ? `${pages} 页` : ''].filter(Boolean).join(' · ')
})
const bookIdRef = computed(() => String(route.params.bookId || ''))
const { settings, patch } = useFabushiReaderSettings(bookIdRef, layoutType)
const backdrops = READER_BACKDROPS
const cssVars = computed(() => readerCssVars(settings.value))
const percent = computed(() => readingPercent(currentPage.value, totalPages.value))
const currentBookmarked = computed(() => bookmarks.value.some((item) => item.page === currentPage.value))
const reflowNote = computed(() => {
  if (textLayerState.value === 'probing') return '正在检测文本层…'
  if (textLayerState.value === 'none') return '影印版没有文本层，不能重排'
  return '重排只读取 PDF 自带的文本层'
})

const { state, loadPct, errorMsg, currentPage, totalPages, load, goPage, markPage, retry,
  pageText, detectTextLayer, extractAllText, searchInPages } = useFabushiPdfReader({
  bookId: bookIdRef,
  pdfFileId: computed(() => book.value.pdfFileId),
  token,
  // 【2026-11-26 按 PC 原型 v8】阅读器是一屏高的固定布局：书页按可视区高度整页适配，一页不用上下滚动就能看全
  fitHeight: true
})

watch(currentPage, (v) => { pageInput.value = v })

/* ============ 目录：章节接口（序 / 部分 / 章 / 节），接口没有页码，只做展示 ============ */
// 【2026-11-26 按 PC 原型 v8】左侧面板的目录。章节由管理端“章节结构”维护，按级别缩进；
// 接口里没有每章对应的页码，所以目录行不做点击跳转（不做点不了的假入口）。
const sections = ref([])
const LEVEL_ORDER = ['preface', 'part', 'chapter', 'section']
const tocRows = computed(() => (Array.isArray(sections.value) ? sections.value : [])
  .map((item) => ({ ...item, depth: LEVEL_ORDER.indexOf(item.levelCode) }))
  .filter((item) => item.depth >= 0)
  .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)))

async function loadSections() {
  try {
    const { data } = await getFabushiSections(route.params.bookId)
    sections.value = (data && data.sections) || []
  } catch (e) {
    sections.value = [] // 目录拉不到不阻断阅读
  }
}

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
    loadSections()
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
/* 【2026-11-26 按 PC 原型 v8】阅读壳的配色取自原型“阅读器（P5）”那组变量（--rs-*，只在这个页面里用）：
   深蓝底、深蓝条、浅字、半透明按钮。书页本身的底色 / 缩放 / 对比度 / 裁边仍由版式设置经 CSS 变量驱动（--reader-*），按书记忆。 */
.fabushi-reader {
  --rs-bg: #16233b; --rs-bar: #1c2b45; --rs-text: #d8e1ef; --rs-muted: #8fa1bd;
  --rs-btn: rgba(255, 255, 255, .14); --rs-btn-line: rgba(255, 255, 255, .3); --rs-ok: #9fd8bd;
  /* 阅读器是一屏高的固定布局：高度 = 视口高 − 顶栏 60px − 内容区上下内边距 24px，页面自己不滚动，书页区内部滚动 */
  gap: 0; height: calc(100vh - 84px); min-height: 520px;
}
/* 阅读壳铺满整个内容区（连内容区的内边距一起盖掉，与原型 .reader-shell 的负外边距同一个意思） */
.reader-shell { position: relative; display: flex; flex-direction: column; flex: 1; height: calc(100% + 24px); min-height: 0; margin: -12px -16px 0; overflow: hidden; background: var(--rs-bg); }
.r-top { display: flex; align-items: center; flex-shrink: 0; gap: 10px; height: 46px; padding: 0 16px; background: var(--rs-bar); color: var(--rs-text); }
.r-top .back { display: inline-flex; align-items: center; gap: 6px; padding: 0; border: 0; background: none; color: var(--rs-text); font-size: 12.5px; font-family: inherit; cursor: pointer; }
.r-top .back:hover, .r-top .r-btn:hover { color: #fff; }
.r-top .back :deep(.el-icon) { font-size: 13px; }
.r-name { color: #fff; font-size: 14px; font-weight: 800; white-space: nowrap; }
.r-sub { overflow: hidden; color: var(--rs-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.r-sub .ok { color: var(--rs-ok); font-weight: 700; }
.r-btn { position: relative; display: inline-flex; align-items: center; gap: 5px; height: 28px; padding: 0 12px; border: 1px solid var(--rs-btn-line); border-radius: var(--fx-r-pill); background: var(--rs-btn); color: var(--rs-text); font-size: 11.5px; font-family: inherit; cursor: pointer; white-space: nowrap; }
.r-btn :deep(.el-icon) { font-size: 12px; }
.r-btn.active { background: rgba(255, 255, 255, .32); color: #fff; }
.r-badge { margin-left: 2px; padding: 0 5px; border-radius: var(--fx-r-pill); background: rgba(255, 255, 255, .22); font-size: 10px; font-weight: 700; }

.r-body { display: flex; flex: 1; min-height: 0; padding: 14px; }
.r-toc { display: flex; flex-direction: column; flex-shrink: 0; width: 250px; margin-right: 14px; padding: 14px; overflow: hidden; border-radius: var(--fx-r-md); background: var(--rs-bar); }
.toc-panel { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.r-toc-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; color: #fff; font-size: 13px; font-weight: 800; }
.r-toc-head .chip2 { padding: 1px 7px; font-size: 10px; }
.toc-list { flex: 1; min-height: 0; overflow-y: auto; }
.toc-row { padding: 8px 10px; border-radius: 6px; color: var(--rs-muted); font-size: 12px; line-height: 1.4; word-break: break-word; }
.toc-row.part { color: var(--rs-text); font-weight: 700; }
.toc-row.lv2 { padding-left: 24px; }
.toc-row.lv3 { padding-left: 38px; }
.r-toc-empty, .r-toc-foot { margin: 8px 0 0; color: var(--rs-muted); font-size: 11px; line-height: 1.6; }
.r-toc-foot { margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--rs-btn); font-size: 10.5px; }
.r-action { width: 100%; margin-bottom: 10px; padding: 7px 10px; border: 1px solid var(--rs-btn-line); border-radius: var(--fx-r-sm); background: var(--rs-btn); color: var(--rs-text); font-size: 12px; font-family: inherit; cursor: pointer; }
.r-action.on { background: rgba(255, 255, 255, .3); color: #fff; font-weight: 700; }
.r-action:disabled { opacity: .5; cursor: default; }
.r-pager { display: flex; justify-content: center; margin-bottom: 6px; }
.r-pager :deep(.el-pagination) { --el-pagination-bg-color: transparent; --el-pagination-button-disabled-bg-color: transparent; --el-pagination-button-color: var(--rs-muted); --el-pagination-hover-color: #fff; }
.r-pager :deep(.el-pager li) { background: transparent; color: var(--rs-muted); }
.r-pager :deep(.el-pager li.is-active) { background: var(--rs-btn); color: #fff; }
.bm-row { display: flex; align-items: center; gap: 4px; margin-top: 6px; }
.bm-jump { flex: 1; overflow: hidden; padding: 7px 9px; border: 1px solid var(--rs-btn); border-radius: 6px; background: transparent; color: var(--rs-text); font-size: 11.5px; font-family: inherit; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.bm-jump:hover { border-color: var(--rs-btn-line); color: #fff; }
.bm-del { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 28px; border: 0; border-radius: 6px; background: transparent; color: var(--rs-muted); cursor: pointer; }
.bm-del:hover { background: var(--rs-btn); color: #fff; }
.bm-del :deep(.el-icon) { font-size: 12px; }

.rs-input { display: flex; gap: 6px; }
.rs-input input { flex: 1; min-width: 0; height: 30px; padding: 0 9px; border: 1px solid var(--rs-btn-line); border-radius: var(--fx-r-sm); background: rgba(255, 255, 255, .06); color: var(--rs-text); font-size: 12px; font-family: inherit; }
.rs-input input::placeholder { color: var(--rs-muted); }
.rs-input input:focus { border-color: rgba(255, 255, 255, .6); outline: none; }
.rs-input button { flex-shrink: 0; padding: 0 11px; border: 1px solid var(--rs-btn-line); border-radius: var(--fx-r-sm); background: var(--rs-btn); color: #fff; font-size: 12px; font-family: inherit; cursor: pointer; }
.rs-input button:disabled { opacity: .45; cursor: default; }
.rs-note { margin: 8px 0 0; color: var(--rs-muted); font-size: 11px; line-height: 1.6; }
.rs-note.warn { color: #f6c98b; }
.rs-hit { display: flex; flex-direction: column; gap: 2px; width: 100%; margin-top: 6px; padding: 6px 8px; border: 1px solid var(--rs-btn); border-radius: 6px; background: transparent; text-align: left; cursor: pointer; }
.rs-hit:hover { border-color: var(--rs-btn-line); }
.rs-page { color: #fff; font-size: 10px; font-weight: 700; }
.rs-snippet { color: var(--rs-text); font-size: 11px; line-height: 1.5; word-break: break-word; }

/* 书页区 */
.paper-zone { position: relative; display: flex; flex: 1; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; border-radius: var(--fx-r-md); background: var(--rs-bar); }
.stage-canvas-wrap { display: flex; flex: 1; justify-content: center; align-items: flex-start; min-height: 0; overflow: auto; padding: 16px; }
/* 【D15 2026-11-23】缩放用 zoom：canvas 的 width/height 由 useFabushiPdfReader 以行内样式写死，
   只有 zoom 能在不改渲染器、不被行内样式覆盖的前提下同时做到"视觉放大 + 撑开滚动区"。
   transform: scale() 不重排，放大后底部不可滚，故不采用。
   兼容性：Chrome/Edge/Safari 全版本；Firefox ≥126。Firefox <126 下 zoom 被忽略 ⇒ 缩放滑杆无效果，
   版式不坏（仍可正常阅读），已在部署说明"已知限制"登记。 */
.stage-canvas { flex-shrink: 0; background: var(--reader-page-bg); border-radius: 4px; box-shadow: 0 4px 12px rgba(0, 0, 0, .45); zoom: var(--reader-zoom); filter: contrast(var(--reader-contrast)); clip-path: inset(var(--reader-crop) round 4px); }
.stage-mask { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: var(--rs-bar); }
.mask-title { color: var(--rs-text); font-size: 14px; font-weight: 700; }
.mask-sub { color: var(--rs-muted); font-size: 12px; }
.mask-retry { --el-component-size: 28px; }

/* 【D24 2026-11-23】重排视图：连续滚动读文本层，字号/行距走 CSS 变量；按原型的“纸面”排 */
.reflow-pane { flex: 1; min-height: 0; overflow-y: auto; padding: 16px; }
.reflow-paper { width: 560px; max-width: 100%; margin: 0 auto; padding: 26px 30px; border-radius: 4px; background: var(--reader-page-bg); box-shadow: 0 4px 12px rgba(60, 50, 20, .18); color: var(--reader-ink); font-family: 'Songti SC', 'STSong', serif; }
.theme-night .reflow-paper { box-shadow: 0 6px 24px rgba(0, 0, 0, .65); }
.reflow-block { position: relative; }
.reflow-mark { margin: 18px 0 8px; padding-bottom: 4px; border-bottom: 1px dashed rgba(150, 140, 120, .35); color: var(--reader-ink); font-size: 10.5px; letter-spacing: .04em; opacity: .7; }
.reflow-text { margin: 0 0 10px; font-size: var(--reader-font-size); line-height: var(--reader-line-height); text-indent: 2em; white-space: pre-wrap; word-break: break-word; }
.reflow-empty { margin: 0; font-size: 12px; font-style: italic; opacity: .7; }
.reflow-loading, .reflow-end { margin: 14px 0 0; font-size: 11px; text-align: center; opacity: .7; }

/* 版式设置浮层（原型 .r-set） */
.r-set { position: absolute; z-index: 20; top: 54px; right: 16px; width: 270px; padding: 12px 14px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-pop); color: var(--text-primary); text-align: left; }
.r-set h5 { display: flex; align-items: center; justify-content: space-between; margin: 0 0 4px; color: var(--text-primary); font-size: 12.5px; font-weight: 800; }
.r-set h5 em { color: var(--text-placeholder); font-size: 10px; font-style: normal; font-weight: 400; }
.r-set .row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 7px 0; border-top: 1px solid var(--fx-line-soft); }
.r-set .row:first-of-type { border-top: 0; }
.r-set .row.col { flex-direction: column; align-items: stretch; }
.row-label { display: flex; flex: 1; flex-direction: column; min-width: 0; color: var(--text-secondary); font-size: 11.5px; }
.row-label small { color: var(--text-placeholder); font-size: 9.5px; line-height: 1.4; }
.r-set .seg { flex-shrink: 0; }
.r-set .seg-item { padding: 3px 9px; font-size: 11px; }
.r-set .seg-item:disabled { opacity: .45; cursor: not-allowed; }
.r-range { display: flex; align-items: center; gap: 8px; margin-top: 6px; color: var(--text-secondary); font-size: 11px; }
.r-range b { min-width: 40px; color: var(--text-primary); font-size: 11px; font-variant-numeric: tabular-nums; text-align: right; }
.r-range input[type='range'] { flex: 1; accent-color: var(--primary-color); }
.set-reset { width: 100%; margin-top: 8px; padding: 6px; border: 1px solid var(--border-light); border-radius: var(--fx-r-sm); background: #fff; color: var(--text-secondary); font-size: 11px; font-family: inherit; cursor: pointer; }
.set-reset:hover { border-color: var(--primary-color); color: var(--primary-color); }

/* 底部翻页条（原型 .r-foot） */
.r-foot { display: flex; align-items: center; flex-shrink: 0; gap: 10px; height: 44px; padding: 0 18px; background: var(--rs-bar); color: var(--rs-text); }
.pg-btn { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 28px; border: 1px solid var(--rs-btn-line); border-radius: var(--fx-r-sm); background: var(--rs-btn); color: #fff; font-size: 13px; cursor: pointer; }
.pg-btn:disabled { opacity: .35; cursor: default; }
.rprog { position: relative; flex: 1 1 120px; min-width: 80px; height: 5px; border-radius: var(--fx-r-pill); background: rgba(255, 255, 255, .2); }
.rprog i { position: absolute; left: 0; top: 0; height: 100%; border-radius: var(--fx-r-pill); background: linear-gradient(90deg, var(--fx-accent), #10b981); }
.r-page { color: var(--rs-text); font-size: 12px; font-weight: 700; font-variant-numeric: tabular-nums; white-space: nowrap; }
.r-jump { display: inline-flex; align-items: center; gap: 4px; color: var(--rs-muted); font-size: 11px; white-space: nowrap; }
.r-jump :deep(.el-input-number) { width: 74px; --el-component-size: 24px; }
.r-jump :deep(.el-input__wrapper) { background: rgba(255, 255, 255, .08); box-shadow: 0 0 0 1px var(--rs-btn-line) inset; }
.r-jump :deep(.el-input__inner) { color: var(--rs-text); font-size: 12px; }
.r-jump :deep(.el-input-number__decrease), .r-jump :deep(.el-input-number__increase) { background: transparent; color: var(--rs-muted); }
.r-bm { display: inline-flex; align-items: center; gap: 5px; height: 28px; padding: 0 12px; border: 1px solid var(--rs-btn-line); border-radius: var(--fx-r-pill); background: var(--rs-btn); color: var(--rs-text); font-size: 11.5px; font-family: inherit; cursor: pointer; white-space: nowrap; }
.r-bm.on { background: #fde9c0; border-color: #fbbf24; color: #92400e; }
.r-bm:disabled { opacity: .6; cursor: default; }
.r-tip { overflow: hidden; color: var(--rs-ok); font-size: 10.5px; text-overflow: ellipsis; white-space: nowrap; }
.r-empty { display: flex; align-items: center; justify-content: center; flex: 1; min-height: 320px; border-radius: var(--fx-r-md); background: #fff; }
@media (max-width: 1100px) {
  .r-toc { width: 200px; }
  .r-tip, .r-jump { display: none; }
}
</style>
