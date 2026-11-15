<template>
  <div class="fabushi-library">
    <div class="page-header">
      <div>
        <h2>书目浏览</h2>
        <p class="page-desc">已通过审核的书目 · 支持电子书下载</p>
      </div>
    </div>

    <div class="library-toolbar">
      <el-select v-model="categoryCode" clearable placeholder="全部类别" style="width: 180px" @change="onFilterChange">
        <el-option v-for="item in categories" :key="item.dictValue" :label="item.dictLabel" :value="item.dictValue" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="搜书名" style="width: 240px" @keyup.enter="onSearch" />
      <el-button type="primary" @click="onSearch">查询</el-button>
    </div>

    <div v-loading="loading" class="book-grid">
      <article v-for="book in books" :key="book.id" class="book-card">
        <div class="book-card-title">{{ book.title }}</div>
        <div class="book-meta">
          <span v-if="book.categoryName">{{ book.categoryName }}</span>
          <span v-if="book.author">{{ book.author }}</span>
          <span v-if="book.pages">{{ book.pages }} 页</span>
        </div>
        <div class="book-actions">
          <el-tag v-if="book.pdfFileId" size="small" type="success" effect="plain">电子书</el-tag>
          <el-button v-if="book.pdfFileId && canDownload" link type="primary" @click="downloadPdf(book)">下载阅读</el-button>
          <span v-else class="book-no-pdf">暂未挂载电子书</span>
        </div>
      </article>
      <el-empty v-if="!loading && !books.length" description="暂无书目" />
    </div>

    <div class="library-pager">
      <el-pagination v-model:current-page="current" :page-size="size" :total="total" layout="prev, pager, next" @current-change="loadBooks" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getFabushiBooks } from '@/api/fabushi'
import { getDictData } from '@/api/system'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canDownload = computed(() => userStore.hasPermission('fabushi:view'))
const categories = ref([])
const categoryCode = ref('')
const keyword = ref('')
const books = ref([])
const total = ref(0)
const current = ref(1)
const size = 12
const loading = ref(false)

const loadCategories = async () => {
  try {
    const { data } = await getDictData('fabushi_category')
    categories.value = Array.isArray(data) ? data : []
  } catch (e) {
    categories.value = []
  }
}

const onFilterChange = () => {
  current.value = 1
  loadBooks()
}

const onSearch = () => {
  current.value = 1
  loadBooks()
}

const loadBooks = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiBooks({
      categoryCode: categoryCode.value || undefined,
      keyword: keyword.value.trim() || undefined,
      status: 'approved',
      current: current.value,
      size,
    })
    books.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally {
    loading.value = false
  }
}

const downloadPdf = async (book) => {
  if (!book.pdfFileId) return
  const ok = await downloadFile(`/api/file/download/${book.pdfFileId}`, {
    name: `${book.title || '法布施书目'}.pdf`,
    headers: { Authorization: `Bearer ${getToken() || ''}` },
  })
  if (ok !== false) ElMessage.success('电子书已开始下载')
}

onMounted(() => {
  loadCategories()
  loadBooks()
})
</script>

<style scoped>
.fabushi-library { padding-bottom: 24px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px; color: var(--text-primary); font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; color: var(--text-muted); font-size: 13px; }
.library-toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); align-items: start; gap: 14px; min-height: 160px; }
.book-card { display: flex; flex-direction: column; min-height: 140px; padding: 18px; background: var(--el-fill-color-lighter); border: 1px solid var(--border-light); border-radius: var(--radius-md); transition: background .18s, box-shadow .18s, transform .18s; }
.book-card:hover { transform: translateY(-2px); background: var(--el-bg-color); box-shadow: var(--shadow-card-hover); }
.book-card-title { color: var(--text-primary); font-size: 16px; font-weight: 600; }
.book-meta { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 8px; color: var(--text-muted); font-size: 12px; }
.book-actions { display: flex; align-items: center; gap: 10px; margin-top: auto; padding-top: 16px; }
.book-no-pdf { color: var(--text-placeholder); font-size: 12px; }
.library-pager { display: flex; justify-content: flex-end; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border-light); }

</style>
