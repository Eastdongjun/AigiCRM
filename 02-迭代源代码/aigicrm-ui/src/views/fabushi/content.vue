<template>
  <div class="fx-page fabushi-content">
    <!-- 【2026-11-26 按 PC 原型 v8】P11：页面直接摆在灰画布上（没有页头标题块），最上面一行是类别页签 -->
    <div class="cat-tabs" role="tablist" aria-label="内容类型">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        class="cat-tab"
        :class="{ active: active === tab.value }"
        role="tab"
        :aria-selected="active === tab.value"
        @click="switchTab(tab.value)"
      >
        {{ tab.label }}<span class="badge">{{ counts[tab.value] || 0 }}</span>
      </button>
    </div>

    <div class="filter-card">
      <!-- 书目：内容分页接口支持按 bookId 查。“已删除”走专用接口，没有这个入参，所以那一页不出这一项。
           原型筛选条里的“类别”就是上面的页签；作者、日期接口没有入参，不做假筛选。 -->
      <template v-if="active !== 'deleted'">
        <span class="fl">书目</span>
        <el-select v-model="bookFilter" clearable filterable placeholder="全部书目" style="width: 190px" @change="resetPage">
          <el-option v-for="book in bookOptions" :key="book.id" :label="book.title" :value="book.id" />
        </el-select>
      </template>
      <span class="fl">关键词</span>
      <!-- 关键词点“查询”或按回车才生效；服务端按人名 / 书籍 / 具体文字搜。“已删除”接口不收关键词，只在已加载的这一页里筛 -->
      <el-input
        v-model="keyword"
        clearable
        :placeholder="active === 'deleted' ? '本页内筛：标题 / 正文 / 发布人 / 书名' : '搜标题 / 正文 / 发布人'"
        style="width: 240px"
        @keyup.enter="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button plain @click="resetFilters">重置</el-button>
    </div>

    <section class="table-card" v-loading="loading">
      <!-- 每一列都给了宽度（合计不超过卡片宽度 1048px，不出横向滚动条），标签与数字不换行；长标题、长书名省略，悬停看全文 -->
      <el-table
        :data="filteredPosts"
        class="content-table"
        :empty-text="appliedKeyword ? '没有匹配的作品' : '当前分类暂无内容'"
        @cell-click="onCellClick"
      >
        <el-table-column label="作品 / 正文摘要" min-width="200" show-overflow-tooltip class-name="work-cell">
          <template #default="{ row }">
            <span class="work">{{ row.title || (row.content || '').slice(0, 42) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="86">
          <template #default="{ row }"><span class="tag" :class="SCOPE_TAG[row.scopeType]">{{ scopeText(row.scopeType) }}</span></template>
        </el-table-column>
        <el-table-column label="关联书目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ bookText(row) }}</template>
        </el-table-column>
        <el-table-column label="作者" width="84" show-overflow-tooltip>
          <template #default="{ row }">{{ row.userName || '—' }}</template>
        </el-table-column>
        <el-table-column label="互动" width="170">
          <template #default="{ row }">
            <span class="num">木鱼{{ row.likeCount || 0 }} · 评论{{ row.commentCount || 0 }} · 复诵{{ row.recitationCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="76">
          <template #default="{ row }"><span class="tag" :class="statusTag(row)">{{ statusText(row) }}</span></template>
        </el-table-column>
        <el-table-column label="发布时间" width="138">
          <template #default="{ row }"><span class="num">{{ formatTime(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="132">
          <template #default="{ row }">
            <div class="acts">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="active !== 'deleted' && row.status === 'published' && canManage" link type="danger" @click="openOffline(row)">下架</el-button>
              <el-button v-if="row.status === 'offline' && canManage" link type="primary" @click="doRestore(row)">恢复</el-button>
              <el-button v-if="active === 'deleted' && canManage" link type="primary" @click="doRestoreDeleted(row)">恢复已删</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="current"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="作品详情" width="560px" class="fx-dialog">
      <div v-if="detailRow" class="detail-body">
        <div class="detail-head">
          <span class="detail-ava">{{ (detailRow.userName || '法').slice(0, 1) }}</span>
          <div class="detail-main">
            <b>{{ detailRow.userName || '—' }}</b>
            <div class="detail-meta">
              {{ formatTime(detailRow.createdAt) }} · {{ scopeText(detailRow.scopeType) }}<template v-if="detailRow.bookTitle"> · 《{{ detailRow.bookTitle }}》</template><template v-if="detailRow.sectionTitle"> › {{ detailRow.sectionTitle }}</template>
            </div>
          </div>
          <span class="tag" :class="statusTag(detailRow)">{{ statusText(detailRow) }}</span>
        </div>
        <div class="detail-title">{{ detailRow.title || '无标题' }}</div>
        <div class="detail-text">{{ detailRow.content }}</div>
        <div class="detail-inter">木鱼 {{ detailRow.likeCount || 0 }} · 评论 {{ detailRow.commentCount || 0 }} · 复诵 {{ detailRow.recitationCount || 0 }}</div>
        <div v-if="detailRow.status === 'offline'" class="offline-note">下架原因：{{ detailRow.offlineReason || '—' }}</div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <!-- 已删除的作品不能再下架（接口对已删除的帖子只能回 404） -->
          <el-button v-if="active !== 'deleted' && detailRow?.status === 'published' && canManage" type="danger" @click="openOffline(detailRow)">下架该作品</el-button>
          <el-button v-if="detailRow?.status === 'offline' && canManage" type="primary" @click="doRestore(detailRow)">恢复该作品</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="offlineVisible" title="下架内容 · 需留痕" width="460px" class="fx-dialog">
      <p class="dialog-tip">作品：{{ offlineRow?.title || '无标题' }} · {{ offlineRow?.userName }} · {{ formatTime(offlineRow?.createdAt) }}</p>
      <el-input v-model="offlineReason" type="textarea" :rows="4" placeholder="下架原因（必填，操作全量留痕）" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="offlineVisible = false">取消</el-button>
          <el-button type="danger" :loading="actionLoading" @click="doOffline">确认下架</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getFabushiPosts, offlineFabushiPost, restoreFabushiPost, getDeletedFabushiPosts, restoreDeletedFabushiPost, getFabushiBooks } from '@/api/fabushi'
import '@/assets/fabushi-v8.css'

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
// 关键词：输入框里的是 keyword，点了“查询”或按回车才生效的那一份是 appliedKeyword（翻页、换书目都带着它）
const keyword = ref('')
const appliedKeyword = ref('')
// 【P11 2026-11-26】书目筛选（内容分页接口的 bookId 入参）
const bookFilter = ref(null)
const bookOptions = ref([])
const detailVisible = ref(false)
const detailRow = ref(null)
const offlineVisible = ref(false)
const offlineRow = ref(null)
const offlineReason = ref('')
const actionLoading = ref(false)

const currentTab = computed(() => tabs.find((tab) => tab.value === active.value) || tabs[0])
// 除“已删除”外的页签由服务端按关键词搜过了，接口回什么就显示什么，不在本地再筛一遍
// （本地只认标题 / 正文 / 发布人，会把按书名命中的行筛掉）；“已删除”接口不收关键词，只能在已加载的这一页里按
// 标题 / 正文 / 发布人 / 书名筛
const filteredPosts = computed(() => {
  const word = appliedKeyword.value.toLowerCase()
  if (active.value !== 'deleted' || !word) return posts.value
  return posts.value.filter((row) => (
    `${row.title || ''} ${row.content || ''} ${row.userName || ''} ${row.bookTitle || ''}`.toLowerCase().includes(word)
  ))
})
const scopeText = (value) => ({ songdu: '每日诵读', suishi: '每日随施', zaidu: '再读一本' }[value] || '内容')
// 类型标签照原型配色：每日诵读蓝、每日随施橙、再读一本绿
const SCOPE_TAG = { songdu: 'blue', suishi: 'orange', zaidu: 'green' }
const formatTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '')
// 状态标签：已删除的帖子行里 status 还是删除前的旧值，按所在页签照实写“已删除”
const statusTag = (row) => {
  if (active.value === 'deleted') return 'gray'
  return row.status === 'offline' ? 'red' : 'green'
}
const statusText = (row) => {
  if (active.value === 'deleted') return '已删除'
  return row.status === 'offline' ? '已下架' : '发布中'
}
/** 关联书目：《书名》 › 章节；随施按规则不关联书目，照实写明。 */
const bookText = (row) => {
  if (row.bookTitle) return `《${row.bookTitle}》${row.sectionTitle ? ` › ${row.sectionTitle}` : ''}`
  return row.scopeType === 'suishi' ? '—（不关联）' : '—'
}

/** 各页签的条数。“已删除”走专用接口（常规分页接口里没有“已删除”这个状态值，从它取恒为 0）；
 *  那个接口要管理权限，没有就不问。各页签各算各的，一个失败只让那一个记 0。 */
const countOf = async (tab) => {
  const params = tab.value === 'deleted'
    ? await getDeletedFabushiPosts({ current: 1, size: 1 })
    : await getFabushiPosts({ scopeType: tab.scope || undefined, status: tab.status, current: 1, size: 1 })
  return Number(params.data && params.data.total) || 0
}

const loadCounts = async () => {
  const results = await Promise.all(tabs.map((tab) => {
    if (tab.value === 'deleted' && !canManage.value) return Promise.resolve(0)
    return countOf(tab).catch(() => 0)
  }))
  tabs.forEach((tab, index) => { counts[tab.value] = results[index] })
}

// 【P11 2026-11-26】书目下拉的选项：与认知广场同一个书目查询（已通过的书目）
const loadBooks = async () => {
  try {
    const { data } = await getFabushiBooks({ status: 'approved', current: 1, size: 200 })
    bookOptions.value = (data && data.records) || []
  } catch (e) {
    bookOptions.value = []
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const tab = currentTab.value
    if (tab.value === 'deleted') {
      // 【D13 计划切片 2】已删除帖走专用端点（逻辑删滤除常规查询）；这个端点只收分页，不支持按书目筛
      const { data } = await getDeletedFabushiPosts({ current: current.value, size })
      posts.value = (data && data.records) || []
      total.value = Number(data && data.total) || 0
      return
    }
    const { data } = await getFabushiPosts({
      scopeType: tab.scope || undefined,
      status: tab.status,
      bookId: bookFilter.value || undefined,
      keyword: appliedKeyword.value || undefined,
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
  appliedKeyword.value = ''
  bookFilter.value = null
  loadData()
}

const resetPage = () => { current.value = 1; loadData() }
/** “查询”按钮与输入框回车：输入框里的关键词从这里生效 */
const search = () => { appliedKeyword.value = keyword.value.trim(); resetPage() }
const resetFilters = () => { keyword.value = ''; appliedKeyword.value = ''; bookFilter.value = null; resetPage() }
const openDetail = (row) => { detailRow.value = row; detailVisible.value = true }
/** 点“作品 / 正文摘要”这一格的任意位置都能打开详情（原有行为：整格可点）；别的格点了没反应 */
const onCellClick = (row, column, cell) => { if (cell && cell.cellIndex === 0) openDetail(row) }
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

onMounted(async () => { await Promise.all([loadData(), loadCounts(), loadBooks()]) })
</script>

<style scoped>
/* 通用件（页签 .cat-tabs、筛选条 .filter-card、标签 .tag、表格卡 .table-card/.pager、Element Plus 的尺寸、弹窗 .fx-dialog）
   在 assets/fabushi-v8.css；这里只写 P11 自己的版式，颜色一律用令牌。 */

.table-card { flex: 1; }
.content-table { width: 100%; }
/* 作品这一格整格可点（打开详情），标题加粗 */
.content-table :deep(.work-cell) { cursor: pointer; }
.work { color: var(--text-primary); font-weight: 700; }
.content-table :deep(.work-cell:hover .work) { color: var(--primary-color); }
/* 互动、发布时间：数字不换行。inline-block + overflow:hidden 会让所在行高出 7px, 所以贴底对齐 */
.num {
  display: inline-block; max-width: 100%; overflow: hidden; color: var(--text-primary); vertical-align: bottom;
  font-variant-numeric: tabular-nums; text-overflow: ellipsis; white-space: nowrap;
}
.acts { display: flex; flex-wrap: wrap; gap: 0 4px; }
.acts :deep(.el-button + .el-button) { margin-left: 0; }
/* 表格卡比表格高时，分页条留在卡片底部 */
.table-card .pager { margin-top: auto; }

/* 作品详情 */
.detail-body { display: flex; flex-direction: column; gap: 10px; }
.detail-head { display: flex; align-items: center; gap: 10px; }
.detail-ava {
  display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 34px; height: 34px;
  border-radius: 50%; background: var(--hero-gradient); color: var(--el-color-white); font-size: 14px; font-weight: 700;
}
.detail-main { flex: 1; min-width: 0; }
.detail-main b { color: var(--text-primary); font-size: 13.5px; }
.detail-meta { margin-top: 2px; color: var(--text-muted); font-size: 11px; }
.detail-title { color: var(--text-primary); font-size: 14px; font-weight: 700; }
.detail-text { color: var(--text-primary); font-size: 13px; line-height: 1.9; white-space: pre-wrap; word-break: break-word; }
.detail-inter { color: var(--text-primary); font-size: 12.5px; font-weight: 700; font-variant-numeric: tabular-nums; }
.offline-note { padding: 8px 10px; border-radius: var(--radius-sm); color: var(--el-color-danger); background: var(--el-color-danger-light-9); font-size: 12px; }

/* 下架 */
.dialog-tip { margin: 0 0 8px; color: var(--text-secondary); font-size: 12.5px; }
</style>
