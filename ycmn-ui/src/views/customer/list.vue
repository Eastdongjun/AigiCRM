<template>
  <div class="customer-list">
    <!-- 顶部标题行 -->
    <div class="page-header">
      <h2>客户管理</h2>
    </div>

    <!-- 搜索栏 + 按钮 同行 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="搜索客户名称/编号..." clearable prefix-icon="Search" style="width: 200px" @keyup.enter="search" />
        <el-select v-model="query.industry" placeholder="全部行业" clearable style="width: 130px" @change="search">
          <el-option v-for="i in industryOptions" :key="i" :label="i" :value="i" />
        </el-select>
        <el-select v-model="query.regionId" placeholder="全部大区" clearable style="width: 130px" @change="search">
          <el-option v-for="o in regionList" :key="o.id" :label="o.deptName" :value="o.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="search">
          <el-option label="正常" value="normal" />
          <el-option label="预警" value="warning" />
          <el-option label="超期" value="expired" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-popover v-model:visible="colPickerVisible" placement="bottom-end" :width="280" trigger="click">
          <template #reference>
            <el-button :icon="Setting">列设置</el-button>
          </template>
          <div class="col-picker">
            <div class="col-picker-header">
              <span>自定义显示列</span>
              <el-button link type="primary" size="small" @click="tempChecked = [...defaultKeys]">重置</el-button>
            </div>
            <el-checkbox-group v-model="tempChecked">
              <div v-for="col in allColumns" :key="col.key" class="col-picker-item">
                <el-checkbox :value="col.key" :disabled="col.disabled">{{ col.label }}</el-checkbox>
              </div>
            </el-checkbox-group>
            <div class="col-picker-footer">
              <el-button size="small" @click="colPickerVisible = false">取消</el-button>
              <el-button type="primary" size="small" @click="confirmColumns">确定</el-button>
            </div>
          </div>
        </el-popover>
        <el-button :icon="Download" @click="handleExport">导出</el-button>
        <el-button :icon="Upload" @click="showImportDialog = true">批量导入</el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push('/customer/add')">新客户</el-button>
      </div>
    </div>

    <!-- 筛选标签栏 -->
    <div class="scope-tabs">
      <span class="scope-tab" :class="{ active: query.scope === '' }" @click="switchScope('')">
        全部 <b>{{ scopeCounts.all || 0 }}</b>
      </span>
      <span class="scope-tab" :class="{ active: query.scope === 'mine' }" @click="switchScope('mine')">
        我的客户 <b>{{ scopeCounts.mine || 0 }}</b>
      </span>
      <span class="scope-tab" :class="{ active: query.scope === 'shared_by_me' }" @click="switchScope('shared_by_me')">
        我分享的 <b>{{ scopeCounts.sharedByMe || 0 }}</b>
      </span>
      <span class="scope-tab" :class="{ active: query.scope === 'shared_to_me' }" @click="switchScope('shared_to_me')">
        分享给我 <b>{{ scopeCounts.sharedToMe || 0 }}</b>
      </span>
    </div>

    <!-- 主体: 左侧面板 + 右侧表格 -->
    <div class="main-body">
      <!-- 左侧区域面板 -->
      <div class="side-panel">
        <div class="region-card">
          <div class="region-header">
            <el-icon><Location /></el-icon>
            <span>我的区域</span>
          </div>
          <div class="user-card">
            <el-avatar :size="32" class="user-avatar">{{ userStore.realName?.charAt(0) }}</el-avatar>
            <div class="user-meta">
              <div class="user-name">{{ userStore.realName }}</div>
              <div class="user-role">{{ userStore.roleName }} · {{ userStore.officeName }}</div>
            </div>
          </div>
        </div>

        <div class="stat-section">
          <div class="stat-row" :class="{ active: !query.industry }" @click="handleFilterIndustry(null)">
            <el-icon><Connection /></el-icon>
            <span class="stat-name">全部</span>
            <span class="stat-count">{{ customerStats.total || 0 }}</span>
          </div>

          <div class="stat-divider">— 行业分类 —</div>

          <div
            v-for="(item, index) in industryList"
            :key="item.name"
            class="stat-row"
            :class="{ active: query.industry === item.name }"
            @click="handleFilterIndustry(item.name)"
          >
            <span class="color-dot" :style="{ background: industryColors[index % industryColors.length] }"></span>
            <span class="stat-name">{{ item.name }}</span>
            <span class="stat-count">{{ item.count }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧表格 -->
      <div class="table-area">
        <div class="table-wrap" ref="tableWrapRef">
        <el-table :data="tableData" :height="tableHeight" stripe style="width: 100%" @row-click="viewDetail" :row-style="{ cursor: 'pointer' }" size="small" :key="tableKey">
          <el-table-column prop="customerCode" label="编号" min-width="110" fixed show-overflow-tooltip />
          <el-table-column prop="customerName" label="订货客户全称" min-width="160" fixed show-overflow-tooltip />
          <el-table-column v-if="colVisible('invoiceName')" prop="invoiceName" label="开票名称" min-width="120" show-overflow-tooltip />
          <el-table-column v-if="colVisible('groupName')" prop="groupName" label="所属集团" min-width="90" show-overflow-tooltip />
          <el-table-column v-if="colVisible('branchCompany')" prop="branchCompany" label="所属分公司" min-width="100" show-overflow-tooltip />
          <el-table-column v-if="colVisible('unitName')" prop="unitName" label="所属单位" min-width="100" show-overflow-tooltip />
          <el-table-column v-if="colVisible('industry')" prop="industry" label="行业" min-width="60" />
          <el-table-column v-if="colVisible('devType')" prop="devType" label="开发类型" min-width="70" />
          <el-table-column v-if="colVisible('capacity')" prop="capacity" label="产能" min-width="60" />
          <el-table-column v-if="colVisible('province')" label="省市县" min-width="80" show-overflow-tooltip>
            <template #default="{ row }">{{ row.province }}{{ row.city }}</template>
          </el-table-column>
          <el-table-column v-if="colVisible('registerDate')" prop="registerDate" label="注册日" min-width="95" />
          <el-table-column v-if="colVisible('lastInvoiceDate')" prop="lastInvoiceDate" label="最近开票" min-width="95" />
          <el-table-column v-if="colVisible('transferDate')" prop="transferDate" label="转移日" min-width="95" />
          <el-table-column v-if="colVisible('expireDate')" label="到期日(360)" min-width="100">
            <template #default="{ row }">
              <span :style="{ color: getExpireColor(row), fontWeight: 'bold' }">{{ row.expireDate }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="colVisible('adjustDate')" label="调整日(180)" min-width="100">
            <template #default="{ row }">
              <span v-if="row.adjustDate">{{ row.adjustDate }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column v-if="colVisible('regionName')" prop="regionName" label="大区" min-width="70" />
          <el-table-column v-if="colVisible('officeName')" prop="officeName" label="办事处" min-width="80" />
          <el-table-column v-if="colVisible('ownerName')" prop="ownerName" label="负责人" min-width="65" />
          <el-table-column v-if="colVisible('managerName')" prop="managerName" label="管理上级" min-width="70" />
          <el-table-column label="状态" width="65" align="center" fixed="right">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row)" size="small" effect="dark" round>
                {{ getStatusText(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="保护措施" width="95" align="center" fixed="right">
            <template #default="{ row }">
              <span class="protect-tag" :class="getProtectClass(row)">{{ getProtectText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="canDelete ? 100 : 70" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="query.scope !== 'shared_to_me'"
                type="danger"
                link
                size="small"
                @click.stop="openRelease(row)"
              >释放</el-button>
              <el-button
                v-if="canDelete"
                type="danger"
                link
                size="small"
                @click.stop="handleDelete(row)"
              >删除</el-button>
              <span v-if="query.scope === 'shared_to_me' && !canDelete" style="color:#c0c4cc;font-size:12px">—</span>
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

    <!-- 释放弹窗 -->
    <el-dialog v-model="showReleaseDialog" title="释放客户到公海" width="420px" :close-on-click-modal="false">
      <p style="margin-bottom:8px">确认将 <b>{{ releaseTarget?.customerName }}</b> 释放到公海池？</p>
      <p style="margin-bottom:12px;color:#999;font-size:13px">释放后该客户进入公海池，其他人可以捞取。</p>
      <el-radio-group v-model="releaseReason">
        <el-radio label="保护期满">保护期满</el-radio>
        <el-radio label="放弃客户">放弃客户</el-radio>
        <el-radio label="离职交接">离职交接</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="showReleaseDialog = false">取消</el-button>
        <el-button type="danger" :loading="releaseLoading" @click="confirmRelease">确认释放</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="批量导入客户" width="520px" :close-on-click-modal="false" @close="closeImportDialog">
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
              <div class="el-upload__tip">仅支持 .xlsx / .xls 格式，客户名称为必填项</div>
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
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Download, Upload, Setting } from '@element-plus/icons-vue'
import { getCustomerList, getCustomerStatistics, getCustomerListCounts, releaseCustomer, importCustomers, downloadImportTemplate, deleteCustomer } from '@/api/customer'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeptList, getDictData } from '@/api/system'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const canDelete = computed(() => userStore.hasPermission('customer:delete'))

// 表格高度：按容器实际可用高度动态计算，保证分页器固定在底部、与左侧面板底边对齐
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

const tableData = ref([])
const total = ref(0)
const customerStats = ref({})
const scopeCounts = ref({})
const regionList = ref([])
const industryOptions = ref([])

const industryColors = ['#409eff', '#67c23a', '#e6a23c', '#9b59b6', '#e91e63', '#999']

// ===== 自定义列 =====
const COLUMN_STORAGE_KEY = 'customer_list_visible_columns'
const allColumns = [
  { key: 'customerCode', label: '编号', disabled: true },
  { key: 'customerName', label: '订货客户全称', disabled: true },
  { key: 'invoiceName', label: '开票名称' },
  { key: 'groupName', label: '所属集团' },
  { key: 'branchCompany', label: '所属分公司' },
  { key: 'unitName', label: '所属单位' },
  { key: 'industry', label: '行业' },
  { key: 'devType', label: '开发类型' },
  { key: 'capacity', label: '产能' },
  { key: 'province', label: '省市县' },
  { key: 'registerDate', label: '注册日' },
  { key: 'lastInvoiceDate', label: '最近开票' },
  { key: 'transferDate', label: '转移日' },
  { key: 'expireDate', label: '到期日(360)' },
  { key: 'adjustDate', label: '调整日(180)' },
  { key: 'regionName', label: '大区' },
  { key: 'officeName', label: '办事处' },
  { key: 'ownerName', label: '负责人' },
  { key: 'managerName', label: '管理上级' },
  { key: 'status', label: '状态', disabled: true },
  { key: 'protect', label: '保护措施', disabled: true },
  { key: 'action', label: '操作', disabled: true },
]
const defaultKeys = allColumns.map(c => c.key)
const lockedKeys = allColumns.filter(c => c.disabled).map(c => c.key)

const loadSavedColumns = () => {
  try {
    const saved = localStorage.getItem(COLUMN_STORAGE_KEY)
    if (saved) {
      // 旧列迁移: hasTerminal(终端)→unitName(所属单位); 旧办事处列同时恢复"办事处"并新增"大区"
      const arr = JSON.parse(saved).flatMap(k => {
        if (k === 'hasTerminal') return ['unitName']
        if (k === 'officeName') return ['officeName', 'regionName']
        return [k]
      })
      // 大区/办事处成对出现(都显示)
      if (arr.includes('regionName') && !arr.includes('officeName')) arr.push('officeName')
      if (arr.includes('officeName') && !arr.includes('regionName')) arr.push('regionName')
      // 确保锁定列始终存在
      return [...new Set([...lockedKeys, ...arr])]
    }
  } catch (e) {}
  return [...defaultKeys]
}

const checkedColumns = ref(loadSavedColumns())
const colVisible = (key) => checkedColumns.value.includes(key)

// 列选择器弹窗状态
const colPickerVisible = ref(false)
const tempChecked = ref([...checkedColumns.value])
const tableKey = ref(0)

watch(colPickerVisible, (v) => {
  if (v) tempChecked.value = [...checkedColumns.value]
})

const confirmColumns = () => {
  const merged = [...new Set([...lockedKeys, ...tempChecked.value])]
  checkedColumns.value = merged
  localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(merged))
  colPickerVisible.value = false
  // 强制刷新表格以重新计算列宽
  tableKey.value++
}

// 释放相关
const showReleaseDialog = ref(false)
const releaseTarget = ref(null)
const releaseReason = ref('放弃客户')
const releaseLoading = ref(false)

const openRelease = (row) => {
  releaseTarget.value = row
  releaseReason.value = '放弃客户'
  showReleaseDialog.value = true
}

const confirmRelease = async () => {
  releaseLoading.value = true
  try {
    await releaseCustomer(releaseTarget.value.id, releaseReason.value)
    ElMessage.success('客户已释放到公海池')
    showReleaseDialog.value = false
    loadData()
    loadStats()
    loadScopeCounts()
  } catch (e) {
    if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
  } finally {
    releaseLoading.value = false
  }
}

// ===== 删除客户（仅管理员） =====
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除客户 “${row.customerName}”（编号 ${row.customerCode}）？删除后不可恢复。`,
      '删除客户',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await deleteCustomer(row.id)
    ElMessage.success('客户已删除')
    loadData()
    loadStats()
    loadScopeCounts()
  } catch (e) {
    if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
  }
}

// ===== 批量导入 =====
const showImportDialog = ref(false)
const importFile = ref(null)
const importLoading = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

const handleFileChange = (file) => {
  importFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先移除已选文件')
}

const handleDownloadTemplate = async () => {
  try {
    await downloadImportTemplate()
    ElMessage.success('模板下载成功')
  } catch (e) {
    ElMessage.error('模板下载失败')
  }
}

const handleImport = async () => {
  if (!importFile.value) return
  importLoading.value = true
  importResult.value = null
  try {
    const res = await importCustomers(importFile.value)
    importResult.value = res.data
    if (res.data.successCount > 0) {
      loadData()
      loadStats()
      loadScopeCounts()
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

const query = reactive({
  current: 1,
  size: 15,
  keyword: '',
  industry: '',
  regionId: '',
  status: '',
  scope: ''
})

const industryList = computed(() => {
  const stats = customerStats.value.industryStats || {}
  const order = industryOptions.value.length > 0 ? industryOptions.value : ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他']
  return order
    .filter(name => stats[name])
    .map(name => ({ name, count: stats[name] }))
})

const switchScope = (scope) => {
  query.scope = scope
  query.current = 1
  loadData()
}

const search = () => {
  query.current = 1
  loadData()
}

const handleFilterIndustry = (industry) => {
  query.industry = industry || ''
  search()
}

const loadData = async () => {
  try {
    const res = await getCustomerList(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {}
}

const loadStats = async () => {
  try {
    const res = await getCustomerStatistics()
    customerStats.value = res.data || {}
  } catch (e) {}
}

const loadScopeCounts = async () => {
  try {
    const res = await getCustomerListCounts()
    scopeCounts.value = res.data || {}
  } catch (e) {}
}

const viewDetail = (row) => {
  router.push(`/customer/detail/${row.id}`)
}

const handleExport = () => {
  const params = new URLSearchParams()
  if (query.keyword) params.append('keyword', query.keyword)
  if (query.industry) params.append('industry', query.industry)
  if (query.regionId) params.append('regionId', query.regionId)
  if (query.status) params.append('status', query.status)
  const token = localStorage.getItem('token')
  const url = `/api/customer/export?${params.toString()}`
  // 用隐藏 a 标签下载（带 token）
  const link = document.createElement('a')
  link.href = url
  // 通过 fetch + blob 方式下载（支持 Authorization header）
  fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    .then(res => res.blob())
    .then(blob => {
      const blobUrl = URL.createObjectURL(blob)
      link.href = blobUrl
      link.download = '客户列表.xlsx'
      link.click()
      URL.revokeObjectURL(blobUrl)
    })
}

const getExpireColor = (row) => {
  if (row.protectionStatus === 'expired') return '#f56c6c'
  if (row.protectionStatus === 'expiring') return '#e6a23c'
  return '#409eff'
}

const getDaysLeft = (row) => {
  if (!row.expireDate) return 999
  const today = new Date()
  const expire = new Date(row.expireDate)
  return Math.ceil((expire - today) / (1000 * 60 * 60 * 24))
}

const statusMap = {
  normal:  { text: '正常', type: 'success' },
  warning: { text: '预警', type: 'warning' },
  expired: { text: '超期', type: 'danger' },
  sea:     { text: '公海', type: 'info' }
}

const getStatusType = (row) => {
  return statusMap[row.status]?.type || 'info'
}

const getStatusText = (row) => {
  return statusMap[row.status]?.text || '—'
}

const getProtectClass = (row) => {
  if (!row.expireDate) return 'protect-normal'
  const days = getDaysLeft(row)
  if (days < 0) return 'protect-danger'
  if (days <= 30) return 'protect-warning'
  if (days <= 180) return 'protect-watch'
  return 'protect-normal'
}

const getProtectText = (row) => {
  if (!row.expireDate) return '—'
  const days = getDaysLeft(row)
  if (days < 0) return `超期(${Math.abs(days)}天)`
  if (days <= 30) return `预警(${days}天)`
  if (days <= 180) return '开发'
  return '年年持续'
}

const loadRegions = async () => {
  try {
    const res = await getDeptList()
    regionList.value = (res.data || []).filter(d => d.deptType === 2)
  } catch (e) {}
}

const loadIndustryOptions = async () => {
  try {
    const res = await getDictData('industry')
    industryOptions.value = (res.data || []).map(d => d.dictValue)
  } catch (e) { industryOptions.value = ['煤矿','化工','电力','洗煤厂','有色','其他'] }
}

onMounted(() => {
  loadData()
  loadStats()
  loadScopeCounts()
  loadRegions()
  loadIndustryOptions()
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
.page-header {
  margin-bottom: 12px;
}
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }

/* 工具栏: 搜索+按钮同一行 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  gap: 8px;
}
.toolbar-right {
  display: flex;
  gap: 8px;
}

/* 筛选标签栏 */
.scope-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 12px;
  padding: 10px 16px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.scope-tab {
  font-size: 14px;
  color: #666;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 4px;
  transition: all 0.2s;
}
.scope-tab:hover { color: #409eff; }
.scope-tab.active {
  color: #409eff;
  background: #ecf5ff;
  font-weight: 600;
  border: 1px solid #b3d8ff;
}
.scope-tab b { margin-left: 4px; }

/* 主体布局 */
.main-body {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

/* 左侧面板 */
.side-panel {
  width: 200px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.region-card {
  background: linear-gradient(135deg, #1a73e8 0%, #1565c0 100%);
  border-radius: 8px;
  padding: 14px;
  color: #fff;
}
.region-header {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 12px;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar {
  background: rgba(255,255,255,0.25);
  color: #fff;
  font-weight: bold;
  font-size: 14px;
}
.user-meta .user-name { font-size: 14px; font-weight: 600; }
.user-meta .user-role { font-size: 11px; opacity: 0.75; margin-top: 1px; }

/* 统计区 */
.stat-section {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  margin-top: 8px;
  padding: 4px 0;
  flex: 1;
}
.stat-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}
.stat-row:hover { background: #f5f7fa; }
.stat-row.active { background: #ecf5ff; color: #409eff; }
.color-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.stat-name { flex: 1; }
.stat-count { font-weight: 600; color: #333; min-width: 20px; text-align: right; }
.stat-row.active .stat-count { color: #409eff; }
.stat-divider {
  font-size: 11px;
  color: #c0c4cc;
  text-align: center;
  padding: 4px 0;
}

/* 页面撑满视口 */
.customer-list {
  height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
}

/* 右侧表格 */
.table-area {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.table-wrap {
  flex: 1;
  min-height: 0;
}
/* 固定行高 */
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

/* 列选择器 */
.col-picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}
.col-picker-item {
  padding: 2px 0;
}
.col-picker-item :deep(.el-checkbox__label) {
  font-size: 13px;
}
.col-picker-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}

/* 保护措施标签 */
.protect-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  white-space: nowrap;
}
.protect-danger { color: #f56c6c; background: #fef0f0; }
.protect-warning { color: #e6a23c; background: #fdf6ec; }
.protect-watch { color: #409eff; background: #ecf5ff; }
.protect-normal { color: #67c23a; background: #f0f9eb; }

/* 批量导入弹窗 */
.import-dialog .import-step {
  margin-bottom: 16px;
}
.import-dialog .step-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}
.import-result {
  margin-top: 16px;
}
.fail-list {
  max-height: 200px;
  overflow-y: auto;
  margin-top: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  background: #fafafa;
}
.fail-header {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}
.fail-item {
  font-size: 12px;
  line-height: 22px;
  display: flex;
  gap: 8px;
}
.fail-row { color: #999; width: 60px; flex-shrink: 0; }
.fail-name { color: #333; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fail-reason { color: #f56c6c; flex: 1; }
</style>
