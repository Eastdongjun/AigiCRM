<template>
  <div class="sea-manage">
    <!-- 框1: 筛选 -->
    <div class="section-box" style="display:flex;align-items:center;gap:12px">
      <span style="font-weight:700;color:#1f2329">公海池</span>
      <span style="color:#888;font-size:13px">选择公海池</span>
      <el-select v-model="selectedPool" placeholder="全部" clearable size="small" style="width:200px" @change="loadData">
        <el-option v-for="p in pools" :key="p.id" :label="`${p.poolName} (${p.regionNames || ''})`" :value="p.id" />
      </el-select>
      <el-button v-if="canImport" size="small" :icon="Upload" @click="showImportDialog=true" style="margin-left:auto">批量导入</el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#409eff"><Document /></el-icon>
            <div>
              <div class="stat-value blue">{{ stats.total || 0 }}</div>
              <div class="stat-label">池内待捞</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#67c23a"><Right /></el-icon>
            <div>
              <div class="stat-value green">{{ stats.newThisMonth || 0 }}</div>
              <div class="stat-label">本月入池</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#e6a23c"><Clock /></el-icon>
            <div>
              <div class="stat-value orange">{{ stats.pendingApproval || 0 }}</div>
              <div class="stat-label">待审批捞取</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-body">
            <el-icon :size="36" color="#67c23a"><CircleCheck /></el-icon>
            <div>
              <div class="stat-value green">{{ stats.claimedThisMonth || 0 }}</div>
              <div class="stat-label">本月成功</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 状态筛选标签 -->
    <div class="status-tabs">
      <span :class="['tab-pill', { active: statusFilter === '' }]" @click="setStatus('')">全部</span>
      <span :class="['tab-pill', { active: statusFilter === 'available' }]" @click="setStatus('available')">待捞取</span>
      <span :class="['tab-pill', { active: statusFilter === 'pending_approval' }]" @click="setStatus('pending_approval')">审批中</span>
      <span :class="['tab-pill', { active: statusFilter === 'claimed' }]" @click="setStatus('claimed')">已捞取</span>
    </div>

    <!-- 筛选栏 -->
    <div class="toolbar" style="margin-bottom: 12px">
      <div class="toolbar-left">
        <el-select v-model="query.industry" placeholder="全行业" clearable style="width: 130px" @change="loadData">
          <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
        </el-select>
        <el-select v-model="query.enterReason" placeholder="全部原因" clearable style="width: 130px" @change="loadData">
          <el-option label="保护期满" value="保护期满" />
          <el-option label="放弃客户" value="放弃客户" />
          <el-option label="离职交接" value="离职交接" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索客户名..." clearable prefix-icon="Search" style="width: 200px" @keyup.enter="loadData" />
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table :data="tableData" :height="tableHeight" stripe style="width: 100%" size="small">
        <el-table-column prop="customerCode" label="编号" min-width="70" />
        <el-table-column label="客户名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <a class="customer-link" @click.stop="$router.push(`/customer/detail/${row.customerId}?from=sea`)">{{ row.customerName }}</a>
          </template>
        </el-table-column>
        <el-table-column label="行业" min-width="70">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.industry">{{ row.industry }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="region" label="办事处" min-width="80" />
        <el-table-column prop="originalOwnerName" label="原负责人" min-width="80" />
        <el-table-column label="进入时间" min-width="100">
          <template #default="{ row }">{{ row.enterDate?.substring(0, 10) }}</template>
        </el-table-column>
        <el-table-column label="入池天数" min-width="80" align="center">
          <template #default="{ row }">
            <span class="days-text">{{ getDays(row) }}天</span>
          </template>
        </el-table-column>
        <el-table-column label="进入原因" min-width="90">
          <template #default="{ row }">{{ getReasonText(row.enterReason) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="160" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'available'" type="primary" size="small" @click="handleClaim(row)">捞取</el-button>
            <el-button v-if="canEdit" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canDelete && row.status === 'available'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            <el-icon v-if="row.status !== 'available' && !canEdit"><View /></el-icon>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="[15, 30, 50]" layout="prev, pager, next" small @change="loadData" />
      </div>
    </div>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="批量导入到公海池" width="520px" :close-on-click-modal="false" @close="closeImportDialog">
      <div class="import-dialog">
        <div class="import-step">
          <div class="step-label">第一步：下载模板</div>
          <el-button type="primary" link @click="handleDownloadTemplate">
            <el-icon><Download /></el-icon> 下载导入模板
          </el-button>
        </div>
        <div class="import-step">
          <div class="step-label">第二步：填写数据后上传</div>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
            drag
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">将Excel文件拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">导入的客户将直接进入公海池（待捞取），客户名称为必填项</div>
            </template>
          </el-upload>
        </div>
        <div v-if="importResult" class="import-result">
          <el-alert
            :title="`导入完成: 成功 ${importResult.successCount} 条，失败 ${importResult.failCount} 条，共 ${importResult.totalCount} 条`"
            :type="importResult.failCount === 0 ? 'success' : 'warning'"
            :closable="false"
            show-icon
          />
          <div v-if="importResult.failures && importResult.failures.length > 0" class="fail-list">
            <div class="fail-header">失败详情：</div>
            <div v-for="f in importResult.failures" :key="f.rowNum" class="fail-item">
              <span class="fail-row">第{{ f.rowNum }}行</span>
              <span class="fail-name">{{ f.customerName || '(空)' }}</span>
              <span class="fail-reason">{{ f.reason }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showImportDialog = false">{{ importResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!importResult" type="primary" :loading="importLoading" :disabled="!importFile" @click="handleImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getSeaList, getSeaStatistics, getVisibleSeaConfigs, deleteSeaPool, importSeaCustomers } from '@/api/sea'
import { downloadImportTemplate } from '@/api/customer'
import { getDictData } from '@/api/system'
import { submitApproval } from '@/api/approval'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const canEdit = computed(() => userStore.hasPermission('sea:pool:edit'))
const canDelete = computed(() => userStore.hasPermission('sea:pool:delete'))
const canImport = computed(() => userStore.hasPermission('sea:pool:import'))

// 批量导入
const showImportDialog = ref(false)
const importFile = ref(null)
const importLoading = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

const handleFileChange = (file) => { importFile.value = file.raw }
const handleExceed = () => { ElMessage.warning('只能上传一个文件，请先移除已选文件') }
const handleDownloadTemplate = async () => {
  try {
    await downloadImportTemplate()
    ElMessage.success('模板下载成功')
  } catch (e) { ElMessage.error('模板下载失败') }
}
const handleImport = async () => {
  if (!importFile.value) return
  importLoading.value = true
  importResult.value = null
  try {
    const res = await importSeaCustomers(importFile.value)
    importResult.value = res.data
    if (res.data.successCount > 0) {
      loadData()
      loadStats()
    }
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    importLoading.value = false
  }
}
const closeImportDialog = () => {
  importFile.value = null
  importResult.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
}

const tableData = ref([])
const total = ref(0)
const stats = ref({})
const pools = ref([])
const selectedPool = ref(null)
const statusFilter = ref('')
const industryOptions = ref([])

const query = reactive({
  current: 1,
  size: 15,
  industry: '',
  enterReason: '',
  keyword: ''
})

const setStatus = (status) => {
  statusFilter.value = status
  query.current = 1
  loadData()
}

const loadData = async () => {
  try {
    const res = await getSeaList({ ...query, poolId: selectedPool.value || undefined })
    let records = res.data?.records || []
    if (statusFilter.value) {
      records = records.filter(r => r.status === statusFilter.value)
    }
    tableData.value = records
    total.value = res.data?.total || 0
  } catch (e) {}
}

const loadStats = async () => {
  try {
    const res = await getSeaStatistics()
    stats.value = res.data || {}
  } catch (e) {}
}

const loadPools = async () => {
  try {
    const res = await getVisibleSeaConfigs()
    pools.value = res.data || []
  } catch (e) {}
}

const handleClaim = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要捞取客户「${row.customerName}」吗？将发起审批申请`, '捞取确认')
    await submitApproval({ type: 'claim', customerId: String(row.customerId), actionAfterApprove: '客户归属→本人' })
    ElMessage.success('捞取申请已提交，等待审批')
    loadData()
    loadStats()
  } catch (e) {}
}

const handleEdit = (row) => {
  router.push(`/customer/edit/${row.customerId}?from=sea`)
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除客户「${row.customerName}」吗？该客户将被移出系统，不可恢复。`, '删除确认', { type: 'warning' })
    await deleteSeaPool(row.id)
    ElMessage.success('删除成功')
    loadData()
    loadStats()
  } catch (e) {}
}

