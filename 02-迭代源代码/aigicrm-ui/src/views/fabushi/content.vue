<template>
  <div class="fabushi-content">
    <div class="caps">
      <button v-for="tab in tabs" :key="tab.value" type="button" class="cap" :class="{ active: active === tab.value }" @click="switchTab(tab.value)">
        {{ tab.label }} <span class="n">{{ counts[tab.value] || 0 }}</span>
      </button>
    </div>

    <div class="filter">
      <el-input v-model="keyword" clearable placeholder="搜标题 / 正文 / 发布人" style="width: 240px" @keyup.enter="resetPage" />
      <el-button type="primary" @click="resetPage">查询</el-button>
      <span class="spacer" />
      <span class="filter-hint">下架/恢复需 fabushi:content:manage</span>
    </div>

    <section class="table-panel" v-loading="loading">
      <table class="data-table">
        <thead>
          <tr><th>作品 / 正文摘要</th><th>类型</th><th>关联书目</th><th>作者</th><th>互动</th><th>复诵</th><th>状态</th><th>发布时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in filteredPosts" :key="row.id">
            <td class="title-cell" @click="openDetail(row)">{{ row.title || (row.content || '').slice(0, 42) }}</td>
            <td><span class="type-tag" :class="row.scopeType">{{ scopeText(row.scopeType) }}</span></td>
            <td>{{ row.bookTitle || '—' }}</td>
            <td>{{ row.userName || '—' }}</td>
            <td class="num">木鱼 {{ row.likeCount || 0 }} · 评论 {{ row.commentCount || 0 }}</td>
            <td class="num">{{ row.recitationCount || 0 }} 次</td>
            <td><span class="status" :class="row.status">{{ row.status === 'offline' ? '已下架' : '发布中' }}</span></td>
            <td>{{ formatTime(row.createdAt) }}</td>
            <td class="actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.status === 'published' && canManage" link type="danger" @click="openOffline(row)">下架</el-button>
              <el-button v-if="row.status === 'offline' && canManage" link type="primary" @click="doRestore(row)">恢复</el-button>
              <el-button v-if="active === 'deleted' && canManage" link type="primary" @click="doRestoreDeleted(row)">恢复已删</el-button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="!loading && !filteredPosts.length" class="empty">当前分类暂无内容</div>
      <div class="pager">
        <span>共 {{ total }} 条</span>
        <el-pagination v-model:current-page="current" :page-size="size" :total="total" layout="prev, pager, next" @current-change="loadData" />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="内容详情" width="620px">
      <div v-if="detailRow" class="detail-body">
        <div class="detail-head">
          <span class="type-tag" :class="detailRow.scopeType">{{ scopeText(detailRow.scopeType) }}</span>
          <b>{{ detailRow.title || '无标题' }}</b>
          <span class="status" :class="detailRow.status">{{ detailRow.status === 'offline' ? '已下架' : '发布中' }}</span>
        </div>
        <div class="detail-meta">{{ detailRow.userName || '—' }} · {{ formatTime(detailRow.createdAt) }}<template v-if="detailRow.bookTitle"> · {{ detailRow.bookTitle }}</template></div>
        <div class="detail-text">{{ detailRow.content }}</div>
        <div class="detail-meta">木鱼 {{ detailRow.likeCount || 0 }} · 评论 {{ detailRow.commentCount || 0 }}</div>
        <div v-if="detailRow.status === 'offline'" class="offline-note">下架原因：{{ detailRow.offlineReason || '—' }}</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detailRow?.status === 'published' && canManage" type="danger" @click="openOffline(detailRow)">下架该作品</el-button>
        <el-button v-if="detailRow?.status === 'offline' && canManage" type="primary" @click="doRestore(detailRow)">恢复该作品</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="offlineVisible" title="下架内容 · 需留痕" width="460px">
      <p class="dialog-tip">作品：{{ offlineRow?.title || '无标题' }} · {{ offlineRow?.userName }} · {{ formatTime(offlineRow?.createdAt) }}</p>
      <el-input v-model="offlineReason" type="textarea" :rows="4" placeholder="下架原因（必填，操作全量留痕）" />
      <template #footer>
        <el-button @click="offlineVisible = false">取消</el-button>
        <el-button type="danger" :loading="actionLoading" @click="doOffline">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getFabushiPosts, offlineFabushiPost, restoreFabushiPost, getDeletedFabushiPosts, restoreDeletedFabushiPost } from '@/api/fabushi'

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('fabushi:content:manage'))
const tabs = [
  { value: 'songdu', label: '每日诵读', scope: 'songdu', status: 'published' },
  { value: 'suishi', label: '每日随施', scope: 'suishi', status: 'published' },
  { value: 'zaidu', label: '再读一本', scope: 'zaidu', status: 'published' },
  { value: 'offline', label: '已下架', scope: '', status: 'offline' },
  { value: 'deleted', label: '已删除', scope: '', status: 'deleted' },
]
const active = ref('songdu')
const counts = reactive({ songdu: 0, suishi: 0, zaidu: 0, offline: 0, deleted: 0 })
const posts = ref([])
const total = ref(0)
const current = ref(1)
const size = 10
const loading = ref(false)
const keyword = ref('')
const detailVisible = ref(false)
const detailRow = ref(null)
const offlineVisible = ref(false)
const offlineRow = ref(null)
const offlineReason = ref('')
const actionLoading = ref(false)

