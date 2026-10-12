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

    <!-- 统计 · 深蓝条(点击指标直达对应Tab) 【C9 二轮 2026-10-08】收口为全局 MetricStrip 组件 -->
    <MetricStrip
      interactive
      :metrics="stripMetrics"
      :tip="`点击指标直达对应状态 · 实时同步今日 ${stats.all || 0} 条`"
      @metric-click="onStripClick"
    />

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
          <span>已通过</span> <span class="tab-count">{{ stats.approvedTotal ?? (stats.approvedThisMonth || 0) }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="rejected">
        <template #label>
          <span class="tab-dot dot-red"></span>
          <span>已驳回</span> <span class="tab-count">{{ stats.rejectedTotal ?? (stats.rejectedThisMonth || 0) }}</span>
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
      <el-table v-loading="listLoading" :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
        <template #empty>
          <div class="ap-empty">
            <p class="ap-empty-text">{{ emptyTabText }}</p>
            <div class="ap-empty-actions">
              <el-button v-if="activeTab === 'pending'" size="small" @click="router.push('/sea')">去公海池发起捞取</el-button>
              <el-button v-if="activeTab === 'pending' && (stats.mine || 0) > 0" size="small" type="primary" plain @click="switchTab('mine')">查看我发起的 {{ stats.mine }} 条</el-button>
            </div>
          </div>
        </template>
        <el-table-column prop="approvalCode" label="编号" min-width="120" sortable>
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
            <!-- 【A2 三轮 2026-10-09】空值占位 '—'：与详情抽屉「未关联客户」、全站 dashIfEmpty 同一口径，
                 此前空值整格留白（截图 07 P2026-041 行） -->
            <div>{{ row.customerName || '—' }}</div>
            <div class="sub-text" v-if="row.customerIndustry">{{ row.customerIndustry }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" min-width="80" sortable :formatter="dashIfEmpty" />
        <el-table-column prop="currentNode" label="当前节点" min-width="100" :formatter="dashIfEmpty" />
        <el-table-column prop="actionAfterApprove" label="通过后动作" min-width="140" show-overflow-tooltip :formatter="dashIfEmpty" />
        <el-table-column prop="status" label="状态" min-width="70" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="getApprovalStatusType(row.status)" size="small" effect="dark">
              {{ getApprovalStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" min-width="100" sortable>
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
                <!-- PR-03: 审批人已停用/删除的待办节点 → 卡单可见性提示(处置需管理员,引擎不自动跳过) -->
                <el-tag v-if="node.approverInvalid" size="small" type="warning" effect="plain">审批人已停用</el-tag>
                <!-- 【议题二.1 裁决】管理员手动转交(仅 admin 且节点待办时可见) -->
                <el-button v-if="isAdmin && (node.status === 'pending' || node.status === 'waiting')"
                  link type="primary" size="small" @click="openTransfer(node)">转交</el-button>
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

    <!-- 【P3 修复】转交弹窗：openTransfer 置 transferVisible 后此前无对应 UI，功能不可用；append-to-body 保证浮于详情抽屉之上 -->
    <el-dialog v-model="transferVisible" title="转交审批节点" width="440px" append-to-body>
      <div class="transfer-node-info" v-if="transferNode">
        当前审批人：{{ transferNode.approverName || '待分配' }}（第 {{ transferNode.nodeOrder || '-' }} 级）
      </div>
      <el-radio-group v-model="transferTargetId" class="transfer-candidates">
        <el-radio v-for="u in transferCandidates" :key="u.id" :value="u.id" class="transfer-candidate">
          {{ u.realName || u.username }}
        </el-radio>
      </el-radio-group>
      <el-empty v-if="!transferCandidates.length" description="暂无可转交候选人" :image-size="60" />
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!transferTargetId" :loading="transferLoading" @click="confirmTransfer">确认转交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getApprovalList, getApprovalDetail, approve, reject, getApprovalStatistics, transferApprovalNode } from '@/api/approval'
import request from '@/utils/request'
import { pageSizes } from '@/utils/pagination'
// 【C9 二轮 2026-10-08】深蓝指标条全局组件
import MetricStrip from '@/components/MetricStrip.vue'
// 【C9 三轮 2026-10-09】审批状态映射收口到 utils/status-maps.js 唯一来源（pending=warning 橙，与页签/时间线同色）；
// 【A2/C10 三轮】表格空值占位唯一口径 dashIfEmpty
import { APPROVAL_STATUS, statusOf } from '@/utils/status-maps'
import { dashIfEmpty } from '@/utils/format'

const userStore = useUserStore()
const router = useRouter()
// 【P4 修复】isAdmin 是 Pinia action，须调用取值；此前当属性访问恒真，非 admin 也见"转交"按钮
const isAdmin = computed(() => userStore.isAdmin())
const canApprove = computed(() => userStore.hasPermission('approval:approve'))
const transferVisible = ref(false)
const transferNode = ref(null)
const transferTargetId = ref(null)
const transferLoading = ref(false)
const transferCandidates = ref([])

const openTransfer = async (node) => {
  transferNode.value = node
  transferTargetId.value = null
  transferVisible.value = true
  try {
    const res = await import('@/api/system').then(m => m.getUserList({ current: 1, size: 200 }))
    transferCandidates.value = (res.data?.records || []).filter(u => u.status === 1)
  } catch { transferCandidates.value = [] }
}

const confirmTransfer = async () => {
  if (!transferNode.value || !transferTargetId.value || !selectedApproval.value) return
  transferLoading.value = true
  try {
    await transferApprovalNode(selectedApproval.value.id, transferNode.value.id, transferTargetId.value)
    ElMessage.success('已转交')
    transferVisible.value = false
    loadData()
    // 【P3 修复】转交成功后刷新详情轨迹；原写法误调不存在的 showDetail（实际为 openDetail），确认必抛 ReferenceError
    if (detailVisible.value && selectedApproval.value) openDetail(selectedApproval.value)
  } catch (e) {
    // 错误提示由 request 拦截器统一弹出
  } finally { transferLoading.value = false }
}
const canReject = computed(() => userStore.hasPermission('approval:reject'))
const activeTab = ref('pending')
const tableData = ref([])
const total = ref(0)
const stats = ref({})
// 【UI 整改 2026-10-08】列表加载态：列表请求期间表格遮罩，慢接口不再静默
const listLoading = ref(false)

// 【C9 二轮 2026-10-08】深蓝指标条收口为全局 MetricStrip：四指标与点击直达页签的映射集中在此
const STRIP_TABS = ['pending', 'mine', 'approved', 'rejected']
const stripMetrics = computed(() => [
  { num: stats.value.pending || 0, label: '待审批' },
  { num: stats.value.mine || 0, label: '我发起的' },
  { num: stats.value.approvedThisMonth || 0, label: '本月通过' },
  { num: stats.value.rejectedThisMonth || 0, label: '本月驳回', alert: (stats.value.rejectedThisMonth || 0) > 0 },
])
const onStripClick = (i) => { activeTab.value = STRIP_TABS[i] || 'pending'; loadData() }

// 【UI 整改 2026-10-08】空态按页签给语境文案（替换默认"暂无数据"）
const EMPTY_TAB_TEXT = {
  pending: '暂无待审批单据，客户捞取 / 转移 / 延期申请提交后将在此出现',
  mine: '你还没有发起过审批申请',
  reviewing: '暂无审批中的申请',
  approved: '本月暂无已通过的审批',
  rejected: '本月暂无驳回记录',
  all: '暂无审批记录',
}
const emptyTabText = computed(() => EMPTY_TAB_TEXT[activeTab.value] || '暂无审批记录')
const switchTab = (tab) => { activeTab.value = tab; loadData() }
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
  listLoading.value = true
  try {
    const res = await getApprovalList({ ...query, tab: activeTab.value })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
  } finally {
    listLoading.value = false
  }
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
  // 【B7 三轮 2026-10-09】类型章不再占用 success 绿（绿=「已通过/正常」成功语义，撞色导致色义不可预期）；
  // 「延期」改 warning 橙（时间警示语义），success 从类型映射中整体退出
  const map = { claim: 'primary', transfer: 'warning', extend: 'warning', storage: 'info' }
  return map[type] || 'info'
}

// 【C9 三轮 2026-10-09】本地映射删除，统一走 utils/status-maps.js 的 APPROVAL_STATUS
// （此前本地 pending='info' 蓝灰 与唯一来源的 pending='warning' 橙冲突，status-maps 的「唯一来源」契约未生效）
const getApprovalStatusType = (status) => statusOf(APPROVAL_STATUS, status).type

const getApprovalStatusText = (status) => statusOf(APPROVAL_STATUS, status).text

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
  // 【体检 2026-10-12】补 waiting 键: 串行流程(PR-02)第 2 阶段起节点常态为 waiting,
  // 原兜底直显英文「waiting」, 与小程序端 label-maps 口径不一
  const map = { pending: '待审批', waiting: '待审批', approved: '已通过', rejected: '已驳回' }
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
.page-desc { color: var(--text-muted); font-size: 13px; font-weight: normal; margin-left: 8px; }
.header-right-info { display: flex; align-items: center; gap: 6px; color: var(--el-color-success); font-size: 13px; }

/* 统计 · 深蓝条已收口为全局 MetricStrip 组件（C9 二轮），本页不再保留条带样式 */
.tab-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 4px; }
/* 【UI 整改 2026-10-08】彩点入语义组：warning/success/danger/info 用 EP 语义色。
   【B7 三轮 2026-10-09】页签点与状态章同源：状态类页签色 = status-maps 里该状态的 type 色
   （待审批/审批中同warning橙、已通过success绿、已驳回danger红），
   「审批中」不再用主色浅档紫（与表格橙色章不同源、被评审判为第三套语言）；
   我发起的=品牌主色 var(--el-color-primary)，全部=中性 info 灰（非状态页签不占语义色） */
.dot-orange { background: var(--el-color-warning); }
.dot-blue { background: var(--el-color-primary); }
.dot-purple { background: var(--el-color-warning); }
.dot-green { background: var(--el-color-success); }
.dot-red { background: var(--el-color-danger); }
.dot-gray { background: var(--el-color-info); }

.tab-count { color: var(--text-muted); font-size: 13px; margin-left: 2px; }
.code-link { padding: 0; height: auto; font-weight: 500; }
.sub-text { font-size: 12px; color: var(--text-muted); }
.approval-manage { height: calc(100vh - 124px); display: flex; flex-direction: column; }

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
  color: var(--text-muted);
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
.detail-code { color: var(--el-color-primary); font-weight: 600; margin-bottom: 6px; }
.detail-title { color: #303133; font-size: 16px; font-weight: 600; }
.detail-descriptions { margin-bottom: 18px; }
.section-title { font-size: 14px; font-weight: 600; color: #303133; margin: 16px 0 10px; }
.extra-data pre {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  background: #fafafa;
  border-radius: var(--radius-sm);
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
/* 【P3】转交弹窗：当前审批人提示 + 候选人纵向单选列表 */
.transfer-node-info {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: #f4f6fa;
  color: #606266;
  font-size: 13px;
}
.transfer-candidates { display: flex; flex-direction: column; gap: 4px; }
.transfer-candidate { height: auto; padding: 6px 0; margin-right: 0; }
/* 【UI 整改 2026-10-08】空态定制：语境文案 + 入口按钮（替换表格默认"暂无数据"整屏空白） */
.ap-empty { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 48px 0; }
.ap-empty-text { margin: 0; color: var(--el-text-color-secondary, #606266); font-size: 13px; }
.ap-empty-actions { display: flex; gap: 8px; }
</style>
