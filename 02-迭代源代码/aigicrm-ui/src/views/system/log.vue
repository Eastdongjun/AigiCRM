<template>
  <div class="log-page">
    <!-- 标题 -->
    <div class="page-header">
      <div>
        <h2>日志管理</h2>
        <p class="page-desc">查看系统全模块的操作、登录、文件、人员与系统事件记录</p>
      </div>
    </div>

    <!-- Tab栏 -->
    <div class="tab-row">
      <div class="tabs">
        <span v-for="t in tabList" :key="t.key" class="tab-item" :class="{ active: activeTab === t.key }" @click="switchTab(t.key)">
          {{ t.label }}
          <span class="tab-badge" v-if="t.count > 0">{{ t.count }}</span>
        </span>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <!-- 【工作流复核确认修复】审计 Tab 隐藏日期筛选: 后端审计接口不支持日期参数, 原控件可见但选了毫无变化 -->
        <el-date-picker v-if="activeTab !== '__audit__'" v-model="dateRange" type="daterange" start-placeholder="开始" end-placeholder="结束"
          value-format="YYYY-MM-DD" style="width:240px" size="small" @change="onDateChange" />
        <el-input v-if="activeTab !== '__audit__'" v-model="keyword" placeholder="搜索操作人/对象..." clearable size="small" style="width:180px"
          @keyup.enter="search" @clear="search" />
        <el-select v-if="activeTab === '__audit__'" v-model="auditEventType" placeholder="事件类型" clearable
          size="small" style="width:200px" @change="search">
          <el-option v-for="t in auditEventOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table v-if="activeTab === '__audit__'" :data="tableData" stripe style="width:100%" size="small"
        empty-text="暂无安全审计记录" :height="tableHeight">
        <el-table-column label="时间" min-width="130">
          <template #default="{ row }">
            <span style="color:var(--el-color-primary)">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="eventType" label="事件" min-width="130" />
        <el-table-column label="操作者" min-width="90">
          <template #default="{ row }">{{ row.actorUsername || row.actorId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" min-width="110" />
      </el-table>
      <el-table v-else :data="tableData" stripe style="width:100%" size="small" empty-text="暂无日志记录" :height="tableHeight">
        <el-table-column label="时间" min-width="110">
          <template #default="{ row }">
            <span style="color:var(--el-color-primary)">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="操作人" min-width="75" />
        <el-table-column label="模块类型" min-width="95">
          <template #default="{ row }">
            <span class="module-tag" :class="moduleClass(row.module)">{{ moduleLabel(row.module) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" min-width="65" />
        <el-table-column label="操作对象" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.detail || '—' }}</template>
        </el-table-column>
        <el-table-column label="详情" width="70" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="list-pager">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <!-- 日志列表分页: layout 加入 sizes 后右侧出现每页条数下拉(15/30/50), 用户可按需切换; 默认仍 15 条/页 -->
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
          :page-sizes="pageSizes(query.size)" layout="sizes, prev, pager, next, jumper" size="small" @change="loadData" @size-change="query.current = 1" />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="500px">
      <el-descriptions :column="1" border v-if="detailRow">
        <el-descriptions-item label="时间">{{ detailRow.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detailRow.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ moduleLabel(detailRow.module) }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detailRow.action }}</el-descriptions-item>
        <el-descriptions-item label="详情">{{ detailRow.detail }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detailRow.ip || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getLogList, getAuditLogList } from '@/api/system'
import request from '@/utils/request'
import { pageSizes } from '@/utils/pagination'

const tableData = ref([])
const total = ref(0)
const counts = ref({})
const activeTab = ref('')
const keyword = ref('')
const dateRange = ref(null)
const detailVisible = ref(false)
const detailRow = ref(null)

const query = reactive({ current: 1, size: 15 })
const AUDIT_TAB = '__audit__'
const auditEventType = ref('')
const auditEventOptions = [
  'LOGIN_FAILED', 'LOGIN_SUCCESS', 'ANON_PROBE', 'ACCESS_DENIED',
  'PERMISSION_CHANGE', 'GROUP_SWITCH', 'SECURITY_ALERT', 'USER_ADMIN',
]

