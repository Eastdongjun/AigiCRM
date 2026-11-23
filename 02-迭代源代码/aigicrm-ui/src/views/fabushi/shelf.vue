<template>
  <div class="fabushi-shelf">
    <div class="page-head">
      <div><h1>我的觉醒</h1><p>每日诵读 · 每日随施 · 再读一本</p></div>
      <el-button @click="router.push('/fabushi/library')">去书目浏览</el-button>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.value" type="button" class="tab" :class="{ active: active === t.value }" @click="active = t.value">{{ t.label }}</button>
    </div>

    <!-- D7 觉醒聚合：三板块入口 + 个人当日/累计数据 -->
    <section v-if="active === 'awaken'" class="panel" v-loading="loading">
      <div class="awaken-strip">
        <div><b>{{ awaken.todayRecitations || 0 }}</b><span>今日诵读</span></div>
        <div><b>{{ awaken.todayGifts || 0 }}</b><span>今日随施</span></div>
        <div><b>{{ awaken.rereadingBooks || 0 }}</b><span>再读一本</span></div>
        <div><b>{{ awaken.finishedThisMonth || 0 }}</b><span>本月读完</span></div>
        <div><b>{{ awaken.totalReadDays || 0 }}</b><span>累计阅读天</span></div>
      </div>
      <div class="board-grid">
        <article class="board-card songdu">
          <div class="board-icon">诵</div>
          <div class="board-body"><h3>每日诵读</h3><p>每天朗读背诵一小段经典，可传文字/图片后语音诵读。</p></div>
          <el-button type="primary" round size="small" @click="openPublish('songdu')">今日诵读</el-button>
        </article>
        <article class="board-card suishi">
          <div class="board-icon">施</div>
          <div class="board-body"><h3>每日随施</h3><p>随时上传任何正能量；免标题、免关联书目，像朋友圈一样简单。</p></div>
          <el-button type="primary" round size="small" @click="openPublish('suishi')">随手施</el-button>
        </article>
        <article class="board-card zaidu">
          <div class="board-icon">读</div>
          <div class="board-body"><h3>再读一本</h3><p>按章节精读同一本书，累计进度，形成自己的书摘与觉醒记录。</p></div>
          <el-button type="primary" round size="small" @click="openPublish('zaidu')">继续精读</el-button>
        </article>
      </div>
      <!-- 【PROTO-AUDIT-20261123 P9】原型「我的本月（诵读/随施/再读）」卡 + D23 我的书摘入口 -->
      <div class="month-card">
        <div class="mc-head">
          <b>我的本月</b>
          <span class="mc-hint">{{ currentMonthLabel }} · 共 {{ personal.monthPosts || 0 }} 条</span>
        </div>
        <div class="mc-cells">
          <div><b>{{ personal.monthSongdu || 0 }}</b><span>诵读</span></div>
          <div><b>{{ personal.monthSuishi || 0 }}</b><span>随施</span></div>
          <div><b>{{ personal.monthZaidu || 0 }}</b><span>再读</span></div>
        </div>
        <el-button size="small" @click="router.push('/fabushi/highlights')">我的书摘（{{ highlightCount }}）</el-button>
      </div>
      <div class="awaken-links">
        <el-button link type="primary" @click="active = 'shelf'">进入我的书架 ›</el-button>
        <el-button link type="primary" @click="active = 'records'">查看阅读记录 ›</el-button>
        <el-button link type="primary" @click="active = 'settings'">提醒设置 ›</el-button>
      </div>
    </section>

    <!-- D20/D23 书架管理 -->
    <section v-else-if="active === 'shelf'" class="panel" v-loading="loading">
      <div class="continue-card" v-if="summary.currentBook && summary.currentBook.bookId">
        <div class="continue-cover">{{ summary.currentBook.cover }}</div>
        <div class="continue-main">
          <span class="continue-kicker">继续阅读 · {{ shelfStatusLabel(summary.currentBook.status) }}</span>
          <h3>{{ summary.currentBook.title }}</h3>
          <p>{{ summary.currentBook.author || '—' }} · 读到第 {{ summary.currentBook.lastPage }} 页<template v-if="summary.currentBook.totalPages"> / {{ summary.currentBook.totalPages }} 页</template></p>
          <div class="progress"><i :style="{ width: progressPct(summary.currentBook) + '%' }" /></div>
        </div>
        <el-button v-if="summary.currentBook.pdfFileId" type="primary" @click="router.push(`/fabushi/read/${summary.currentBook.bookId}`)">一键续读</el-button>
      </div>

      <div class="shelf-toolbar">
        <div class="chips">
          <button v-for="s in SHELF_STATUS_OPTIONS" :key="s.value" type="button" class="chip" :class="{ active: statusFilter === s.value }" @click="statusFilter = s.value">{{ s.label }}</button>
        </div>
        <el-select v-model="groupFilter" style="width: 150px" placeholder="全部分组">
          <el-option label="全部分组" value="all" />
          <el-option v-for="g in groupOptions" :key="g.key" :label="g.label" :value="g.key" />
        </el-select>
        <el-select v-model="sortMode" style="width: 130px">
          <el-option v-for="s in SHELF_SORT_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="keyword" placeholder="书架内搜索" style="width: 160px" clearable />
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="grid">封面</el-radio-button>
          <el-radio-button value="list">列表</el-radio-button>
        </el-radio-group>
        <el-button size="small" :type="manageMode ? 'primary' : 'default'" @click="toggleManage">{{ manageMode ? '完成管理' : '管理' }}</el-button>
      </div>

      <div class="group-bar" v-if="groupOptions.length">
        <span class="group-label">分组</span>
        <button v-for="g in groupOptions" :key="g.key" type="button" class="group-chip" :class="{ active: groupFilter === g.key }" @click="groupFilter = g.key">
          {{ g.label }} <small>{{ g.count }}</small>
        </button>
        <el-button link type="primary" size="small" @click="groupDialogVisible = true">管理分组</el-button>
      </div>

      <div v-if="!visibleShelf.length" class="empty">当前筛选下没有书架记录，去书目浏览加入一本吧。</div>

      <div v-else :class="viewMode === 'grid' ? 'shelf-grid' : 'shelf-list'">
        <article v-for="book in pagedShelf" :key="book.id" class="shelf-card" :class="{ selected: selected.includes(book.id) }" @click="onCardClick(book)">
          <div class="cover-wrap">
            <div class="cover">{{ book.cover }}</div>
            <span v-if="book.pinned" class="pin">★</span>
            <span v-if="book.status === 'finished'" class="done">✓</span>
            <span v-else-if="book.progressPct === 0 && book.status === 'wish'" class="new">NEW</span>
            <el-checkbox v-if="manageMode" :model-value="selected.includes(book.id)" class="pick" @click.stop @change="() => toggleSelected(book.id)" />
          </div>
          <div class="shelf-main">
            <div class="shelf-title">{{ book.title }}</div>
            <div class="shelf-meta">{{ book.author || '—' }}<template v-if="book.pages"> · {{ book.pages }} 页</template><template v-if="book.lastPage"> · {{ book.lastPage }} 页</template></div>
            <div class="progress"><i :style="{ width: book.progressPct + '%' }" /></div>
            <div class="card-foot">
              <el-select :model-value="book.status" size="small" style="width: 92px" @click.stop @change="(v) => changeStatus(book, v)">
                <el-option v-for="s in SHELF_STATUS_OPTIONS.filter((x) => x.value !== 'all')" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
              <span class="pct">{{ book.progressPct }}%</span>
            </div>
          </div>
          <div v-if="manageMode" class="card-actions" @click.stop>
            <el-button link size="small" @click="togglePin(book)">{{ book.pinned ? '取消置顶' : '置顶' }}</el-button>
            <el-button link size="small" @click="move(book, -1)">↑</el-button>
            <el-button link size="small" @click="move(book, 1)">↓</el-button>
            <el-button link type="danger" size="small" @click="remove(book)">移出</el-button>
          </div>
          <el-button v-else-if="book.pdfFileId" class="read-btn" type="primary" link size="small" @click.stop="router.push(`/fabushi/read/${book.bookId}`)">续读</el-button>
        </article>
      </div>

      <div v-if="shelfTotalPages > 1" class="inline-pager">
        <span>共 {{ visibleShelf.length }} 本</span>
        <el-pagination v-model:current-page="shelfPage" :page-size="shelfSize" :total="visibleShelf.length" layout="prev, pager, next" small />
      </div>

      <div v-if="manageMode && selected.length" class="batch-bar">
        <span>已选 {{ selected.length }} 本</span>
        <el-button size="small" @click="batch('pin')">置顶</el-button>
        <el-button size="small" @click="batch('unpin')">取消置顶</el-button>
        <el-select v-model="batchGroupId" placeholder="加入分组" size="small" style="width: 140px">
          <el-option v-for="g in customGroups" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
        <el-button size="small" @click="batch('group')">加入分组</el-button>
        <el-button size="small" type="danger" @click="batch('remove')">移出书架（保留进度）</el-button>
        <el-button size="small" @click="selected = []">取消</el-button>
      </div>
    </section>

    <section v-else-if="active === 'records'" class="panel">
      <div class="records-head">
        <span>阅读记录</span>
        <el-input v-model="month" placeholder="YYYY-MM" style="width: 130px" @change="loadRecords" />
      </div>
      <div class="record-stats"><span>本月阅读 <b>{{ records.readDays || 0 }}</b> 天</span><span>阅读 <b>{{ records.readCount || 0 }}</b> 次</span></div>
      <div class="record-list">
        <div v-for="day in pagedRecordDays" :key="day.date" class="record-row"><span>{{ day.date }}</span><b>{{ day.count }} 次</b></div>
        <div v-if="!(records.days || []).length" class="empty">本月暂无阅读记录</div>
        <div v-if="recordTotalPages > 1" class="inline-pager">
          <el-pagination v-model:current-page="recordPage" :page-size="recordSize" :total="(records.days || []).length" layout="prev, pager, next" small />
        </div>
      </div>
    </section>

    <section v-else class="panel">
      <div class="settings-head">阅读提醒</div>
      <el-form label-width="120px" class="settings-form">
        <el-form-item label="缺卡提醒"><el-switch v-model="settings.reminderEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="频率">
          <el-select v-model="settings.freqCode" style="width: 180px">
            <el-option label="每天" value="daily" /><el-option label="工作日" value="weekday" /><el-option label="休息日" value="weekend" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒时间"><el-time-picker v-model="settings.remindTime" format="HH:mm" value-format="HH:mm" placeholder="20:00" /></el-form-item>
        <el-form-item label="木鱼音效"><el-switch v-model="settings.soundEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="saving" @click="saveSettings">保存设置</el-button></el-form-item>
      </el-form>
      <p class="hint">微信订阅消息授权与降级策略为小程序端能力；PC 仅保存个人提醒设置。</p>
    </section>

    <el-dialog v-model="groupDialogVisible" title="书架分组管理" width="460px">
      <div class="group-manage-list">
        <div v-for="g in customGroups" :key="g.id" class="group-manage-row">
          <span>{{ g.name }}（{{ g.bookCount || 0 }} 本）</span>
          <span>
            <el-button link size="small" @click="renameGroup(g)">改名</el-button>
            <el-button link type="danger" size="small" @click="removeGroup(g)">删除</el-button>
          </span>
        </div>
        <div v-if="!customGroups.length" class="empty">还没有自建分组</div>
      </div>
      <div class="group-create">
        <el-input v-model="newGroupName" placeholder="新建分组名（如：案头常读）" maxlength="64" />
        <el-button type="primary" @click="createGroup">新建</el-button>
      </div>
      <template #footer><el-button @click="groupDialogVisible = false">关闭</el-button></template>
    </el-dialog>
    <!-- 【P9B 2026-11-23】书摘已迁到独立页 /fabushi/highlights，此处弹窗下架 -->
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getFabushiShelf, getFabushiAwakenStatus, updateFabushiShelf, batchFabushiShelf,
  removeFabushiShelf, getFabushiRecords, getFabushiSettings, saveFabushiSettings,
  getFabushiShelfGroups, createFabushiShelfGroup, renameFabushiShelfGroup, deleteFabushiShelfGroup,
  getFabushiPersonalStats, getMyFabushiBookmarks,
} from '@/api/fabushi'
import {
  SHELF_STATUS_OPTIONS, SHELF_SORT_OPTIONS, shelfStatusLabel, progressPct, filterShelf, sortShelf,
} from '@/utils/fabushi-shelf'
import { usePagedList } from '@/composables/usePagedList'

