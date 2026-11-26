<template>
  <div class="fx-page fabushi-highlights">
    <!-- 【2026-11-26 按 PC 原型 v8】原型里没有这一屏的画稿：只跟着新配色走——搜索行用共享的筛选条，
         按书分组的卡用共享的 .panel。页头标题块撤掉（面包屑里有“我的书摘”），副标题的条数说明挪到筛选条右侧。功能没动 -->
    <div class="filter-card">
      <span class="fl">搜索</span>
      <el-input v-model="keyword" placeholder="搜书名或备注" clearable style="width: 220px" />
      <el-button @click="router.push('/fabushi/shelf')">回我的觉醒</el-button>
      <span class="spacer" />
      <span class="filter-note">阅读器里「加书签」并写一句备注，就会沉淀到这里 · 共 {{ visibleCount }} 条 · {{ groups.length }} 本书</span>
    </div>

    <div v-if="loading" v-loading="true" class="loading-block" />

    <div v-else-if="!items.length" class="empty-state panel">
      <div class="empty-icon">摘</div>
      <h3>还没有书摘</h3>
      <p>打开任意一本电子书，翻到想记的那一页，点「加书签」并写一句备注，这里就会按书归集。</p>
      <el-button type="primary" @click="router.push('/fabushi/library')">去书目浏览</el-button>
    </div>

    <div v-else-if="!groups.length" class="empty-state panel">
      <h3>没有匹配的书摘</h3>
      <p>换个关键词试试，或清空搜索。</p>
      <el-button @click="keyword = ''">清空搜索</el-button>
    </div>

    <div v-else class="group-list">
      <section v-for="g in pagedGroups" :key="g.key" class="hl-group panel">
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

    <div v-if="totalPages > 1" class="hl-pager panel">
      <span>共 {{ groups.length }} 本书</span>
      <el-pagination v-model:current-page="current" :page-size="size" :total="total" layout="prev, pager, next" small />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyFabushiBookmarks, removeFabushiBookmark } from '@/api/fabushi'
import { usePagedList } from '@/composables/usePagedList'
import '@/assets/fabushi-v8.css'

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
const { current, size, total, totalPages, paged: pagedGroups, reset: resetPage } = usePagedList(groups, { pageSize: 6 })
watch(keyword, resetPage)

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
/* 通用件（筛选条 .filter-card、卡片 .panel）在 assets/fabushi-v8.css；这里只写这一页自己的。
   颜色一律用令牌（原先空态图标那块写死的紫色渐变换成了品牌渐变）。 */
.loading-block { min-height: 160px; }
.empty-state { padding: 48px 16px; text-align: center; }
.empty-state .empty-icon { display: inline-flex; align-items: center; justify-content: center; width: 52px; height: 52px; margin-bottom: 10px; border-radius: 18px; color: #fff; background: var(--hero-gradient); font-size: 20px; font-weight: 800; }
.empty-state h3 { margin: 0 0 6px; color: var(--text-primary); font-size: 15px; }
.empty-state p { margin: 0 0 14px; color: var(--text-muted); font-size: 12px; }

.group-list { display: flex; flex-direction: column; gap: 10px; }
.hl-group { overflow: hidden; }
.hl-group-head { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: var(--fx-table-head); cursor: pointer; }
.hl-caret { color: var(--text-muted); font-size: 15px; transition: transform .15s; transform: rotate(0deg); }
.hl-caret.open { transform: rotate(90deg); }
.hl-book-title { color: var(--text-primary); font-size: 14px; }
.hl-count { padding: 1px 7px; border-radius: var(--fx-r-pill); color: var(--fx-pill-gray); background: var(--fx-pill-gray-bg); font-size: 11px; font-variant-numeric: tabular-nums; }
.hl-spacer { flex: 1; }
.hl-items { margin: 0; padding: 4px 14px 10px; list-style: none; }
.hl-item { padding: 8px 0; border-top: 1px solid var(--fx-line-soft); }
.hl-item:first-child { border-top: 0; }
.hl-line { display: flex; align-items: center; gap: 10px; }
.hl-page { flex: none; padding: 2px 9px; border: 1px solid var(--border-light); border-radius: var(--fx-r-pill); color: var(--primary-color); background: var(--el-bg-color); font-size: 12px; font-weight: 600; cursor: pointer; font-family: inherit; }
.hl-page:disabled { color: var(--text-placeholder); cursor: default; }
.hl-note { flex: 1; margin: 0; color: var(--text-primary); font-size: 13px; line-height: 1.5; word-break: break-word; }
.hl-note.muted { color: var(--text-muted); font-style: italic; }
.hl-time { flex: none; color: var(--text-muted); font-size: 11px; }
.hl-pager { display: flex; align-items: center; justify-content: space-between; padding: 9px 16px; color: var(--text-secondary); font-size: 12px; }
</style>
