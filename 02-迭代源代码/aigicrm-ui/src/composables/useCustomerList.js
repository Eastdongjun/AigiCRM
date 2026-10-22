// 客户列表页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route）：customer/list.vue 实例化一次并 provide，
// 顶部栏/统计卡/主表格/分页与 4 个弹窗 inject 同一上下文。
// 共享内容：Tab/搜索/筛选/分页 + 客户数据与计数 + 列设置 + 高级筛选组织人员树 + 弹窗显隐与目标 + 加载刷新动作。
// 弹窗内部工作态（导入文件/导入结果等）留在各弹窗组件内，仅注入共享态与刷新回调。
// 注：本次重构顺带清除历史遗留死代码（三套列设置中的两套、未接线多条件筛选、未接线表格高度机等），
//     保留的列设置自 UI 整改 2026-10-08 起持久化到 localStorage（刷新不丢）；tab/搜索词/页码同步 route.query。
import { ref, reactive, computed, inject, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomerList, getCustomerStatistics, releaseCustomer, deleteCustomer, archiveCustomer, restoreCustomer, getSimpleUserList } from '@/api/customer'
import { getDeptList, getDictData, getDeptTree } from '@/api/system'
import { useUserStore } from '@/store/user'
import { downloadFile } from '@/utils/download'
import { getToken } from '@/utils/token'
import { confirmAction } from '@/utils/confirm'
import { customerEmptyText } from '@/utils/customer-empty-state'
import { customerStatusTagType, customerStatusText } from '@/utils/customer-status'
import { INDUSTRY_FALLBACK_OPTIONS } from '@/utils/dict-fallback'
import { getCustomerListCountsByFilter } from '@/api/customer'

export const CustomerListKey = Symbol('customerList')

// 【⑬A 2026-10-17 用户裁决】默认页签 'innovate'(成熟创新)→'all'(全部)：成熟创新=0 的销售员(14/31)
// 打开列表即空表(此前配套误导文案已由 ⑬B 修复)，裁决改为默认展示全部客户。
// 导出常量供 spec 钉住默认值，防止后续重构悄悄翻回 'innovate'。
export const CUSTOMER_LIST_DEFAULT_TAB = 'all'

// 【B4 2026-10-20】列设置白名单提升为模块级导出(与 DEFAULT_TAB 同款钉法):
// MainTable 的 vis() 门控列必须都在此清单内, 缺位=列永久隐藏且设置器无开关可开。
// 导出供 useCustomerList.spec 做白名单一致性守卫。
export const CUSTOMER_ALL_COLS = [
  { key: 'customerCode', label: '编号' }, { key: 'customerName', label: '客户全称' }, { key: 'invoiceName', label: '开票名称' },
  { key: 'groupName', label: '集团' }, { key: 'branchCompany', label: '分公司' },
  { key: 'unitName', label: '所属单位' },
  // 【B4 2026-10-20】负责人/上级补进白名单——此前 MainTable 用 vis() 门控这两列但
  // 白名单缺位, 列被永久隐藏且列设置器(只遍历白名单)无开关可开(全局债务审计 B4)
  { key: 'ownerName', label: '负责人' }, { key: 'managerName', label: '上级' },
  { key: 'industry', label: '行业' }, { key: 'capacity', label: '产能' },
  // 【2026-09-13】终端列：字段此前只存在于库与导入模板，列表/详情/表单都无展示或录入入口
  { key: 'hasTerminal', label: '终端' },
  { key: 'province', label: '省市' }, { key: 'address', label: '厂址' }, { key: 'registerDate', label: '注册日' },
  { key: 'lastInvoiceDate', label: '最近开票' }, { key: 'transferDate', label: '转移日' }, { key: 'expireDate', label: '到期日' },
  { key: 'adjustDate', label: '调整日' }, { key: 'regionName', label: '大区' }, { key: 'officeName', label: '办事处' },
  { key: 'deptName', label: '部门' },
]

/** 子组件注入父级 customer/list.vue 提供的共享上下文 */
export function injectCustomerList() {
  const ctx = inject(CustomerListKey)
  if (!ctx) throw new Error('injectCustomerList 必须在 customer/list.vue 的组件内使用')
  return ctx
}

