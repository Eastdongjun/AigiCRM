<template>
  <div class="task-page">
    <div class="page-header">
      <div>
        <h2>我的任务</h2>
        <p class="page-desc">跟进承诺形成的待办事项 · 完成必须记录结果</p>
      </div>
    </div>

    <!-- 统计卡片（2026-09-20 Bento 重设计：主卡渐变突出"待办"，数字滚动动画） -->
    <div class="stat-bento">
      <div class="stat-card primary">
        <div class="stat-icon-chip"><el-icon :size="22"><List /></el-icon></div>
        <div class="stat-info">
          <div class="stat-label">待办任务</div>
          <div class="stat-num">{{ disp.pending }}</div>
          <div class="stat-sub">今日到期 {{ stats.todayDue || 0 }} · 逾期 {{ stats.overdue || 0 }}</div>
        </div>
      </div>
      <div class="stat-card mini orange">
        <div class="stat-icon-chip"><el-icon :size="20"><Clock /></el-icon></div>
        <div class="stat-info">
          <div class="stat-label">今日到期</div>
          <div class="stat-num">{{ disp.todayDue }}</div>
        </div>
      </div>
      <div class="stat-card mini red">
        <div class="stat-icon-chip"><el-icon :size="20"><Warning /></el-icon></div>
        <div class="stat-info">
          <div class="stat-label">已逾期</div>
          <div class="stat-num">{{ disp.overdue }}</div>
        </div>
      </div>
      <div class="stat-card mini green">
        <div class="stat-icon-chip"><el-icon :size="20"><CircleCheck /></el-icon></div>
        <div class="stat-info">
          <div class="stat-label">本月完成</div>
          <div class="stat-num">{{ disp.completedThisMonth }}</div>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="toolbar">
      <el-radio-group v-model="statusFilter" size="small" @change="() => { query.current = 1; loadData() }">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="pending">待办</el-radio-button>
        <el-radio-button value="completed">已完成</el-radio-button>
        <el-radio-button value="cancelled">已取消</el-radio-button>
      </el-radio-group>
      <el-checkbox v-model="overdueOnly" size="small" @change="() => { query.current = 1; loadData() }">仅看逾期</el-checkbox>
    </div>

    <!-- 任务列表 -->
    <el-table :data="tasks" stripe size="small" empty-text="暂无任务" v-loading="loading">
      <el-table-column label="任务" min-width="200">
        <template #default="{ row }">
          <span :class="{ 'task-done': row.status === 'completed' }">{{ row.title }}</span>
          <el-tag v-if="row.overdue" size="small" type="danger" style="margin-left:6px">逾期</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="customerName" label="关联客户" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.customerName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="dueDate" label="截止时间" width="100" sortable />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'cancelled' ? 'info' : row.overdue ? 'danger' : 'primary'" size="small">
            {{ row.status === 'pending' ? '待办' : row.status === 'completed' ? '已完成' : '已取消' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成结果" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.completionNote || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" link type="primary" size="small" @click="showComplete(row)">完成</el-button>
          <el-button v-if="row.status === 'pending'" link type="danger" size="small" @click="showCancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="list-pager">
      <!-- 【P5 修复】翻页直接 loadData；原 @change 强制重置回第 1 页导致永久卡第一页（对齐 approval 写法） -->
      <!-- 【C9 2026-10-08 二轮】补齐 sizes/jumper，与其他列表页分页形态一致 -->
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        :page-sizes="pageSizes(query.size)" layout="sizes, prev, pager, next, jumper" size="small"
        @size-change="query.current = 1" @change="loadData" />
    </div>

    <!-- 完成弹窗 -->
    <el-dialog v-model="completeVisible" title="完成任务" width="440px">
      <p style="color:var(--el-color-info);font-size:13px;margin-bottom:12px">{{ completingTask?.title }}</p>
      <el-input v-model="completionNote" type="textarea" :rows="3" placeholder="完成结果（必填，不是简单勾掉）" />
      <template #footer>
        <el-button @click="completeVisible=false">取消</el-button>
        <el-button type="primary" :disabled="!completionNote.trim()" :loading="actionLoading" @click="confirmComplete">确认完成</el-button>
      </template>
    </el-dialog>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelVisible" title="取消任务" width="440px">
      <el-input v-model="cancelReason" type="textarea" :rows="2" placeholder="取消原因（必填）" />
      <template #footer>
        <el-button @click="cancelVisible=false">取消</el-button>
        <el-button type="danger" :disabled="!cancelReason.trim()" :loading="actionLoading" @click="confirmCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskList, getTaskStatistics, completeTask, cancelTask } from '@/api/task'
import { pageSizes } from '@/utils/pagination'

const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const stats = ref({})
// 数字滚动动画：目标值到位后 800ms ease-out 滚到目标（工作台"动态"感的关键）
const disp = reactive({ pending: 0, todayDue: 0, overdue: 0, completedThisMonth: 0 })
function animateNum(key, target) {
  const from = disp[key] || 0
  const to = Number(target) || 0
  const t0 = performance.now()
  const step = (t) => {
    const p = Math.min(1, (t - t0) / 800)
    const ease = 1 - Math.pow(1 - p, 3)
    disp[key] = Math.round(from + (to - from) * ease)
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}
const query = reactive({ current: 1, size: 20 })
const statusFilter = ref('')
const overdueOnly = ref(false)
const actionLoading = ref(false)

const completeVisible = ref(false)
const completingTask = ref(null)
const completionNote = ref('')
const cancelVisible = ref(false)
const cancelReason = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const params = { current: query.current, size: query.size }
    if (statusFilter.value) params.status = statusFilter.value
    if (overdueOnly.value) params.overdue = true
    const res = await getTaskList(params)
    tasks.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

const loadStats = async () => {
  try {
    const res = await getTaskStatistics()
    stats.value = res.data || {}
    animateNum('pending', stats.value.pending)
    animateNum('todayDue', stats.value.todayDue)
    animateNum('overdue', stats.value.overdue)
    animateNum('completedThisMonth', stats.value.completedThisMonth)
  } catch {}
}

const showComplete = (row) => {
  completingTask.value = row
  completionNote.value = ''
  completeVisible.value = true
}

const confirmComplete = async () => {
  actionLoading.value = true
  try {
    await completeTask(completingTask.value.id, completionNote.value)
    ElMessage.success('已完成')
    completeVisible.value = false
    loadData(); loadStats()
  } catch {} finally { actionLoading.value = false }
}

const showCancel = (row) => { cancelReason.value = ''; cancelVisible.value = true; completingTask.value = row }

const confirmCancel = async () => {
  actionLoading.value = true
  try {
    await cancelTask(completingTask.value.id, cancelReason.value)
    ElMessage.success('已取消')
    cancelVisible.value = false
    loadData(); loadStats()
  } catch {} finally { actionLoading.value = false }
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped>
.task-page { padding: 0 }
.page-header h2 { font-size: 20px; font-weight: 600; margin: 0 0 4px }
.page-desc { color: #94a3b8; font-size: 13px; margin: 0 }
/* 【2026-09-20 Bento 统计卡】主卡(待办)渐变宽卡 + 三张浅灰瓦片小卡；数字滚动由 disp 驱动 */
.stat-bento { display: grid; grid-template-columns: 1.5fr 1fr 1fr 1fr; gap: 14px; margin-bottom: 18px; align-items: stretch }
.stat-card { border: none; border-radius: var(--radius-lg); display: flex; gap: 14px; align-items: flex-start; padding: 20px 22px; height: 100%;
  box-sizing: border-box; transition: transform .18s, box-shadow .18s }
.stat-card:hover { transform: translateY(-2px) }
.stat-card.primary { background: linear-gradient(135deg, #3b82f6 0%, #2563eb 60%, #1d4ed8 100%); color: #fff;
  box-shadow: 0 10px 26px rgba(37, 99, 235, .30) }
.stat-card.primary:hover { box-shadow: 0 14px 32px rgba(37, 99, 235, .38) }
.stat-card.primary .stat-label { color: rgba(255, 255, 255, .82) }
.stat-card.primary .stat-num { color: #fff; font-size: 40px }
.stat-card.primary .stat-sub { margin-top: 6px; font-size: 12px; color: rgba(255, 255, 255, .78) }
.stat-card.primary .stat-icon-chip { background: rgba(255, 255, 255, .18); color: #fff }
.stat-card.mini { background: #f2f4f7; align-items: center }
.stat-card.mini:hover { background: #fff; box-shadow: 0 8px 22px rgba(16, 24, 40, .09) }
.stat-icon-chip { width: 40px; height: 40px; border-radius: 12px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center }
.stat-card.mini.orange .stat-icon-chip { background: #ffedd5; color: #ea580c }
.stat-card.mini.red .stat-icon-chip { background: #fee2e2; color: #dc2626 }
.stat-card.mini.green .stat-icon-chip { background: #dcfce7; color: #16a34a }
.stat-info { min-width: 0 }
.stat-label { font-size: 12px; color: #9ca3af; letter-spacing: 1px }
/* 【B6】大数字唯一档 --font-num-hero(30px)，与工作台/统计页同一角色同一档 */
.stat-num { font-size: var(--font-num-hero); font-weight: 800; margin-top: 4px; line-height: 1.1; font-variant-numeric: tabular-nums }
.stat-card.mini.orange .stat-num { color: #ea580c }
.stat-card.mini.red .stat-num { color: #dc2626 }
.stat-card.mini.green .stat-num { color: #16a34a }
.toolbar { display: flex; gap: 16px; align-items: center; margin-bottom: 12px }
.task-done { text-decoration: line-through; color: #94a3b8 }
.list-pager { display: flex; justify-content: flex-end; margin-top: 12px }
</style>
