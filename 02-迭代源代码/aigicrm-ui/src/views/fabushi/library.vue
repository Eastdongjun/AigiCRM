<template>
  <div class="fabushi-library">
    <div class="page-head">
      <div>
        <h1>书目浏览</h1>
        <p>按类别浏览已通过审核的书目，在线阅读或写心得</p>
      </div>
    </div>

    <div class="filter">
      <span class="fl">筛选</span>
      <el-input v-model="keyword" clearable placeholder="搜书名 / 作者" style="width: 220px" @keyup.enter="onSearch" />
      <el-select v-model="sort" style="width: 130px" @change="onSearch">
        <el-option label="最近更新" value="latest" />
        <el-option label="书名排序" value="title" />
      </el-select>
      <span class="spacer" />
      <el-button type="primary" @click="onSearch">查询</el-button>
    </div>

    <div class="book-layout">
      <aside class="rail">
        <div class="rail-title">书目分类</div>
        <button class="rail-item" :class="{ active: !categoryCode }" @click="selectCategory('')">
          <span>全部</span><span class="n">{{ allBooks.length }}</span>
        </button>
        <button
          v-for="category in categories"
          :key="category.dictValue"
          class="rail-item"
          :class="{ active: categoryCode === category.dictValue }"
          @click="selectCategory(category.dictValue)"
        >
          <span>{{ category.dictLabel }}</span>
          <span class="n">{{ categoryCounts[category.dictValue] || 0 }}</span>
        </button>
      </aside>

      <section class="book-main" v-loading="loading">
        <div class="book-grid">
          <article v-for="book in sortedBooks" :key="book.id" class="book-card">
            <div class="book-title-row">
              <h3>{{ book.title }}</h3>
              <span v-if="book.pdfFileId" class="pdf-tag">电子书</span>
            </div>
            <div class="book-sub">{{ book.author || '未知作者' }}<template v-if="book.editionType"> · {{ editionLabel(book.editionType) }}</template><template v-if="book.pages"> · {{ book.pages }} 页</template></div>
            <div class="book-desc">{{ book.categoryName || '法布施书目' }} · 已通过审核</div>
            <div class="book-foot">
              <span v-if="book.pdfFileId" class="book-meta">支持在线阅读</span>
              <span v-else class="book-meta">暂未挂载电子书</span>
              <el-button v-if="book.pdfFileId" size="small" type="primary" @click="openReader(book)">在线阅读</el-button>
              <el-button size="small" @click="writePost(book)">写心得</el-button>
            </div>
          </article>
          <el-empty v-if="!loading && !sortedBooks.length" description="暂无书目" />
        </div>
        <div class="library-pager">
          <el-pagination v-model:current-page="current" :page-size="size" :total="total" layout="prev, pager, next" @current-change="loadBooks" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFabushiBooks } from '@/api/fabushi'
import { getDictData } from '@/api/system'

const router = useRouter()
const categories = ref([])
const editionLabels = ref({})
const categoryCode = ref('')
const keyword = ref('')
const sort = ref('latest')
const allBooks = ref([])
const books = ref([])
const total = ref(0)
const current = ref(1)
const size = 12
const loading = ref(false)

const editionLabel = (value) => editionLabels.value[value] || value

const categoryCounts = computed(() => allBooks.value.reduce((counts, book) => {
  const key = book.categoryCode || ''
  counts[key] = (counts[key] || 0) + 1
  return counts
}, {}))

const sortedBooks = computed(() => {
  if (sort.value === 'title') return [...books.value].sort((a, b) => String(a.title).localeCompare(String(b.title), 'zh-CN'))
  return books.value
})

const loadCategories = async () => {
  try {
    const [{ data: categoryData }, { data: editionData }] = await Promise.all([
      getDictData('fabushi_category'),
      getDictData('fabushi_edition_type'),
    ])
    categories.value = Array.isArray(categoryData) ? categoryData : []
    editionLabels.value = Object.fromEntries((editionData || []).map((item) => [item.dictValue, item.dictLabel]))
  } catch (e) {
    categories.value = []
    editionLabels.value = {}
  }
}

const loadAllBooks = async () => {
  try {
    const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 200 })
    allBooks.value = (data && data.records) || []
  } catch (e) {
    allBooks.value = []
  }
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

const onSearch = () => {
  current.value = 1
  loadBooks()
}

const selectCategory = (value) => {
  categoryCode.value = value
  current.value = 1
  loadBooks()
}

const openReader = (book) => {
  if (!book.pdfFileId) return
  router.push(`/fabushi/read/${book.id}`)
}

const writePost = (book) => router.push({ path: '/fabushi/feed', query: { bookId: book.id } })

onMounted(async () => {
  await Promise.all([loadCategories(), loadAllBooks()])
  await loadBooks()
})
</script>

<style scoped>
.fabushi-library { display: flex; flex-direction: column; gap: 10px; }
.page-head h1 { margin: 0; color: var(--text-primary); font-size: 20px; font-weight: 800; }
.page-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; }
.filter { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.fl { color: var(--text-secondary); font-size: 12px; }
.spacer { flex: 1; }
.book-layout { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 12px; align-items: start; }
.rail { padding: 10px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.rail-title { margin-bottom: 8px; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.rail-item { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 8px 9px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 12px; text-align: left; cursor: pointer; }
.rail-item:hover { background: var(--el-fill-color-light); }
.rail-item.active { color: var(--primary-color); background: var(--el-color-primary-light-9); font-weight: 700; }
.rail-item .n { padding: 1px 6px; border-radius: var(--radius-pill); color: var(--text-muted); background: var(--el-fill-color-light); font-size: 10px; }
.rail-item.active .n { color: var(--primary-color); background: var(--el-color-primary-light-8); }
.book-main { min-width: 0; }
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); align-items: start; gap: 10px; min-height: 160px; }
.book-card { display: flex; flex-direction: column; min-height: 176px; padding: 13px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); transition: background .18s, box-shadow .18s; }
.book-card:hover { background: var(--el-bg-color); box-shadow: var(--shadow-card-hover); }
.book-title-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.book-title-row h3 { margin: 0; color: var(--text-primary); font-size: 14px; font-weight: 700; }
.pdf-tag { flex-shrink: 0; padding: 1px 6px; border-radius: var(--radius-sm); color: var(--el-color-success); background: var(--el-color-success-light-9); font-size: 10px; }
.book-sub { margin-top: 5px; color: var(--text-muted); font-size: 11px; }
.book-desc { margin-top: 7px; color: var(--text-secondary); font-size: 11.5px; line-height: 1.6; }
.book-foot { display: flex; align-items: center; gap: 8px; margin-top: auto; padding-top: 10px; }
.book-meta { flex: 1; color: var(--text-muted); font-size: 10.5px; }
.library-pager { display: flex; justify-content: flex-end; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border-light); }
</style>
