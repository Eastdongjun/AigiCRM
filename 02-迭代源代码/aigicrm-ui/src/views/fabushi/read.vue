<template>
  <div class="fabushi-reader">
    <div class="reader-head">
      <el-button link class="back" @click="router.back()">‹ 返回书目</el-button>
      <b>{{ book.title || '在线阅读' }}</b>
      <span class="reader-sub">{{ book.author || '' }}<template v-if="book.pages"> · {{ book.pages }} 页</template></span>
      <span class="spacer" />
      <el-button v-if="book.pdfFileId && canDownload" link class="download" @click="downloadPdf">下载</el-button>
    </div>

    <div class="reader-body">
      <aside class="reader-rail">
        <div class="rail-title">阅读说明</div>
        <p>当前为 PDF 在线阅读。页码、目录与进度由浏览器 PDF 阅读器提供；结构化章节与进度记忆为二期。</p>
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
import { getFabushiBooks } from '@/api/fabushi'
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
    pdfUrl.value = `/api/file/download/${book.value.pdfFileId}?token=${encodeURIComponent(token)}`
  } catch (e) {
    error.value = '书目录入失败，请稍后再试'
  } finally {
    loading.value = false
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
.fabushi-reader { display: flex; flex-direction: column; min-height: calc(100vh - 150px); overflow: hidden; border-radius: var(--radius-md); background: var(--sidebar-bg); }
.reader-head { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-bottom: 1px solid rgba(255, 255, 255, .08); color: var(--sidebar-text); }
.reader-head b { color: var(--el-color-white); font-size: 14px; }
.reader-sub { color: var(--text-placeholder); font-size: 11px; }
.reader-head .back, .reader-head .download { color: var(--sidebar-text); }
.reader-head .back:hover, .reader-head .download:hover { color: var(--el-color-white); }
.spacer { flex: 1; }
.reader-body { display: grid; grid-template-columns: 220px minmax(0, 1fr); flex: 1; min-height: 0; }
.reader-rail { padding: 16px 14px; border-right: 1px solid rgba(255, 255, 255, .08); color: var(--sidebar-text); }
.rail-title { margin-bottom: 10px; color: var(--el-color-white); font-size: 12px; font-weight: 700; }
.reader-rail p { margin: 0; color: var(--text-placeholder); font-size: 11.5px; line-height: 1.8; }
.rail-meta { display: flex; flex-direction: column; gap: 7px; margin-top: 16px; }
.rail-meta span { color: var(--text-placeholder); font-size: 10.5px; }
.rail-meta b { color: var(--sidebar-text); font-size: 12px; font-weight: 500; }
.reader-frame { display: flex; flex-direction: column; min-height: 0; padding: 12px; background: var(--el-color-info-dark-2); }
.reader-frame iframe { flex: 1; width: 100%; min-height: 620px; border: 0; border-radius: var(--radius-sm); background: var(--el-color-white); }
</style>