const tabList = computed(() => [
  { key: '', label: '全部日志', count: counts.value['全部'] || 0 },
  { key: '客户', label: '操作日志', count: counts.value['客户'] || 0 },
  { key: '登录', label: '登录日志', count: counts.value['登录'] || 0 },
  { key: '文件', label: '文件日志', count: counts.value['文件'] || 0 },
  { key: '人员', label: '人员日志', count: counts.value['人员'] || 0 },
  { key: '系统', label: '系统日志', count: counts.value['系统'] || 0 },
  // 【BS-R 2026-09-19】安全审计(append-only):登录/匿名探测/403/权限与集团变更/用户管理
  { key: '__audit__', label: '安全审计', count: 0 },
])

const switchTab = (key) => {
  activeTab.value = key
  query.current = 1
  loadData()
}

const search = () => { query.current = 1; loadData() }

const onDateChange = () => {
  query.current = 1
  loadData()
  loadCounts()
}

const loadData = async () => {
  try {
    // 【BS-R】安全审计页签走 append-only 审计表(事件类型筛选)
    if (activeTab.value === AUDIT_TAB) {
      const params = { current: query.current, size: query.size }
      if (auditEventType.value) params.eventType = auditEventType.value
      const res = await getAuditLogList(params)
      tableData.value = res.data?.records || []
      total.value = res.data?.total || 0
      return
    }
    const params = { current: query.current, size: query.size }
    if (activeTab.value) params.module = activeTab.value
    if (keyword.value) params.keyword = keyword.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getLogList(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {}
}

const loadCounts = async () => {
  try {
    const params = {}
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await request.get('/system/logs/counts', { params })
    counts.value = res.data || {}
  } catch {}
}

const formatTime = (dt) => {
  if (!dt) return '—'
  const s = String(dt)
  // "2026-06-07T14:32:00" → "06-07 14:32"
  const m = s.match(/(\d{4})-(\d{2})-(\d{2})T?(\d{2}):(\d{2})/)
  if (m) return `${m[2]}-${m[3]} ${m[4]}:${m[5]}`
  return s.substring(0, 16)
}

const moduleLabel = (m) => {
  const map = { '客户': '客户管理', '登录': '登录认证', '文件': '文件管理', '人员': '人员管理', '系统': '权限管理' }
  return map[m] || m || '其他'
}
const moduleClass = (m) => {
  const map = { '客户': 'mod-blue', '登录': 'mod-green', '文件': 'mod-orange', '人员': 'mod-purple', '系统': 'mod-purple' }
  return map[m] || 'mod-gray'
}

const showDetail = (row) => {
  detailRow.value = row
  detailVisible.value = true
}

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(() => {
  loadData()
  loadCounts()
  nextTick(updateTableHeight)
  if (tableWrapRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableWrapRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.log-page { height: calc(100vh - 100px); display: flex; flex-direction: column; }
.page-header { margin-bottom: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 4px; }
.page-desc { font-size: 13px; color: var(--text-muted); margin: 0; }

/* Tab行 */
.tab-row { display: flex; align-items: center; margin-bottom: 12px; }
.tabs { display: flex; gap: 6px; }
.tab-item { padding: 6px 14px; border-radius: 20px; font-size: 13px; color: #666; cursor: pointer; transition: all .2s; display: inline-flex; align-items: center; gap: 4px; }
.tab-item:hover { background: #eef4ff; color: #1d4ed8; }
.tab-item.active { background: #eef4ff; color: #1d4ed8; font-weight: 600; }
.tab-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: #e8e8e8; color: #666; font-size: 12px; font-weight: 600; }
.tab-item.active .tab-badge { background: #1d4ed8; color: #fff; }

/* 筛选栏 */
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar-left { display: flex; gap: 8px; }

/* 表格区域 */
.table-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.table-wrap { flex: 1; min-height: 0; }
.table-area :deep(.el-table td.el-table__cell),
.table-area :deep(.el-table th.el-table__cell) {
  height: 40px;
  padding: 4px 0;
  box-sizing: border-box;
}

/* 模块标签 */
.module-tag { padding: 2px 10px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.mod-blue { color: #1d4ed8; background: #eef4ff; }
.mod-green { color: var(--el-color-success); background: #f0f9eb; }
.mod-orange { color: var(--el-color-warning); background: #fdf6ec; }
.mod-purple { color: #9b59b6; background: #f3ecf9; }
.mod-gray { color: var(--el-color-info); background: #f4f4f5; }

/* 分页 */
.list-pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid #f0f2f5;
  background: #fafbfc;
  color: var(--text-muted);
  font-size: 12px;
  flex-shrink: 0;
}
</style>
