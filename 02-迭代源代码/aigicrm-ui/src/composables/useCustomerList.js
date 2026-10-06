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
import { customerEmptyText } from '@/utils/customer-empty-state'
import { customerStatusTagType, customerStatusText } from '@/utils/customer-status'
import { INDUSTRY_FALLBACK_OPTIONS } from '@/utils/dict-fallback'
import { getCustomerListCountsByFilter } from '@/api/customer'

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
  // 【容量加固·服务端分页 2026-10-05】总数与页签计数改由服务端返回(此前一次拉 500 条本地分页/本地过滤)
  const total = ref(0)
  const tabCounts = ref({})
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

  // 【容量加固·服务端分页 2026-10-05】页签改为静态定义 + 服务端计数(tabCounts):
  // 原实现基于"一次拉 500 条"的本地分区计数,数据不全即计数失真;现由 /list/counts-by-filter 按当前筛选返回
  const TAB_DEFS = [
    { type: 'all', label: '全部', color: '#303133' },
    { type: 'innovate', label: '成熟创新', color: '#409eff' },
    { type: 'break', label: '突破开拓', color: '#e6a23c' },
    { type: 'found', label: '铺垫宣传', color: '#67c23a' },
    // 【2026-09-23 需求修正】「新建改建扩建大修」是一个完整分类词(V2026.09.23.6 撤销拆分),客户列表含本分类页签
    { type: 'engineering', label: '新建改建扩建大修', color: '#64748b' },
  ]
  const TAB_DEV_TYPE = { innovate: '成熟创新', break: '突破开拓', found: '铺垫宣传', engineering: '新建改建扩建大修' }

  const tabs = computed(() => TAB_DEFS.map(d => ({
    ...d,
    count: tabCounts.value[d.type === 'all' ? '全部' : TAB_DEV_TYPE[d.type]] ?? 0,
  })))

  // 【容量加固·服务端分页 2026-10-05】表格数据=服务端当前页(filteredList 不再本地过滤/本地分页)
  const filteredList = computed(() => tableData.value)

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

  const allIndustries = computed(() => industryOptions.value.length ? industryOptions.value : INDUSTRY_FALLBACK_OPTIONS)

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
  // 【容量加固·服务端分页 2026-10-05】原"一次拉 500 条 + 本地三层过滤(filterCustomers) + 本地分页"
  // 改为服务端分页/服务端过滤:搜索词/快速筛选/高级筛选/页签全部拼为查询参数;
  // 三层过滤语义逐项对齐——keyword 多词AND、行业/状态精确(csv)、省市包含(LIKE)、保护=protectionStatus、
  // 负责人=姓名模糊(后端解析为 ownerId)、注册/到期区间 ge/le、组织=office或dept命中、人员=ownerId IN、
  // 部门=含子树。orgIds 旧本地实现基于已拉数据间接推导 owner,服务端按字段直配(语义更准)。
  const buildListParams = () => {
    const p = { keyword: (searchText.value || '').trim(), scope: query.scope }
    const industry = filters.industry || query.industry || (query.industries?.length ? query.industries.join(',') : '')
    if (industry) p.industry = industry
    if (advFilter.industry) p.industry = advFilter.industry
    if (query.regionId) p.regionId = query.regionId
    else if (query.regionIds?.length) p.regionId = query.regionIds[0]
    // 状态合并:高级筛选与快速筛选同列同语义(eq),高级筛选优先;归档开关按旧行为覆盖为 include_archived
    const status = advFilter.status || filters.status || query.status || (query.statuses?.length ? query.statuses.join(',') : '')
    p.status = showArchived.value ? 'include_archived' : (status || '')
    const devType = TAB_DEV_TYPE[activeTab.value]
    if (devType) p.devType = devType
    if (advFilter.province) p.province = advFilter.province.trim()
    if (advFilter.city) p.city = advFilter.city.trim()
    if (advFilter.protect) p.protectionStatus = advFilter.protect
    if (advFilter.owner) p.ownerName = advFilter.owner
    if (advFilter.regDate && advFilter.regDate.length === 2) { p.regDateStart = advFilter.regDate[0]; p.regDateEnd = advFilter.regDate[1] }
    if (advFilter.expDate && advFilter.expDate.length === 2) { p.expDateStart = advFilter.expDate[0]; p.expDateEnd = advFilter.expDate[1] }
    if (advFilter.orgIds?.length) p.officeIds = advFilter.orgIds.join(',')
    if (advFilter.userIds?.length) p.userIds = advFilter.userIds.join(',')
    if (advFilter.deptId) p.deptId = advFilter.deptId
    ;(query.filters || []).forEach(f => {
      p[f.field] = Array.isArray(f.value) ? f.value.join(',') : f.value
    })
    return p
  }

  const loadData = async () => {
    try {
      const params = { ...buildListParams(), current: page.value, size: pageSize.value }
      const res = await getCustomerList(params)
      tableData.value = res.data.records || []
      total.value = res.data.total || 0
      loadTabCounts()
    } catch (e) {}
  }

  const loadTabCounts = async () => {
    try {
      const res = await getCustomerListCountsByFilter(buildListParams())
      tabCounts.value = res.data || {}
    } catch (e) {}
  }

  // 【容量加固】输入/筛选变化统一 400ms 防抖后走服务端(原为纯本地即时过滤,现需一次轻量请求)
  let reloadTimer = null
  const debouncedReload = () => {
    clearTimeout(reloadTimer)
    reloadTimer = setTimeout(() => { loadData() }, 400)
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
    } catch (e) { industryOptions.value = INDUSTRY_FALLBACK_OPTIONS }
  }

  // ---- 导航/筛选动作 ----
  const search = () => {
    // 【容量加固·服务端分页 2026-10-05】搜索词经 watch(searchText) 已防抖触发服务端查询;
    // 显式点击/回车仍立即加载一次(即时反馈),并重置页码
    page.value = 1
    loadData()
  }

  const onTabChange = () => {
    // 【容量加固·服务端分页】页签=服务端 devType 参数,切换必须重载;沿用旧版"切页签清快速筛选"的行为
    page.value = 1
    filters.search = ''
    filters.industry = ''
    filters.status = ''
    loadData()
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

  // 【C1 2026-10-04→服务端分页 2026-10-05】高级筛选任意变化统一重置页码并防抖重载:
  // 弹窗内各控件是即时生效的 v-model 绑定，统一在源头 watch，避免逐控件接 @change 漏接
  watch(advFilter, () => { page.value = 1; debouncedReload() }, { deep: true })

  // 【容量加固·服务端分页】搜索词输入即查(400ms 防抖,替代旧的本地即时过滤体验)
  watch(searchText, () => { page.value = 1; debouncedReload() })

  // 快速筛选（行业/状态）变化:同上重置页码+防抖重载(原为纯本地过滤无需请求)
  const onQuickFilterChange = () => { page.value = 1; debouncedReload() }

  // 分页事件(服务端分页:v-model 已更新页码/页大小,此处触发重载)
  const onCurrentChange = () => { loadData() }
  const onSizeChange = () => { page.value = 1; loadData() }

  const viewDetail = (row) => {
    router.push(`/customer/detail/${row.id}`)
  }

  // ---- 操作 ----
  const handleExport = async () => {
    // 【容量加固·服务端分页 2026-10-05】导出与列表共用同一份筛选参数(buildListParams):
    // 此前导出只带快速筛选 query.*,高级筛选条件不进导出(口径不一致);后端 /export 已同步支持全部参数,
    // 并改走不分页 selectList(旧 page(1,10000) 会被 maxLimit=500 截断)
    const params = new URLSearchParams()
    Object.entries(buildListParams()).forEach(([k, v]) => {
      if (v !== '' && v !== null && v !== undefined) params.append(k, String(v))
    })
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
    page, pageSize, total,
    canDelete, canRelease, showArchived, toggleArchived, handleArchive, handleRestore,
    tabs, filteredList, tabCounts,
    advFilterCount, allIndustries, emptyTableText,
    isShared, getExpireColor, getDaysLeft, getStatusType, getStatusText, getProtectClass, getProtectText,
    loadData, loadStats, loadScopeCounts, refreshAll,
    loadFilterData, loadBizConfig, loadRegions, loadIndustryOptions,
    search, onTabChange, onDeptFilterChange, onQuickFilterChange, clearAdvFilter, viewDetail,
    onCurrentChange, onSizeChange,
    handleExport, handleDelete, openRelease, confirmRelease,
  }
}
