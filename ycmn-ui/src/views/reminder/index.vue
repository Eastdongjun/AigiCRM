<template>
  <div class="reminder-manage">
    <div class="page-header">
      <h2>提醒管理</h2>
    </div>

    <!-- 搜索栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="搜索客户名称..." clearable prefix-icon="Search" style="width: 200px" @change="loadData" />
        <el-select v-model="query.officeId" placeholder="全部办事处" clearable style="width: 140px" @change="loadData">
          <el-option v-for="d in offices" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="query.ownerId" placeholder="全部负责人" clearable filterable style="width: 140px" @change="loadData">
          <el-option v-for="u in owners" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
        <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="officeName" label="办事处" min-width="80" />
        <el-table-column prop="expireDate" label="到期日" min-width="100" />
        <el-table-column label="剩余天数" min-width="80" align="center">
          <template #default="{ row }">
            <span :class="getDaysClass(row.remainingDays)">{{ row.remainingDays }}天</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" min-width="70" />
        <el-table-column label="状态" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.remainingDays)" size="small">{{ getStatusText(row.remainingDays) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="今日跟进" min-width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.todayFollowedUp" class="followed-tag">✓ 已跟进</span>
            <span v-else class="unfollowed-tag">未跟进</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.todayFollowedUp" link type="primary" size="small" @click="handleFollowUp(row)">已跟进</el-button>
            <el-button v-else link type="info" size="small" disabled>已跟进</el-button>
            <el-button link type="warning" size="small" @click="handleExtend(row)">申请延期</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[15, 30, 50]"
          layout="prev, pager, next"
          small
          @change="loadData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getExpiringList, processReminder } from '@/api/reminder'
import { submitApproval } from '@/api/approval'
import { getDeptList } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const tableData = ref([])
const total = ref(0)
const offices = ref([])
const owners = ref([])

const query = reactive({
  current: 1,
  size: 15,
  keyword: '',
  officeId: null,
  ownerId: null
})

const loadData = async () => {
  try {
    const res = await getExpiringList(query)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {}
}

const resetQuery = () => {
  query.keyword = ''
  query.officeId = null
  query.ownerId = null
  query.current = 1
  loadData()
}

const handleFollowUp = async (row) => {
  try {
    await processReminder(row.reminderId)
    row.todayFollowedUp = true
    ElMessage.success('已标记今日跟进')
    userStore.refreshBadges()
  } catch (e) {}
}

const handleExtend = async (row) => {
  try {
    await submitApproval({ type: 'extend', customerId: String(row.customerId), actionAfterApprove: '保护期延长' })
    ElMessage.success('延期申请已提交，等待审批')
  } catch (e) {}
}

const getDaysClass = (days) => {
  if (days <= 7) return 'days-danger'
  if (days <= 30) return 'days-warning'
  return 'days-normal'
}

const getStatusType = (days) => {
  if (days <= 7) return 'danger'
  if (days <= 30) return 'warning'
  if (days <= 90) return 'info'
  return 'success'
}

const getStatusText = (days) => {
  if (days <= 7) return '即将逾期'
  if (days <= 30) return '临期提醒'
  if (days <= 90) return '关注'
  return '正常'
}

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(async () => {
  loadData()
  try {
    const res = await getDeptList()
    offices.value = (res.data || []).filter(d => d.deptType === 3)
  } catch (e) {}
  try {
    const res = await getSimpleUserList()
    owners.value = res.data || []
  } catch (e) {}
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
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar-left { display: flex; gap: 8px; }
.toolbar-right { display: flex; gap: 8px; }
.reminder-manage { height: calc(100vh - 100px); display: flex; flex-direction: column; }
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
.days-danger { color: #f56c6c; font-weight: bold; }
.days-warning { color: #e6a23c; font-weight: bold; }
.days-normal { color: #67c23a; }
.followed-tag { color: #67c23a; font-size: 12px; font-weight: 500; }
.unfollowed-tag { color: #e6a23c; font-size: 12px; }
</style>
