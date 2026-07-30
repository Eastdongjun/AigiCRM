<template>
  <div class="customer-list">
    <!-- 顶部工具栏 -->
    <div style="display:flex;gap:8px;align-items:center;margin-bottom:10px;flex-shrink:0">
      <el-input v-model="searchText" placeholder="搜索客户名称..." clearable size="small" style="width:220px" @change="search" />
      <div style="margin-left:auto;display:flex;gap:8px">
        <el-button :icon="Upload" size="small" @click="showImportDialog=true">批量导入</el-button>
        <el-button :icon="Download" size="small" @click="handleExport">导出</el-button>
        <el-button type="primary" :icon="Plus" size="small" @click="$router.push('/customer/add')">新客户</el-button>
      </div>
    </div>

    <!-- 三栏：按开发类型 -->
    <div class="type-sections">
      <div v-for="t in typeSections" :key="t.type" class="type-card" :class="t.cls">
        <div class="type-header">
          <span class="type-dot"></span>
          <span class="type-label">{{ t.label }}</span>
          <span class="type-count">{{ filteredData(t).length }} / {{ t.data.length }}</span>
          <div class="type-header-ctrl">
            <el-input v-model="sectionState[t.type].search" placeholder="搜索..." size="small" clearable style="width:140px" />
            <el-select v-model="sectionState[t.type].filterIndustry" placeholder="行业" size="small" clearable style="width:90px">
              <el-option v-for="ind in allIndustries" :key="ind" :label="ind" :value="ind" />
            </el-select>
            <el-select v-model="sectionState[t.type].filterStatus" placeholder="状态" size="small" clearable style="width:80px">
              <el-option label="正常" value="normal" /><el-option label="预警" value="warning" /><el-option label="超期" value="expired" />
            </el-select>
            <el-button size="small" @click="openColPicker(t)">列设置</el-button>
          </div>
        </div>
        <div class="type-table-wrap">
          <el-table :data="filteredData(t)" stripe style="width: 100%" @row-click="viewDetail" :row-style="{ cursor: 'pointer' }" size="small" max-height="260">
            <el-table-column type="index" width="45" label="#" />
            <el-table-column v-if="t.vis('customerCode')" prop="customerCode" label="编号" width="100" show-overflow-tooltip sortable />
            <el-table-column v-if="t.vis('customerName')" prop="customerName" label="客户全称" width="140" show-overflow-tooltip />
            <el-table-column v-if="t.vis('invoiceName')" prop="invoiceName" label="开票名称" width="120" show-overflow-tooltip />
            <el-table-column v-if="t.vis('devType')" prop="devType" label="开发类型" width="80" />
            <el-table-column v-if="t.vis('groupName')" prop="groupName" label="集团" width="90" show-overflow-tooltip />
            <el-table-column v-if="t.vis('branchCompany')" prop="branchCompany" label="分公司" width="100" show-overflow-tooltip />
            <el-table-column v-if="t.vis('unitName')" prop="unitName" label="所属单位" width="100" show-overflow-tooltip />
            <el-table-column v-if="t.vis('industry')" prop="industry" label="行业" width="60" />
            <el-table-column v-if="t.vis('capacity')" prop="capacity" label="产能" width="60" />
            <el-table-column v-if="t.vis('province')" label="省市" width="60" show-overflow-tooltip>
              <template #default="{ row }">{{ row.province }}{{ row.city }}</template>
            </el-table-column>
            <el-table-column v-if="t.vis('address')" prop="address" label="厂址" width="120" show-overflow-tooltip />
            <el-table-column v-if="t.vis('registerDate')" prop="registerDate" label="注册日" width="80" />
            <el-table-column v-if="t.vis('lastInvoiceDate')" prop="lastInvoiceDate" label="最近开票" width="80" />
            <el-table-column v-if="t.vis('transferDate')" prop="transferDate" label="转移日" width="80" />
            <el-table-column v-if="t.vis('expireDate')" prop="expireDate" label="到期日" width="85" sortable>
              <template #default="{ row }">
                <span :style="{ color: getExpireColor(row), fontWeight: 'bold' }">{{ row.expireDate || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="t.vis('adjustDate')" prop="adjustDate" label="调整日" width="85">
              <template #default="{ row }"><span v-if="row.adjustDate">{{ row.adjustDate }}</span><span v-else>—</span></template>
            </el-table-column>
            <el-table-column v-if="t.vis('regionName')" prop="regionName" label="大区" width="60" />
            <el-table-column v-if="t.vis('officeName')" prop="officeName" label="办事处" width="70" />
            <el-table-column v-if="t.vis('ownerName')" prop="ownerName" label="负责人" width="60" />
            <el-table-column v-if="t.vis('managerName')" prop="managerName" label="管理上级" width="70" />
            <el-table-column v-if="t.vis('remark')" prop="remark" label="备注" width="120" show-overflow-tooltip />
            <el-table-column v-if="t.vis('status')" label="状态" width="90" align="center" sortable prop="status">
              <template #default="{ row }">
                <el-tag v-if="isShared(row)" type="warning" size="small" effect="dark" round>被分享</el-tag>
                <el-tag v-else :type="getStatusType(row)" size="small" effect="dark" round>{{ getStatusText(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="t.vis('protect')" label="保护" width="80" align="center">
              <template #default="{ row }">
                <span class="protect-tag" :class="getProtectClass(row)">{{ getProtectText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="canDelete" label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <template v-if="!isShared(row)">
                  <el-button type="danger" link size="small" @click.stop="openRelease(row)">释放</el-button>
                  <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
                </template>
                <span v-else style="color:#c0c4cc;font-size:12px">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 列选择弹窗 -->
    <el-dialog v-model="colPickerVisible2" title="自定义显示列" width="320px" :close-on-click-modal="true">
      <el-checkbox-group v-model="tempCols">
        <div v-for="col in selectableCols" :key="col.key" style="padding:3px 0"><el-checkbox :value="col.key">{{ col.label }}</el-checkbox></div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="colPickerVisible2=false">取消</el-button>
        <el-button type="primary" @click="confirmColPicker">确定</el-button>
      </template>
    </el-dialog>

    <!-- 筛选值选择弹窗 -->
    <el-dialog v-model="filterDialogVisible" :title="'选择' + (filterFieldOptions[filterDialogField]?.label || '')" width="400px" :close-on-click-modal="true">
      <el-checkbox-group v-model="filterDialogValues">
        <el-checkbox v-for="o in (filterFieldOptions[filterDialogField]?.options || [])" :key="o.value" :label="o.value" :value="o.value" style="display:block;margin-bottom:8px">{{ o.label }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="filterDialogVisible=false">取消</el-button>
        <el-button type="primary" @click="confirmFilter">确定</el-button>
      </template>
    </el-dialog>

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

const isShared = (row) => {
  if (!userStore.userId || userStore.roleName === '系统管理员') return false
  return row.ownerId && row.ownerId !== userStore.userId
}

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
  { key: 'action', label: '操作' },
]
const defaultKeys = allColumns.filter(c => !['action','transferDate'].includes(c.key)).map(c => c.key)
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

const searchText = ref('')

const query = reactive({
  current: 1,
  size: 100,
  keyword: '',
  industry: '',
  industries: [],
  regionId: '',
  regionIds: [],
  status: '',
  statuses: [],
  scope: '',
  filters: []
})

// 多条件筛选
const newFilterField = ref('')
const filterDialogVisible = ref(false)
const filterDialogField = ref('')
const filterDialogValue = ref('')
const filterDialogValues = ref([])

const filterFieldOptions = reactive({
  industry: { label: '行业', options: [] },
  devType: { label: '开发类型', options: [{label:'成熟',value:'成熟'},{label:'突破',value:'突破'}] },
  groupName: { label: '所属集团', options: [] },
  status: { label: '状态', options: [{label:'正常',value:'normal'},{label:'预警',value:'warning'},{label:'超期',value:'expired'}] },
  regionId: { label: '大区', options: [] }
})

const loadFilterOptions = async () => {
  try {
    const res = await getCustomerList({ current:1, size:9999 })
    const all = res.data.records || []
    filterFieldOptions.groupName.options = [...new Set(all.map(r=>r.groupName).filter(Boolean))].sort().map(v=>({label:v,value:v}))
    filterFieldOptions.industry.options = [...new Set(all.map(r=>r.industry).filter(Boolean))].sort().map(v=>({label:v,value:v}))
    filterFieldOptions.regionId.options = regionList.value.map(o=>({label:o.deptName,value:o.id}))
  } catch(e) {}
}

const editFilter = (f) => {
  filterDialogField.value = f.field
  filterDialogValues.value = [...f.value]
  filterDialogVisible.value = true
}

const onAddFilter = (field) => {
  if (!field) return
  filterDialogField.value = field
  filterDialogValues.value = (query.filters.find(f=>f.field===field)?.value || []).slice()
  filterDialogVisible.value = true
  newFilterField.value = ''
}

const confirmFilter = () => {
  const field = filterDialogField.value
  query.filters = query.filters.filter(f => f.field !== field)
  if (filterDialogValues.value.length > 0) {
    const opts = filterFieldOptions[field].options
    const labels = filterDialogValues.value.map(v => { const o = opts.find(o=>o.value===v); return o?o.label:v })
    query.filters.push({ field, label: filterFieldOptions[field].label, value: [...filterDialogValues.value], valueLabel: labels.join(',') })
  }
  filterDialogVisible.value = false
  search()
}

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
  query.keyword = searchText.value
  query.current = 1
  loadData()
}

const handleFilterIndustry = (industry) => {
  query.industry = industry || ''
  search()
}

const loadData = async () => {
  try {
    const params = { current: 1, size: 9999, keyword: query.keyword, scope: query.scope }
    if (query.industry) params.industry = query.industry
    if (query.industries && query.industries.length) params.industry = query.industries.join(',')
    if (query.regionId) params.regionId = query.regionId
    if (query.regionIds && query.regionIds.length) params.regionId = query.regionIds[0]
    if (query.status) params.status = query.status
    if (query.statuses && query.statuses.length) params.status = query.statuses.join(',')
    if (query.filters && query.filters.length) {
      query.filters.forEach(f => {
        params[f.field] = Array.isArray(f.value) ? f.value.join(',') : f.value
      })
    }
    const res = await getCustomerList(params)
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

// ===== 每栏独立筛选状态 =====
const sectionState = reactive({
  '成熟创新': { search: '', filterIndustry: '', filterStatus: '' },
  '突破开拓': { search: '', filterIndustry: '', filterStatus: '' },
  '铺垫宣传': { search: '', filterIndustry: '', filterStatus: '' },
})

const allIndustries = computed(() => [...new Set(tableData.value.map(r => r.industry).filter(Boolean))].sort())

const colPickerVisible2 = ref(false)
const tempCols = ref([])
const currentColSection = ref(null)
const selectableCols = [
  { key: 'customerCode', label: '编号' },
  { key: 'customerName', label: '客户全称' },
  { key: 'invoiceName', label: '开票名称' },
  { key: 'devType', label: '开发类型' },
  { key: 'groupName', label: '所属集团' },
  { key: 'branchCompany', label: '分公司' },
  { key: 'unitName', label: '所属单位' },
  { key: 'industry', label: '行业' },
  { key: 'capacity', label: '产能' },
  { key: 'province', label: '省市' },
  { key: 'address', label: '厂址' },
  { key: 'registerDate', label: '注册日' },
  { key: 'lastInvoiceDate', label: '最近开票' },
  { key: 'transferDate', label: '转移日' },
  { key: 'expireDate', label: '到期日' },
  { key: 'adjustDate', label: '调整日' },
  { key: 'regionName', label: '大区' },
  { key: 'officeName', label: '办事处' },
  { key: 'ownerName', label: '负责人' },
  { key: 'managerName', label: '管理上级' },
  { key: 'remark', label: '备注' },
  { key: 'status', label: '状态' },
  { key: 'protect', label: '保护措施' },
]
const defaultSectionCols = selectableCols.map(c => c.key)
const sectionColPrefs = reactive({})

const getSectionVis = (type) => {
  const prefs = sectionColPrefs[type]
  return (key) => prefs ? prefs.includes(key) : defaultSectionCols.includes(key)
}

const filteredData = (t) => {
  const st = sectionState[t.type]
  if (!st) return t.data
  let rows = t.data
  const kw = (st.search || '').toLowerCase()
  if (kw) rows = rows.filter(r => (r.customerName || '').toLowerCase().includes(kw) || (r.customerCode || '').toLowerCase().includes(kw))
  if (st.filterIndustry) rows = rows.filter(r => r.industry === st.filterIndustry)
  if (st.filterStatus) rows = rows.filter(r => r.status === st.filterStatus)
  return rows
}

const openColPicker = (t) => {
  currentColSection.value = t.type
  tempCols.value = [...(sectionColPrefs[t.type] || defaultSectionCols)]
  colPickerVisible2.value = true
}

const confirmColPicker = () => {
  if (currentColSection.value) {
    sectionColPrefs[currentColSection.value] = [...tempCols.value]
  }
  colPickerVisible2.value = false
}

// ===== 按开发类型三栏分组 =====
const typeSections = computed(() => {
  const order = ['成熟创新', '突破开拓', '铺垫宣传']
  const config = {
    '成熟创新': { cls: 'type-innovate', label: '成熟创新' },
    '突破开拓': { cls: 'type-break', label: '突破开拓' },
    '铺垫宣传': { cls: 'type-found', label: '铺垫宣传' },
  }
  return order.map(type => {
    const data = tableData.value.filter(r => (r.devType || '') === type)
    return {
      type, cls: config[type]?.cls || '', label: config[type]?.label || type, data,
      search: sectionState[type]?.search || '',
      filterIndustry: sectionState[type]?.filterIndustry || '',
      filterStatus: sectionState[type]?.filterStatus || '',
      vis: getSectionVis(type),
      _searchRef: sectionState[type],
    }
  }).filter(t => t.data.length > 0)
})

const viewDetail = (row) => {
  router.push(`/customer/detail/${row.id}`)
}

const handleExport = () => {
  const params = new URLSearchParams()
  if (query.keyword) params.append('keyword', query.keyword)
  if (query.industry) params.append('industry', query.industry)
  if (query.industries && query.industries.length) query.industries.forEach(v => params.append('industries', v))
  if (query.regionId) params.append('regionId', query.regionId)
  if (query.regionIds && query.regionIds.length) query.regionIds.forEach(v => params.append('regionIds', v))
  if (query.status) params.append('status', query.status)
  if (query.statuses && query.statuses.length) query.statuses.forEach(v => params.append('statuses', v))
  if (query.filters && query.filters.length) {
    query.filters.forEach(f => {
      if (Array.isArray(f.value)) f.value.forEach(v => params.append('filter_'+f.field, v))
      else params.append('filter_'+f.field, f.value)
    })
  }
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
  loadFilterOptions()
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
.filter-row { display:flex; gap:8px; align-items:center; flex-wrap:wrap; margin-bottom:10px; padding:0 }
.filter-row .el-tag { cursor:default }

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

.customer-list { display:flex; flex-direction:column; height:calc(100vh - 90px) }
.section-box { background:#fff; border:1px solid #e4e7ed; border-radius:8px; padding:14px 20px; margin-bottom:8px; box-shadow:0 1px 3px rgba(0,0,0,.04); flex-shrink:0 }
.section-box.table-box { flex:1; padding:0; display:flex; flex-direction:column; overflow:hidden; min-height:0 }
.section-box.table-box > div:first-child { flex:1; overflow:auto }
.type-sections { display: flex; flex-direction: column; gap: 10px; flex: 1; min-height: 0; overflow: auto }
.stats-bar { display:flex; gap:40px; align-items:center; flex-wrap:wrap; padding:4px 0 }
.stat-item { display:flex; align-items:center; gap:14px }
.stat-icon { width:48px; height:48px; border-radius:12px; display:flex; align-items:center; justify-content:center }
.stat-icon svg { width:22px; height:22px }
.stat-icon.c-blue { background:#e8f0fe; color:#1a73e8 }
.stat-icon.c-green { background:#e6f4ea; color:#137333 }
.stat-icon.c-orange { background:#fef0e0; color:#e37400 }
.stat-icon.c-purple { background:#f0e8fd; color:#7627bf }
.stat-num { font-size:24px; font-weight:800; color:#1f2329; line-height:1.2 }
.stat-lbl { font-size:13px; color:#888; margin-top:2px }

.table-area {
  flex: 1;
  min-width: 0;
  background:#fff;
  border:1px solid #e4e7ed;
  border-radius:10px;
  overflow:hidden;
  box-shadow:0 1px 3px rgba(0,0,0,.04);
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
.table-area :deep(.el-table th.el-table__cell) { font-size:14px; font-weight:700; color:#1f2329 }
.table-area :deep(.el-table td.el-table__cell) { font-size:13px }
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 20px;
  border-top: 1px solid #ebeef5;
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
  line-height: 1;
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

/* ===== 三栏分类卡片 ===== */
.type-card { border: 2px solid #e4e7ed; border-radius: 10px; overflow: hidden; background: #fff }
.type-card.type-innovate { border-color: #409eff; background: #f5f9ff }
.type-card.type-break { border-color: #e6a23c; background: #fefaf3 }
.type-card.type-found { border-color: #67c23a; background: #f4faf5 }
.type-header { display: flex; align-items: center; gap: 8px; padding: 8px 16px; border-bottom: 1px solid rgba(0,0,0,.06) }
.type-dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0 }
.type-innovate .type-dot { background: #409eff }
.type-break .type-dot { background: #e6a23c }
.type-found .type-dot { background: #67c23a }
.type-label { font-size: 14px; font-weight: 700; color: #1f2329; white-space: nowrap }
.type-count { font-size: 12px; color: #909399; white-space: nowrap }
.type-header-ctrl { display: flex; align-items: center; gap: 6px; margin-left: auto }
.type-table-wrap { padding: 0 }

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
