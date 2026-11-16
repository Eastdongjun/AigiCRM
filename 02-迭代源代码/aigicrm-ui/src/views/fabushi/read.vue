<template>
  <div class="fabushi-reader">
    <div class="reader-head">
      <el-button link class="back" @click="router.back()">‹ 返回书目</el-button>
      <b>{{ book.title || '在线阅读' }}</b>
      <span class="reader-sub">{{ book.author || '' }}<template v-if="book.pages"> · {{ book.pages }} 页</template></span>
      <span class="spacer" />
      <span class="page-control">第 <el-input-number v-model="currentPage" :min="1" :max="totalPages || 9999" size="small" controls-position="right" @change="applyPage" /> 页<template v-if="totalPages"> / {{ totalPages }}</template></span>
      <el-button link class="download" :loading="savingProgress" @click="saveProgress">保存进度</el-button>
      <el-button v-if="book.pdfFileId && canDownload" link class="download" @click="downloadPdf">下载</el-button>
    </div>

    <div class="reader-body">
      <aside class="reader-rail">
        <div class="rail-title">阅读说明</div>
        <p>当前为 PDF 在线阅读。页码进度会保存到个人书架；结构化目录/章节记忆为下一批。</p>
        <div class="rail-meta">
          <span>书目</span><b>{{ book.title || '—' }}</b>
          <span>作者</span><b>{{ book.author || '—' }}</b>
          <span>页数</span><b>{{ book.pages || '—' }}</b>
        </div>
      </aside>

      <div class="reader-frame" v-loading="loading">
        <iframe v-if="pdfUrl" :src="pdfUrl" title="法布施 PDF 在线阅读" />
        <el-empty v-else-if="!loading" :description="error || '该书目暂未挂载电子书'" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getFabushiBooks, getFabushiProgress, saveFabushiProgress } from '@/api/fabushi'
import { refreshDownloadToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canDownload = computed(() => userStore.hasPermission('fabushi:view'))
const book = ref({ id: null, title: '', author: '', pages: null, pdfFileId: null })
const pdfUrl = ref('')
const basePdfUrl = ref('')
const currentPage = ref(1)
const totalPages = ref(null)
const savingProgress = ref(false)
const loading = ref(true)
const error = ref('')

const loadBook = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 200 })
    const books = (data && data.records) || []
    book.value = books.find((item) => String(item.id) === String(route.params.bookId)) || { id: null, title: '', author: '', pages: null, pdfFileId: null }
    if (!book.value.id) {
      error.value = '书目不存在或未通过审核'
      return
    }
    if (!book.value.pdfFileId) {
      error.value = '该书目暂未挂载电子书'
      return
    }
    const token = await refreshDownloadToken()
    if (!token) {
      error.value = '阅读凭证获取失败，请重新登录后再试'
      return
    }
    basePdfUrl.value = `/api/file/download/${book.value.pdfFileId}?token=${encodeURIComponent(token)}`
    try {
      const { data: p } = await getFabushiProgress(book.value.id)
      currentPage.value = Number(p && p.lastPage) || 1
      totalPages.value = Number(p && p.totalPages) || book.value.pages || null
    } catch (e) {
      totalPages.value = book.value.pages || null
    }
    applyPage()
  } catch (e) {
    error.value = '书目录入失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

const applyPage = () => {
  if (!basePdfUrl.value) return
  const page = Math.max(1, Number(currentPage.value) || 1)
  pdfUrl.value = `${basePdfUrl.value}#page=${page}`
}

const saveProgress = async () => {
  if (!book.value.id) return
  savingProgress.value = true
  try {
    await saveFabushiProgress(book.value.id, { lastPage: currentPage.value, totalPages: totalPages.value })
    ElMessage.success('阅读进度已保存')
  } finally {
    savingProgress.value = false
  }
}

const downloadPdf = async () => {
  if (!book.value.pdfFileId) return
  await downloadFile(`/api/file/download/${book.value.pdfFileId}`, {
    name: `${book.value.title || '法布施书目'}.pdf`,
    headers: { Authorization: `Bearer ${getToken() || ''}` },
  })
}

onMounted(loadBook)
</script>

<style scoped>
/* 【书卷化 2026-11-16】纸色头/侧栏, 保留深灰衬底衬托 PDF 书页 */
.fabushi-reader { display: flex; flex-direction: column; min-height: calc(100vh - 150px); overflow: hidden; border: 1px solid #e8e0d3; border-radius: 12px; background: #fffdf8; }
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
.rail-meta { display: flex; flex-direction: column; gap: 7px; margin-top: 16px; }
.rail-meta span { color: #a67c52; font-size: 10.5px; }
.rail-meta b { color: #2e2a24; font-size: 12px; font-weight: 500; }
.reader-frame { display: flex; flex-direction: column; min-height: 0; padding: 12px; background: #4a4238; }
.reader-frame iframe { flex: 1; width: 100%; min-height: 620px; border: 0; border-radius: 6px; background: #fffdf8; }
</style>
