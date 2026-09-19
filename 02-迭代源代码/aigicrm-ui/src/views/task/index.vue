<template>
  <div class="task-page">
    <div class="page-header">
      <div>
        <h2>我的任务</h2>
        <p class="page-desc">跟进承诺形成的待办事项 · 完成必须记录结果</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">待办</div>
          <div class="stat-num blue">{{ stats.pending || 0 }}</div>
        </div>
        <el-icon class="stat-icon icon-blue" :size="28"><List /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">今日到期</div>
          <div class="stat-num orange">{{ stats.todayDue || 0 }}</div>
        </div>
        <el-icon class="stat-icon icon-orange" :size="28"><Clock /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">已逾期</div>
          <div class="stat-num red">{{ stats.overdue || 0 }}</div>
        </div>
        <el-icon class="stat-icon icon-red" :size="28"><Warning /></el-icon>
      </div>
      <div class="stat-card">
        <div class="stat-info">
          <div class="stat-label">本月完成</div>
          <div class="stat-num green">{{ stats.completedThisMonth || 0 }}</div>
        </div>
        <el-icon class="stat-icon icon-green" :size="28"><CircleCheck /></el-icon>
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
      <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
        layout="prev, pager, next" size="small" @change="() => { query.current = 1; loadData() }" />
    </div>

    <!-- 完成弹窗 -->
    <el-dialog v-model="completeVisible" title="完成任务" width="440px">
      <p style="color:#909399;font-size:13px;margin-bottom:12px">{{ completingTask?.title }}</p>
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

const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const stats = ref({})
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
.stat-row { display: grid; grid-template-columns: repeat(4,1fr); gap: 12px; margin-bottom: 16px }
.stat-card { display: flex; justify-content: space-between; align-items: center; padding: 16px; background: #fff; border-radius: 10px; border: 1px solid #e2e8f0 }
.stat-label { font-size: 12px; color: #94a3b8 }
.stat-num { font-size: 24px; font-weight: 700; margin-top: 4px }
.stat-num.blue { color: #2563eb } .stat-num.orange { color: #ea580c }
.stat-num.red { color: #dc2626 } .stat-num.green { color: #16a34a }
.stat-icon { opacity: .8 }
.toolbar { display: flex; gap: 16px; align-items: center; margin-bottom: 12px }
.task-done { text-decoration: line-through; color: #94a3b8 }
.list-pager { display: flex; justify-content: flex-end; margin-top: 12px }
</style>
