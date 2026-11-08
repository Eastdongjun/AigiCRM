// 【2026-10-23 四项服务拆分·用户令】tabBar「四项服务」= 轻量客户记录快查视图。
// 【2026-10-26 用户令】轻量页与完整版(四项服务应用)排法分工: 完整版=按客户归集封面卡(原地不动);
// 轻量页默认=打散记录流(不绑客户, 一次上传=一条记录, 按上传时间排), 「按客户」可切回归集视图。
// 数据链路同源(getCustomerRecords 按客户分组 + 本地筛选排序), 打散=分组结果拉平, 不加接口。
// 【11-08 审计项6·审计09章新发现7】getGrantCheck 死导入移除: 轻量页无 grant 消费点
// (canEdit/canDelete 系操作都在完整版/详情页), 仅完整版 pages/incentive/index 与 detail 在用。
const { getCustomerRecords, getCategoryTree, getUsers } = require('../../../api/incentive')
const { getDownloadToken } = require('../../../api/file')
const { getDictData } = require('../../../api/system')
const { incentiveRecords } = require('../../../data/demo')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')
const { buildIncentiveFiles, buildIncentiveFileGroups } = require('../../../utils/incentive-files')
const { paginate } = require('../../../utils/paging')
// 【2026-11-02 用户令】组织筛选退役: buildDeptLevelOptions 引用随之移除

// 缩略图凭证: 模块级缓存(与完整版同款机制, 本页独立一份); 失败不阻断列表(退化为首字面卡)
// 【2026-11-08 图片预览可靠性】缓存加时效: 后端下载 token TTL=1h(JwtTokenProvider
// DOWNLOAD_TOKEN_TTL_MS=3600000), 命中超 50 分钟(留 10 分钟余量)即视为失效重取——本页
// 是 tabBar 常驻页, 此前命中分支不判时效, 常驻超 1h 后行首缩略直链全 401。
let dlTokenCache = ''
let dlTokenCacheUserId = null
let dlTokenCacheIssuedAt = 0
const DL_TOKEN_FRESH_MS = 50 * 60 * 1000
function setHomeDlToken(token, userId) {
  dlTokenCache = token || ''
  dlTokenCacheUserId = token ? (userId ?? null) : null
  dlTokenCacheIssuedAt = token ? Date.now() : 0
}
const TAG_COLORS = { 安装前: '#b45309', 安装中: '#1d4fd7', 安装后: '#047857', 跟踪: '#7c3aed', 佐证: '#64748b' }

function normalizeRecords(customers) {
  return (customers || []).map((customer) => {
    const records = customer.records || []
    const latest = records.reduce((best, item) => {
      if (!best) return item
      return String(item.createdAt || '') > String(best.createdAt || '') ? item : best
    }, null)
    return {
      id: latest ? latest.id : customer.customerId,
      customerId: customer.customerId,
      recordIds: records.map((item) => item.id).filter(Boolean),
      name: customer.customerName,
      industry: customer.industry || '其他',
      score: customer.totalScore || 0,
      count: customer.count || records.length,
      category: latest ? latest.category || latest.type || latest.categoryName || '未分类' : '未分类',
      unread: Boolean(customer.unread || records.some((item) => item.unread)),
      summary: latest ? latest.description || latest.remark || latest.product || '暂无详情描述' : '暂无详情描述',
      files: latest ? buildIncentiveFiles(latest) : [],
      fileGroups: latest ? buildIncentiveFileGroups(latest) : [],
      records: records.map((item) => ({ ...item, files: buildIncentiveFiles(item), fileGroups: buildIncentiveFileGroups(item) })),
      detail: latest
    }
  })
}

function buildFileGroupView(fileGroups) {
  return (fileGroups || []).map((group, groupIndex) => ({
    ...group,
    groupIndex,
    countText: `${(group.files || []).length} 个`,
    slotText: `${group.label} ${(group.files || []).length}`,
    files: (group.files || []).map((file, fileIndex) => ({
      ...file,
      fileIndex,
      tagText: file.tag || '',
      tagColor: TAG_COLORS[file.tag] || '#64748b',
      showTag: Boolean(file.tag)
    }))
  }))
}

