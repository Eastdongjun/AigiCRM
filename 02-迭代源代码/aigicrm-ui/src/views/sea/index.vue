<template>
  <div class="sea-page">
    <!-- 头部 -->
    <div class="sea-hero">
      <div class="sea-hero-left">
        <h1>公海池</h1>
        <div class="sea-hero-sub">
          {{ currentPool ? `${currentPool.poolName} · ${currentPool.regionNames || '全部区域'}` : `可见公海池 ${pools.length} 个` }}
        </div>
      </div>
      <div class="sea-hero-right">
        <el-select v-model="selectedPool" placeholder="选择公海池" clearable style="width:240px" @change="search">
          <el-option v-for="p in pools" :key="p.id" :label="`${p.poolName} (${p.regionNames || ''})`" :value="p.id" />
        </el-select>
        <el-button @click="router.push('/sea/records')"><el-icon><Tickets /></el-icon> 捞取记录</el-button>
        <el-button v-if="canImport" type="primary" @click="showImportDialog=true"><el-icon><Upload /></el-icon> 批量导入</el-button>
      </div>
    </div>

    <!-- 统计 · 深蓝条(与全站设计语言一致) -->
    <div class="sea-strip">
      <div class="ss-metric">
        <div class="ss-num">{{ stats.total || 0 }}</div>
        <div class="ss-lbl">池内待捞</div>
      </div>
      <div class="ss-divider"></div>
      <div class="ss-metric">
        <div class="ss-num">{{ stats.newThisMonth || 0 }}</div>
        <div class="ss-lbl">本月入池</div>
      </div>
      <div class="ss-divider"></div>
      <div class="ss-metric">
        <div class="ss-num" :class="{ 'ss-alert': (stats.pendingApproval || 0) > 0 }">{{ stats.pendingApproval || 0 }}</div>
        <div class="ss-lbl">待审批捞取</div>
      </div>
      <div class="ss-divider"></div>
      <div class="ss-metric">
        <div class="ss-num">{{ stats.claimedThisMonth || 0 }}</div>
        <div class="ss-lbl">本月成功</div>
      </div>
      <div class="ss-tip">{{ currentPool ? currentPool.poolName : '全部公海池' }} · {{ currentPool ? (currentPool.regionNames || '全部区域') : pools.length + ' 个池' }}</div>
    </div>

    <!-- 筛选栏 -->
    <div class="sea-filter">
      <div class="sea-status-tabs">
        <span :class="['sea-tab',{active:statusFilter===''}]" @click="setStatus('')">全部</span>
        <span :class="['sea-tab',{active:statusFilter==='available'}]" @click="setStatus('available')">待捞取</span>
        <span :class="['sea-tab',{active:statusFilter==='pending_approval'}]" @click="setStatus('pending_approval')">审批中</span>
        <span :class="['sea-tab',{active:statusFilter==='claimed'}]" @click="setStatus('claimed')">已捞取</span>
      </div>
      <div class="sea-filters">
        <el-tree-select
          v-model="query.deptId"
          :data="deptTree"
          check-strictly
          clearable
          node-key="id"
          :props="{ label: 'deptName', children: 'children' }"
          placeholder="选择部门（含全部子部门）"
          class="f-dept"
          @change="search"
        />
        <el-select v-model="query.industry" placeholder="全行业" clearable class="f-sel" @change="search">
          <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
        </el-select>
        <el-select v-model="query.enterReason" placeholder="全部原因" clearable class="f-sel" @change="search">
          <el-option label="保护期满" value="保护期满" />
          <el-option label="放弃客户" value="放弃客户" />
          <el-option label="离职交接" value="离职交接" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索客户名称..." clearable prefix-icon="Search" class="f-kw" @keyup.enter="search" />
        <el-button :disabled="!hasFilters" @click="resetFilters">重置</el-button>
      </div>
    </div>

    <div v-if="hasFilters" class="filter-summary">
      <span>当前筛选</span>
      <el-tag v-if="statusFilter" size="small" closable @close="setStatus('')">{{ getStatusText(statusFilter) }}</el-tag>
      <el-tag v-if="currentPool" size="small" closable @close="clearPool">{{ currentPool.poolName }}</el-tag>
      <el-tag v-if="query.industry" size="small" closable @close="query.industry=''; search()">{{ query.industry }}</el-tag>
      <el-tag v-if="query.enterReason" size="small" closable @close="query.enterReason=''; search()">{{ getReasonText(query.enterReason) }}</el-tag>
      <el-tag v-if="query.keyword" size="small" closable @close="query.keyword=''; search()">{{ query.keyword }}</el-tag>
    </div>

    <!-- 表格 -->
    <div class="sea-table-wrap">
      <div class="sea-table-inner" ref="tableWrapRef">
        <el-table v-loading="listLoading" :data="tableData" :height="tableHeight" stripe size="small" :empty-text="emptyTableText">
          <el-table-column prop="customerCode" label="编号" width="140" />
          <el-table-column label="客户名称" min-width="180" show-overflow-tooltip>
            <template #default="{row}">
              <a class="sea-link" @click.stop="$router.push(`/customer/detail/${row.customerId}?from=sea`)">{{ row.customerName }}</a>
            </template>
          </el-table-column>
          <el-table-column label="行业" width="90">
            <template #default="{row}"><el-tag size="small" v-if="row.industry">{{ row.industry }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="region" label="办事处" width="100" />
          <el-table-column prop="originalOwnerName" label="原负责人" width="90" />
          <el-table-column label="进入时间" width="100">
            <template #default="{row}">{{ row.enterDate?.substring(0,10) }}</template>
          </el-table-column>
          <el-table-column label="入池天数" width="80" align="center">
            <template #default="{row}"><span class="sea-days" :class="daysTone(row)">{{ getDays(row) }}天</span></template>
          </el-table-column>
          <el-table-column label="原因" width="90">
            <template #default="{row}">{{ getReasonText(row.enterReason) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{row}">
              <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="176" align="center" fixed="right" class-name="sea-op-col">
            <template #default="{row}">
              <el-button
                v-if="row.status==='available'&&row.canClaim"
                type="primary"
                size="small"
                :loading="claimingId === row.id"
                @click="handleClaim(row)"
              >捞取</el-button>
              <el-button v-if="row.canEdit" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button v-if="row.canDelete&&row.status==='available'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="sea-pager">
        <span class="sea-pager-info">{{ total>0?(query.current-1)*query.size+1:0 }}-{{ Math.min(query.current*query.size,total) }} / 共 {{ total }} 条</span>
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="pageSizes(query.size)" layout="sizes, prev, pager, next, jumper" size="small" @change="loadData" @size-change="query.current = 1" />
      </div>
    </div>

    <el-dialog v-model="showImportDialog" title="批量导入到公海池" width="520px" :close-on-click-modal="false" @close="closeImportDialog">
      <div class="import-step"><div class="step-label">第一步：下载模板</div><el-button type="primary" link @click="handleDownloadTemplate"><el-icon><Download /></el-icon> 下载导入模板</el-button></div>
      <div class="import-step"><div class="step-label">第二步：填写数据后上传</div>
        <el-upload ref="uploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleFileChange" :on-exceed="handleExceed" drag>
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">将Excel文件拖到此处，或<em>点击选择</em></div>
          <template #tip><div class="el-upload__tip">导入的客户将直接进入公海池（待捞取），客户名称为必填项</div></template>
        </el-upload>
      </div>
      <div v-if="importResult" class="import-result">
        <el-alert :title="`导入完成: 成功 ${importResult.successCount} 条，失败 ${importResult.failCount} 条，共 ${importResult.totalCount} 条`" :type="importResult.failCount===0?'success':'warning'" :closable="false" show-icon />
        <div v-if="importResult.failures?.length" class="fail-list">
          <div class="fail-header">失败详情：</div>
          <div v-for="f in importResult.failures" :key="f.rowNum" class="fail-item"><span class="fail-row">第{{ f.rowNum }}行</span><span class="fail-name">{{ f.customerName||'(空)' }}</span><span class="fail-reason">{{ f.reason }}</span></div>
        </div>
      </div>
      <template #footer><el-button @click="showImportDialog=false">{{ importResult?'关闭':'取消' }}</el-button><el-button v-if="!importResult" type="primary" :loading="importLoading" :disabled="!importFile" @click="handleImport">开始导入</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Right, Clock, CircleCheck, Document, Tickets } from '@element-plus/icons-vue'
