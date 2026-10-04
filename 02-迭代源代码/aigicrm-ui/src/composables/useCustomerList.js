// 客户列表页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：customer/list.vue 实例化一次并 provide，
// 顶部栏/统计卡/主表格/分页与 4 个弹窗 inject 同一上下文。
// 共享内容：Tab/搜索/筛选/分页 + 客户数据与计数 + 列设置 + 高级筛选组织人员树 + 弹窗显隐与目标 + 加载刷新动作。
// 弹窗内部工作态（导入文件/导入结果等）留在各弹窗组件内，仅注入共享态与刷新回调。
// 注：本次重构顺带清除历史遗留死代码（三套列设置中的两套、未接线多条件筛选、未接线表格高度机等），
//     保留的列设置 = 模板实际使用的内存态（无 localStorage 持久化）。
import { ref, reactive, computed, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomerList, getCustomerStatistics, getCustomerListCounts, releaseCustomer, deleteCustomer, archiveCustomer, restoreCustomer, getSimpleUserList } from '@/api/customer'
import { getDeptList, getDictData, getDeptTree } from '@/api/system'
import { useUserStore } from '@/store/user'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'
import { paginate } from '@/utils/pagination'
import { filterCustomers } from '@/utils/list-filter'
import { customerEmptyText } from '@/utils/customer-empty-state'
import { customerStatusTagType, customerStatusText } from '@/utils/customer-status'

export const CustomerListKey = Symbol('customerList')

/** 子组件注入父级 customer/list.vue 提供的共享上下文 */
export function injectCustomerList() {
  const ctx = inject(CustomerListKey)
  if (!ctx) throw new Error('injectCustomerList 必须在 customer/list.vue 的组件内使用')
  return ctx
}