function buildRecordView(record) {
  const fileGroupsView = buildFileGroupView(record.fileGroups)
  // 与完整版同款: 原始 records/files/fileGroups 大数组不进 data(存页面内存), 视图只带封面/计数字段;
  // 【2026-10-27 瘦身】detail(最新子记录原样, 含扁平文件字段)同样剥出 data, 时间折算成 coverTime 标量
  const { records: _omitRecords, files: _omitFiles, fileGroups: _omitGroups, detail, ...rest } = record
  let coverFile = null
  for (let gi = 0; gi < fileGroupsView.length && !coverFile; gi += 1) {
    const hit = (fileGroupsView[gi].files || []).find((file) => file.type === 'image' && file.id)
    if (hit) coverFile = { id: hit.id }
  }
  return {
    ...rest,
    coverTime: String((detail && detail.createdAt) || record.createdAt || ''),
    scoreText: `${record.score || 0} 分`,
    countText: `${record.count || 0}条`,
    fileCountText: `${(record.files || []).length}文件`,
    fileCountTotal: fileGroupsView.reduce((sum, group) => sum + (group.files || []).length, 0),
    coverFile: coverFile || {},
    coverChar: (record.name || '客').trim().charAt(0).toUpperCase(),
    childCount: (record.records || []).length
  }
}

function pickOption(options, index, fallback, key) {
  const item = (options || [])[Number(index) || 0]
  if (!item) return fallback
  if (key) return item[key] || fallback
  return item || fallback
}

function buildIncentiveView(data) {
  const isLoading = Boolean(data.loading)
  // 两级分类胶囊文案只跟随"已确认"的选择(categoryMainIndex/categorySubIndex):
  // 滚轮位置(categoryValue)在 columnchange 即变, 用户取消时轮子回弹、文案不得跟着走
  const catMainIndex = Number(data.categoryMainIndex) || 0
  const catSubIndex = Number(data.categorySubIndex) || 0
  const catMain = (data.categoryMains || [CATEGORY_ALL])[catMainIndex] || CATEGORY_ALL
  const catSub = (data.categorySubs || ['全部'])[catSubIndex] || '全部'
  return {
    refreshLoading: isLoading,
    refreshText: isLoading ? '刷新中' : '刷新',
    searchLoading: isLoading,
    searchText: isLoading ? '查询中' : '查询',
    showLoading: isLoading,
    pagerLoading: isLoading,
    loadingText: '加载中...',
    // 【2026-11-02 用户令】类型/组织/日期范围筛选退役(wxml 已删), 对应视图文案一并清理
    userText: `${pickOption(data.userNames, data.selectedUserIndex, '全部人员')}⌄`,
    orderText: `${pickOption(data.orderOptions, data.selectedOrderIndex, '最新记录在前')}⌄`,
    categoryText: catMainIndex === 0 ? (catSubIndex === 0 || catSub === '全部' ? '分类' : catSub) : (catSubIndex === 0 || catSub === '全部' ? catMain : `${catMain}·${catSub}`),
    showRecordEmpty: !isLoading && ((data.listMode === 'time' ? data.flatRecordCount : data.recordCount) || 0) === 0
  }
}

// 【2026-10-29 字典接线】行业选项=系统字典 industry 优先(loadIndustryOptions 拉取覆盖 data.industries),
// 本常量降级为字典缺行/接口失败兜底; 兜底已含字典现量「新行业」(原硬编码缺该项致筛不出)。
const INDUSTRIES = ['全部行业', '冶金', '煤矿', '化工', '电力', '有色金属', '机械', '工程公司', '维修厂', '其他终端客户', '新行业']
// 【2026-11-02 用户令】DEV_TYPES 兜底常量随类型筛选退役移除
// 【2026-10-29 归树批】产品筛选选项=分类树"产品试点"分支派生(applyCategoryTree 里覆盖), 本常量降级为
// 兜底; 兜底补上此前手抄漏掉的「其他」(与 PC 目录对齐——旧 MP 扁平清单只有 15 个小类)。
const PRODUCT_OPTIONS = ['全部产品', '防腐', '加固', '钢结构', '煤气管道', '其他', '公共区', '装置区', '水泵', '渣泵', '介质泵', '静密封', '动密封', '橡胶密封', '润滑油', '润滑脂', '润滑剂']
// 【2026-10-27 用户令 B 案】分类筛选改两级(大类→子类): 原扁平 picker 只列子类名, 四大主类
// (产品试点/交流拜访/外出参观/两谈两做方案)不可选, 且选项只从"每客户最新一条"收集会漏分类。
// 两级数据源=分类树接口; 树不可用时降级为"全部分类 + 记录推导子类"单层(fail-open)。
const CATEGORY_ALL = '全部分类'
// 演示态分类树(与生产树同形: categoryName/children)
const DEMO_CATEGORY_TREE = [
  { categoryName: '产品试点', children: [{ categoryName: '涂层类' }, { categoryName: '防滑板类' }, { categoryName: '机封类' }, { categoryName: '密封类' }, { categoryName: '润滑类' }] },
  { categoryName: '交流拜访', children: [{ categoryName: '技术交流会' }, { categoryName: '高层拜访' }] },
  { categoryName: '外出参观', children: [{ categoryName: '南京工厂参观' }, { categoryName: '终端考察' }] },
  { categoryName: '两谈两做方案', children: [{ categoryName: '项目方案' }, { categoryName: '价值服务手册' }, { categoryName: '使用报告' }] }
]
// 【2026-10-26 用户令"要有按时间排序"】原三档(默认/分值高低)改四档: 时间两档显式化,
// 「默认排序」即接口顺序=最新在前, 由首档承接; 值 time-*/score-* 交 sort() 分派
const ORDER_OPTIONS = ['最新记录在前', '最早记录在前', '分值从高到低', '分值从低到高']
const ORDER_VALUES = ['time-desc', 'time-asc', 'score-desc', 'score-asc']
// 封面卡时间 = 其最新一条记录的 createdAt(normalizeRecords 的 detail 即最新子记录)。
// 【2026-10-27 瘦身】视图行不再携带 detail 原始记录(其扁平文件字段/ID数组会整份进 setData),
// 时间在 buildRecordView 里折算成标量 coverTime 带出, 排序只认 coverTime
function coverTimeOf(item) {
  return String((item && item.coverTime) || (item && item.detail && item.detail.createdAt) || (item && item.createdAt) || '')
}