import { getSeaList, getSeaStatistics, getVisibleSeaConfigs, deleteSeaPool, importSeaCustomers } from '@/api/sea'
import { downloadImportTemplate } from '@/api/customer'
import { getDictData, getScopeDepts } from '@/api/system'
import { submitApproval } from '@/api/approval'
import { useUserStore } from '@/store/user'
import { pageSizes } from '@/utils/pagination'

const router = useRouter(); const userStore = useUserStore()
const canImport = computed(() => userStore.hasPermission('sea:import'))
const emptyTableText = computed(() => pools.value.length
  ? '暂无符合条件的公海客户'
  : '当前账号暂无可见公海池，请联系管理员检查数据范围')

const showImportDialog = ref(false); const importFile = ref(null); const importLoading = ref(false); const importResult = ref(null); const uploadRef = ref(null)
const handleFileChange = (file) => { importFile.value = file.raw }
const handleExceed = () => { ElMessage.warning('只能上传一个文件') }
const handleDownloadTemplate = async () => { try { await downloadImportTemplate(); ElMessage.success('模板下载成功') } catch(e) { ElMessage.error('模板下载失败') } }
const handleImport = async () => { if(!importFile.value) return; importLoading.value=true; importResult.value=null; try { const res=await importSeaCustomers(importFile.value); importResult.value=res.data; if(res.data.successCount>0){loadData();loadStats()} } catch(e) {} finally { importLoading.value=false } }
const closeImportDialog = () => { importFile.value=null; importResult.value=null; if(uploadRef.value) uploadRef.value.clearFiles() }