export function useCustomerList() {
  const router = useRouter()
  const userStore = useUserStore()

  // ---- 页面导航/筛选态 ----
  const activeTab = ref('innovate')
  const searchText = ref('')
  const filters = reactive({ search: '', industry: '', status: '' })
  // 【议题五.4】归档筛选: 默认隐藏归档客户, 勾选后列表含归档(后端 include_archived)
  const showArchived = ref(false)
  const toggleArchived = (val) => { showArchived.value = val; query.status = ''; query.statuses = []; page.value = 1; loadData() }
  const advFilterTab = ref('basic')
  const advFilter = reactive({ industry: '', province: '', city: '', status: '', protect: '', owner: '', regDate: null, expDate: null, orgIds: [], userIds: [], deptId: null, deptIds: [] })
  const canFilterByOrg = ref(false)
  const canFilterByUsers = ref(false)
  const filterOrgTree = ref([])
  const filterUserOptions = ref([])
  // 服务端查询参数（导出/加载共用）
  const query = reactive({ current: 1, size: 100, keyword: '', industry: '', industries: [], regionId: '', regionIds: [], status: '', statuses: [], scope: '', filters: [] })

  // ---- 数据态 ----
  const tableData = ref([])
  const customerStats = ref({})
  const scopeCounts = ref({})
  const regionList = ref([])
  const industryOptions = ref([])
  const warningDays = ref(30)
  const adjustDays = ref(180)

  // ---- 列设置（内存态，页面级模板实际使用）----
  const allCols = [
    { key: 'customerCode', label: '编号' }, { key: 'customerName', label: '客户全称' }, { key: 'invoiceName', label: '开票名称' },
    { key: 'groupName', label: '集团' }, { key: 'branchCompany', label: '分公司' },
    { key: 'unitName', label: '所属单位' }, { key: 'industry', label: '行业' }, { key: 'capacity', label: '产能' },
    // 【2026-09-13】终端列：字段此前只存在于库与导入模板，列表/详情/表单都无展示或录入入口
    { key: 'hasTerminal', label: '终端' },
    { key: 'province', label: '省市' }, { key: 'address', label: '厂址' }, { key: 'registerDate', label: '注册日' },
    { key: 'lastInvoiceDate', label: '最近开票' }, { key: 'transferDate', label: '转移日' }, { key: 'expireDate', label: '到期日' },
    { key: 'adjustDate', label: '调整日' }, { key: 'regionName', label: '大区' }, { key: 'officeName', label: '办事处' },
    { key: 'deptName', label: '部门' },
  ]
  const visibleCols = ref(allCols.map(c => c.key))
  const vis = (key) => visibleCols.value.includes(key)

  // ---- 弹窗显隐与目标 ----
  const showAdvFilter = ref(false)
  const colPickerVisible = ref(false)
  const showImportDialog = ref(false)
  const showReleaseDialog = ref(false)
  const releaseTarget = ref(null)
  const releaseReason = ref('放弃客户')
  const releaseLoading = ref(false)

  // ---- 分页 ----
  const page = ref(1)
  const pageSize = ref(30)

  // ---- 计算 ----
  const canDelete = computed(() => userStore.hasPermission('customer:delete'))
  const canRelease = computed(() => userStore.hasPermission('customer:edit'))

  const tabs = computed(() => {
    const all = [
      { type: 'all', label: '全部', color: '#303133', count: filteredAllData.value.length, data: filteredAllData.value }
    ]
    const types = { '成熟创新': { type: 'innovate', label: '成熟创新', color: '#409eff' },
      '突破开拓': { type: 'break', label: '突破开拓', color: '#e6a23c' },
      '铺垫宣传': { type: 'found', label: '铺垫宣传', color: '#67c23a' },
      // 【2026-09-23 需求修正】「新建改建扩建大修」是一个完整分类词(V2026.09.23.6 撤销拆分),客户列表加本分类页签
      '新建改建扩建大修': { type: 'engineering', label: '新建改建扩建大修', color: '#64748b' } }
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
    // 【2026-09-23 UI】搜索合并:原 filters.search(表内第二个搜索框)与顶部 searchText 并存致同页双搜索框;
    // 统一由顶部 searchText 驱动(同时作用于全量与当前Tab),filters.search 仅作兼容字段保留
    return filterCustomers(tab.data, searchText.value, filters, advFilter, {
      uidsSource: tableData.value, orgKeys: ['officeId', 'deptId'],
    })
  })

  const filteredList = computed(() => paginate(currentTabData.value, page.value, pageSize.value))

  const emptyTableText = computed(() => customerEmptyText(tableData.value))

  const advFilterCount = computed(() => {
    let n = 0
    for (const k in advFilter) {
      // 【M4 2026-10-04】角标补统计部门条件：deptIds 由 deptId 派生（onDeptFilterChange
      // 收集子部门），只按 deptId 计 1 个条件，避免同一条件双计
      if (k === 'deptIds') continue
      if (k === 'deptId') { if (advFilter.deptId) n++; continue }
      const v = advFilter[k]
      if (v) {
        if (k === 'orgIds' || k === 'userIds') { if (v.length) n++ }
        else if (typeof v === 'string' && v.trim()) n++
      }
    }
    return n
  })

  const allIndustries = computed(() => [...new Set(tableData.value.map(r => r.industry).filter(Boolean))].sort())

  // ---- 表格辅助 ----
  const isShared = (row) => {
    if (!userStore.userId || userStore.roleName === '系统管理员') return false
    return row.ownerId && row.ownerId !== userStore.userId
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
  // 状态字典统一在 utils/customer-status.js（提醒详情页复用同口径，勿再内联副本）
  const getStatusType = (row) => customerStatusTagType(row?.status)
  const getStatusText = (row) => customerStatusText(row?.status)
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

  // ---- 加载 ----
  const loadData = async () => {
    try {
      const params = { current: 1, size: 500, keyword: query.keyword, scope: query.scope }
      if (query.industry) params.industry = query.industry
      if (query.industries && query.industries.length) params.industry = query.industries.join(',')
      if (query.regionId) params.regionId = query.regionId
      if (query.regionIds && query.regionIds.length) params.regionId = query.regionIds[0]
      if (query.status) params.status = query.status
      if (query.statuses && query.statuses.length) params.status = query.statuses.join(',')
      // 【议题五.4】显示已归档: 后端语义 include_archived=默认口径(不含公海)+含归档, 覆盖状态下拉
      if (showArchived.value) params.status = 'include_archived'
      if (query.filters && query.filters.length) {
        query.filters.forEach(f => {
          params[f.field] = Array.isArray(f.value) ? f.value.join(',') : f.value
        })
      }
      const res = await getCustomerList(params)
      tableData.value = res.data.records
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

  // 释放/删除/导入成功后统一刷新
  const refreshAll = () => { loadData(); loadStats(); loadScopeCounts() }

  const loadFilterData = async () => {
    try {
      // 【布局级缓存】auth/info 由布局加载并缓存 30s，这里直接读 store，避免每页重复请求
      await userStore.fetchUserInfo()
      const ds = userStore.dataScope
      const mgd = userStore.managedOrgIds || []
      canFilterByOrg.value = ds === 'all' || mgd.length > 0
      if (canFilterByOrg.value) {
        // 【D4 2026-10-04】改走统一 request 封装（api 层）：自动带 token、统一错误映射与 401 处理；
        // 此前裸 fetch + 手拼 Authorization，请求失败时丢失拦截器的提示/登出能力
        const orgRes = await getDeptTree()
        filterOrgTree.value = orgRes?.data || []
      }
      if (ds === 'all' || ds === 'org_and_children' || mgd.length > 0) {
        // 用简易用户接口（只需 customer:view，不需要 system:user:view——避免销售员等角色 403）
        const userRes = await getSimpleUserList()
        filterUserOptions.value = (userRes?.data || []).map(u => ({ id: u.id, realName: u.realName }))
        canFilterByUsers.value = filterUserOptions.value.length > 1
      }
    } catch (e) {
      // 错误已由 request 拦截器提示
    }
  }

  // 客户业务参数:从公开配置读取(提醒/调整期),配置改动即时生效,不再硬编码
  const loadBizConfig = async () => {
    try {
      // 【布局级缓存】configs/public 由布局加载并缓存 60s，这里直接读 store
      await userStore.loadSystemConfig()
      const cfg = userStore.publicConfig || {}
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
    } catch (e) { industryOptions.value = ['煤矿', '化工', '电力', '洗煤厂', '有色', '其他'] }
  }

  // ---- 导航/筛选动作 ----
  const search = () => {
    query.keyword = searchText.value
    query.current = 1
    // 显示分页变量是 page（el-pagination v-model:current-page="page"），筛选后必须重置，
    // 否则过滤结果变少时停在旧页码可能越界白屏（分页最佳实践）
    page.value = 1
    loadData()
  }

  const onTabChange = () => {
    page.value = 1
    filters.search = ''
    filters.industry = ''
    filters.status = ''
  }

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

  const clearAdvFilter = () => {
    // 【M4 2026-10-04】清空全部必须连同部门字段一起重置：deptId 置 null 即清空树选中（v-model），
    // deptIds 是其派生的子部门集合；此前漏清导致"清空后部门过滤仍生效"
    Object.assign(advFilter, { industry: '', province: '', city: '', status: '', protect: '', owner: '', regDate: null, expDate: null, orgIds: [], userIds: [], deptId: null, deptIds: [] })
  }

  // 【C1 2026-10-04】高级筛选任意条件变化（弹窗内即时编辑/清空全部/部门树选择）统一重置页码：
  // 筛选后结果变少时停在旧页码可能越界白屏（分页最佳实践，同 search() 的既有修复）。
  // 弹窗内各控件是即时生效的 v-model 绑定，统一在源头 watch，避免逐控件接 @change 漏接
  watch(advFilter, () => { page.value = 1 }, { deep: true })

  // 【C1 2026-10-04】快速筛选（行业/状态）变化后同理必须重置页码（筛选项纯本地过滤，无需重载）
  const onQuickFilterChange = () => { page.value = 1 }

  const viewDetail = (row) => {
    router.push(`/customer/detail/${row.id}`)
  }

  // ---- 操作 ----
  const handleExport = async () => {
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
        if (Array.isArray(f.value)) f.value.forEach(v => params.append('filter_' + f.field, v))
        else params.append('filter_' + f.field, f.value)
      })
    }
    // 【D4 2026-10-04】token 改经 utils/token 统一读取（与 request.js 同源），不再裸读 localStorage；
    // 下载本体仍走统一 downloadFile（超时/中文失败提示），blob 经封装层支持需改 utils/download.js（本批次文件范围外）
    const token = getToken()
    const url = `/api/customer/export?${params.toString()}`
    // F3/F4: 统一下载工具（带 Authorization header + 超时 + 失败提示）
    await downloadFile(url, { name: '客户列表.xlsx', headers: { Authorization: `Bearer ${token}` } })
  }

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
      refreshAll()
    } catch (e) {
      if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
    }
  }

  // 【议题五.4】归档: 默认隐藏+不参与统计与到期提醒; 恢复: 回到 normal
  const handleArchive = async (row) => {
    try {
      await ElMessageBox.confirm(
        `确认归档客户 “${row.customerName}”？归档后列表默认隐藏, 不参与统计与到期提醒, 可随时恢复。`,
        '归档客户', { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' }
      )
    } catch (e) { return }
    try {
      await archiveCustomer(row.id)
      ElMessage.success('客户已归档')
      refreshAll()
    } catch (e) {
      if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
    }
  }
  const handleRestore = async (row) => {
    try {
      await restoreCustomer(row.id)
      ElMessage.success('客户已恢复')
      refreshAll()
    } catch (e) {
      if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
    }
  }

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
      refreshAll()
    } catch (e) {
      if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
    } finally {
      releaseLoading.value = false
    }
  }

  return {
    activeTab, searchText, filters, advFilterTab, advFilter,
    canFilterByOrg, canFilterByUsers, filterOrgTree, filterUserOptions, query,
    tableData, customerStats, scopeCounts, regionList, industryOptions,
    warningDays, adjustDays,
    allCols, visibleCols, vis,
    showAdvFilter, colPickerVisible, showImportDialog,
    showReleaseDialog, releaseTarget, releaseReason, releaseLoading,
    page, pageSize,
    canDelete, canRelease, showArchived, toggleArchived, handleArchive, handleRestore,
    tabs, filteredAllData, currentTabData, filteredList,
    advFilterCount, allIndustries, emptyTableText,
    isShared, getExpireColor, getDaysLeft, getStatusType, getStatusText, getProtectClass, getProtectText,
    loadData, loadStats, loadScopeCounts, refreshAll,
    loadFilterData, loadBizConfig, loadRegions, loadIndustryOptions,
    search, onTabChange, onDeptFilterChange, onQuickFilterChange, clearAdvFilter, viewDetail,
    handleExport, handleDelete, openRelease, confirmRelease,
  }
}
