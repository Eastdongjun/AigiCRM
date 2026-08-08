<template>
  <div class="reminder-manage">
    <div class="page-header">
      <h2>提醒管理</h2>
    </div>

    <!-- 搜索栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="搜索客户名称..." clearable prefix-icon="Search" style="width: 200px" @change="search" />
        <el-tree-select
          v-model="query.deptId"
          :data="offices"
          check-strictly
          clearable
          node-key="id"
          :props="{ label: 'deptName', children: 'children' }"
          placeholder="选择部门（含全部子部门）"
          style="width: 190px"
          @change="search"
        />
        <el-select v-model="query.ownerId" placeholder="全部负责人" clearable filterable style="width: 140px" @change="search">
          <el-option v-for="u in owners" :key="u.id" :label="u.realName" :value="u.id" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button type="danger" plain size="small" @click="handleClearAll">一键清空</el-button>
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table :data="pagedData" stripe style="width:100%" size="default" :height="tableHeight">
        <el-table-column label="类型" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._type === 'share'" type="warning" size="small" effect="dark">分享</el-tag>
            <el-tag v-else-if="row._type === 'invoice'" :type="row._invoiceTagType" size="small" effect="dark">开票</el-tag>
            <el-tag v-else type="info" size="small">到期</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="提示内容" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row._type === 'share'">{{ row._shareLabel }}</span>
            <span v-else-if="row._type === 'invoice'">{{ row._invoiceLabel }}</span>
            <span v-else>{{ row.remainingDays }}天后到期</span>
          </template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期日" width="100">
          <template #default="{ row }"><span v-if="row._type !== 'share'">{{ row.expireDate || '—' }}</span><span v-else>—</span></template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="80" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row._type === 'share'" type="warning" size="small">待查看</el-tag>
            <el-tag v-else-if="row._type === 'invoice'" :type="row._invoiceTagType" size="small">待处理</el-tag>
            <el-tag v-else :type="getStatusType(row.remainingDays)" size="small">{{ getStatusText(row.remainingDays) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row._type === 'share'">
              <el-button link type="primary" size="small" @click="handleProcessShare(row)">已读</el-button>
            </template>
            <template v-else-if="row._type === 'invoice'">
              <el-button link type="primary" size="small" @click="handleProcessShare(row)">已读</el-button>
            </template>
            <template v-else>
              <el-button v-if="!row.todayFollowedUp" link type="primary" size="small" @click="handleFollowUp(row)">已跟进</el-button>
              <el-button v-else link type="info" size="small" disabled>已跟进</el-button>
              <el-button link type="warning" size="small" @click="handleExtend(row)">申请延期</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="rem-pager">
        <span class="rem-pager-info">{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }} / 共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[15, 30, 50]"
          layout="prev, pager, next"
          :pager-count="5"
          size="small"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExpiringList, getReminderList, processReminder } from '@/api/reminder'
import { submitApproval } from '@/api/approval'
import { getScopeDepts } from '@/api/system'
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
  deptId: null,
  ownerId: null
})

const search = () => { query.current = 1; loadData() }
const loadData = async () => {
  try {
    const res = await getExpiringList(query)
    const expiring = (res.data?.records || []).map(r => ({ ...r, _type: 'expiring' }))
    // 追加分享提醒
    let shareRecords = []
    try {
      const types = ['share_to', 'share_by', 'share_owner']
      const labelMap = { share_to: '客户已分享给你', share_by: '你分享了客户', share_owner: '你的客户被分享' }
      // 【性能 2026-08-08】三个分享类型互不依赖，串行 for 改为 Promise.all 并发
      const srs = await Promise.all(types.map(t =>
        getReminderList({ current: 1, size: 20, type: t, status: 'pending', deptId: query.deptId })))
      for (let i = 0; i < types.length; i++) {
        const t = types[i]
        for (const item of (srs[i]?.data?.records || [])) {
          shareRecords.push({
            reminderId: item.id,
            customerId: item.customerId,
            customerName: item.customerName,
            ownerName: item.ownerName,
            officeName: item.officeName || '',
            _type: 'share',
            _shareLabel: labelMap[item.reminderType] || item.reminderType,
          })
        }
      }
    } catch {}
    // 追加开票提醒（超期 invoice_overdue + 待首次开票 invoice_never）
    let invoiceRecords = []
    try {
      const itypes = ['invoice_overdue', 'invoice_never']
      const irs = await Promise.all(itypes.map(t =>
        getReminderList({ current: 1, size: 20, type: t, status: 'pending', deptId: query.deptId })))
      for (let i = 0; i < itypes.length; i++) {
        const t = itypes[i]
        for (const item of (irs[i]?.data?.records || [])) {
          invoiceRecords.push({
            reminderId: item.id,
            customerId: item.customerId,
            customerName: item.customerName,
            ownerName: item.ownerName,
            officeName: item.officeName || '',
            _type: 'invoice',
            _invoiceTagType: t === 'invoice_overdue' ? 'danger' : 'warning',
            _invoiceLabel: t === 'invoice_overdue'
              ? `已超期 ${(item.overdueDays || 0)} 天未开票`
              : '待首次开票',
          })
        }
      }
    } catch {}
    tableData.value = [...shareRecords, ...invoiceRecords, ...expiring]
    total.value = tableData.value.length
  } catch (e) {}
}

const resetQuery = () => {
  query.keyword = ''
  query.deptId = null
  query.ownerId = null
  query.current = 1
  loadData()
}

// 客户端分页
const pagedData = computed(() => {
  const start = (query.current - 1) * query.size
  return tableData.value.slice(start, start + query.size)
})

const handleClearAll = async () => {
  try {
    await ElMessageBox.confirm('确认清空所有提醒？分享/开票提醒标记已读，到期提醒标记已跟进。', '一键清空', { type: 'warning' })
  } catch { return }
  const all = tableData.value.filter(r => r.reminderId)
  let done = 0
  for (const r of all) {
    try { await processReminder(r.reminderId); done++ } catch {}
  }
  ElMessage.success(`已处理 ${done} 条提醒`)
  loadData()
  userStore.refreshBadges()
}

const handleProcessShare = async (row) => {
  try {
    await processReminder(row.reminderId)
    ElMessage.success('已标记已读')
    loadData()
    userStore.refreshBadges()
  } catch (e) {}
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
    const res = await getScopeDepts()
    offices.value = res.data || []
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
.table-area { flex: 1; min-height: 0; display: flex; flex-direction: column; background: #fff; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden }
.table-wrap { flex: 1; min-height: 0; }
.rem-pager {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  padding: 10px 16px;
  border-top: 1px solid #f0f2f5;
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
  background: #fafbfc;
  overflow-x: auto;
}
.rem-pager-info { color: #909399; white-space: nowrap; margin-right: auto; }
.rem-pager .el-pagination { flex-shrink: 0; }
.days-danger { color: #f56c6c; font-weight: bold; }
.days-warning { color: #e6a23c; font-weight: bold; }
.days-normal { color: #67c23a; }
.followed-tag { color: #67c23a; font-size: 13px; font-weight: 500; }
.unfollowed-tag { color: #e6a23c; font-size: 13px; }
.table-area :deep(.el-table td.el-table__cell) { font-size: 14px; padding: 10px 0 }
.table-area :deep(.el-table th.el-table__cell) { font-size: 14px; font-weight: 600; padding: 10px 0 }
</style>