const tableData = ref([]); const total = ref(0); const stats = ref({}); const pools = ref([]); const selectedPool = ref(null); const statusFilter = ref(''); const industryOptions = ref([]); const deptTree = ref([])
const listLoading = ref(false); const claimingId = ref(null)
const query = reactive({ current:1, size:15, industry:'', enterReason:'', keyword:'', deptId:null })
const currentPool = computed(() => pools.value.find(p => p.id === selectedPool.value))
const hasFilters = computed(() => Boolean(selectedPool.value || statusFilter.value || query.industry || query.enterReason || query.keyword || query.deptId))
const setStatus = (s) => { statusFilter.value=s; query.current=1; loadData() }
const clearPool = () => { selectedPool.value = null; search() }
const resetFilters = () => {
  selectedPool.value = null
  statusFilter.value = ''
  query.industry = ''
  query.enterReason = ''
  query.keyword = ''
  query.deptId = null
  query.current = 1
  loadData()
}

const loadData = async () => {
  listLoading.value = true
  try { const res=await getSeaList({...query, poolId:selectedPool.value||undefined, status:statusFilter.value||undefined}); tableData.value=res.data?.records||[]; total.value=res.data?.total||0 } catch(e) {
  } finally { listLoading.value = false }
}
// 筛选变化回到第 1 页（分页 @change 仍用 loadData 保持当前页）
const search = () => { query.current = 1; loadData() }
const loadStats = async () => { try { const res=await getSeaStatistics(); stats.value=res.data||{} } catch(e) {} }
const loadPools = async () => { try { const res=await getVisibleSeaConfigs(); pools.value=res.data||[] } catch(e) {} }

const handleClaim = async (row) => {
  try {
    await ElMessageBox.confirm(`确定捞取「${row.customerName}」？将发起审批申请`,'捞取确认')
    claimingId.value = row.id
    await submitApproval({type:'claim',customerId:String(row.customerId),actionAfterApprove:'客户归属→本人'})
    ElMessage.success('捞取申请已提交'); loadData(); loadStats(); userStore.refreshBadges()
  } catch(e) {
    // 【修复 2026-08-09 B5】区分用户取消与后端 403/失败,不再静默吞错
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '捞取失败')
    }
  } finally {
    claimingId.value = null
  }
}
const handleEdit = (row) => router.push(`/customer/edit/${row.customerId}?from=sea`)
const handleDelete = async (row) => { try { await ElMessageBox.confirm(`确定删除「${row.customerName}」？不可恢复。`,'删除确认',{type:'warning'}); await deleteSeaPool(row.id); ElMessage.success('已删除'); loadData(); loadStats() } catch(e) {} }

const getDays = (row) => { if(!row.enterDate) return 0; return Math.floor((new Date()-new Date(row.enterDate))/(86400000)) }
// 入池天数分档着色：>30 天红、>14 天橙、其余中性
const daysTone = (row) => { const d = getDays(row); return d > 30 ? 'over' : d > 14 ? 'warn' : '' }
const getReasonText = (r) => ({protection_expired:'保护期满',abandoned:'放弃客户',resignation:'离职交接'}[r]||r)
const getStatusText = (s) => ({available:'待捞取',pending_approval:'审批中',claimed:'已捞取'}[s]||s)
const getStatusType = (s) => ({available:'primary',pending_approval:'warning',claimed:'success'}[s]||'info')

