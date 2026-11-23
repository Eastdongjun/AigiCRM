<template>
  <div class="fabushi-highlights">
    <div class="page-head">
      <div>
        <h1>我的书摘</h1>
        <p>阅读器里「加书签」并写一句备注，就会沉淀到这里 · 共 {{ visibleCount }} 条 · {{ groups.length }} 本书</p>
      </div>
      <div class="head-actions">
        <el-input v-model="keyword" placeholder="搜书名或备注" clearable style="width: 200px" />
        <el-button @click="router.push('/fabushi/shelf')">回我的觉醒</el-button>
      </div>
    </div>

    <div v-if="loading" v-loading="true" class="loading-block" />

    <div v-else-if="!items.length" class="empty-state">
      <div class="empty-icon">摘</div>
      <h3>还没有书摘</h3>
      <p>打开任意一本电子书，翻到想记的那一页，点「加书签」并写一句备注，这里就会按书归集。</p>
      <el-button type="primary" @click="router.push('/fabushi/library')">去书目浏览</el-button>
    </div>

    <div v-else-if="!groups.length" class="empty-state">
      <h3>没有匹配的书摘</h3>
      <p>换个关键词试试，或清空搜索。</p>
      <el-button @click="keyword = ''">清空搜索</el-button>
    </div>

    <div v-else class="group-list">
      <section v-for="g in groups" :key="g.key" class="hl-group">
        <header class="hl-group-head" @click="toggle(g.key)">
          <span class="hl-caret" :class="{ open: !collapsed[g.key] }">›</span>
          <b class="hl-book-title">《{{ g.bookTitle }}》</b>
          <span class="hl-count">{{ g.list.length }} 条</span>
          <span class="hl-spacer" />
          <el-button
            v-if="g.bookId"
            link
            type="primary"
            size="small"
            @click.stop="router.push(`/fabushi/read/${g.bookId}`)"
          >继续阅读 ›</el-button>
        </header>
        <ul v-show="!collapsed[g.key]" class="hl-items">
          <li v-for="h in g.list" :key="h.id" class="hl-item">
            <div class="hl-line">
              <button type="button" class="hl-page" :disabled="!g.bookId" @click="openAt(g, h)">
                第 {{ h.page }} 页
              </button>
              <p class="hl-note" :class="{ muted: !h.note }">{{ h.note || '（无备注，只有页码书签）' }}</p>
              <span class="hl-time">{{ formatDate(h.createdAt) }}</span>
              <el-button link type="danger" size="small" @click="remove(h)">删除</el-button>
            </div>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyFabushiBookmarks, removeFabushiBookmark } from '@/api/fabushi'

const router = useRouter()
const loading = ref(true)
const items = ref([])
const keyword = ref('')
/** 折叠状态按 bookId 记，默认全展开（原型 P9B：按书分组，组头可折叠） */
const collapsed = reactive({})

const formatDate = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')

/**
 * 按书分组：保持后端「按创建时间倒序」的顺序，组内也保持倒序，
 * 组间顺序按每组第一条出现的位置（即最近有书摘的书排在前面）。
 */
const groups = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const filtered = kw
    ? items.value.filter((h) => String(h.bookTitle || '').toLowerCase().includes(kw)
        || String(h.note || '').toLowerCase().includes(kw))
    : items.value
  const bucket = new Map()
  for (const h of filtered) {
    const key = 'b' + (h.bookId || 0)
    if (!bucket.has(key)) {
      bucket.set(key, { key, bookId: h.bookId, bookTitle: h.bookTitle || '—', list: [] })
    }
    bucket.get(key).list.push(h)
  }
  return [...bucket.values()]
})

/** 副标题跟随筛选：搜索时若仍报总数（共 4 条却只列 1 本书）会让人以为漏了数据 */
const visibleCount = computed(() => groups.value.reduce((n, g) => n + g.list.length, 0))

function toggle(key) {
  collapsed[key] = !collapsed[key]
}

function openAt(group, h) {
  if (!group.bookId) return
  router.push({ path: `/fabushi/read/${group.bookId}`, query: { page: h.page } })
}

async function remove(h) {
  try {
    await ElMessageBox.confirm(`删除《${h.bookTitle}》第 ${h.page} 页的书摘？`, '删除书摘', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch (e) {
    return
  }
  try {
    await removeFabushiBookmark(h.id)
    items.value = items.value.filter((x) => x.id !== h.id)
    ElMessage.success('已删除')
  } catch (e) {
    // 全局拦截器已提示
  }
}

onMounted(async () => {
  try {
    const { data } = await getMyFabushiBookmarks()
    items.value = data || []
  } catch (e) {
    items.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.fabushi-highlights { display: flex; flex-direction: column; gap: 12px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.page-head h1 { margin: 0; color: var(--text-primary); font-size: 20px; font-weight: 800; }
.page-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.loading-block { min-height: 160px; }
.empty-state { padding: 48px 16px; text-align: center; border: 1px dashed var(--border-light); border-radius: var(--radius-md); }
.empty-state .empty-icon { display: inline-flex; align-items: center; justify-content: center; width: 52px; height: 52px; margin-bottom: 10px; border-radius: 18px; color: #fff; background: linear-gradient(135deg, #8d7fc7, #5a4c9b); font-size: 20px; font-weight: 800; }
.empty-state h3 { margin: 0 0 6px; color: var(--text-primary); font-size: 15px; }
.empty-state p { margin: 0 0 14px; color: var(--text-muted); font-size: 12px; }

.group-list { display: flex; flex-direction: column; gap: 10px; }
.hl-group { border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); overflow: hidden; }
.hl-group-head { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: var(--el-fill-color-lighter); cursor: pointer; }
.hl-caret { color: var(--text-muted); font-size: 15px; transition: transform .15s; transform: rotate(0deg); }
.hl-caret.open { transform: rotate(90deg); }
.hl-book-title { color: var(--text-primary); font-size: 14px; }
.hl-count { padding: 1px 7px; border-radius: 999px; color: var(--text-secondary); background: var(--el-fill-color); font-size: 11px; }
.hl-spacer { flex: 1; }
.hl-items { margin: 0; padding: 4px 14px 10px; list-style: none; }
.hl-item { padding: 8px 0; border-top: 1px solid var(--border-light); }
.hl-item:first-child { border-top: 0; }
.hl-line { display: flex; align-items: center; gap: 10px; }
.hl-page { flex: none; padding: 2px 9px; border: 1px solid var(--border-light); border-radius: 999px; color: var(--primary-color); background: var(--el-bg-color); font-size: 12px; font-weight: 600; cursor: pointer; }
.hl-page:disabled { color: var(--text-muted); cursor: default; }
.hl-note { flex: 1; margin: 0; color: var(--text-primary); font-size: 13px; line-height: 1.5; word-break: break-word; }
.hl-note.muted { color: var(--text-muted); font-style: italic; }
.hl-time { flex: none; color: var(--text-muted); font-size: 11px; }
</style>