// 【2026-10-26 打散视图】一条上传记录 → 记录流行(客户名只进检索不显示, 行首=该记录首图缩略)
function buildFlatRow(record, customerName) {
  const files = record.files || buildIncentiveFiles(record)
  const time = String(record.createdAt || record.createTime || '')
  const categoryName = record.categoryName || record.category || record.type || ''
  // 行首封面: 记录文件里的首张图(与客户封面卡同款取图口径); 无图退化分类首字面
  let coverFile = null
  for (let i = 0; i < (files || []).length && !coverFile; i += 1) {
    if (files[i] && files[i].type === 'image' && files[i].id) coverFile = { id: files[i].id }
  }
  return {
    id: record.id,
    customerName: record.customerName || customerName || '未命名客户',
    categoryName,
    coverFile: coverFile || {},
    coverChar: (categoryName || '记').trim().charAt(0).toUpperCase(),
    productText: [record.specificProduct, record.relatedProducts, record.product].filter(Boolean).join(' · '),
    uploaderName: record.userName || record.updatedByName || record.createByName || '',
    score: Number(record.score) || 0,
    createdAt: time,
    timeText: time.replace('T', ' ').slice(0, 16),
    fileCount: (files || []).length
  }
}

/** 打散记录流: 全部分组的子记录拉平(分组无子记录时兜底把分组本身当一条, 兼容演示数据) */
function buildFlatRecordRows(groups, childMap) {
  const rows = []
  ;(groups || []).forEach((group) => {
    const children = (childMap && childMap[group.id]) || group.records || []
    if ((children || []).length) {
      children.forEach((record) => {
        if (record && record.id !== undefined && record.id !== null) rows.push(buildFlatRow(record, group.name))
      })
    } else if (group && group.id !== undefined && group.createdAt) {
      rows.push(buildFlatRow(group, group.name))
    }
  })
  return rows
}
// 【2026-11-02 用户令】组织筛选退役: FALLBACK_ORGS/DEV_TYPES 兜底常量随之移除