const tableWrapRef = ref(null); const tableHeight = ref(300); let resizeObserver = null
const updateTableHeight = () => { if(tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight }
onMounted(async () => {
  loadPools(); loadData(); loadStats()
  try { const res=await getScopeDepts(); deptTree.value=res.data||[] } catch(e) {}
  try { const res=await getDictData('industry'); industryOptions.value=(res.data||[]).map(d=>d.dictValue) } catch(e) { industryOptions.value=['煤矿','化工','电力','洗煤厂','有色'] }
  nextTick(updateTableHeight); if(tableWrapRef.value) { resizeObserver=new ResizeObserver(updateTableHeight); resizeObserver.observe(tableWrapRef.value) }
})
onBeforeUnmount(() => { if(resizeObserver) resizeObserver.disconnect() })
</script>

<style scoped>
.sea-page { height:calc(100vh - 124px); display:flex; flex-direction:column; gap:0 }

/* Hero */
.sea-hero { display:flex; justify-content:space-between; align-items:center; padding:0 0 16px; flex-shrink:0 }
.sea-hero-left h1 { font-size:20px; font-weight:700; color:#1f2329; margin:0 0 4px }
.sea-hero-sub { font-size:13px; color:#909399 }
.sea-hero-right { display:flex; gap:8px; align-items:center }

/* Stats · 深蓝条 */
.sea-strip {
  display:flex; align-items:center; gap:24px;
  background:linear-gradient(135deg,#16337a 0%,#1d4ed8 58%,#2563eb 100%);
  border-radius:12px; padding:14px 24px; margin-bottom:14px; flex-shrink:0;
  box-shadow:0 6px 20px rgba(22,51,122,.14)
}
.ss-metric { min-width:76px }
.ss-num { font-size:24px; font-weight:700; color:#fff; line-height:1.2 }
.ss-num.ss-alert { color:#fca5a5 }
.ss-lbl { font-size:12px; color:rgba(255,255,255,.66); margin-top:2px }
.ss-divider { width:1px; height:34px; background:rgba(255,255,255,.18) }
.ss-tip { margin-left:auto; font-size:12px; color:rgba(255,255,255,.55) }

/* Filter */
.sea-filter { background:#fff; border-radius:10px; padding:14px 18px; margin-bottom:12px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 1px 3px rgba(0,0,0,.04); flex-shrink:0 }
.filter-summary { display:flex; align-items:center; gap:8px; margin:-4px 0 12px; color:#909399; font-size:12px; flex-shrink:0 }
.sea-status-tabs { display:flex; gap:4px; background:#f2f4f7; padding:3px; border-radius:8px }
.sea-tab { padding:5px 16px; border-radius:7px; font-size:13px; cursor:pointer; color:#606266; transition:all .2s; font-weight:500 }
.sea-tab:hover { background:rgba(255,255,255,.6) }
.sea-tab.active { background:#fff; color:#1d4ed8; font-weight:600; box-shadow:0 1px 3px rgba(0,0,0,.08) }
.sea-filters { display:flex; gap:8px; align-items:center }
.f-sel { width:120px } .f-kw { width:200px } .f-dept { width:180px }

/* Table */
.sea-table-wrap { flex:1; min-height:0; display:flex; flex-direction:column; background:#fff; border-radius:10px; box-shadow:0 1px 3px rgba(0,0,0,.04); overflow:hidden }
.sea-table-inner { flex:1; min-height:0; padding:8px 12px 0 }
.sea-page :deep(.el-table td.el-table__cell), .sea-page :deep(.el-table th.el-table__cell) { height:38px; padding:3px 0 }
.sea-link { color:#409eff; cursor:pointer; font-weight:500 } .sea-link:hover { color:#66b1ff; text-decoration:underline }
/* 入池天数按停留时长分档（此前一律橙色，与「待审批/超期」语义混用） */
.sea-days { font-weight:700; color: var(--ds-text-2, #4e5969) }
.sea-days.warn { color:#e6a23c } .sea-days.over { color:#f56c6c }
:deep(.sea-op-col .cell) { white-space: nowrap }
.sea-pager { display:flex; justify-content:space-between; align-items:center; padding:10px 16px; border-top:1px solid #f0f2f5; color:#909399; font-size:12px; flex-shrink:0; background:#fafbfc }
.sea-pager-info { color:#909399; white-space:nowrap }

.import-step { margin-bottom:16px } .step-label { font-size:14px; font-weight:600; color:#333; margin-bottom:8px }
.import-result { margin-top:16px } .fail-list { max-height:200px; overflow-y:auto; margin-top:8px; border:1px solid #ebeef5; border-radius:4px; padding:8px; background:#fafafa }
.fail-header { font-size:13px; font-weight:600; color:#333; margin-bottom:4px }
.fail-item { font-size:12px; line-height:22px; display:flex; gap:8px }
.fail-row { color:#999; width:60px; flex-shrink:0 } .fail-name { color:#333 } .fail-reason { color:#f56c6c; flex:1 }
</style>
