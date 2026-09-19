<template>
  <div class="approval-manage">
    <div class="page-header">
      <div>
        <h2>审批管理 <span class="page-desc">客户资源归属与保护期管控中心</span></h2>
      </div>
      <div class="header-right-info">
        <el-icon><CircleCheck /></el-icon>
        <span>实时同步 · 今日更新 {{ stats.all || 0 }} 条</span>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="activeTab = 'pending'; loadData()">
          <div class="stat-body">
            <div class="stat-icon-wrap orange-bg">
              <el-icon :size="24"><Clock /></el-icon>
            </div>
            <div>
              <div class="stat-label">待审批</div>
              <div class="stat-value orange">{{ stats.pending || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="activeTab = 'mine'; loadData()">
          <div class="stat-body">
            <div class="stat-icon-wrap blue-bg">
              <el-icon :size="24"><Promotion /></el-icon>
            </div>
            <div>
              <div class="stat-label">我发起的</div>
              <div class="stat-value blue">{{ stats.mine || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="activeTab = 'approved'; loadData()">
          <div class="stat-body">
            <div class="stat-icon-wrap green-bg">
              <el-icon :size="24"><CircleCheck /></el-icon>
            </div>
            <div>
              <div class="stat-label">本月通过</div>
              <div class="stat-value green">{{ stats.approvedThisMonth || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" @click="activeTab = 'rejected'; loadData()">
          <div class="stat-body">
            <div class="stat-icon-wrap red-bg">
              <el-icon :size="24"><CircleClose /></el-icon>
            </div>
            <div>
              <div class="stat-label">本月驳回</div>
              <div class="stat-value red">{{ stats.rejectedThisMonth || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab 栏 -->
    <el-tabs v-model="activeTab" @tab-change="search" style="margin-bottom: 0">
      <el-tab-pane name="pending">
        <template #label>
          <span class="tab-dot dot-orange"></span>
          <span>待审批</span> <span class="tab-count">{{ stats.pending || 0 }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="mine">
        <template #label>
          <span class="tab-dot dot-blue"></span>
          <span>我发起的</span> <span class="tab-count">{{ stats.mine || 0 }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="reviewing">
        <template #label>
          <span class="tab-dot dot-purple"></span>
          <span>审批中</span> <span class="tab-count">{{ stats.reviewing || 0 }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="approved">
        <template #label>
          <span class="tab-dot dot-green"></span>
          <span>已通过</span> <span class="tab-count">{{ stats.approvedThisMonth || 0 }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="rejected">
        <template #label>
          <span class="tab-dot dot-red"></span>
          <span>已驳回</span> <span class="tab-count">{{ stats.rejectedThisMonth || 0 }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="all">
        <template #label>
          <span class="tab-dot dot-gray"></span>
          <span>全部</span> <span class="tab-count">{{ stats.all || 0 }}</span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <!-- 筛选栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-tree-select
          v-model="query.deptId"
          :data="deptTree"
          clearable
          check-strictly
          node-key="id"
          :props="{ label: 'deptName', children: 'children' }"
          placeholder="选择部门（含全部子部门）"
          style="width: 180px"
          @change="search" />
        <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 130px" @change="search">
          <el-option label="捞取" value="claim" />
          <el-option label="转移" value="transfer" />
          <el-option label="延期" value="extend" />
          <el-option label="存储空间" value="storage" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索编号、客户名、申请人..."
          clearable
          prefix-icon="Search"
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        />
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
        <el-table-column prop="approvalCode" label="编号" min-width="120">
          <template #default="{ row }">
            <el-button link type="primary" class="code-link" @click.stop="openDetail(row)">{{ row.approvalCode }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.approvalType)" size="small" effect="plain">{{ getTypeText(row.approvalType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客户" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.customerName }}</div>
            <div class="sub-text" v-if="row.customerIndustry">{{ row.customerIndustry }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" min-width="80" />
        <el-table-column prop="currentNode" label="当前节点" min-width="100" />
        <el-table-column prop="actionAfterApprove" label="通过后动作" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="getApprovalStatusType(row.status)" size="small" effect="dark">
              {{ getApprovalStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="100">
          <template #default="{ row }">{{ row.createdAt?.substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="90" align="center" v-if="activeTab === 'pending' && (canApprove || canReject)">
          <template #default="{ row }">
            <el-button v-if="canApprove" type="success" size="small" circle @click.stop="handleApprove(row)">
              <el-icon><Check /></el-icon>
            </el-button>
            <el-button v-if="canReject" type="danger" size="small" circle @click.stop="handleReject(row)">
              <el-icon><Close /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="list-pager">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="pageSizes(query.size)"
          layout="sizes, prev, pager, next, jumper"
          @size-change="query.current = 1"
          size="small"
          @change="loadData"
        />
      </div>
    </div>

    <el-drawer
      v-model="detailVisible"
      title="审批详情"
      size="520px"
      destroy-on-close
      class="approval-detail-drawer"
    >
      <el-skeleton :rows="6" animated :loading="detailLoading">
        <template v-if="selectedApproval">
          <div class="detail-head">
            <div>
              <div class="detail-code">{{ selectedApproval.approvalCode }}</div>
              <div class="detail-title">{{ getTypeText(selectedApproval.approvalType) }}审批 · {{ selectedApproval.customerName || '未关联客户' }}</div>
            </div>
            <el-tag :type="getApprovalStatusType(selectedApproval.status)" effect="dark">
              {{ getApprovalStatusText(selectedApproval.status) }}
            </el-tag>
          </div>

          <el-descriptions :column="1" border class="detail-descriptions">
            <el-descriptions-item label="申请人">{{ selectedApproval.applicantName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ selectedApproval.currentNode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="通过后动作">{{ selectedApproval.actionAfterApprove || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发起时间">{{ formatDateTime(selectedApproval.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDateTime(selectedApproval.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="selectedApproval.extraData" class="extra-data">
            <div class="section-title">申请补充</div>
            <pre>{{ formatExtraData(selectedApproval.extraData) }}</pre>
          </div>

          <div class="section-title">审批轨迹</div>
          <el-timeline class="node-timeline">
            <el-timeline-item
              v-for="node in approvalNodes"
              :key="node.id || `${node.approvalId}-${node.nodeOrder}`"
              :type="getNodeTimelineType(node.status, node.action)"
              :timestamp="formatDateTime(node.operatedAt)"
            >
              <div class="node-row">
                <span>第 {{ node.nodeOrder || '-' }} 级 · {{ node.approverName || '待分配' }}</span>
                <el-tag size="small" :type="getNodeTagType(node.status, node.action)" effect="plain">
                  {{ getNodeStatusText(node.status, node.action) }}
                </el-tag>
              </div>
              <div v-if="node.comment" class="node-comment">{{ node.comment }}</div>
            </el-timeline-item>
          </el-timeline>
        </template>
      </el-skeleton>

      <template #footer>
        <div class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="selectedApproval && activeTab === 'pending' && canReject"
            type="danger"
            :loading="actionLoading"
            @click="handleReject(selectedApproval)"
          >驳回</el-button>
          <el-button
            v-if="selectedApproval && activeTab === 'pending' && canApprove"
            type="success"
            :loading="actionLoading"
            @click="handleApprove(selectedApproval)"
          >通过</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getApprovalList, getApprovalDetail, approve, reject, getApprovalStatistics } from '@/api/approval'
import request from '@/utils/request'
import { pageSizes } from '@/utils/pagination'

const userStore = useUserStore()
const canApprove = computed(() => userStore.hasPermission('approval:approve'))
const canReject = computed(() => userStore.hasPermission('approval:reject'))
const activeTab = ref('pending')
const tableData = ref([])
const total = ref(0)
const stats = ref({})
const detailVisible = ref(false)
const detailLoading = ref(false)
const actionLoading = ref(false)
const selectedApproval = ref(null)
const approvalNodes = ref([])

const query = reactive({ current: 1, size: 15, type: '', keyword: '', deptId: null })
const deptTree = ref([])

// 部门树（范围感知接口：当前用户可见部门树，含子部门；列表接口后端按 deptId 递归展开）
const loadDeptTree = async () => {
  try {
    const res = await request.get('/common/scope/depts')
    deptTree.value = res.data || []
  } catch (e) {}
}

const loadData = async () => {
  try {
    const res = await getApprovalList({ ...query, tab: activeTab.value })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {}
}
// 筛选/Tab 变化回到第 1 页（分页 @change 仍用 loadData 保持当前页）
const search = () => { query.current = 1; loadData() }

const loadStats = async () => {
  try {
    const res = await getApprovalStatistics()
    stats.value = res.data || {}
  } catch (e) {}
}

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  selectedApproval.value = row
  approvalNodes.value = []
  try {
    const res = await getApprovalDetail(row.id)
    selectedApproval.value = res.data?.approval || row
    approvalNodes.value = res.data?.nodes || []
  } catch (e) {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const handleApprove = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批意见', '审批通过', {
      inputValue: '同意',
      inputPlaceholder: '审批意见',
      inputValidator: value => Boolean(value && value.trim()),
      inputErrorMessage: '审批意见不能为空'
    })
    actionLoading.value = true
    await approve(row.id, { comment: value.trim() })
    ElMessage.success('审批通过')
    detailVisible.value = false
    loadData()
    loadStats()
    userStore.refreshBadges()
  } catch (e) {
  } finally {
    actionLoading.value = false
  }
}

const handleReject = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', {
      inputPlaceholder: '驳回原因',
      inputValidator: value => Boolean(value && value.trim()),
      inputErrorMessage: '驳回原因不能为空'
    })
    actionLoading.value = true
    await reject(row.id, { comment: value.trim() })
    ElMessage.success('已驳回')
    detailVisible.value = false
    loadData()
    loadStats()
    userStore.refreshBadges()
  } catch (e) {
  } finally {
    actionLoading.value = false
  }
}

const getTypeText = (type) => {
  const map = { claim: '捞取', transfer: '转移', extend: '延期', storage: '空间' }
  return map[type] || type
}

const getTypeTag = (type) => {
  const map = { claim: 'primary', transfer: 'warning', extend: 'success', storage: 'info' }
  return map[type] || 'info'
}

const getApprovalStatusType = (status) => {
  const map = { pending: 'info', reviewing: 'warning', approved: 'success', rejected: 'danger' }
  return map[status] || 'info'
}

const getApprovalStatusText = (status) => {
  const map = { pending: '待提交', reviewing: '审批中', approved: '已通过', rejected: '已驳回' }
  return map[status] || status
}

const getNodeTimelineType = (status, action) => {
  if (status === 'approved' || action === 'approve') return 'success'
  if (status === 'rejected' || action === 'reject') return 'danger'
  return 'warning'
}

const getNodeTagType = (status, action) => {
  if (status === 'approved' || action === 'approve') return 'success'
  if (status === 'rejected' || action === 'reject') return 'danger'
  return 'warning'
}

const getNodeStatusText = (status, action) => {
  if (action === 'cancelled') return '已作废'
  const map = { pending: '待审批', approved: '已通过', rejected: '已驳回' }
  return map[status] || status || '待审批'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').substring(0, 19)
}

const formatExtraData = (value) => {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (e) {
    return value
  }
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
  loadStats()
  loadDeptTree()
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
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.page-desc { color: #999; font-size: 13px; font-weight: normal; margin-left: 8px; }
.header-right-info { display: flex; align-items: center; gap: 6px; color: #67c23a; font-size: 13px; }

.stat-cards { margin-bottom: 12px; }
.stat-card { border-radius: 10px; cursor: pointer; transition: transform 0.2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-body { display: flex; align-items: center; gap: 16px; }
.stat-icon-wrap {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.blue-bg { background: rgba(64,158,255,0.15); color: #409eff; }
.green-bg { background: rgba(103,194,58,0.15); color: #67c23a; }
.orange-bg { background: rgba(230,162,60,0.15); color: #e6a23c; }
.red-bg { background: rgba(245,108,108,0.15); color: #f56c6c; }
.stat-label { color: #999; font-size: 13px; }
.stat-value { font-size: 32px; font-weight: bold; margin-top: 2px; }
.stat-value.blue { color: #409eff; }
.stat-value.orange { color: #e6a23c; }
.stat-value.green { color: #67c23a; }
.stat-value.red { color: #f56c6c; }

.tab-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
.dot-orange { background: #e6a23c; }
.dot-blue { background: #409eff; }
.dot-purple { background: #9b59b6; }
.dot-green { background: #67c23a; }
.dot-red { background: #f56c6c; }
.dot-gray { background: #909399; }

.tab-count { color: #999; font-size: 13px; margin-left: 2px; }
.code-link { padding: 0; height: auto; font-weight: 500; }
.sub-text { font-size: 12px; color: #999; }
.approval-manage { height: calc(100vh - 100px); display: flex; flex-direction: column; }

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
.list-pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid #f0f2f5;
  background: #fafbfc;
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}
.detail-code { color: #409eff; font-weight: 600; margin-bottom: 6px; }
.detail-title { color: #303133; font-size: 16px; font-weight: 600; }
.detail-descriptions { margin-bottom: 18px; }
.section-title { font-size: 14px; font-weight: 600; color: #303133; margin: 16px 0 10px; }
.extra-data pre {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  background: #fafafa;
  border-radius: 6px;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
.node-timeline { padding-left: 4px; }
.node-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #303133;
}
.node-comment {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