const router = useRouter()
const tabs = [
  { value: 'awaken', label: '觉醒总览' },
  { value: 'shelf', label: '我的书架' },
  { value: 'records', label: '阅读记录' },
  { value: 'settings', label: '提醒设置' }
]
const active = ref('awaken')
const loading = ref(false)
const saving = ref(false)
const shelf = ref([])
const groups = ref([])
const summary = ref({ total: 0, currentBook: null })
const awaken = ref({ todayRecitations: 0, todayGifts: 0, rereadingBooks: 0, finishedThisMonth: 0, totalReadDays: 0 })
// 【PROTO-AUDIT-20261123 P9】我的本月（三类型拆分）+ 我的书摘计数
const personal = ref({ monthSongdu: 0, monthSuishi: 0, monthZaidu: 0, monthPosts: 0 })
/** 【P9B 2026-11-23】只取条数用于按钮徽标，书摘列表在独立页 /fabushi/highlights */
const highlightCount = ref(0)
const currentMonthLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月`
})
const loadHighlightCount = async () => {
  try {
    const { data } = await getMyFabushiBookmarks()
    highlightCount.value = (data || []).length
  } catch (e) {
    highlightCount.value = 0
  }
}
const month = ref(new Date().toISOString().slice(0, 7))
const records = ref({ days: [], readDays: 0, readCount: 0 })
const settings = reactive({ reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 })
const statusFilter = ref('all')
const groupFilter = ref('all')
const sortMode = ref('recent')
const keyword = ref('')
const viewMode = ref('grid')
const manageMode = ref(false)
const selected = ref([])
const batchGroupId = ref(null)
const groupDialogVisible = ref(false)
const newGroupName = ref('')

const groupOptions = computed(() => {
  const all = [{ key: 'all', value: 'all', label: '全部分组', type: 'all', count: shelf.value.length }]
  return all.concat(groups.value.map((g) => ({
    key: g.id,
    value: g.id,
    label: g.name,
    type: g.groupType,
    categoryCode: g.categoryCode,
    count: g.bookCount || 0
  })))
})
const customGroups = computed(() => groups.value.filter((g) => g.groupType === 'custom'))
const currentGroup = computed(() => groupOptions.value.find((g) => String(g.key) === String(groupFilter.value)) || null)
const visibleShelf = computed(() => sortShelf(
  filterShelf(shelf.value, { status: statusFilter.value, keyword: keyword.value, group: currentGroup.value }),
  sortMode.value
))
const { current: shelfPage, size: shelfSize, totalPages: shelfTotalPages, paged: pagedShelf, reset: resetShelfPage } = usePagedList(visibleShelf, { pageSize: 12 })
const recordDays = computed(() => records.value.days || [])
const { current: recordPage, size: recordSize, totalPages: recordTotalPages, paged: pagedRecordDays } = usePagedList(recordDays, { pageSize: 10 })
watch([statusFilter, groupFilter, keyword, sortMode, viewMode], resetShelfPage)

const loadShelf = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiShelf()
    shelf.value = (data && data.records) || []
    groups.value = (data && data.groups) || []
    summary.value = (data && data.summary) || { total: 0, currentBook: null }
  } finally { loading.value = false }
}
const loadAwaken = async () => {
  const { data } = await getFabushiAwakenStatus()
  awaken.value = data || {}
}
const loadPersonal = async () => {
  try {
    const { data } = await getFabushiPersonalStats()
    personal.value = data || { monthSongdu: 0, monthSuishi: 0, monthZaidu: 0, monthPosts: 0 }
  } catch (e) {
    personal.value = { monthSongdu: 0, monthSuishi: 0, monthZaidu: 0, monthPosts: 0 }
  }
}
const loadRecords = async () => {
  const { data } = await getFabushiRecords(month.value)
  records.value = data || { days: [], readDays: 0, readCount: 0 }
}
const loadSettings = async () => {
  const { data } = await getFabushiSettings()
  Object.assign(settings, data || {})
}
const loadGroups = async () => {
  const { data } = await getFabushiShelfGroups()
  groups.value = data || []
}

const openPublish = (scope) => router.push(`/fabushi/feed?scope=${scope}`)
const toggleManage = () => { manageMode.value = !manageMode.value; selected.value = [] }
const onCardClick = (book) => {
  if (manageMode.value) toggleSelected(book.id)
  else if (book.pdfFileId) router.push(`/fabushi/read/${book.bookId}`)
}
const toggleSelected = (id) => {
  selected.value = selected.value.includes(id) ? selected.value.filter((x) => x !== id) : [...selected.value, id]
}
const changeStatus = async (book, status) => {
  await updateFabushiShelf(book.id, { status })
  book.status = status
  ElMessage.success('已更新阅读状态')
}
const togglePin = async (book) => {
  await updateFabushiShelf(book.id, { pinned: book.pinned ? 0 : 1 })
  book.pinned = book.pinned ? 0 : 1
  ElMessage.success(book.pinned ? '已置顶' : '已取消置顶')
}
const move = async (book, delta) => {
  const ids = visibleShelf.value.map((x) => x.id)
  const index = ids.indexOf(book.id)
  const target = index + delta
  if (target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target], ids[index]]
  await batchFabushiShelf({ ids, action: 'reorder' })
  await loadShelf()
}
const batch = async (action) => {
  await batchFabushiShelf({ ids: selected.value, action, groupId: action === 'group' ? batchGroupId.value : undefined })
  selected.value = []
  ElMessage.success('批量操作完成')
  await loadShelf()
}
const remove = async (book) => {
  await removeFabushiShelf(book.id)
  ElMessage.success('已移出书架，阅读进度与上传内容保留')
  await loadShelf()
}
const createGroup = async () => {
  if (!newGroupName.value.trim()) { ElMessage.warning('请输入分组名'); return }
  await createFabushiShelfGroup(newGroupName.value.trim())
  newGroupName.value = ''
  await Promise.all([loadGroups(), loadShelf()])
}
const renameGroup = async (g) => {
  try {
    const { value } = await ElMessageBox.prompt('新分组名', '重命名分组', { inputValue: g.name, inputValidator: (v) => Boolean(v && v.trim()) || '分组名不能为空' })
    await renameFabushiShelfGroup(g.id, value.trim())
    await Promise.all([loadGroups(), loadShelf()])
  } catch (e) { if (e !== 'cancel' && e !== 'close') throw e }
}
const removeGroup = async (g) => {
  try {
    await ElMessageBox.confirm(`删除「${g.name}」？书架中的书会回到未分组，阅读进度不受影响。`, '确认删除', { type: 'warning' })
    await deleteFabushiShelfGroup(g.id)
    await Promise.all([loadGroups(), loadShelf()])
  } catch (e) { if (e !== 'cancel' && e !== 'close') throw e }
}
const saveSettings = async () => {
  saving.value = true
  try { await saveFabushiSettings({ ...settings }); ElMessage.success('设置已保存') } finally { saving.value = false }
}

onMounted(async () => {
  await Promise.all([loadShelf(), loadAwaken(), loadRecords(), loadSettings(), loadPersonal(), loadHighlightCount()])
})
</script>

<style scoped>
.fabushi-shelf { display: flex; flex-direction: column; gap: 10px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; }
.page-head h1 { margin: 0; color: var(--text-primary); font-size: 20px; font-weight: 800; }
.page-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; }
.tabs { display: flex; gap: 4px; padding: 4px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.tab { flex: 1; padding: 8px; border: 0; border-radius: var(--radius-sm); color: var(--text-secondary); background: transparent; font-size: 13px; font-weight: 600; cursor: pointer; }
.tab.active { color: var(--el-color-white); background: var(--hero-gradient); }
.panel { padding: 14px 16px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-bg-color); }
.awaken-strip { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; padding-bottom: 14px; border-bottom: 1px solid var(--border-light); }
.awaken-strip div { text-align: center; }
.awaken-strip b { display: block; color: var(--primary-color); font-size: 22px; }
.awaken-strip span { color: var(--text-muted); font-size: 11px; }
.board-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-top: 14px; }
.board-card { display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px solid var(--border-light); border-radius: var(--radius-md); background: var(--el-fill-color-lighter); }
.board-icon { display: flex; align-items: center; justify-content: center; width: 42px; height: 42px; flex: none; border-radius: 14px; color: #fff; font-size: 18px; font-weight: 800; }
.board-card.songdu .board-icon { background: linear-gradient(135deg, #d98b57, #b5543a); }
.board-card.suishi .board-icon { background: linear-gradient(135deg, #6fa7a0, #3d7772); }
.board-card.zaidu .board-icon { background: linear-gradient(135deg, #8d7fc7, #5a4c9b); }
.board-body { flex: 1; min-width: 0; }
.board-body h3 { margin: 0; color: var(--text-primary); font-size: 14px; }
.board-body p { margin: 5px 0 0; color: var(--text-muted); font-size: 11px; line-height: 1.5; }
.month-card { margin: 14px 0 10px; padding: 12px 14px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.mc-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px; }
.mc-head b { font-size: 14px; }
.mc-hint { font-size: 12px; color: var(--text-muted); }
.mc-cells { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 10px; }
.mc-cells div { text-align: center; }
.mc-cells b { display: block; font-size: 20px; color: var(--primary-color); }
.mc-cells span { font-size: 11px; color: var(--text-muted); }
.awaken-links { display: flex; gap: 12px; margin-top: 14px; }
.continue-card { display: flex; align-items: center; gap: 14px; padding: 12px; margin-bottom: 12px; border-radius: var(--radius-md); background: var(--hero-gradient); color: #fff; }
.continue-cover { display: flex; align-items: center; justify-content: center; width: 48px; height: 64px; flex: none; border-radius: 6px; background: rgba(255,255,255,.22); font-size: 11px; }
.continue-main { flex: 1; }
.continue-kicker { font-size: 11px; opacity: .8; }
.continue-card h3 { margin: 4px 0; color: #fff; font-size: 15px; }
.continue-card p { margin: 0 0 8px; font-size: 11px; opacity: .85; }
.continue-card .progress { background: rgba(255,255,255,.25); }
.continue-card .progress i { background: #fff; }
.shelf-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 10px; }
.chips { display: flex; gap: 4px; flex-wrap: wrap; }
.chip, .group-chip { padding: 5px 10px; border: 1px solid var(--border-light); border-radius: var(--radius-pill); color: var(--text-secondary); background: transparent; font-size: 11px; cursor: pointer; }
.chip.active, .group-chip.active { border-color: var(--primary-color); color: var(--primary-color); background: var(--el-color-primary-light-9); }
.group-bar { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 8px 0; border-top: 1px solid var(--border-light); }
.group-label { color: var(--text-muted); font-size: 11px; }
.group-chip small { opacity: .6; }
.shelf-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 10px; }
.shelf-list { display: flex; flex-direction: column; gap: 8px; }
.inline-pager { display: flex; align-items: center; justify-content: space-between; padding: 10px 2px; color: var(--text-muted); font-size: 11px; }
.shelf-card { position: relative; display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid var(--border-light); border-radius: var(--radius-md); cursor: pointer; }
.shelf-card:hover { border-color: var(--primary-color); }
.shelf-card.selected { border-color: var(--primary-color); box-shadow: 0 0 0 2px var(--el-color-primary-light-8); }
.cover-wrap { position: relative; flex: none; }
.cover { display: flex; align-items: center; justify-content: center; width: 44px; height: 58px; padding: 4px; border-radius: var(--radius-sm); color: #fff; background: var(--hero-gradient); font-size: 10px; text-align: center; }
.pin, .done, .new { position: absolute; top: -6px; right: -6px; padding: 1px 4px; border-radius: 8px; color: #fff; background: #c88931; font-size: 9px; }
.done { background: #3f8f6f; }
.new { background: #b5543a; }
.pick { position: absolute; bottom: -6px; right: -6px; }
.shelf-main { flex: 1; min-width: 0; }
.shelf-title { overflow: hidden; color: var(--text-primary); font-size: 13px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.shelf-meta { margin-top: 4px; color: var(--text-muted); font-size: 11px; }
.progress { height: 4px; margin-top: 7px; border-radius: var(--radius-pill); background: var(--el-fill-color-light); overflow: hidden; }
.progress i { display: block; height: 100%; background: var(--primary-color); }
.card-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 7px; }
.pct { color: var(--text-muted); font-size: 11px; }
.card-actions { display: flex; flex-wrap: wrap; gap: 2px; max-width: 130px; }
.batch-bar { position: sticky; bottom: 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 12px; padding: 10px; border-radius: var(--radius-md); background: var(--el-fill-color-light); }
.records-head { display: flex; justify-content: space-between; align-items: center; color: var(--text-primary); font-size: 13px; font-weight: 700; }
.record-stats { display: flex; gap: 24px; margin-top: 12px; color: var(--text-muted); font-size: 12px; }
.record-stats b { color: var(--primary-color); font-size: 18px; }
.record-list { margin-top: 10px; border-top: 1px solid var(--border-light); }
.record-row { display: flex; justify-content: space-between; padding: 8px 0; color: var(--text-secondary); font-size: 12px; border-bottom: 1px solid var(--border-light); }
.settings-head { color: var(--text-primary); font-size: 13px; font-weight: 700; margin-bottom: 12px; }
.settings-form { max-width: 480px; }
.hint { color: var(--text-placeholder); font-size: 11px; }
.group-manage-list { max-height: 220px; overflow: auto; }
.group-manage-row { display: flex; justify-content: space-between; padding: 6px 0; color: var(--text-secondary); font-size: 12px; }
.group-create { display: flex; gap: 8px; margin-top: 12px; }
.empty { padding: 24px 0; color: var(--text-placeholder); text-align: center; font-size: 12px; }
@media (max-width: 900px) {
  .awaken-strip { grid-template-columns: repeat(3, 1fr); }
  .board-grid { grid-template-columns: 1fr; }
}
</style>