const getDays = (row) => {
  if (!row.enterDate) return 0
  return Math.floor((new Date() - new Date(row.enterDate)) / (1000 * 60 * 60 * 24))
}

const getReasonText = (reason) => ({ protection_expired: '保护期满', abandoned: '放弃客户', resignation: '离职交接' }[reason] || reason)
const getStatusText = (status) => ({ available: '待捞取', pending_approval: '审批中', claimed: '已捞取' }[status] || status)
const getStatusType = (status) => ({ available: 'primary', pending_approval: 'warning', claimed: 'success' }[status] || 'info')

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(async () => {
  loadPools()
  loadData()
  loadStats()
  try {
    const res = await getDictData('industry')
    industryOptions.value = (res.data || []).map(d => d.dictValue)
  } catch (e) { industryOptions.value = ['煤矿','化工','电力','洗煤厂','有色'] }
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
.page-header { margin-bottom: 12px; display: flex; justify-content: space-between; align-items: flex-start; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.page-desc { color: #999; font-size: 13px; }

/* 批量导入弹窗 */
.import-dialog .import-step { margin-bottom: 16px; }
.import-dialog .step-label { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 8px; }
.import-result { margin-top: 16px; }
.fail-list { max-height: 200px; overflow-y: auto; margin-top: 8px; border: 1px solid #ebeef5; border-radius: 4px; padding: 8px; background: #fafafa; }
.fail-header { font-size: 13px; font-weight: 600; color: #333; margin-bottom: 4px; }
.fail-item { font-size: 12px; line-height: 22px; display: flex; gap: 8px; }
.fail-row { color: #999; width: 60px; flex-shrink: 0; }
.fail-name { color: #333; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fail-reason { color: #f56c6c; flex: 1; }

.pool-selector { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; padding: 10px 16px; background: #fff; border-radius: 8px; border: 1px solid #ebeef5; }
.pool-label { font-size: 14px; color: #666; font-weight: 500; }

.stat-cards { margin-bottom: 12px; }
.stat-card { border-radius: 8px; }
.stat-body { display: flex; align-items: center; gap: 16px; }
.stat-value { font-size: 32px; font-weight: bold; line-height: 1.2; }
.stat-value.blue { color: #409eff; }
.stat-value.green { color: #67c23a; }
.stat-value.orange { color: #e6a23c; }
.stat-label { color: #999; font-size: 12px; margin-top: 4px; }

.status-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.tab-pill { padding: 4px 16px; border-radius: 16px; font-size: 13px; cursor: pointer; color: #666; background: #f5f7fa; transition: all 0.2s; }
.tab-pill:hover { color: #409eff; background: #ecf5ff; }
.tab-pill.active { color: #fff; background: #409eff; }

.toolbar { display: flex; justify-content: space-between; align-items: center; }
.toolbar-left { display: flex; gap: 8px; }

.days-text { color: #e6a23c; font-weight: bold; }
.sea-manage { height: calc(100vh - 100px); display: flex; flex-direction: column; }

.table-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.table-wrap {
  flex: 1;
  min-height: 0;
}
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
.customer-link { color: #409eff; cursor: pointer; }
.customer-link:hover { color: #66b1ff; text-decoration: underline; }
</style>