Page({
  data: {
    incentiveView: buildIncentiveView({
      industries: INDUSTRIES,
      productOptions: PRODUCT_OPTIONS,
      orderOptions: ORDER_OPTIONS,
      userNames: ['全部人员'],
      recordCount: 0,
      flatRecordCount: 0,
      listMode: 'time',
      loading: false
    }),
    order: 'time-desc',
    orderOptions: ORDER_OPTIONS,
    selectedOrderIndex: 0,
    // 【2026-10-26 打散视图】轻量页默认 time=打散记录流(用户令), customer=按客户归集封面卡。
    // 【2026-10-27 深度检查瘦身】全量数组(allRecords/records/flatRecords)改存页面实例 this._*,
    // data 只留 paged 切片与计数——排序/筛选不再整包 setData(项目瘦身基线: thumb.wxs 233KB 教训)
    listMode: 'time',
    pagedFlatRecords: [],
    flatPagerTotal: 0,
    flatRecordCount: 0,
    category: CATEGORY_ALL,
    categoryNames: [],
    categories: ['全部'],
    // 【B 案】两级分类选择器(multiSelector 双列): 大类列=全部分类+四大类, 子类列=全部+该大类子类;
    // categoryValue=滚轮位置(columnchange 即变, 取消时回弹), categoryMain/SubIndex=已确认选择(驱动文案与过滤)
    categoryMains: [CATEGORY_ALL],
    categorySubs: ['全部'],
    categoryRange: [[CATEGORY_ALL], ['全部']],
    categoryValue: [0, 0],
    categoryMainIndex: 0,
    categorySubIndex: 0,
    industries: INDUSTRIES,
    productOptions: PRODUCT_OPTIONS,
    keyword: '',
    selectedIndustryIndex: 0,
    selectedProductIndex: 0,
    users: [],
    userNames: ['全部人员'],
    selectedUserIndex: 0,
    recordCount: 0,
    pagedRecords: [],
    page: 1,
    pageSize: 10,
    pagerTotal: 0,
    loading: false,
    canUploadEvidence: false
  },

  refreshIncentiveView() {
    this.setData({ incentiveView: buildIncentiveView(this.data) })
  },

  onLoad() {
    if (!requireLogin()) return
    this.setData({ apiBase: (getApp().globalData && getApp().globalData.apiBaseUrl) || '' })
    if (!getApp().globalData.demoMode && !hasPermission('incentive:view')) {
      wx.showToast({ title: '没有四项服务查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    this.setData({ canUploadEvidence: !getApp().globalData.demoMode && hasPermission('incentive:add') })
    this._categoryChildrenByMain = null
    this.loadCategoryTree()
    this.loadIndustryOptions()
    this.ensureDlToken().then(() => {
      this.loadRecords()
    })
    this.loadUsers()
    this._loadedOnce = true
  },

  // 与完整版同款回流契约: 上传/删除后 forceRefresh.incentive 命中即强制刷新; 30s 节流只拦纯回流
  onShow() {
    // 【2026-10-24 自定义 tabBar】每页独立实例, onShow 同步选中态(勿删, 否则选中滞留旧页)
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 1 })
    if (this._loadedOnce) {
      const forceRefresh = getApp().globalData.forceRefresh
      if (forceRefresh && forceRefresh.incentive) {
        forceRefresh.incentive = false
        this._lastShowTs = 0
      }
      const now = Date.now()
      if (now - (this._lastShowTs || 0) < 30000) return
      this._lastShowTs = now
      this.ensureDlToken().then(() => {
        this.loadRecords()
      })
    }
  },

  loadRecords() {
    const app = getApp()
    if (app.globalData.demoMode) {
      const decorated = incentiveRecords.map((item) => ({ category: item.category || item.type || item.categoryName || '未分类', count: item.count || 1, files: buildIncentiveFiles(item), fileGroups: buildIncentiveFileGroups(item), ...item }))
      this.applyRecordSource(this.rebuildRecordViews(decorated))
      return
    }

    this.setData({ loading: true }, () => this.refreshIncentiveView())
    const params = this.buildRecordParams()
    getCustomerRecords(params).then((rows) => {
      this.applyRecordSource(this.rebuildRecordViews(normalizeRecords(rows)))
    }).catch((error) => showErrorWithRetry(error, '四项服务加载失败', () => this.loadRecords())).finally(() => {
      this.setData({ loading: false }, () => this.refreshIncentiveView())
    })
  },

  /** 全量视图行落实例字段(不进 data), data 只带分类选项——排序/筛选产物同样只留切片(瘦身基线) */
  applyRecordSource(records) {
    this._allRecords = records
    const categories = ['全部', ...new Set((records || []).map((item) => item.category))]
    const patch = { categories }
    // 分类树不可用(降级态): 子类列=全部+记录推导分类, 记录源更新时同列刷新
    if (!this._categoryChildrenByMain) {
      const subs = this.categorySubsOf(0)
      patch.categorySubs = subs
      patch.categoryRange = [this.data.categoryMains || [CATEGORY_ALL], subs]
    }
    this.setData(patch, () => this.sort())
  },

  // 【B 案】两级分类: 大类列=全部分类+四大主类; 子类列=全部+该大类子类(树不可用时=记录推导分类)
  loadCategoryTree() {
    if (getApp().globalData.demoMode) {
      this.applyCategoryTree(DEMO_CATEGORY_TREE)
      return
    }
    getCategoryTree().then((rows) => {
      this.applyCategoryTree(Array.isArray(rows) ? rows : [])
    }).catch(() => {
      // fail-open: 树拉不到不阻断列表, 筛选降级为"全部分类+记录子类"单层
      this.applyCategoryTree(null)
    })
  },

  // 【2026-10-29 字典接线】行业筛选选项=系统字典 industry(字典加值即跟随); 失败/空保持兜底常量(fail-open)
  loadIndustryOptions() {
    if (getApp().globalData.demoMode) return
    getDictData('industry').then((rows) => {
      const labels = (Array.isArray(rows) ? rows : [])
        .filter((r) => r && r.status !== 0)
        .map((r) => r && r.dictValue)
        .filter(Boolean)
      if (!labels.length) return
      const industries = ['全部行业', ...labels]
      const selectedIndustryIndex = Math.min(this.data.selectedIndustryIndex || 0, industries.length - 1)
      this.setData({ industries, selectedIndustryIndex }, () => this.refreshIncentiveView())
    }).catch(() => {})
  },

  // 【2026-11-02 用户令】类型筛选退役(页面只留搜索+行业/产品平铺):
  // 原 loadDevTypeOptions(字典 dev_type 接线, 2026-11-01 审计修复引入)随筛选一起移除

  applyCategoryTree(nodes) {
    if (nodes && nodes.length) {
      const mains = []
      const childrenByMain = {}
      ;(nodes || []).forEach((node) => {
        const name = node && (node.categoryName || node.name)
        if (!name) return
        mains.push(name)
        childrenByMain[name] = (node.children || [])
          .map((child) => (child && (child.categoryName || child.name)) || '')
          .filter(Boolean)
      })
      if (!mains.length) {
        this.applyCategoryTree(null)
        return
      }
      this._categoryChildrenByMain = childrenByMain
      this.setData({ categoryMains: [CATEGORY_ALL, ...mains] }, () => this.syncCategoryPicker())
      // 【2026-10-29 归树批】产品筛选选项随树"产品试点"分支派生(大类→小类拉平); 无三级结构保持兜底
      const trial = (nodes || []).find((n) => n && ((n.categoryCode || '') === 'trial' || (n.categoryName || n.name) === '产品试点'))
      const productNames = []
      ;(((trial && trial.children) || [])).forEach((child) => {
        ;((child && child.children) || []).forEach((g) => {
          const name = (g && (g.categoryName || g.name)) || ''
          if (name && productNames.indexOf(name) < 0) productNames.push(name)
        })
      })
      if (productNames.length) {
        const productOptions = ['全部产品', ...productNames]
        const selectedProductIndex = Math.min(this.data.selectedProductIndex || 0, productOptions.length - 1)
        this.setData({ productOptions, selectedProductIndex }, () => this.refreshIncentiveView())
      }
      return
    }
    this._categoryChildrenByMain = null
    const subs = this.categorySubsOf(0)
    this.setData({ categoryMains: [CATEGORY_ALL], categorySubs: subs, categoryRange: [[CATEGORY_ALL], subs] }, () => this.syncCategoryPicker())
  },

  /** 当前大类下的子类列(树态: 全部+该大类子类; 降级态: 全部+记录推导分类) */
  categorySubsOf(mainIndex) {
    const children = this._categoryChildrenByMain
    if (!children) {
      return ['全部', ...(this.data.categories || []).filter((name) => name && name !== '全部')]
    }
    if (Number(mainIndex) <= 0) return ['全部']
    const main = (this.data.categoryMains || [])[Number(mainIndex)]
    return ['全部', ...((main && children[main]) || [])]
  },

  /** 大类列/子类列变化后同步 range 与 value(选中越界即回夹) */
  syncCategoryPicker() {
    const mains = this.data.categoryMains || [CATEGORY_ALL]
    const value = this.data.categoryValue || [0, 0]
    const mainIndex = Math.min(Math.max(0, Number(value[0]) || 0), mains.length - 1)
    const subs = this.categorySubsOf(mainIndex)
    const subIndex = Math.min(Math.max(0, Number(value[1]) || 0), subs.length - 1)
    this.setData({
      categorySubs: subs,
      categoryRange: [mains, subs],
      categoryValue: [mainIndex, subIndex]
    }, () => this.refreshIncentiveView())
  },

  /** 滚轮联动: 大类列变化即刷新子类列并归零; 子类列滚动不落 data(取消选择时滚轮回弹到 data.value) */
  onCategoryColumnChange(e) {
    const detail = (e && e.detail) || {}
    if (Number(detail.column) !== 0) return
    const mainIndex = Math.max(0, Number(detail.value) || 0)
    const subs = this.categorySubsOf(mainIndex)
    this.setData({
      categorySubs: subs,
      categoryRange: [this.data.categoryMains || [CATEGORY_ALL], subs],
      categoryValue: [mainIndex, 0]
    })
  },

  /** 确认两级选择 → categoryNames(过滤机制不变: 客户视图按子记录分类匹配, 打散视图按行分类匹配) */
  chooseCategory(e) {
    const pair = (e && e.detail && e.detail.value) || [0, 0]
    const mains = this.data.categoryMains || [CATEGORY_ALL]
    const mainIndex = Math.min(Math.max(0, Number(pair[0]) || 0), mains.length - 1)
    const subs = this.categorySubsOf(mainIndex)
    const subIndex = Math.min(Math.max(0, Number(pair[1]) || 0), subs.length - 1)
    const sub = subs[subIndex] || '全部'
    let names = []
    if (mainIndex > 0) {
      // 大类·全部子类 → 该大类全部子类名; 大类·子类 → 单个子类名
      names = subIndex > 0 && sub !== '全部' ? [sub] : subs.slice(1)
    } else if (!this._categoryChildrenByMain && subIndex > 0 && sub !== '全部') {
      // 降级态: 大类列只有"全部分类", 子类列直接按名过滤
      names = [sub]
    }
    this.setData({
      categoryValue: [mainIndex, subIndex],
      categoryMainIndex: mainIndex,
      categorySubIndex: subIndex,
      categorySubs: subs,
      categoryRange: [mains, subs],
      category: mainIndex > 0 ? mains[mainIndex] : (names.length === 1 ? sub : CATEGORY_ALL),
      categoryNames: names
    }, () => this.refreshIncentiveView())
    this.sort()
  },

  /** 取消选择: 滚轮回弹到已确认位置(文案/过滤只认已确认选择, 无需回滚) */
  onCategoryCancel() {
    this.setData({ categoryValue: [Number(this.data.categoryMainIndex) || 0, Number(this.data.categorySubIndex) || 0] })
  },

  rebuildRecordViews(decorated) {
    this._childRawById = {}
    ;(decorated || []).forEach((group) => {
      if (group && group.id !== undefined && group.id !== null) {
        this._childRawById[group.id] = group.records || []
      }
    })
    return (decorated || []).map(buildRecordView)
  },

  buildRecordParams() {
    const params = {}
    const keyword = (this.data.keyword || '').trim()
    const selectedUser = this.data.users[this.data.selectedUserIndex - 1]
    const industry = this.data.industries[this.data.selectedIndustryIndex]
    const product = this.data.productOptions[this.data.selectedProductIndex]
    if (keyword) {
      params.customerName = keyword
      params.keyword = keyword
    }
    if (selectedUser) params.salesperson = selectedUser.realName || selectedUser.userName || selectedUser.name
    if (industry && industry !== '全部行业') params.industry = industry
    if (product && product !== '全部产品') params.product = product
    // 【2026-11-02 用户令】类型/组织/日期范围筛选退役(页面只留搜索+行业/产品平铺), 不再发参
    return params
  },

  applyLocalRecordFilters(records) {
    const keyword = (this.data.keyword || '').trim().toLowerCase()
    const industry = this.data.industries[this.data.selectedIndustryIndex]
    const product = this.data.productOptions[this.data.selectedProductIndex]
    return records.filter((item) => {
      // 【2026-11-09 用户令"只检索含有关键字的记录"】客户名不再参与关键字匹配(防客户名沾边客户的
      // 全部记录被带出); 2026-11-02 深度检索的备注/详情等字段维持不动(本页既有口径, 非本次指令范围)
      const text = [item.name, item.summary, item.remark, item.description, item.detail, item.preInstallQuestion, item.relatedProducts, item.specificProduct, item.product].filter(Boolean).join(' ').toLowerCase()
      if (keyword && !text.includes(keyword)) return false
      if (industry && industry !== '全部行业' && item.industry !== industry) return false
      if (product && product !== '全部产品') {
        const productText = [item.relatedProducts, item.specificProduct, item.product].filter(Boolean).join(' ').toLowerCase()
        if (productText && !productText.includes(product.toLowerCase())) return false
      }
      return true
    })
  },

  loadUsers() {
    if (getApp().globalData.demoMode) {
      const users = [{ id: 1, realName: '张三' }, { id: 2, realName: '李四' }]
      this.setData({ users, userNames: ['全部人员', ...users.map((item) => item.realName)] }, () => this.refreshIncentiveView())
      return
    }
    getUsers().then((rows) => {
      const users = Array.isArray(rows) ? rows : []
      this.setData({ users, userNames: ['全部人员', ...users.map((item) => item.realName || item.userName || item.name || '未命名')] }, () => this.refreshIncentiveView())
    }).catch((error) => showErrorWithRetry(error, '人员筛选加载失败', () => this.loadUsers()))
  },

  // 【2026-11-02 用户令】组织筛选退役: loadOrgs/applyOrgs 随筛选一起移除(数据只留搜索+行业/产品)

  sort() {
    // 【性能 2026-10-27】按当前视图单算: 筛选/排序变化只重算展示中的那套全量行,
    // 另一套标脏待切换视图时补算(此前每次双算两套, 生产 516 条记录时中端机可感);
    // 两套共用 _allRecords/_childRawById 源, 口径不变
    const categoryNames = this.data.categoryNames || []
    const childMatches = (item) => {
      if (!categoryNames.length) return true
      if (categoryNames.indexOf(item.category) !== -1) return true
      const children = (this._childRawById && this._childRawById[item.id]) || []
      return children.some((child) => {
        const name = child.category || child.categoryName || child.type
        return name && categoryNames.indexOf(name) !== -1
      })
    }
    const patch = { page: 1, pageSize: this.data.pageSize || 10 }
    this._otherModeDirty = true
    if (this.data.listMode === 'time') {
      // 【打散视图】记录流与封面卡共用筛选(分组已过服务端+本地筛选, 拉平即继承),
      // 分类/关键词/日期在行上二次过滤, 排序四档作用于单条记录(时间=行自身 createdAt)
      const keywordFlat = (this.data.keyword || '').trim().toLowerCase()
      let flat = buildFlatRecordRows(this._allRecords || [], this._childRawById).filter((row) => {
        if (categoryNames.length && categoryNames.indexOf(row.categoryName) === -1) return false
        if (keywordFlat) {
          // 【2026-11-09 用户令"只检索含有关键字的记录"】打散视图 haystack 去 customerName——
          // 客户名含关键字=该客户全部行都命中=客户级扩散, 与服务端两路口径+主记录过滤一致
          const haystack = `${row.categoryName} ${row.productText} ${row.uploaderName} ${row.timeText}`.toLowerCase()
          if (!haystack.includes(keywordFlat)) return false
        }
        // 【2026-11-02 用户令】日期范围筛选退役, 客户视图的起止日期本地过滤随之移除
        return true
      })
      if (this.data.order === 'score-asc') flat.sort((a, b) => a.score - b.score)
      if (this.data.order === 'score-desc') flat.sort((a, b) => b.score - a.score)
      if (this.data.order === 'time-asc') flat.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      if (this.data.order === 'time-desc') flat.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      const pagedFlat = paginate(flat, 1, patch.pageSize)
      // 瘦身: 全量 flat 只存实例, data 只带当前页切片与计数(渲染只用 pagedFlatRecords)
      this._flatRecords = flat
      patch.pagedFlatRecords = pagedFlat.rows
      patch.flatPagerTotal = pagedFlat.total
      patch.flatRecordCount = flat.length
    } else {
      let records = this.applyLocalRecordFilters(this._allRecords || []).filter(childMatches)
      if (this.data.order === 'score-asc') records.sort((a, b) => (Number(a.score) || 0) - (Number(b.score) || 0))
      if (this.data.order === 'score-desc') records.sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0))
      if (this.data.order === 'time-asc') records.sort((a, b) => coverTimeOf(a).localeCompare(coverTimeOf(b)))
      if (this.data.order === 'time-desc') records.sort((a, b) => coverTimeOf(b).localeCompare(coverTimeOf(a)))
      const paged = paginate(records, 1, patch.pageSize)
      // 瘦身: 全量 records 只存实例, data 只带当前页切片与计数(渲染只用 pagedRecords)
      this._records = records
      patch.pagedRecords = paged.rows
      patch.pagerTotal = paged.total
      patch.recordCount = records.length
    }
    this.setData(patch, () => this.refreshIncentiveView())
  },

  applyPaging(page, pageSize) {
    // 打散视图与客户视图各自分页, pager 组件按 listMode 取对应总数(源在实例字段)
    const source = this.data.listMode === 'time' ? this._flatRecords : this._records
    const paged = paginate(source || [], page === undefined ? this.data.page : page, pageSize || this.data.pageSize)
    const patch = {
      page: paged.page,
      pageSize: paged.pageSize,
      pagerTotal: paged.total
    }
    if (this.data.listMode === 'time') {
      patch.pagedFlatRecords = paged.rows
      patch.flatPagerTotal = paged.total
    } else {
      patch.pagedRecords = paged.rows
    }
    this.setData(patch)
  },

  onPagerChange(e) {
    const detail = e.detail || {}
    this.applyPaging(Number(detail.page) || 1, Number(detail.pageSize) || this.data.pageSize)
  },

  chooseUser(e) {
    this.setData({ selectedUserIndex: Number(e.detail.value) || 0 }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  inputKeyword(e) {
    this.setData({ keyword: e.detail.value || '' })
  },

  searchRecords() {
    this.loadRecords()
  },

  // 【2026-11-02 用户令·平铺直选】行业/产品从 picker 下拉改为平铺 chips 点击
  // (e.currentTarget.dataset.index; picker 的 e.detail.value 口径退役)
  chooseIndustry(e) {
    const idx = Number(e.currentTarget.dataset.index) || 0
    if (idx === this.data.selectedIndustryIndex) return
    this.setData({ selectedIndustryIndex: idx }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  chooseProduct(e) {
    const idx = Number(e.currentTarget.dataset.index) || 0
    if (idx === this.data.selectedProductIndex) return
    this.setData({ selectedProductIndex: idx }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  // 【2026-11-02 用户令】原类型/组织/起止日期四个下拉筛选 handler 随筛选退役一并移除
  // (交互只留搜索+行业/产品平铺 chips; 防复活钉见 check-mobile-incentive-contract)

  resetRecordFilters() {
    this.setData({
      keyword: '',
      selectedIndustryIndex: 0,
      selectedProductIndex: 0,
      selectedUserIndex: 0,
      selectedOrderIndex: 0,
      order: 'time-desc',
      category: CATEGORY_ALL,
      categoryNames: [],
      categoryValue: [0, 0],
      categoryMainIndex: 0,
      categorySubIndex: 0
    }, () => { this.syncCategoryPicker(); this.refreshIncentiveView() })
    this.loadRecords()
  },

  chooseOrder(e) {
    const index = Number(e.detail.value) || 0
    this.setData({ selectedOrderIndex: index, order: ORDER_VALUES[index] || 'time-desc' }, () => this.refreshIncentiveView())
    this.sort()
  },

  goUpload() {
    if (!this.data.canUploadEvidence) return wx.showToast({ title: '没有四项服务上传权限', icon: 'none' })
    wx.navigateTo({ url: '/pages/incentive/upload/index' })
  },

  // 【2026-10-26 用户令】轻量页双视图切换: time=打散记录流(默认) / customer=按客户归集封面卡
  switchListMode(e) {
    const mode = e.currentTarget.dataset.mode
    if (mode !== 'time' && mode !== 'customer') return
    if (mode === this.data.listMode) return
    this.setData({ listMode: mode, page: 1 }, () => {
      // 【性能 2026-10-27】sort 只算当前视图, 切到标脏的另一套时补算一次(切片/计数随批落地)
      if (this._otherModeDirty) this.sort()
      else this.applyPaging(1)
      this.refreshIncentiveView()
    })
  },

  // 打散视图点行: 该条记录直达拨盘详情页(与点客户卡同款 detail?id=记录id)
  openFlatRecord(e) {
    const index = Number(e.currentTarget.dataset.index)
    const row = this.data.pagedFlatRecords[index]
    if (!row || !row.id) return
    wx.navigateTo({ url: '/pages/incentive/detail/index?id=' + row.id })
  },

  // 【2026-10-26 用户令"展示按拨盘来,不要一个客户一个档案"】点客户直达既有拨盘详情页
  // (pages/incentive/detail: 年/月/日三联转盘+按所选日期的记录, 一次上传=一条记录,
  // 编辑/追加/删除/预览操作全保留); 原 records 平铺清单页随本批退役。
  openCustomer(e) {
    const index = Number(e.currentTarget.dataset.index)
    const record = this.data.pagedRecords[index]
    if (!record) return
    const children = (this._childRawById && this._childRawById[record.id]) || []
    const first = children[0]
    if (!first || !first.id) return wx.showToast({ title: '该客户暂无记录', icon: 'none' })
    wx.navigateTo({ url: '/pages/incentive/detail/index?id=' + first.id })
  },

  ensureDlToken() {
    if (getApp().globalData.demoMode) return Promise.resolve()
    const user = getApp().globalData.currentUser
    const userId = user ? (user.id != null ? user.id : user.username) : null
    // 【2026-11-08 图片预览可靠性】命中分支判时效: 超 50 分钟(DL_TOKEN_FRESH_MS, 后端
    // TTL=1h 留 10 分钟余量)视为失效重取——tabBar 常驻超 1h 后行首缩略直链全 401;
    // 时效失效不清缓存/不清 data 旧值, 重取失败即保留旧 token(聊胜于无, 时间戳未
    // 刷新=下次回流仍会重试)。
    const sameUser = dlTokenCacheUserId != null && dlTokenCacheUserId === userId
    const cacheUsable = Boolean(dlTokenCache) && sameUser && Date.now() - dlTokenCacheIssuedAt <= DL_TOKEN_FRESH_MS
    if (!sameUser && dlTokenCache) {
      setHomeDlToken('', null)
      this._dlTokenPromise = null
    }
    if (cacheUsable && !this.data.dlToken) this.setData({ dlToken: dlTokenCache })
    if (cacheUsable || this._dlTokenPromise) return this._dlTokenPromise || Promise.resolve()
    this._dlTokenPromise = getDownloadToken().then((result) => {
      this._dlTokenPromise = null
      const token = result && (result.token || (result.data && result.data.token)) || ''
      setHomeDlToken(token, userId)
      this.setData({ dlToken: token })
    }).catch(() => {
      this._dlTokenPromise = null
      // 【2026-11-08 图片预览可靠性】请求失败保留旧 token(聊胜于无): 模块缓存与 data 均
      // 未被清掉, 缩略图继续用旧直链, 下次回流仍会重试
    }).then(() => {})
    return this._dlTokenPromise
  },

  refresh() {
    this.loadRecords()
    this.loadUsers()
  }
})