const currentTab = computed(() => tabs.find((tab) => tab.value === active.value) || tabs[0])
const filteredPosts = computed(() => posts.value.filter((row) => {
  if (!keyword.value) return true
  const text = `${row.title || ''} ${row.content || ''} ${row.userName || ''}`.toLowerCase()
  return text.includes(keyword.value.toLowerCase())
}))
const scopeText = (value) => ({ songdu: '每日诵读', suishi: '每日随施', zaidu: '再读一本' }[value] || '内容')
const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')

const loadCounts = async () => {
  const results = await Promise.all(tabs.map((tab) => getFabushiPosts({ scopeType: tab.scope || undefined, status: tab.status, current: 1, size: 1 })))
  tabs.forEach((tab, index) => { counts[tab.value] = Number(results[index]?.data?.total) || 0 })
}

const loadData = async () => {
  loading.value = true
  try {
    const tab = currentTab.value
    if (tab.value === 'deleted') {
      // 【D13 计划切片 2】已删除帖走专用端点（逻辑删滤除常规查询）
      const { data } = await getDeletedFabushiPosts({ current: current.value, size })
      posts.value = (data && data.records) || []
      total.value = Number(data && data.total) || 0
      return
    }
    const { data } = await getFabushiPosts({
      scopeType: tab.scope || undefined,
      status: tab.status,
      current: current.value,
      size,
    })
    posts.value = (data && data.records) || []
    total.value = Number(data && data.total) || 0
  } finally {
    loading.value = false
  }
}

const switchTab = (value) => {
  active.value = value
  current.value = 1
  keyword.value = ''
  loadData()
}

const resetPage = () => { current.value = 1; loadData() }
const openDetail = (row) => { detailRow.value = row; detailVisible.value = true }
const openOffline = (row) => { offlineRow.value = row; offlineReason.value = ''; offlineVisible.value = true }

const doOffline = async () => {
  if (!offlineReason.value.trim()) { ElMessage.warning('下架原因不能为空'); return }
  actionLoading.value = true
  try {
    await offlineFabushiPost(offlineRow.value.id, offlineReason.value.trim())
    ElMessage.success('已下架（留痕）')
    offlineVisible.value = false
    detailVisible.value = false
    await Promise.all([loadData(), loadCounts()])
  } finally { actionLoading.value = false }
}

const doRestoreDeleted = async (row) => {
  actionLoading.value = true
  try {
    await restoreDeletedFabushiPost(row.id)
    ElMessage.success('已恢复为发布中')
    await Promise.all([loadData(), loadCounts()])
  } finally { actionLoading.value = false }
}

const doRestore = async (row) => {
  actionLoading.value = true
  try {
    await restoreFabushiPost(row.id)
    ElMessage.success('已恢复')
    detailVisible.value = false
    await Promise.all([loadData(), loadCounts()])
  } finally { actionLoading.value = false }
}

onMounted(async () => { await Promise.all([loadData(), loadCounts()]) })
</script>

<style scoped>
.fabushi-content { display: flex; flex-direction: column; gap: 10px; }
.caps { display: flex; gap: 4px; padding: 4px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.cap { flex: 1; padding: 8px 6px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 13px; font-weight: 600; cursor: pointer; }
.cap.active { color: var(--el-color-white); background: var(--hero-gradient); box-shadow: var(--shadow-card-hover); }
.cap .n { padding: 1px 6px; border-radius: var(--radius-pill); background: var(--el-fill-color-darker); font-size: 10px; }
.cap.active .n { background: rgba(255, 255, 255, .25); }
.filter { display: flex; align-items: center; gap: 9px; padding: 9px 12px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.spacer { flex: 1; }
.filter-hint { color: var(--text-placeholder); font-size: 11px; }
.table-panel { border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { padding: 10px 12px; color: var(--text-placeholder); background: var(--el-fill-color-lighter); font-size: 11px; font-weight: 500; text-align: left; }
.data-table td { padding: 10px 12px; color: var(--text-secondary); border-top: 1px solid var(--border-light); }
.title-cell { color: var(--text-primary) !important; font-weight: 600; cursor: pointer; }
.type-tag { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; }
.type-tag.heart { color: var(--primary-color); background: var(--el-color-primary-light-9); }
.type-tag.recitation { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.type-tag.znl { color: var(--el-color-warning-dark-2); background: var(--el-color-warning-light-9); }
.status { padding: 1px 6px; border-radius: var(--radius-sm); font-size: 10px; }
.status.published { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.status.offline { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.actions { white-space: nowrap; }
.num { font-variant-numeric: tabular-nums; }
.empty { padding: 28px; color: var(--text-placeholder); text-align: center; font-size: 12px; }
.pager { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-top: 1px solid var(--border-light); color: var(--text-muted); font-size: 11px; }
.detail-body { display: flex; flex-direction: column; gap: 8px; }
.detail-head { display: flex; align-items: center; gap: 8px; }
.detail-head b { color: var(--text-primary); font-size: 15px; }
.detail-meta { color: var(--text-placeholder); font-size: 11px; }
.detail-text { color: var(--text-secondary); font-size: 13px; line-height: 1.85; white-space: pre-wrap; }
.offline-note { padding: 8px 10px; border-radius: var(--radius-sm); color: var(--el-color-danger); background: var(--el-color-danger-light-9); font-size: 12px; }
.dialog-tip { margin: 0 0 8px; color: var(--text-secondary); font-size: 12px; }
</style>
