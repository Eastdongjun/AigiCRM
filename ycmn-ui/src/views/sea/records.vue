<template>
  <div class="claim-records">
    <div class="page-header">
      <div>
        <h2>捞取审批记录</h2>
        <p class="page-desc">查看客户捞取申请与审批结果</p>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#409eff"><Document /></el-icon>
            <div>
              <div class="stat-value blue">{{ kpi.total || 0 }}</div>
              <div class="stat-label">总记录</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#67c23a"><CircleCheck /></el-icon>
            <div>
              <div class="stat-value green">{{ kpi.approved || 0 }}</div>
              <div class="stat-label">已通过</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#f56c6c"><CircleClose /></el-icon>
            <div>
              <div class="stat-value red">{{ kpi.rejected || 0 }}</div>
              <div class="stat-label">已驳回</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#e6a23c"><Clock /></el-icon>
            <div>
              <div class="stat-value orange">{{ kpi.pending || 0 }}</div>
              <div class="stat-label">待审批</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-date-picker v-model="query.dateFrom" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" size="small" style="width: 140px" @change="search" />
        <el-date-picker v-model="query.dateTo" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" size="small" style="width: 140px" @change="search" />
        <el-select v-model="query.status" placeholder="全部状态" clearable size="small" style="width: 120px" @change="search">
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="待审批" value="reviewing" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索客户/捞取人/审批人..." clearable prefix-icon="Search" size="small" style="width: 220px" @keyup.enter="search" />
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table :data="records" stripe style="width: 100%" size="small" :height="tableHeight">
        <el-table-column label="申请日期" min-width="110">
          <template #default="{ row }">
            <span style="color: #999; font-size: 12px">{{ formatDate(row.applyDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="行业" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.industry" type="info">{{ row.industry }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applicantName" label="捞取人" min-width="80" align="center" />
        <el-table-column prop="approverName" label="审批人" min-width="80" align="center" />
        <el-table-column label="状态" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="dark" round>
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批时间" min-width="110">
          <template #default="{ row }">
            <span style="color: #999; font-size: 12px">{{ formatDate(row.approveTime) }}</span>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="[15, 30, 50]" layout="prev, pager, next" size="small" @change="loadData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getClaimRecords } from '@/api/sea'

const records = ref([])
const total = ref(0)
const kpi = ref({})

const query = reactive({
  current: 1,
  size: 15,
  status: '',
  dateFrom: '',
  dateTo: '',
  keyword: ''
})

const search = () => { query.current = 1; loadData() }

const loadData = async () => {
  try {
    const res = await getClaimRecords(query)
    const data = res.data || {}
    records.value = data.records || []
    total.value = data.total || 0
    kpi.value = data.stats || {}
  } catch (e) {}
}

const formatDate = (dt) => {
  if (!dt) return '—'
  return String(dt).substring(0, 16).replace('T', ' ')
}

const getStatusText = (s) => ({ approved: '已通过', rejected: '已驳回', reviewing: '待审批' }[s] || s)
const getStatusType = (s) => ({ approved: 'success', rejected: 'danger', reviewing: 'warning' }[s] || 'info')

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(() => {
  loadData()
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
.page-header { margin-bottom: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.page-desc { color: #999; font-size: 13px; }
.stat-cards { margin-bottom: 12px; }
.stat-card { border-radius: 8px; }
.stat-body { display: flex; align-items: center; gap: 16px; }
.stat-value { font-size: 32px; font-weight: bold; line-height: 1.2; }
.stat-value.blue { color: #409eff; }
.stat-value.green { color: #67c23a; }
.stat-value.red { color: #f56c6c; }
.stat-value.orange { color: #e6a23c; }
.stat-label { color: #999; font-size: 12px; margin-top: 4px; }
.claim-records { height: calc(100vh - 100px); display: flex; flex-direction: column; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar-left { display: flex; gap: 8px; }
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
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}
</style>
