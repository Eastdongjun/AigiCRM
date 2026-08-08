<template>
  <div class="customer-list">
    <!-- 顶部工具栏 -->
    <div class="top-bar">
      <el-input v-model="searchText" placeholder="搜索客户名称..." clearable size="default" style="width:240px" @change="search" />
        <el-button @click="showAdvFilter=true"><el-icon><Filter /></el-icon> 高级筛选<el-tag v-if="advFilterCount>0" size="small" type="warning" style="margin-left:4px">{{ advFilterCount }}</el-tag></el-button>
      <div class="top-bar-right">
        <el-button :icon="Upload" @click="showImportDialog=true">批量导入</el-button>
        <el-button :icon="Download" @click="handleExport">导出</el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push('/customer/add')">新客户</el-button>
      </div>
    </div>

    <el-row :gutter="16" style="margin-bottom:12px">
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-blue"><div class="stat-row"><div class="stat-icon-box blue"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="5"/><path d="M3 21v-2a7 7 0 0 1 7-7h4a7 7 0 0 1 7 7v2"/></svg></div><div class="stat-info"><div class="stat-num">{{ filteredAllData.length }}</div><div class="stat-label">客户总数</div></div></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-orange"><div class="stat-row"><div class="stat-icon-box orange"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg></div><div class="stat-info"><div class="stat-num">{{ tabs.find(t=>t.type===activeTab)?.count||0 }}</div><div class="stat-label">{{ tabs.find(t=>t.type===activeTab)?.label||'' }}</div></div></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-red"><div class="stat-row"><div class="stat-icon-box red"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><div class="stat-info"><div class="stat-num">{{ tableData.filter(c=>c.status==='warning'||c.status==='expired').length }}</div><div class="stat-label">预警/超期</div></div></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover" class="stat-card stat-green"><div class="stat-row"><div class="stat-icon-box green"><svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg></div><div class="stat-info"><div class="stat-num">{{ tableData.filter(c=>c.status==='expired').length }}</div><div class="stat-label">已过期</div></div></div></el-card></el-col>
    </el-row>

    <!-- Tab 切换 -->
    <el-card shadow="never" class="main-card" :style="{ borderColor: activeTab==='innovate'?'#409eff':activeTab==='break'?'#e6a23c':'#67c23a', borderWidth:'2px' }">
      <el-tabs v-model="activeTab" @tab-change="onTabChange" class="customer-tabs">
        <el-tab-pane v-for="tab in tabs" :key="tab.type" :label="tab.label" :name="tab.type">
          <template #label>
            <span class="tab-label">
              <span class="tab-dot" :style="{ background: tab.color }"></span>
              {{ tab.label }}
              <el-tag :color="tab.color" size="small" effect="dark" round style="margin-left:6px">{{ tab.count }}</el-tag>
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-input v-model="filters.search" placeholder="搜索..." size="small" clearable style="width:160px" />
        <el-select v-model="filters.industry" placeholder="行业" size="small" clearable style="width:100px">
          <el-option v-for="ind in allIndustries" :key="ind" :label="ind" :value="ind" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" size="small" clearable style="width:90px">
          <el-option label="正常" value="normal" /><el-option label="预警" value="warning" /><el-option label="超期" value="expired" />
        </el-select>
        <el-button size="small" @click="colPickerVisible=true">列设置</el-button>
        <span class="filter-count">共 {{ filteredList.length }} 条</span>
      </div>

      <!-- 表格 -->
      <el-table :data="filteredList" stripe :default-sort="{ prop: 'customerCode', order: 'descending' }"
        @row-click="viewDetail" :row-style="{ cursor: 'pointer' }" style="width:100%">
        <el-table-column type="index" width="50" label="#" sortable :index="(i) => (page - 1) * pageSize + i + 1" />
        <el-table-column v-if="vis('customerCode')" prop="customerCode" label="编号" min-width="140" sortable />
        <el-table-column v-if="vis('customerName')" prop="customerName" label="客户全称" min-width="220" sortable />
        <el-table-column v-if="vis('invoiceName')" prop="invoiceName" label="开票名称" width="130" show-overflow-tooltip sortable />        <el-table-column v-if="vis('groupName')" prop="groupName" label="集团" width="100" show-overflow-tooltip sortable />
        <el-table-column v-if="vis('branchCompany')" prop="branchCompany" label="分公司" width="100" show-overflow-tooltip sortable />
        <el-table-column v-if="vis('unitName')" prop="unitName" label="所属单位" width="100" show-overflow-tooltip sortable />
        <el-table-column v-if="vis('industry')" prop="industry" label="行业" width="70" sortable />
        <el-table-column v-if="vis('capacity')" prop="capacity" label="产能" width="70" sortable />
        <el-table-column v-if="vis('province')" label="省市" width="70" show-overflow-tooltip sortable prop="province">
          <template #default="{ row }">{{ row.province }}{{ row.city }}</template>
        </el-table-column>
        <el-table-column v-if="vis('address')" prop="address" label="厂址" width="130" show-overflow-tooltip sortable />
        <el-table-column v-if="vis('registerDate')" prop="registerDate" label="注册日" width="90" sortable />
        <el-table-column v-if="vis('lastInvoiceDate')" prop="lastInvoiceDate" label="最近开票" width="90" sortable />
        <el-table-column v-if="vis('transferDate')" prop="transferDate" label="转移日" width="90" sortable />
        <el-table-column v-if="vis('expireDate')" prop="expireDate" label="到期日" width="90" sortable>
          <template #default="{ row }">
            <span :style="{ color: getExpireColor(row), fontWeight: 'bold' }">{{ row.expireDate || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="vis('adjustDate')" prop="adjustDate" label="调整日" width="90" sortable>
          <template #default="{ row }"><span v-if="row.adjustDate">{{ row.adjustDate }}</span><span v-else>—</span></template>
        </el-table-column>
        <el-table-column v-if="vis('regionName')" prop="regionName" label="大区" width="70" sortable />
        <el-table-column v-if="vis('officeName')" prop="officeName" label="办事处" width="80" sortable />
        <el-table-column v-if="vis('deptName')" prop="deptName" label="部门" width="80" sortable />
        <el-table-column v-if="vis('ownerName')" prop="ownerName" label="负责人" width="70" sortable />
        <el-table-column v-if="vis('managerName')" prop="managerName" label="上级" width="70" sortable />
        <el-table-column label="状态" width="90" align="center" sortable prop="status">
          <template #default="{ row }">
            <el-tag v-if="isShared(row)" type="warning" size="small" effect="dark" round>被分享</el-tag>
            <el-tag v-else :type="getStatusType(row)" size="small" effect="dark" round>{{ getStatusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="保护" width="80" align="center" sortable prop="protectionStatus">
          <template #default="{ row }">
            <span class="protect-tag" :class="getProtectClass(row)">{{ getProtectText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="canDelete" label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="!isShared(row)">
              <el-button type="danger" link size="small" @click.stop="openRelease(row)">释放</el-button>
              <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
            </template>
            <span v-else style="color:#c0c4cc;font-size:12px">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <div :style="{ borderTopColor: activeTab==='innovate'?'#409eff':activeTab==='break'?'#e6a23c':'#67c23a', borderTopWidth:'2px' }" style="display:flex;justify-content:flex-end;padding:4px 16px;background:#fff;border-top-style:solid;flex-shrink:0">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[30, 50, 100]"
        :total="currentTabData.length"
        layout="total, sizes, prev, pager, next, jumper"
        background
        size="small"
      />
    </div>

    <el-dialog v-model="colPickerVisible" title="列设置" width="520px" :close-on-click-modal="false">
      <div style="margin-bottom:8px;display:flex;gap:8px">
        <el-button size="small" @click="visibleCols=allCols.map(c=>c.key)">全选</el-button>
        <el-button size="small" @click="visibleCols=[]">全不选</el-button>
      </div>
      <el-row :gutter="12">
        <el-col :span="8" v-for="col in allCols" :key="col.key" style="margin-bottom:6px">
          <el-checkbox v-model="visibleCols" :label="col.key" :value="col.key" border size="small" style="width:100%">{{ col.label }}</el-checkbox>
        </el-col>
      </el-row>
      <template #footer><el-button @click="colPickerVisible=false">取消</el-button><el-button type="primary" @click="colPickerVisible=false">确定</el-button></template>
    </el-dialog>

    <!-- 高级筛选 -->
    <el-dialog v-model="showAdvFilter" title="高级筛选" width="680px" :close-on-click-modal="false" destroy-on-close>
      <el-tabs v-model="advFilterTab" type="border-card">
        <el-tab-pane label="基本筛选" name="basic">
          <el-form label-width="70px" label-position="right">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="行业"><el-select v-model="advFilter.industry" clearable placeholder="全部" style="width:100%"><el-option v-for="ind in allIndustries" :key="ind" :label="ind" :value="ind"/></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="省"><el-input v-model="advFilter.province" placeholder="如：山西" clearable/></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="市"><el-input v-model="advFilter.city" placeholder="如：太原" clearable/></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="状态"><el-select v-model="advFilter.status" clearable placeholder="全部" style="width:100%"><el-option label="正常" value="normal"/><el-option label="预警" value="warning"/><el-option label="超期" value="expired"/></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="保护"><el-select v-model="advFilter.protect" clearable placeholder="全部" style="width:100%"><el-option label="保护中" value="active"/><el-option label="即将到期" value="expiring"/><el-option label="已到期" value="expired"/></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="负责人"><el-select v-model="advFilter.owner" clearable filterable placeholder="全部" style="width:100%"><el-option v-for="u in filterUserOptions" :key="u.id" :label="u.realName" :value="u.realName"/></el-select></el-form-item></el-col>
            </el-row>
            <el-divider content-position="left">日期范围</el-divider>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="注册日"><el-date-picker v-model="advFilter.regDate" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="到期日"><el-date-picker v-model="advFilter.expDate" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
            </el-row>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="组织/人员" name="org">
          <el-form label-width="70px">
            <el-form-item label="所属部门" v-if="canFilterByOrg">
              <el-tree-select
                v-model="advFilter.deptId"
                :data="filterOrgTree"
                clearable
                check-strictly
                node-key="id"
                :props="{ label: 'deptName', children: 'children' }"
                placeholder="选择部门（含全部子部门）"
                style="width:100%"
                @change="onDeptFilterChange" />
            </el-form-item>
            <el-form-item label="组织" v-if="canFilterByOrg">
              <el-tree-select
                v-model="advFilter.orgIds"
                :data="filterOrgTree"
                multiple
                check-strictly
                show-checkbox
                node-key="id"
                :props="{ label: 'orgName', children: 'children' }"
                placeholder="选择组织（可多选）"
                style="width:100%"
                clearable
                collapse-tags
              />
              <div class="form-tip">选中组织后，将显示该组织下所有人员的客户</div>
            </el-form-item>
            <el-form-item label="人员" v-if="canFilterByUsers">
              <el-select v-model="advFilter.userIds" multiple filterable placeholder="选择人员（可多选）" style="width:100%" clearable collapse-tags>
                <el-option v-for="u in filterUserOptions" :key="u.id" :label="u.realName" :value="u.id"/>
              </el-select>
            </el-form-item>
            <el-empty v-if="!canFilterByOrg && !canFilterByUsers" description="当前账号无组织/人员筛选权限，仅可查看自己数据" :image-size="60"/>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="clearAdvFilter">清空全部</el-button>
        <el-button @click="showAdvFilter=false">取消</el-button>
        <el-button type="primary" @click="showAdvFilter=false">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="showImportDialog" title="批量导入客户" width="500px" class="import-dialog" :close-on-click-modal="false">
      <div class="import-step"><div class="step-label">1. 下载模板</div>
        <el-button type="primary" plain size="small" @click="downloadTemplate">下载导入模板 (.xlsx)</el-button></div>
      <div class="import-step"><div class="step-label">2. 选择文件</div>
        <el-upload ref="importUpload" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleFileChange" drag>
          <el-icon class="el-icon--upload"><Upload /></el-icon><div class="el-upload__text">拖拽文件或<em>点击上传</em></div>
        </el-upload></div>
      <div v-if="importResult" class="import-result">
        <el-alert :title="importResult.title" :type="importResult.type" :closable="false" show-icon />
        <div v-if="importResult.failList && importResult.failList.length" class="fail-list">
          <div class="fail-header">失败明细</div>
          <div v-for="(item, idx) in importResult.failList" :key="idx" class="fail-item">
            <span class="fail-row">第{{ item.row }}行</span><span class="fail-name">{{ item.name }}</span><span class="fail-reason">{{ item.reason }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showImportDialog=false">取消</el-button>
        <el-button type="primary" @click="doImport" :disabled="!importFile">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 释放确认弹窗 -->
    <el-dialog v-model="showReleaseDialog" title="释放客户到公海池" width="480px" :close-on-click-modal="false">
      <div style="padding: 8px 0;">
        <p style="margin-bottom: 12px; color: #303133; font-size: 15px;">
          确认将客户 <strong style="color: #e6a23c;">{{ releaseTarget?.customerName }}</strong> 释放到公海池？
        </p>
        <el-form label-width="80px">
          <el-form-item label="释放原因">
            <el-input v-model="releaseReason" placeholder="请输入释放原因" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
        <el-alert type="warning" :closable="false" show-icon style="margin-top: 4px;">
          <template #title>
            释放后，该客户的负责人将被清空，所有分享关系将被删除。其他用户可从公海池捞取该客户。
          </template>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="showReleaseDialog = false">取消</el-button>
        <el-button type="danger" :loading="releaseLoading" @click="confirmRelease">确认释放</el-button>
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
import { paginate } from '@/utils/pagination'
import { filterCustomers } from '@/utils/list-filter'

const colPickerVisible = ref(false)
const activeTab = ref('innovate')
const filters = reactive({ search: "", industry: "", status: "" })
const showAdvFilter = ref(false)
const advFilterTab = ref('basic')
const canFilterByOrg = ref(false)
const canFilterByUsers = ref(false)
const filterOrgTree = ref([])
const filterUserOptions = ref([])
const advFilter = reactive({ industry:'', province:'', city:'', status:'', protect:'', owner:'', regDate:null, expDate:null, orgIds:[], userIds:[], deptId:null, deptIds:[] })

// 部门筛选：选中部门 → 收集其全部子部门 id（部门可无限嵌套）
const onDeptFilterChange = (val) => {
  // 筛选变化必须重置页码，否则过滤后停在旧页码可能越界白屏（分页最佳实践）
  page.value = 1
  const ids = []
  const collect = (nodes) => {
    for (const n of nodes || []) {
      ids.push(n.id)
      if (n.children?.length) collect(n.children)
    }
  }
  if (val) {
    const find = (nodes) => {
      for (const n of nodes || []) {
        if (n.id === val) return n
        const hit = find(n.children)
        if (hit) return hit
      }
      return null
    }
    const node = find(filterOrgTree.value)
    collect(node ? [node] : [])
  }
  advFilter.deptIds = ids
}

const allCols = [
  { key: 'customerCode', label: '编号' },{ key: 'customerName', label: '客户全称' },{ key: 'invoiceName', label: '开票名称' },
  { key: 'groupName', label: '集团' },{ key: 'branchCompany', label: '分公司' },
  { key: 'unitName', label: '所属单位' },{ key: 'industry', label: '行业' },{ key: 'capacity', label: '产能' },
  { key: 'province', label: '省市' },{ key: 'address', label: '厂址' },{ key: 'registerDate', label: '注册日' },
  { key: 'lastInvoiceDate', label: '最近开票' },{ key: 'transferDate', label: '转移日' },{ key: 'expireDate', label: '到期日' },
  { key: 'adjustDate', label: '调整日' },{ key: 'regionName', label: '大区' },{ key: 'officeName', label: '办事处' },
  { key: 'deptName', label: '部门' },
]
const visibleCols = ref(allCols.map(c => c.key))
const vis = (key) => visibleCols.value.includes(key)

const tabs = computed(() => {
  const all = [
    { type: 'all', label: '全部', color: '#303133', count: filteredAllData.value.length, data: filteredAllData.value }
  ]
  const types = { '成熟创新': { type: 'innovate', label: '成熟创新', color: '#409eff' },
    '突破开拓': { type: 'break', label: '突破开拓', color: '#e6a23c' },
    '铺垫宣传': { type: 'found', label: '铺垫宣传', color: '#67c23a' } }
  const grouped = Object.entries(types).map(([devType, cfg]) => ({
    ...cfg,
    count: filteredAllData.value.filter(c => c.devType === devType).length,
    data: filteredAllData.value.filter(c => c.devType === devType)
  }))
  return [...all, ...grouped]
})


const filteredAllData = computed(() =>
  filterCustomers(tableData.value, searchText.value, filters, advFilter, {
    uidsSource: tableData.value, orgKeys: ['officeId', 'orgId'],
  })
)

const currentTabData = computed(() => {
  const tab = tabs.value.find(t => t.type === activeTab.value)
  if (!tab) return []
  return filterCustomers(tab.data, filters.search, filters, advFilter, {
    uidsSource: tableData.value, orgKeys: ['officeId', 'deptId'],
  })
})

const page = ref(1)
const pageSize = ref(30)
const filteredList = computed(() => paginate(currentTabData.value, page.value, pageSize.value))

const advFilterCount = computed(() => { let n=0; for(const k in advFilter){ const v=advFilter[k]; if(v){ if(k==='orgIds'||k==='userIds'){ if(v.length)n++ } else if(typeof v==='string' && v.trim())n++ } } return n })
const loadFilterData = async () => {
  try {
    const token = localStorage.getItem('token')
    const infoRes = await fetch('/api/auth/info', {headers:{Authorization:'Bearer '+token}}).then(r=>r.json())
    const user = infoRes?.data
    if (user) {
      canFilterByOrg.value = user.dataScope === 'all' || (user.managedOrgIds||[]).length > 0
      if (canFilterByOrg.value) {
        const orgRes = await fetch('/api/org/tree', {headers:{Authorization:'Bearer '+token}}).then(r=>r.json())
        filterOrgTree.value = orgRes?.data || []
      }
      if (user.dataScope === 'all' || user.dataScope === 'org_and_children' || (user.managedOrgIds||[]).length > 0) {
        // 用简易用户接口（只需 customer:view，不需要 system:user:view——避免销售员等角色 403）
        const userRes = await fetch('/api/customer/users/simple', {headers:{Authorization:'Bearer '+token}}).then(r=>r.json())
        filterUserOptions.value = (userRes?.data || []).map(u => ({id:u.id, realName:u.realName}))
        canFilterByUsers.value = filterUserOptions.value.length > 1
      }
    }
  } catch(e) {}
}
const clearAdvFilter = () => { Object.assign(advFilter, {industry:'',province:'',city:'',status:'',protect:'',owner:'',regDate:null,expDate:null,orgIds:[],userIds:[]}) }
const onTabChange = () => { page.value = 1; filters.search = ''; filters.industry = ''; filters.status = '' }
import { getDeptList, getDictData, getPublicConfig } from '@/api/system'
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
// 客户业务参数(声明必须先于列设置数组使用,避免 TDZ)
const protectionDays = ref(360)
const warningDays = ref(30)
const adjustDays = ref(180)
const allColumns = [
  { key: 'customerCode', label: '编号', disabled: true },
  { key: 'customerName', label: '订货客户全称', disabled: true },
  { key: 'invoiceName', label: '开票名称' },
  { key: 'groupName', label: '所属集团' },
  { key: 'branchCompany', label: '所属分公司' },
  { key: 'unitName', label: '所属单位' },
  { key: 'industry', label: '行业' },
  
  { key: 'capacity', label: '产能' },
  { key: 'province', label: '省市县' },
  { key: 'registerDate', label: '注册日' },
  { key: 'lastInvoiceDate', label: '最近开票' },
  { key: 'transferDate', label: '转移日' },
  { key: 'expireDate', label: `到期日(${protectionDays})` },
  { key: 'adjustDate', label: `调整日(${adjustDays})` },
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

// 筛选选项懒加载:页面初始不再全量拉客户(省一个 200KB+ 请求),首次打开筛选面板时才加载并缓存
let filterOptionsLoaded = false
const loadFilterOptions = async () => {
  if (filterOptionsLoaded) return
  filterOptionsLoaded = true
  try {
    const res = await getCustomerList({ current:1, size:500 })
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
  loadFilterOptions()
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
  // 显示分页变量是 page（el-pagination v-model:current-page="page"），筛选后必须重置，
  // 否则过滤结果变少时停在旧页码可能越界白屏（分页最佳实践）
  page.value = 1
  loadData()
}

const handleFilterIndustry = (industry) => {
  query.industry = industry || ''
  search()
}

const loadData = async () => {
  try {
    const params = { current: 1, size: 500, keyword: query.keyword, scope: query.scope }
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
/* sectionState removed */

const allIndustries = computed(() => [...new Set(tableData.value.map(r => r.industry).filter(Boolean))].sort())

const colPickerVisible2 = ref(false)
const tempCols = ref([])
const currentColSection = ref(null)
const selectableCols = [
  { key: 'customerCode', label: '编号' },
  { key: 'customerName', label: '客户全称' },
  { key: 'invoiceName', label: '开票名称' },
  
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
  { key: 'status', label: '状态' },
  { key: 'protect', label: '保护措施' },
]
const defaultSectionCols = selectableCols.map(c => c.key)
const sectionColPrefs = reactive({})

const getSectionVis = (type) => {
  const prefs = sectionColPrefs[type]
  return (key) => prefs ? prefs.includes(key) : defaultSectionCols.includes(key)
}

/* filteredData removed */

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
/* typeSections removed */

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
  if (days <= warningDays) return 'protect-warning'
  if (days <= adjustDays) return 'protect-watch'
  return 'protect-normal'
}

const getProtectText = (row) => {
  if (!row.expireDate) return '—'
  const days = getDaysLeft(row)
  if (days < 0) return `超期(${Math.abs(days)}天)`
  if (days <= warningDays) return `预警(${days}天)`
  if (days <= adjustDays) return '开发'
  return '年年持续'
}

// 客户业务参数:从公开配置读取(保护期/提醒/调整期),配置改动即时生效,不再硬编码
const loadBizConfig = async () => {
  try {
    const res = await getPublicConfig()
    const cfg = res.data || {}
    if (cfg['customer.protection.days']) protectionDays.value = Number(cfg['customer.protection.days'])
    if (cfg['customer.warning.days']) warningDays.value = Number(cfg['customer.warning.days'])
    if (cfg['customer.adjust.days']) adjustDays.value = Number(cfg['customer.adjust.days'])
  } catch (e) {}
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
  loadBizConfig()
  loadData()
  loadStats()
  loadScopeCounts()
  loadRegions()
  loadIndustryOptions()
  loadFilterData()
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
.stat-card { border-radius: 10px; transition: transform .2s; }
.stat-card:hover { transform: translateY(-2px); }
.stat-row { display: flex; align-items: center; gap: 14px; }
.stat-icon-box { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.stat-icon-box.blue { background: #e8f4fd; color: #409eff; }
.stat-icon-box.orange { background: #fef5e7; color: #e6a23c; }
.stat-icon-box.green { background: #eaf7ea; color: #67c23a; }
.stat-icon-box.red { background: #fef0f0; color: #f56c6c; }
.stat-num { font-size: 24px; font-weight: 700; color: #1f2329; line-height: 1.2; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
.customer-list { padding: 16px; }
.top-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.top-bar-right { margin-left: auto; display: flex; gap: 8px; }
.main-card { border-radius: 10px; }
.main-card :deep(.el-card__body) { padding: 0 16px 16px; }
.customer-tabs :deep(.el-tabs__header) { margin-bottom: 0; }
.tab-label { display: flex; align-items: center; gap: 6px; }
.tab-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.filter-bar { display: flex; gap: 8px; align-items: center; padding: 8px 0; flex-wrap: wrap; }
.filter-count { font-size: 12px; color: #909399; margin-left: auto; }
.main-card :deep(.el-table) { max-height: calc(100vh - 358px); }
.main-card :deep(.el-table__inner-wrapper) { min-height: 0; }
.main-card :deep(.el-table__body-wrapper) { flex: 1; min-height: 0; max-height: calc(100vh - 358px - 57px); overflow-y: auto; }
.main-card :deep(.el-table__header-wrapper) { flex-shrink: 0; }
.main-card :deep(.el-table__empty-block) { flex: 1; }

.customer-list :deep(.el-table th) { white-space: nowrap; }
.customer-list :deep(.el-table th .cell) { display: flex !important; align-items: center !important; gap: 2px; white-space: nowrap; }
.customer-list :deep(.el-table .caret-wrapper) { flex-shrink: 0; margin-left: 0; }

</style>