export function useCustomerList() {
  const router = useRouter()
  const userStore = useUserStore()

  // 【容量加固·服务端分页】页签静态定义 + 服务端计数(tabCounts)（须先于下方 route.query 初始化，供 tab 参数校验）
  // 【B7 2026-10-08】页签点色一律引用主题变量，不再写字面量（全部=主文字色/突破=warning/铺垫=success/工程=info）
  const TAB_DEFS = [
    { type: 'all', label: '全部', color: 'var(--el-text-color-primary)' },
    { type: 'innovate', label: '成熟创新', color: 'var(--el-color-primary)' },
    { type: 'break', label: '突破开拓', color: 'var(--el-color-warning)' },
    { type: 'found', label: '铺垫宣传', color: 'var(--el-color-success)' },
    // 【2026-09-23 需求修正】「新建改建扩建大修」是一个完整分类词(V2026.09.23.6 撤销拆分),客户列表含本分类页签
    { type: 'engineering', label: '新建改建扩建大修', color: 'var(--el-color-info)' },
  ]
  const TAB_DEV_TYPE = { innovate: '成熟创新', break: '突破开拓', found: '铺垫宣传', engineering: '新建改建扩建大修' }

  // ---- 页面导航/筛选态 ----
  // 【UI 整改 2026-10-08·列表态保持】activeTab/搜索词/页码初始值改从 route.query 恢复
  // （detail 页"返回客户列表"带参回跳），列表每次加载成功后再同步回 route.query，
  // 消除此前"进详情返回即丢筛选条件"（原 activeTab 每次重置）
  // 【⑬A 2026-10-17】回退默认 'innovate'→CUSTOMER_LIST_DEFAULT_TAB('all')，见常量处注释
  const route = useRoute()
  const sanitizeTab = (v) => (TAB_DEFS.some(d => d.type === v) ? v : CUSTOMER_LIST_DEFAULT_TAB)
  const activeTab = ref(sanitizeTab(String(route.query.tab || '')))
  const searchText = ref(String(route.query.kw || ''))
  const filters = reactive({ search: '', industry: '', status: '' })
  // 【A3 2026-10-08·下钻带参接入】工作台「已超期」卡 / 统计页行业与办事处下钻跳
  // /customer?status=..&industry=..&office=..：status/industry 落到快速筛选条（用户可见可清除），
  // office 在部门树加载后解析为组织子树（见 loadFilterData）。首次加载即按参数过滤，消费后 URL 由 syncListQuery 清干净
  const initStatus = String(route.query.status || '')
  if (['normal', 'warning', 'expired', 'archived'].includes(initStatus)) filters.status = initStatus
  const initIndustry = String(route.query.industry || '')
  if (initIndustry) filters.industry = initIndustry
  const pendingOfficeName = String(route.query.office || '')
  // 【议题五.4】归档筛选: 默认隐藏归档客户, 勾选后列表含归档(后端 include_archived)
  // 【工作流复核确认修复】勾选/取消时同步清空快速+高级状态选择(与"状态优先"新口径配套, 防旧状态残留再冲突)
  const showArchived = ref(false)
  const toggleArchived = (val) => { showArchived.value = val; query.status = ''; query.statuses = []; filters.status = ''; advFilter.status = ''; page.value = 1; loadData() }
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
  const regionList = ref([])
  const industryOptions = ref([])
  const warningDays = ref(30)
  const adjustDays = ref(180)

  // ---- 列设置（localStorage 持久化；【UI 整改 2026-10-08】此前为内存态，刷新即重置为全列）----
  const allCols = CUSTOMER_ALL_COLS
  const VISIBLE_COLS_KEY = 'aigicrm.customer.visibleCols'
  const defaultVisibleCols = () => allCols.map(c => c.key)
  const loadVisibleCols = () => {
    try {
      const raw = localStorage.getItem(VISIBLE_COLS_KEY)
      if (!raw) return defaultVisibleCols()
      const arr = JSON.parse(raw)
      if (!Array.isArray(arr)) return defaultVisibleCols()
      // 过滤掉已下线的列 key，避免历史残留配置撑出幽灵列
      return arr.filter(k => allCols.some(c => c.key === k))
    } catch (e) {
      return defaultVisibleCols()
    }
  }
  const visibleCols = ref(loadVisibleCols())
  watch(visibleCols, (v) => {
    try { localStorage.setItem(VISIBLE_COLS_KEY, JSON.stringify(v)) } catch (e) {}
  }, { deep: true })
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
  // 【UI 整改 2026-10-08·列表态保持】页码从 route.query 恢复（detail 返回带参回跳）
  const page = ref(Math.max(1, parseInt(route.query.page, 10) || 1))
  const pageSize = ref(30)

  // ---- 计算 ----
  const canDelete = computed(() => userStore.hasPermission('customer:delete'))
  const canRelease = computed(() => userStore.hasPermission('customer:edit'))

  const tabs = computed(() => TAB_DEFS.map(d => ({
    ...d,
    count: tabCounts.value[d.type === 'all' ? '全部' : TAB_DEV_TYPE[d.type]] ?? 0,
  })))

  // 【容量加固·服务端分页 2026-10-05】表格数据=服务端当前页(filteredList 不再本地过滤/本地分页)
  const filteredList = computed(() => tableData.value)

  // 【FM3 2026-10-07】空态判定带服务端 total（tableData 现为当前页，单看它区分不了"筛选无匹配"与"范围无数据"）
  // 【UI 整改 2026-10-08】带搜索词时改提示"未找到与XX匹配"，不再误导去找管理员查数据范围
  // 【⑬B 2026-10-17】带页签第四参：非"全部"页签空表用 tabCounts['全部']（counts 接口同口径但不含 devType）
  // 区分"该分类为空"与"数据范围无客户"，修 NM005/郑磊案的误导文案
  const emptyTableText = computed(() => customerEmptyText(tableData.value, total.value, buildListParams().keyword, {
    allTotal: tabCounts.value['全部'],
    tabLabel: TAB_DEV_TYPE[activeTab.value],
  }))

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
    if (row.protectionStatus === 'expired') return 'var(--el-color-danger)'
    if (row.protectionStatus === 'expiring') return 'var(--el-color-warning)'
    return 'var(--el-color-primary)'
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
    // 行业合并:高级筛选与快速筛选同列同语义(eq),高级筛选优先——与下方状态列口径一致
    const industry = filters.industry || query.industry || (query.industries?.length ? query.industries.join(',') : '')
    if (industry) p.industry = industry
    if (advFilter.industry) p.industry = advFilter.industry
    if (query.regionId) p.regionId = query.regionId
    else if (query.regionIds?.length) p.regionId = query.regionIds[0]
    // 状态合并:高级筛选与快速筛选同列同语义(eq),高级筛选优先;归档开关与具体状态互斥——
    // 【工作流复核确认修复】具体状态优先(选中"已归档"即只看归档), 仅在无状态选择时 include_archived
    // 生效(原 showArchived 无条件覆盖, 勾选后任何状态下拉选择都静默失效且 UI 仍显示已选状态)
    const status = advFilter.status || filters.status || query.status || (query.statuses?.length ? query.statuses.join(',') : '')
    p.status = status || (showArchived.value ? 'include_archived' : '')
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

  // 【FM1 2026-10-07】请求序号守卫（照 search.vue M6 现成模式）：搜索/筛选变化会连发请求，
  // 慢的旧响应后到会覆盖新结果（旧数据闪回/页签计数错乱）；发请求前自增序号并在写回前比对，不等则丢弃。
  // 计数器放组合式函数闭包内，作用域等价 search.vue 的 script setup 顶层（每路由实例独立一套）
  let listSeq = 0
  let countsSeq = 0

  // 【UI 整改 2026-10-08·D13 加载态】列表请求期间 MainTable 表格 v-loading（参照 sea/index.vue 既有写法）
  const listLoading = ref(false)
  // 【C12 2026-10-08】列表加载失败页面级错误态（区别于空态）：失败时 MainTable 顶部显示错误条+重试，
  // 不再把失败静默吞成"空列表"；成功加载即清除
  const listError = ref(false)

  // 【UI 整改 2026-10-08·列表态保持】把 tab/搜索词/页码同步进 route.query（replace 不产生历史记录），
  // detail 页"返回客户列表"据此带参回跳；默认值不入参保持 URL 干净
  const syncListQuery = () => {
    const q = {}
    if (activeTab.value && activeTab.value !== CUSTOMER_LIST_DEFAULT_TAB) q.tab = activeTab.value
    const kw = (searchText.value || '').trim()
    if (kw) q.kw = kw
    if (page.value > 1) q.page = String(page.value)
    const cur = route.query
    if (String(cur.tab || '') !== (q.tab || '') || String(cur.kw || '') !== (q.kw || '') || String(cur.page || '') !== (q.page || '')) {
      router.replace({ query: q }).catch(() => {})
    }
  }

  const loadData = async () => {
    const seq = ++listSeq
    listLoading.value = true
    // 【⑬B 2026-10-17】页签计数与列表并发发起（原在列表成功后才发）：空态文案的"该分类为空"分支
    // 依赖 tabCounts['全部']，晚发会让误导性数据范围文案先闪一拍；counts 更轻量通常先回
    loadTabCounts()
    try {
      const params = { ...buildListParams(), current: page.value, size: pageSize.value }
      const res = await getCustomerList(params)
      if (seq !== listSeq) return // 已有更新的列表请求在途/完成，丢弃本次旧响应
      tableData.value = res.data.records || []
      total.value = res.data.total || 0
      listError.value = false
      syncListQuery()
    } catch (e) {
      // 【C12 2026-10-08】不再静默：错误详情已由 request 拦截器提示，这里补页面级错误态供重试
      if (seq === listSeq) listError.value = true
    } finally {
      // 仅由最新一次请求熄灯；旧请求晚到时不得关掉在途新请求的 loading
      if (seq === listSeq) listLoading.value = false
    }
  }

  const loadTabCounts = async () => {
    const seq = ++countsSeq
    try {
      const res = await getCustomerListCountsByFilter(buildListParams())
      if (seq !== countsSeq) return // 已有更新的计数请求在途/完成，丢弃本次旧响应
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

  // 释放/删除/导入成功后统一刷新
  const refreshAll = () => { loadData(); loadStats(); loadTabCounts() }

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
      // 【A3 2026-10-08】统计页办事处下钻带来的 office 名称参数：树加载后解析为该组织+全部子部门的
      // orgIds 筛选（与高级筛选"所属组织"同一参数通道，UI 高级筛选弹窗里可见可清）。树不可用时静默放弃
      if (pendingOfficeName && filterOrgTree.value.length) {
        const findByName = (nodes) => {
          for (const n of nodes || []) {
            if (n.deptName === pendingOfficeName) return n
            const hit = findByName(n.children)
            if (hit) return hit
          }
          return null
        }
        const node = findByName(filterOrgTree.value)
        if (node) {
          const ids = []
          const collect = (ns) => { for (const n of ns || []) { ids.push(n.id); collect(n.children) } }
          collect([node])
          advFilter.orgIds = ids
        }
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
    // 【FL4 2026-10-07】立即加载前清掉 watch(searchText) 挂起的防抖定时器:
    // 否则回车立即加载后防抖到点又补一枪,输入→回车常态连发 2-3 组冗余请求
    clearTimeout(reloadTimer)
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

  // 【UI 整改 2026-10-08】clearSearch 程序化清空时挂起下方两个筛选 watch（由其自行立即加载一次，避免同参连发两枪）
  let suppressFilterWatch = false

  // 【UI 整改 2026-10-08·⑦空态】是否存在可清空的搜索/筛选条件（空态区据此显示"清空搜索与筛选"按钮）
  const hasActiveFilter = computed(() => {
    if (String(searchText.value || '').trim()) return true
    if (filters.industry || filters.status) return true
    return advFilterCount.value > 0
  })

  // 【UI 整改 2026-10-08·⑦空态】一键清空搜索词+快速筛选+高级筛选并回第一页（MainTable 空态按钮）
  const clearSearch = () => {
    suppressFilterWatch = true
    searchText.value = ''
    filters.industry = ''
    filters.status = ''
    clearAdvFilter()
    page.value = 1
    clearTimeout(reloadTimer)
    loadData()
    nextTick(() => { suppressFilterWatch = false })
  }

  // 【C1 2026-10-04→服务端分页 2026-10-05】高级筛选任意变化统一重置页码并防抖重载:
  // 弹窗内各控件是即时生效的 v-model 绑定，统一在源头 watch，避免逐控件接 @change 漏接
  watch(advFilter, () => { if (suppressFilterWatch) return; page.value = 1; debouncedReload() }, { deep: true })

  // 【容量加固·服务端分页】搜索词输入即查(400ms 防抖,替代旧的本地即时过滤体验)
  watch(searchText, () => { if (suppressFilterWatch) return; page.value = 1; debouncedReload() })

  // 快速筛选（行业/状态）变化:同上重置页码+防抖重载(原为纯本地过滤无需请求)
  const onQuickFilterChange = () => { page.value = 1; debouncedReload() }

  // 分页事件(服务端分页:v-model 已更新页码/页大小,此处触发重载)
  const onCurrentChange = () => { loadData() }
  const onSizeChange = () => { page.value = 1; loadData() }

  const viewDetail = (row) => {
    // 【UI 整改 2026-10-08·列表态保持】携带当前 tab/搜索词/页码进详情 query，
    // detail 页"返回客户列表"据此带参回跳（键名与 syncListQuery 同一套）
    const listQuery = {}
    if (activeTab.value && activeTab.value !== CUSTOMER_LIST_DEFAULT_TAB) listQuery.tab = activeTab.value
    const kw = (searchText.value || '').trim()
    if (kw) listQuery.kw = kw
    if (page.value > 1) listQuery.page = String(page.value)
    // 【工作流复核确认修复 2026-10-09】被分享行(非本人客户)进详情按只读打开:
    // readonly 此前仅由 query.from=share 驱动但全 UI 无生成点,被分享用户可见可点
    // 全部写按钮,与分享弹窗"该用户可查看此客户信息(只读)"承诺矛盾(后端 assertCanEdit
    // 有兜底,此处补齐 UI 层口径,与列表端 isShared 禁选/操作列「—」同判据)
    if (isShared(row)) listQuery.readonly = '1'
    router.push({ path: `/customer/detail/${row.id}`, query: listQuery })
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
    // 【C9 三轮 2026-10-09】删除确认收敛到 utils/confirm.js confirmAction（统一红色确认钮/取消左置/app-confirm 皮肤）
    try {
      // 【2026-10-17 文案对齐】客户删除为软删除：移入回收站，保留期内管理员可恢复
      await confirmAction(
        `确认删除客户 “${row.customerName}”（编号 ${row.customerCode}）？删除后将移入回收站，保留期内可由管理员恢复。`,
        '删除客户',
        { confirmText: '确认删除' }
      )
    } catch (e) {
      return // 用户取消
    }
    try {
      await deleteCustomer(row.id)
      ElMessage.success('客户已删除')
      refreshAll()
    } catch (e) {
      // 【工作流复核确认修复】错误提示由 request 拦截器统一弹出, 此处不再重复弹同文案(原双弹)
    }
  }

  // 【议题五.4】归档: 默认隐藏+不参与统计与到期提醒; 恢复: 回到 normal
  const handleArchive = async (row) => {
    // 【C9 三轮 2026-10-09】归档确认同样收敛到 confirmAction；归档可恢复、非危险操作，danger:false 不染红
    try {
      await confirmAction(
        `确认归档客户 “${row.customerName}”？归档后列表默认隐藏, 不参与统计与到期提醒, 可随时恢复。`,
        '归档客户',
        { confirmText: '确认归档', danger: false }
      )
    } catch (e) { return }
    try {
      await archiveCustomer(row.id)
      ElMessage.success('客户已归档')
      refreshAll()
    } catch (e) {
      // 【工作流复核确认修复】错误提示由 request 拦截器统一弹出, 此处不再重复弹同文案(原双弹)
    }
  }
  const handleRestore = async (row) => {
    try {
      await restoreCustomer(row.id)
      ElMessage.success('客户已恢复')
      refreshAll()
    } catch (e) {
      // 【工作流复核确认修复】错误提示由 request 拦截器统一弹出, 此处不再重复弹同文案(原双弹)
    }
  }

  const openRelease = (row) => {
    releaseTarget.value = row
    releaseReason.value = '放弃客户'
    showReleaseDialog.value = true
  }

  const confirmRelease = async () => {
    // 【UI 整改 2026-10-08·⑧】释放原因必填（ReleaseDialog 确认钮已空值禁用，此处兜底防绕过）
    if (!String(releaseReason.value || '').trim()) {
      ElMessage.warning('请填写释放原因')
      return
    }
    releaseLoading.value = true
    try {
      await releaseCustomer(releaseTarget.value.id, releaseReason.value)
      ElMessage.success('客户已释放到公海池')
      showReleaseDialog.value = false
      refreshAll()
    } catch (e) {
      // 【工作流复核确认修复】错误提示由 request 拦截器统一弹出, 此处不再重复弹同文案(原双弹)
    } finally {
      releaseLoading.value = false
    }
  }

  // ---- 批量操作（【UI 整改 2026-10-08·D15】此前全应用零批量操作；行级动作口径见 MainTable :selectable）----
  const tableRef = ref(null) // MainTable 回传 el-table 实例，批量完成后 clearSelection 清空勾选
  const selectedRows = ref([])
  const onSelectionChange = (rows) => { selectedRows.value = rows }
  // selection 列已按 isShared 禁选；此处再按动作口径过滤：释放/归档仅未归档行（归档行只可恢复），删除不限归档
  const batchReleasable = computed(() => selectedRows.value.filter(r => !isShared(r) && r.status !== 'archived'))
  const batchArchivable = computed(() => selectedRows.value.filter(r => !isShared(r) && r.status !== 'archived'))
  const batchDeletable = computed(() => selectedRows.value.filter(r => !isShared(r)))

  /** 批量执行：逐条调用、统计成功/失败，结束后清空勾选并统一刷新（失败明细已由请求层提示） */
  const runBatch = async (rows, action, successLabel) => {
    let ok = 0
    let fail = 0
    for (const r of rows) {
      try { await action(r); ok++ } catch (e) { fail++ }
    }
    if (ok) ElMessage.success(`${successLabel} ${ok} 个客户`)
    if (fail) ElMessage.warning(`${fail} 个客户操作失败，请重试`)
    tableRef.value?.clearSelection?.()
    selectedRows.value = []
    refreshAll()
  }

  const handleBatchRelease = async () => {
    const rows = batchReleasable.value
    if (!rows.length) return ElMessage.warning('所选客户中没有可释放的客户（已归档行请先恢复）')
    let reason = ''
    try {
      const { value } = await ElMessageBox.prompt(
        `将为选中的 ${rows.length} 个客户填写同一释放原因（必填）。释放后负责人清空、进入公海池，其他用户可捞取。`,
        '批量释放客户',
        {
          confirmButtonText: '确认释放', cancelButtonText: '取消', inputType: 'textarea',
          inputPlaceholder: '请输入释放原因（必填）', inputValidator: (v) => (v && v.trim()) ? true : '释放原因必填',
          confirmButtonClass: 'el-button--warning',
        }
      )
      reason = value.trim()
    } catch (e) {
      return // 用户取消
    }
    await runBatch(rows, (r) => releaseCustomer(r.id, reason), '已释放')
  }

  const handleBatchArchive = async () => {
    const rows = batchArchivable.value
    if (!rows.length) return ElMessage.warning('所选客户中没有可归档的客户（已归档行请先恢复）')
    try {
      await confirmAction(
        `确认归档选中的 ${rows.length} 个客户？归档后列表默认隐藏，不参与统计与到期提醒，可随时恢复。`,
        '批量归档客户',
        { confirmText: '确认归档', danger: false }
      )
    } catch (e) { return }
    await runBatch(rows, (r) => archiveCustomer(r.id), '已归档')
  }

  const handleBatchDelete = async () => {
    const rows = batchDeletable.value
    if (!rows.length) return ElMessage.warning('请先勾选要删除的客户')
    try {
      // 【D15】批量删除走 confirmAction 红色确认（utils/confirm 统一危险操作视觉）
      await confirmAction(
        `确认删除选中的 ${rows.length} 个客户？删除后将移入回收站，保留期内可由管理员恢复。`,
        '批量删除客户',
        { confirmText: '确认删除' }
      )
    } catch (e) { return }
    await runBatch(rows, (r) => deleteCustomer(r.id), '已删除')
  }

  return {
    activeTab, searchText, filters, advFilterTab, advFilter,
    canFilterByOrg, canFilterByUsers, filterOrgTree, filterUserOptions, query,
    tableData, customerStats, regionList, industryOptions,
    warningDays, adjustDays,
    allCols, visibleCols, vis,
    showAdvFilter, colPickerVisible, showImportDialog,
    showReleaseDialog, releaseTarget, releaseReason, releaseLoading,
    page, pageSize, total,
    canDelete, canRelease, showArchived, toggleArchived, handleArchive, handleRestore,
    tabs, filteredList, tabCounts,
    advFilterCount, allIndustries, emptyTableText, hasActiveFilter, clearSearch,
    isShared, getExpireColor, getDaysLeft, getStatusType, getStatusText, getProtectClass, getProtectText,
    listLoading, listError, tableRef, selectedRows, onSelectionChange,
    batchReleasable, batchArchivable, batchDeletable,
    handleBatchRelease, handleBatchArchive, handleBatchDelete,
    loadData, loadStats, refreshAll,
    loadFilterData, loadBizConfig, loadRegions, loadIndustryOptions,
    search, onTabChange, onDeptFilterChange, onQuickFilterChange, clearAdvFilter, viewDetail,
    onCurrentChange, onSizeChange,
    handleExport, handleDelete, openRelease, confirmRelease,
  }
}
