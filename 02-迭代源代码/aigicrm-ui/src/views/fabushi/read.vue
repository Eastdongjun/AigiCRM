<template>
  <div class="fabushi-reader">
    <div class="reader-head">
      <el-button link class="back" @click="router.back()">‹ 返回书目</el-button>
      <b>{{ book.title || '在线阅读' }}</b>
      <span class="reader-sub">{{ book.author || '' }}<template v-if="totalPages"> · {{ totalPages }} 页</template></span>
      <span class="spacer" />
      <span class="page-control" v-if="state === 'ready'">
        第 <el-input-number v-model="pageInput" :min="1" :max="totalPages || 1" size="small" controls-position="right" @change="onPageInput" /> 页<template v-if="totalPages"> / {{ totalPages }}</template>
      </span>
      <el-button v-if="book.pdfFileId && canDownload" link class="download" @click="downloadPdf">下载</el-button>
    </div>

    <div v-if="!bookError" class="reader-body">
      <aside class="reader-rail">
        <div class="rail-title">阅读说明</div>
        <p>单页阅读，翻页自动记录进度；下次从书目进入，可接着上次页码继续。</p>
        <p class="rail-tip">← → 方向键翻页</p>
        <div class="rail-meta">
          <span>书目</span><b>{{ book.title || '—' }}</b>
          <span>作者</span><b>{{ book.author || '—' }}</b>
          <span>页数</span><b>{{ totalPages || book.pages || '—' }}</b>
        </div>
      </aside>

      <section class="reader-stage">
        <div class="stage-canvas-wrap">
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
          <span class="stage-page">{{ currentPage }} / {{ totalPages }}</span>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getFabushiBook } from '@/api/fabushi'
import { getToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { useUserStore } from '@/store/user'
import { useFabushiPdfReader } from '@/composables/useFabushiPdfReader'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const book = ref({ id: null, title: '', author: '', pages: null, pdfFileId: null })
const bookError = ref('')
const pageInput = ref(1)
const canvasEl = ref(null)
const token = ref(getToken() || '')
const canDownload = computed(() => userStore.hasPermission('fabushi:view'))

const { state, loadPct, errorMsg, currentPage, totalPages, load, goPage, retry } = useFabushiPdfReader({
  bookId: computed(() => String(route.params.bookId || '')),
  pdfFileId: computed(() => book.value.pdfFileId),
  token
})

watch(currentPage, (v) => { pageInput.value = v })

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
    if (!b.pdfFileId) {
      bookError.value = '该书目暂未挂载电子书'
      return
    }
    await load(() => canvasEl.value)
  } catch (e) {
    // 后端 404「书目不存在」/403 已由请求拦截器转成中文 message
    bookError.value = (e && e.message) || '书目录入失败，请稍后再试'
  }
}

function onPageInput() {
  goPage(pageInput.value)
}
function prevPage() {
  goPage(currentPage.value - 1)
}
function nextPage() {
  goPage(currentPage.value + 1)
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
/* 【书卷化 2026-11-16 P3】纸色头/侧栏 + 深灰衬底衬托 PDF 书页；翻页导航落在纸色条上 */
.fabushi-reader { display: flex; flex-direction: column; height: calc(100vh - 150px); min-height: 520px; overflow: hidden; border: 1px solid #e8e0d3; border-radius: 12px; background: #fffdf8; }
.reader-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-bottom: 1px solid #e8e0d3; color: #2e2a24; }
.reader-head b { color: #2e2a24; font-family: 'Songti SC', 'STSong', serif; font-size: 15px; }
.reader-sub { color: #a67c52; font-size: 11px; }
.reader-head .back, .reader-head .download { color: #2d5a4a; }
.page-control { display: inline-flex; align-items: center; gap: 4px; color: #a67c52; font-size: 11px; }
.page-control :deep(.el-input-number) { width: 86px; }
.reader-head .back:hover, .reader-head .download:hover { color: #b5543a; }
.spacer { flex: 1; }

.reader-body { display: grid; grid-template-columns: 220px minmax(0, 1fr); flex: 1; min-height: 0; }
.reader-rail { padding: 16px 14px; border-right: 1px solid #e8e0d3; color: #6b6257; background: #f4ede0; }
.rail-title { margin-bottom: 10px; color: #2e2a24; font-family: 'Songti SC', 'STSong', serif; font-size: 12px; font-weight: 700; }
.reader-rail p { margin: 0; color: #9c9184; font-size: 11.5px; line-height: 1.8; }
.rail-tip { margin-top: 8px !important; color: #a67c52 !important; font-size: 11px !important; }
.rail-meta { display: flex; flex-direction: column; gap: 7px; margin-top: 16px; }
.rail-meta span { color: #a67c52; font-size: 10.5px; }
.rail-meta b { color: #2e2a24; font-size: 12px; font-weight: 500; }

.reader-stage { position: relative; display: flex; flex-direction: column; min-height: 0; background: #4a4238; }
.stage-canvas-wrap { flex: 1; display: flex; justify-content: center; align-items: flex-start; overflow: auto; padding: 16px; }
.stage-canvas { background: #fffdf8; border-radius: 4px; box-shadow: 0 6px 24px rgba(0, 0, 0, .35); }
.stage-mask { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; background: #4a4238; }
.mask-title { color: #e8e0d3; font-family: 'Songti SC', 'STSong', serif; font-size: 14px; }
.mask-sub { color: #a67c52; font-size: 12px; }
.mask-retry { margin-top: 6px; }

.stage-nav { display: flex; align-items: center; justify-content: center; gap: 18px; padding: 10px 16px; border-top: 1px solid #e8e0d3; background: #fffdf8; }
.stage-nav :deep(.el-button) { border-radius: 999px; border-color: #d8cdbb; color: #2d5a4a; background: #fffdf8; }
.stage-nav :deep(.el-button:hover:not(.is-disabled)) { color: #b5543a; border-color: #b5543a; }
.stage-page { min-width: 72px; text-align: center; color: #a67c52; font-family: 'Songti SC', 'STSong', serif; font-size: 12px; }

.reader-empty { flex: 1; display: flex; align-items: center; justify-content: center; }
</style>
