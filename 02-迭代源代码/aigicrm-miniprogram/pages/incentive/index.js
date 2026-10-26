const { getCategoryStats, getCategoryTree, getCustomerRecords, getGrantCheck, getRanking, getReadRanking, getReadStats, getRecords, getScoreboard, getUsers } = require('../../api/incentive')
const { getDownloadUrl, getDownloadToken } = require('../../api/file')
const { getDepts } = require('../../api/system')
const { incentiveRecords } = require('../../data/demo')
const { hasPermission, isAdmin, requireLogin } = require('../../utils/auth')
const { fetchRemoteAttachment } = require('../../utils/attachment')
const { showError, showErrorWithRetry } = require('../../utils/feedback')
const { buildIncentiveFileGroups, buildIncentiveFiles } = require('../../utils/incentive-files')
const { paginate } = require('../../utils/paging')
const { buildDeptLevelOptions } = require('../../utils/tree')

// 【#4+#7 2026-10-12】列表页佐证照片墙: 缩略图凭证模块级缓存(取一次复用), 未就绪时图片退化为
// 文档面卡仍可点开大图; TAG_COLORS 与详情页同款槽位配色
// 【F21 2026-10-13】缓存按用户隔离: 换账号登录后旧凭证立即失效重取, 杜绝跨用户下载凭证滞留。
let dlTokenCache = ''
let dlTokenCacheUserId = null
function setIncentiveDlToken(token, userId) {
  dlTokenCache = token || ''
  dlTokenCacheUserId = token ? (userId ?? null) : null
}
const TAG_COLORS = { 安装前: '#b45309', 安装中: '#1d4fd7', 安装后: '#047857', 跟踪: '#7c3aed', 佐证: '#64748b' }
// 【setData 瘦身 2026-10-13】缩略图 URL 拼接移交 thumb.wxs(渲染层), 此处仅维护 token 供其引用

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

function flattenCategoryTree(nodes = [], level = 0, result = [], parent = null) {
  nodes.forEach((item) => {
    const name = item.categoryName || item.name || '未命名分类'
    result.push({
      id: item.id,
      name,
      score: item.score || 0,
      level,
      count: item.count || item.fileCount || item._recordCount || 0,
      parentId: (parent && parent.id) || item.parentId || 0,
      parentName: (parent && parent.name) || ''
    })
    if (item.children && item.children.length) flattenCategoryTree(item.children, level + 1, result, { id: item.id, name })
  })
  return result
}

function buildSummary(records) {
  const categorySet = new Set()
  let recordCount = 0
  let fileCount = 0
  let unreadCount = 0
  let score = 0
  records.forEach((item) => {
    if (item.category) categorySet.add(item.category)
    recordCount += item.count || 0
    // 【setData 瘦身 2026-10-13】视图已不带原始 files 数组, 用构造期预算的 fileCountTotal
    fileCount += item.fileCountTotal !== undefined ? item.fileCountTotal : (item.files || []).length
    unreadCount += item.unread ? 1 : 0
    score += item.score || 0
  })
  return { customers: records.length, records: recordCount, categories: categorySet.size, files: fileCount, unread: unreadCount, score }
}

function normalizeScoreRows(rows, type) {
  return (rows || []).map((item, index) => ({
    id: item.customerId || item.userId || item.deptId || item.id || `${type}-${index}`,
    rank: item.rank || index + 1,
    name: item.customerName || item.realName || item.userName || item.deptName || item.name || '未命名',
    score: item.totalScore || item.score || item.points || 0,
    records: item.recordCount || item.count || item.totalRecords || 0,
    categoryCount: item.categoryCount || (item.byCategory ? Object.keys(item.byCategory).length : 0),
    metaText: `${item.recordCount || item.count || item.totalRecords || 0} 条记录 · ${item.categoryCount || (item.byCategory ? Object.keys(item.byCategory).length : 0)} 类`,
    scoreText: `${item.totalScore || item.score || item.points || 0} 分`
  }))
}

function normalizeStatDetailRecord(item) {
  const files = buildIncentiveFiles(item)
  const fileGroups = buildIncentiveFileGroups(item)
  return {
    ...item,
    files,
    fileGroups,
    fileGroupsView: buildFileGroupView(fileGroups),
    customerName: item.customerName || item.customer || item.name || '未命名客户',
    userName: item.userName || item.updatedByName || item.createByName || item.realName || '未命名',
    score: item.score || item.points || 0,
    scoreText: `${item.score || item.points || 0} 分`,
    timeText: item.createdAt || item.createTime || item.updatedAt || '-',
    remarkText: item.remark || item.preInstallQuestion || item.description || item.summary || '-',
    metaText: `${item.userName || item.updatedByName || item.createByName || item.realName || '未命名'} · ${item.createdAt || item.createTime || item.updatedAt || '-'}`,
    showFiles: fileGroups.length > 0
  }
}

function buildCategoryTreeView(rows) {
  return (rows || []).map((item) => ({
    ...item,
    // 真实树顶级节点 score=0(分值挂在子分类),无分值时只展示条数,避免"0分 · N条"误导
    metaText: item.score > 0 ? `${item.score}分 · ${item.count || 0}条` : `${item.count || 0}条`
  }))
}

function filterStatDetailRecords(records, keyword) {
  const list = Array.isArray(records) ? records : []
  const kw = String(keyword || '').trim().toLowerCase()
  if (!kw) return list
  return list.filter((item) => {
    const hay = `${item.customerName || ''} ${item.userName || ''} ${item.remarkText || ''} ${item.categoryName || ''}`
    return hay.toLowerCase().indexOf(kw) >= 0
  })
}

function paginateStatDetailRecords(records, page, pageSize) {
  const list = Array.isArray(records) ? records : []
  const size = pageSize > 0 ? pageSize : 10
  const total = list.length
  const totalPages = Math.max(1, Math.ceil(total / size))
  const safePage = Math.min(Math.max(1, page || 1), totalPages)
  const start = (safePage - 1) * size
  return {
    // sourceIndex 指向完整 records，附件预览/下载仍按全量数组取数
    rows: list.slice(start, start + size).map((item, index) => ({ ...item, sourceIndex: start + index })),
    total,
    totalPages,
    page: safePage,
    pageSize: size
  }
}

function buildStatDetailView(statDetail) {
  const all = statDetail.records || []
  const keyword = statDetail.keyword || ''
  const filtered = filterStatDetailRecords(all, keyword)
  const paged = paginateStatDetailRecords(filtered, statDetail.page, statDetail.pageSize)
  const totalScore = filtered.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
  return {
    ...statDetail,
    pagedRecords: paged.rows,
    page: paged.page,
    pageSize: paged.pageSize,
    total: paged.total,
    totalPages: paged.totalPages,
    totalScore,
    keyword,
    metaText: `${statDetail.parentName || '全部分类'} · ${paged.total} 条 · ${totalScore} 分`,
    showEmpty: !statDetail.loading && paged.total === 0,
    emptyText: keyword ? '没有匹配的明细' : '该分类暂无激励明细'
  }
}

function buildReadStatsView(rows) {
  return (rows || []).map((item, index) => ({
    ...item,
    rankText: `${index + 1}. ${item.realName || item.userName || item.name || '未命名'}`,
    countText: `已读 ${item.readCount || 0} / 未读 ${item.unreadCount || 0}`
  }))
}

function buildReadRankingView(rows) {
  return (rows || []).map((item, index) => ({
    ...item,
    rankText: `${index + 1}. ${item.realName || item.userName || item.name || '未命名'}`,
    countText: `${item.readCount || item.count || 0} 次`
  }))
}

function buildFileGroupView(fileGroups) {
  return (fileGroups || []).map((group, groupIndex) => ({
    ...group,
    groupIndex,
    countText: `${(group.files || []).length} 个`,
    slotText: `${group.label} ${(group.files || []).length}`,
    // 【setData 瘦身 2026-10-13】不再生成 thumbUrl(签名 URL 每条数百字节, 903 文件共 233KB)与
    // nameDisplay(与 file.name 重复)——缩略图 URL 由 thumb.wxs 在渲染层用 apiBase+dlToken 拼接
    files: (group.files || []).map((file, fileIndex) => ({
      ...file,
      fileIndex,
      tagText: file.tag || '',
      tagColor: TAG_COLORS[file.tag] || '#64748b',
      showTag: Boolean(file.tag)
    }))
  }))
}

function buildChildRecordView(record) {
  const fileGroupsView = buildFileGroupView(record.fileGroups)
  return {
    ...record,
    titleText: record.categoryName || record.category || record.type || '未分类',
    readStatusText: record.unread ? '未读' : '已读',
    readStatusClass: record.unread ? 'tag-unread inline-read-status' : 'tag-read inline-read-status',
    metaText: `${record.period || '未设周期'} · ${record.userName || record.updatedByName || '未命名'} · ${record.createdAt || record.createTime || '-'}`,
    scoreText: `${record.score || record.points || 0} 分`,
    summaryText: record.preInstallQuestion || record.remark || '',
    showSummary: Boolean(record.preInstallQuestion || record.remark),
    showFiles: fileGroupsView.length > 0,
    showAppendAction: Boolean(record.canEdit),
    showDeleteAction: Boolean(record.canDelete),
    fileGroupsView
  }
}

function buildRecordView(record) {
  const fileGroupsView = buildFileGroupView(record.fileGroups)
  // 【setData 瘦身 2026-10-13】原始 records/files/fileGroups 大数组不进 data(存页面内存
  // _childRawById/_allDecorated), 68 客户组曾因此把 setData 顶到 1470KB(超 1MB 真机硬限)
  // 【需求C 2026-10-16】列表页=封面卡: 子记录视图不再进 data(点卡时在 openCustomerDetail 现构建),
  // 新增封面字段 coverFile(首图,仅传 id 供 thumb.wxs)/coverChar(无图时渐变首字)
  const { records: _omitRecords, files: _omitFiles, fileGroups: _omitGroups, ...rest } = record
  let coverFile = null
  for (let gi = 0; gi < fileGroupsView.length && !coverFile; gi += 1) {
    const hit = (fileGroupsView[gi].files || []).find((file) => file.type === 'image' && file.id)
    if (hit) coverFile = { id: hit.id }
  }
  return {
    ...rest,
    scoreText: `${record.score || 0} 分`,
    countText: `${record.count || 0}条`,
    fileCountText: `${(record.files || []).length}文件`,
    fileCountTotal: fileGroupsView.reduce((sum, group) => sum + (group.files || []).length, 0),
    readStatusText: record.unread ? '未读' : '已读',
    readStatusClass: record.unread ? 'tag-unread' : 'tag-read',
    showFiles: fileGroupsView.length > 0,
    fileGroupsView,
    coverFile: coverFile || {},
    coverChar: (record.name || '客').trim().charAt(0).toUpperCase(),
    childCount: (record.records || []).length,
    showCustomerAction: Boolean(record.customerId),
    showAppendAction: Boolean(record.canEdit),
    showDeleteAction: Boolean(record.canDelete)
  }
}

function buildTabs(activeTab) {
  return [
    { key: 'records', label: '客户记录' },
    { key: 'categories', label: '分类树' },
    { key: 'stats', label: '统计' },
    { key: 'ranking', label: '排行' },
    { key: 'reads', label: '阅读' }
  ].map((item) => ({
    ...item,
    className: activeTab === item.key ? 'active' : ''
  }))
}

function pickOption(options, index, fallback, key) {
  const item = (options || [])[Number(index) || 0]
  if (!item) return fallback
  if (key) return item[key] || fallback
  return item || fallback
}

function buildIncentiveView(data) {
  const isLoading = Boolean(data.loading)
  return {
    showRecords: data.activeTab === 'records',
    showCategories: data.activeTab === 'categories',
    showStats: data.activeTab === 'stats',
    showRanking: data.activeTab === 'ranking',
    showReads: data.activeTab === 'reads',
    refreshLoading: isLoading,
    refreshText: isLoading ? '刷新中' : '刷新',
    searchLoading: isLoading,
    searchText: isLoading ? '查询中' : '查询',
    showLoading: isLoading,
    pagerLoading: isLoading,
    loadingText: '加载中...',
    previewLoading: Boolean(data.previewing),
    industryText: `${pickOption(data.industries, data.selectedIndustryIndex, '全部行业')}⌄`,
    devTypeText: `${pickOption(data.devTypes, data.selectedDevTypeIndex, '全部类型')}⌄`,
    productText: `${pickOption(data.productOptions, data.selectedProductIndex, '全部产品')}⌄`,
    orgText: `${pickOption(data.orgOptions, data.selectedOrgIndex, '全部组织', 'label')}⌄`,
    startDateText: `${data.startDate || '开始日期'}⌄`,
    endDateText: `${data.endDate || '截止日期'}⌄`,
    userText: `${pickOption(data.userNames, data.selectedUserIndex, '全部人员')}⌄`,
    orderText: `${pickOption(data.orderOptions, data.selectedOrderIndex, '最新记录在前')}⌄`,
    readPeriodText: `${pickOption(data.readPeriodOptions, data.selectedReadPeriodIndex, data.readPeriod || currentMonth(), 'label')}⌄`,
    showRecordEmpty: !isLoading && (data.records || []).length === 0,
    // 【工作流复核确认修复】空态统一加 !isLoading 守卫(与 records tab 同口径, 弱网首屏不显示"暂无"假空态)
    showCategoryEmpty: !isLoading && (data.categoryTree || []).length === 0,
    showCategoryStatsEmpty: !isLoading && (data.categoryStats || []).length === 0,
    showScoreboardEmpty: !isLoading && (data.scoreboard || []).length === 0,
    showDeptRankingEmpty: !isLoading && (data.deptRanking || []).length === 0,
    showReadStatsEmpty: !isLoading && (data.readStats || []).length === 0,
    // 【I5】加载失败与"确实没有数据"分开: 失败时展示错误+重试, 不再伪装成"暂无排行"
    showReadRankingError: Boolean(data.readRankingError),
    showReadRankingEmpty: !isLoading && !data.readRankingError && (data.readRanking || []).length === 0,
    showReadStatsPanel: Boolean(data.canViewReadStats),
    showReadRankingPanel: Boolean(data.canViewReadRanking),
    showNoReadPermission: !data.canViewReadStats && !data.canViewReadRanking
  }
}

const INDUSTRIES = ['全部行业', '冶金', '煤矿', '化工', '电力', '有色金属', '机械', '工程公司', '维修厂', '其他终端客户']
const DEV_TYPES = ['全部类型', '成熟创新', '突破开拓', '铺垫宣传']
const PRODUCT_OPTIONS = ['全部产品', '防腐', '加固', '钢结构', '煤气管道', '公共区', '装置区', '水泵', '渣泵', '介质泵', '静密封', '动密封', '橡胶密封', '润滑油', '润滑脂', '润滑剂']
// 【2026-10-26 用户令"要有按时间排序"】与轻量页(pages/incentive/home)同款四档,
// 「默认排序」(=接口顺序=最新在前)由首档显式承接
const ORDER_OPTIONS = ['最新记录在前', '最早记录在前', '分值从高到低', '分值从低到高']
const ORDER_VALUES = ['time-desc', 'time-asc', 'score-desc', 'score-asc']
function coverTimeOf(item) {
  return String((item && item.detail && item.detail.createdAt) || item.createdAt || '')
}
const FALLBACK_ORGS = [
  { id: 1, deptName: '内蒙古大区', deptType: 2, parentId: 0 },
  { id: 11, deptName: '内蒙办', deptType: 3, parentId: 1 },
  { id: 2, deptName: '山西大区', deptType: 2, parentId: 0 },
  { id: 21, deptName: '山西办', deptType: 3, parentId: 2 }
]

function currentMonth() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function buildReadPeriodOptions() {
  const date = new Date()
  const options = []
  for (let index = 0; index < 12; index += 1) {
    const monthDate = new Date(date.getFullYear(), date.getMonth() - index, 1)
    const value = `${monthDate.getFullYear()}-${String(monthDate.getMonth() + 1).padStart(2, '0')}`
    options.push({ label: `${monthDate.getFullYear()}年${monthDate.getMonth() + 1}月`, value })
  }
  return options
}

function currentUserId() {
  const user = getApp().globalData.currentUser || {}
  return user.id || user.userId || ''
}

Page({
  data: {
    activeTab: 'records',
    tabs: buildTabs('records'),
    incentiveView: buildIncentiveView({
      activeTab: 'records',
      industries: INDUSTRIES,
      devTypes: DEV_TYPES,
      productOptions: PRODUCT_OPTIONS,
      orgOptions: [{ id: '', deptName: '全部组织', label: '全部组织' }],
      orderOptions: ORDER_OPTIONS,
      userNames: ['全部人员'],
      readPeriodOptions: buildReadPeriodOptions(),
      readPeriod: currentMonth(),
      records: [],
      categoryTree: [],
      categoryStats: [],
      scoreboard: [],
      deptRanking: [],
      readStats: [],
      readRanking: [],
      loading: false
    }),
    order: 'time-desc',
    orderOptions: ORDER_OPTIONS,
    selectedOrderIndex: 0,
    category: '全部',
    categoryNames: [],
    categories: ['全部'],
    industries: INDUSTRIES,
    devTypes: DEV_TYPES,
    productOptions: PRODUCT_OPTIONS,
    orgOptions: [{ id: '', deptName: '全部组织', label: '全部组织' }],
    keyword: '',
    selectedIndustryIndex: 0,
    selectedDevTypeIndex: 0,
    selectedProductIndex: 0,
    selectedOrgIndex: 0,
    startDate: '',
    endDate: '',
    users: [],
    userNames: ['全部人员'],
    selectedUserIndex: 0,
    categoryTree: [],
    categoryTreeView: [],
    categoryStats: [],
    categoryStatsPage: 1,
    categoryStatsPageSize: 10,
    categoryStatsTotal: 0,
    categoryStatsPaged: [],
    statDetail: {
      visible: false,
      loading: false,
      name: '',
      parentName: '',
      score: 0,
      records: [],
      totalScore: 0,
      keyword: '',
      page: 1,
      pageSize: 10
    },
    statDetailView: buildStatDetailView({ visible: false, loading: false, records: [], totalScore: 0 }),
    allRecords: [],
    // records 仍是"筛选+排序后的全量"(汇总/空态/翻页都基于它), pagedRecords 才是列表实际渲染的当页切片
    records: [],
    pagedRecords: [],
    page: 1,
    pageSize: 10,
    pagerTotal: 0,
    summary: { customers: 0, records: 0, categories: 0, files: 0, unread: 0, score: 0 },
    loading: false,
    readStats: [],
    readStatsView: [],
    readStatsPage: 1,
    readStatsPageSize: 10,
    readStatsTotal: 0,
    readStatsPaged: [],
    readRanking: [],
    readRankingView: [],
    readRankingPage: 1,
    readRankingPageSize: 10,
    readRankingTotal: 0,
    readRankingPaged: [],
    readPeriod: currentMonth(),
    readPeriodOptions: buildReadPeriodOptions(),
    selectedReadPeriodIndex: 0,
    readRankingError: '',
    scoreboard: [],
    deptRanking: [],
    scoreboardPage: 1,
    scoreboardPageSize: 10,
    scoreboardTotal: 0,
    scoreboardPaged: [],
    deptPage: 1,
    deptPageSize: 10,
    deptTotal: 0,
    deptPaged: [],
    canViewReadStats: false,
    canViewReadRanking: false,
    canUploadEvidence: false,
    canDeleteRecords: false,
    canEditRecords: false,
    previewing: false,
    previewVideoPath: ''
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
    this.setData({
      canViewReadStats: getApp().globalData.demoMode || hasPermission('incentive:read-stats') || isAdmin(),
      canViewReadRanking: getApp().globalData.demoMode || hasPermission('incentive:read-ranking:view') || isAdmin(),
      canUploadEvidence: !getApp().globalData.demoMode && hasPermission('incentive:add'),
      canDeleteRecords: !getApp().globalData.demoMode && isAdmin()
    })
    this.loadGrantCheck()
    this.ensureDlToken().then(() => {
      this.loadRecords()
    })
    this.loadCategories()
    this.loadUsers()
    this.loadOrgs()
    this.loadScoreboard()
    this.loadReadStats()
    this.loadReadRanking()
    this._loadedOnce = true
  },

  // 【PR-12 2026-09-19 首批】上传/追加成功 switchTab 回列表即见结果:每次显示刷新记录与统计
  // 【修复 2026-09-20】分类树随 onShow 刷新(此前仅 onLoad, 登录前失败后一直空树); 统计由树完成后串联
  onShow() {
    if (this._loadedOnce) {
      // 【PR-12 契约修复 2026-10-07】写后回流的强制刷新: 上传/追加(upload finishSubmit)与
      // 删除(detail removeRecord)成功后置 globalData.forceRefresh.incentive, 此处命中即清标记
      // 并把 _lastShowTs 归零 —— 绕过下方 30s 节流一次。"写完回来立刻见结果"是 PR-12 契约,
      // 节流只拦纯回流的流量放大,不拦写后回流。
      const forceRefresh = getApp().globalData.forceRefresh
      if (forceRefresh && forceRefresh.incentive) {
        forceRefresh.incentive = false
        this._lastShowTs = 0 // 归零使下方 TTL 判定必然通过(绕过节流一次), 刷新后 _lastShowTs 重新起算
      }
      // 【容量止血 2026-10-05】onShow 30s 节流：本页回流 5-6 个请求(含 loadScoreboard 内部 2 个)，
      // 是小程序端最大流量放大器(容量评估流量模型专项)；30s 内回流不重拉，超过 30s 返回照常刷新，
      // 搜索/切分类等显式操作不受影响。已读标记后立即返回看不到最新排行的窗口最长 30s，可接受。
      const now = Date.now()
      if (now - (this._lastShowTs || 0) < 30000) return
      this._lastShowTs = now
      this.ensureDlToken().then(() => {
        this.loadRecords()
      })
      this.loadCategories()
      this.loadScoreboard()
      this.loadReadStats()
      // 【L13 2026-10-04】与 onLoad 对称补齐: 阅读排行随回流刷新, 避免从详情页返回后数据陈旧
      this.loadReadRanking()
    }
  },

  loadGrantCheck() {
    if (getApp().globalData.demoMode) return
    getGrantCheck().then((grant) => {
      this.setData({
        canDeleteRecords: Boolean(grant && grant.canDelete) || isAdmin(),
        canEditRecords: Boolean(grant && grant.canEdit) || isAdmin()
      })
      this.applyRecordActionFlags()
    }).catch(() => this.applyRecordActionFlags())
  },

  canDeleteRecord(record) {
    if (!record || getApp().globalData.demoMode) return false
    const userId = currentUserId()
    const ownerId = record.userId || record.salespersonId || (record.detail && (record.detail.userId || record.detail.salespersonId))
    return Boolean(this.data.canDeleteRecords || (userId && ownerId && String(userId) === String(ownerId)))
  },

  canEditRecord(record) {
    if (!record || getApp().globalData.demoMode) return false
    const userId = currentUserId()
    const ownerId = record.userId || record.salespersonId || (record.detail && (record.detail.userId || record.detail.salespersonId))
    return Boolean(this.data.canEditRecords || (userId && ownerId && String(userId) === String(ownerId)))
  },

  applyRecordActionFlags() {
    // 【setData 瘦身 2026-10-13】视图不再携带原始 records(重建会丢懒加载的子记录明细),
    // grant 到达只影响操作位, 就地局部更新
    // 【F8 2026-10-13】子记录操作位同批刷新: 原始源(_childRawById)重算 + 已展开的 childRecordsView
    // 就地同步——旧代码只刷父级, 子级沿用 grant 前初值, 业务员误缺追加/删除按钮至下次 loadRecords。
    const upd = {}
    ;['allRecords', 'records'].forEach((key) => {
      ;(this.data[key] || []).forEach((item, i) => {
        upd[`${key}[${i}].canDelete`] = this.canDeleteRecord(item)
        upd[`${key}[${i}].canEdit`] = this.canEditRecord(item)
        upd[`${key}[${i}].showAppendAction`] = Boolean(this.canEditRecord(item))
        upd[`${key}[${i}].showDeleteAction`] = Boolean(this.canDeleteRecord(item))
        const children = (item.id != null && this._childRawById && this._childRawById[item.id]) || []
        children.forEach((child) => {
          child.canDelete = this.canDeleteRecord(child)
          child.canEdit = this.canEditRecord(child)
        })
        ;(item.childRecordsView || []).forEach((cv, j) => {
          const childCanEdit = Boolean(cv.canEdit !== undefined ? this.canEditRecord(cv) : false)
          const childCanDelete = Boolean(cv.canDelete !== undefined ? this.canDeleteRecord(cv) : false)
          upd[`${key}[${i}].childRecordsView[${j}].canEdit`] = childCanEdit
          upd[`${key}[${i}].childRecordsView[${j}].canDelete`] = childCanDelete
          upd[`${key}[${i}].childRecordsView[${j}].showAppendAction`] = childCanEdit
          upd[`${key}[${i}].childRecordsView[${j}].showDeleteAction`] = childCanDelete
        })
      })
    })
    this.setData(upd, () => this.refreshIncentiveView())
  },

  /** 【setData 瘦身】装饰后的组数据(含原始 records)只存页面内存, 视图按需构建; 子记录原始数据按组 id 索引供懒展开;
   *  【F7 复审根修】装饰源同时按 id 索引(_decoratedById), 照片墙取文件以 id 锚定, 不受排序/筛选重排影响 */
  rebuildRecordViews(decorated) {
    this._allDecorated = decorated
    this._childRawById = {}
    this._decoratedById = {}
    ;(decorated || []).forEach((group) => {
      if (group && group.id !== undefined && group.id !== null) {
        this._childRawById[group.id] = group.records || []
        this._decoratedById[group.id] = group
      }
    })
    return (decorated || []).map(buildRecordView)
  },

  loadRecords() {
    const app = getApp()
    if (app.globalData.demoMode) {
      const decorated = incentiveRecords.map((item) => ({ category: item.category || item.type || item.categoryName || '未分类', count: item.count || 1, files: buildIncentiveFiles(item), fileGroups: buildIncentiveFileGroups(item), canDelete: false, ...item }))
      const records = this.rebuildRecordViews(decorated)
      const filtered = this.applyLocalRecordFilters(records)
      this.setData({ allRecords: records, records: filtered, categories: ['全部', ...new Set(records.map((item) => item.category))], summary: buildSummary(filtered) }, () => this.refreshIncentiveView())
      this.sort()
      return
    }

    this.setData({ loading: true }, () => this.refreshIncentiveView())
    const params = this.buildRecordParams()
    getCustomerRecords(params).then((rows) => {
      const decorated = normalizeRecords(rows).map((item) => ({ ...item, canDelete: this.canDeleteRecord(item), canEdit: this.canEditRecord(item), records: (item.records || []).map((record) => ({ ...record, canDelete: this.canDeleteRecord(record), canEdit: this.canEditRecord(record) })) }))
      const records = this.rebuildRecordViews(decorated)
      this.setData({ allRecords: records, records, categories: ['全部', ...new Set(records.map((item) => item.category))], summary: buildSummary(records) }, () => this.refreshIncentiveView())
      this.sort()
    }).catch((error) => showErrorWithRetry(error, '四项服务加载失败', () => this.loadRecords())).finally(() => {
      this.setData({ loading: false }, () => this.refreshIncentiveView())
    })
  },

  buildRecordParams() {
    const params = {}
    const keyword = (this.data.keyword || '').trim()
    const selectedUser = this.data.users[this.data.selectedUserIndex - 1]
    const industry = this.data.industries[this.data.selectedIndustryIndex]
    const devType = this.data.devTypes[this.data.selectedDevTypeIndex]
    const product = this.data.productOptions[this.data.selectedProductIndex]
    const org = this.data.orgOptions[this.data.selectedOrgIndex]
    if (keyword) {
      params.customerName = keyword
      params.keyword = keyword
    }
    if (selectedUser) params.salesperson = selectedUser.realName || selectedUser.userName || selectedUser.name
    if (industry && industry !== '全部行业') params.industry = industry
    if (devType && devType !== '全部类型') params.devType = devType
    if (product && product !== '全部产品') params.product = product
    if (org && org.id) params.orgIds = String(org.id)
    if (this.data.startDate) params.startDate = this.data.startDate
    if (this.data.endDate) params.endDate = this.data.endDate
    return params
  },

  applyLocalRecordFilters(records) {
    const keyword = (this.data.keyword || '').trim().toLowerCase()
    const industry = this.data.industries[this.data.selectedIndustryIndex]
    const devType = this.data.devTypes[this.data.selectedDevTypeIndex]
    const product = this.data.productOptions[this.data.selectedProductIndex]
    const org = this.data.orgOptions[this.data.selectedOrgIndex]
    const startDate = this.data.startDate
    const endDate = this.data.endDate
    return records.filter((item) => {
      const text = [item.customerName, item.name, item.summary, item.remark, item.description, item.relatedProducts, item.specificProduct, item.product].filter(Boolean).join(' ').toLowerCase()
      if (keyword && !text.includes(keyword)) return false
      if (industry && industry !== '全部行业' && item.industry !== industry) return false
      // 【I1】customer-records 按客户分组返回的行不带 devType/产品字段(devType 属客户、产品在子记录),
      // 服务端已按这两个条件筛过; 本地只在行确实携带该字段时才二次校验, 否则缺失字段被恒判不等会清空全部结果
      if (devType && devType !== '全部类型' && item.devType && item.devType !== devType) return false
      if (product && product !== '全部产品') {
        const productText = [item.relatedProducts, item.specificProduct, item.product].filter(Boolean).join(' ').toLowerCase()
        if (productText && !productText.includes(product.toLowerCase())) return false
      }
      if (org && org.id && item.officeId && String(item.officeId) !== String(org.id)) return false
      const dateValue = String(item.createdAt || item.createTime || '').slice(0, 10)
      if (startDate && dateValue && dateValue < startDate) return false
      if (endDate && dateValue && dateValue > endDate) return false
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

  loadOrgs() {
    if (getApp().globalData.demoMode) {
      this.applyOrgs(FALLBACK_ORGS)
      return
    }
    // 【PR-04】真实模式接口失败：报错并清空组织候选，不回退演示组织
    getDepts().then((rows) => {
      this.applyOrgs(rows || [])
    }).catch((error) => {
      showErrorWithRetry(error, '组织列表加载失败，请重试', () => this.loadOrgs())
      this.applyOrgs([])
    })
  },

  applyOrgs(list) {
    // 组织树构建共用 utils/tree.js(incentive/sea/statistics 三处同构); 本页差异仅根节点文案"全部组织"
    const result = buildDeptLevelOptions(list, { rootLabel: '全部组织', fallbackName: '未命名组织' })
    this.setData({ orgOptions: result, selectedOrgIndex: Math.min(this.data.selectedOrgIndex, result.length - 1) }, () => this.refreshIncentiveView())
  },

  loadCategories() {
    if (getApp().globalData.demoMode) {
      const categoryTree = [
        { id: 'trial', name: '产品试点', score: 3, level: 0, count: 2 },
        { id: 'talk', name: '两谈两做', score: 2, level: 0, count: 1 },
        { id: 'visit', name: '交流拜访', score: 1, level: 0, count: 3 },
        { id: 'tour', name: '外出参观', score: 1, level: 0, count: 1 }
      ]
      this.setData({
        categoryTree,
        categoryTreeView: buildCategoryTreeView(categoryTree)
      }, () => this.refreshIncentiveView())
      return
    }
    getCategoryTree().then((rows) => {
      const categoryTree = flattenCategoryTree(rows || [])
      this.setData({ categoryTree, categoryTreeView: buildCategoryTreeView(categoryTree) }, () => this.refreshIncentiveView())
      this.loadCategoryStats()
    }).catch((error) => showErrorWithRetry(error, '四项服务分类加载失败', () => this.loadCategories()))
  },

  loadCategoryStats() {
    if (getApp().globalData.demoMode) {
      this.setData({ categoryStats: [{ id: 'trial', name: '产品试点', count: 2, score: 45 }, { id: 'visit', name: '交流拜访', count: 3, score: 28 }] }, () => { this.refreshIncentiveView(); this.applyCategoryStatsPaging(1, this.data.categoryStatsPageSize) })
      return
    }
    getCategoryStats().then((payload) => {
      // 后端 category-stats 返回 byCategory:[{categoryId,fileCount,totalScore}] 只有 id; 名称/父子关系从分类树映射
      const rows = payload && (payload.byCategory || payload.children || payload.rows || payload.list || payload.data || [])
      const treeById = {}
      ;(this.data.categoryTree || []).forEach((node) => { treeById[String(node.id)] = node })
      const categoryStats = Array.isArray(rows) ? rows.map((item) => {
        const id = item.categoryId || item.childId || item.id || item.code || item.categoryCode
        const node = treeById[String(id)]
        return {
          id,
          parentId: (node && node.parentId) || item.parentCategoryId || item.parentId || item.parentCode || '',
          parentName: (node && node.parentName) || item.parentCategoryName || item.parentName || '',
          name: (node && node.name) || item.categoryName || item.name || item.childName || '未命名分类',
          count: item.fileCount || item.count || item.totalFiles || 0,
          score: item.totalScore || item.score || 0
        }
      }).filter((item) => item.id !== undefined && item.id !== null && item.id !== '') : []
      this.setData({ categoryStats }, () => { this.refreshIncentiveView(); this.applyCategoryStatsPaging(1, this.data.categoryStatsPageSize) })
    }).catch((error) => showErrorWithRetry(error, '四项服务统计加载失败', () => this.loadCategoryStats()))
  },

  loadReadStats() {
    if (!this.data.canViewReadStats) return
    if (getApp().globalData.demoMode) {
      const readStats = [{ userId: 1, realName: '张三', readCount: 6, unreadCount: 2 }]
      this.setData({ readStats, readStatsView: buildReadStatsView(readStats) }, () => { this.refreshIncentiveView(); this.applyReadStatsPaging(1, this.data.readStatsPageSize) })
      return
    }
    getReadStats({ period: this.data.readPeriod }).then((rows) => {
      const readStats = rows || []
      this.setData({ readStats, readStatsView: buildReadStatsView(readStats) }, () => { this.refreshIncentiveView(); this.applyReadStatsPaging(1, this.data.readStatsPageSize) })
    }).catch((error) => {
      this.setData({ readStats: [], readStatsView: [], readStatsPaged: [], readStatsTotal: 0 }, () => this.refreshIncentiveView())
      showErrorWithRetry(error, '已读统计加载失败', () => this.loadReadStats())
    })
  },

  loadReadRanking() {
    if (!this.data.canViewReadRanking) return
    if (getApp().globalData.demoMode) {
      const readRanking = [{ userId: 1, realName: '张三', readCount: 12 }, { userId: 2, realName: '李四', readCount: 8 }]
      this.setData({ readRanking, readRankingView: buildReadRankingView(readRanking) }, () => this.refreshIncentiveView())
      return
    }
    getReadRanking({ period: this.data.readPeriod }).then((payload) => {
      const rows = payload && (payload.records || payload.rows || payload.list || payload.ranking || payload.data || payload)
      const readRanking = Array.isArray(rows) ? rows : []
      this.setData({ readRanking, readRankingView: buildReadRankingView(readRanking), readRankingError: '' }, () => { this.refreshIncentiveView(); this.applyReadRankingPaging(1, this.data.readRankingPageSize) })
    }).catch((error) => {
      // 【I5】失败不再被空 catch 吞掉: 清空旧数据的同时置错误态并给重试入口, 与真实空数据区分
      this.setData({ readRanking: [], readRankingView: [], readRankingPaged: [], readRankingTotal: 0, readRankingError: '阅读排行加载失败' }, () => this.refreshIncentiveView())
      showErrorWithRetry(error, '阅读排行加载失败', () => this.loadReadRanking())
    })
  },

  chooseReadPeriod(e) {
    const index = Number(e.detail.value || 0)
    const option = this.data.readPeriodOptions[index] || this.data.readPeriodOptions[0]
    this.setData({ selectedReadPeriodIndex: index, readPeriod: option ? option.value : currentMonth() }, () => this.refreshIncentiveView())
    this.loadReadStats()
    this.loadReadRanking()
  },

  loadScoreboard() {
    if (getApp().globalData.demoMode) {
      this.setData({
        scoreboard: [
          { id: 1, rank: 1, name: '华东智能制造集团', score: 45, records: 6, categoryCount: 3 },
          { id: 2, rank: 2, name: '山西晋能集团中阳煤业', score: 32, records: 4, categoryCount: 2 }
        ],
        deptRanking: [
          { id: 1, rank: 1, name: '华东销售部', score: 96, records: 13, categoryCount: 4 },
          { id: 2, rank: 2, name: '北方服务部', score: 72, records: 9, categoryCount: 3 }
        ]
      }, () => { this.refreshIncentiveView(); this.applyScoreboardPaging(1, this.data.scoreboardPageSize); this.applyDeptPaging(1, this.data.deptPageSize) })
      return
    }
    Promise.all([
      getScoreboard().catch((error) => {
        showError(error, '个人积分榜加载失败')
        return []
      }),
      getRanking({ type: 'dept' }).catch((error) => {
        showError(error, '部门排行加载失败')
        return []
      })
    ]).then(([scoreRows, deptRows]) => {
      this.setData({
        scoreboard: normalizeScoreRows(scoreRows && (scoreRows.records || scoreRows.rows || scoreRows.list || scoreRows.data || scoreRows), 'customer'),
        deptRanking: normalizeScoreRows(deptRows && (deptRows.records || deptRows.rows || deptRows.list || deptRows.data || deptRows), 'dept')
      }, () => { this.refreshIncentiveView(); this.applyScoreboardPaging(1, this.data.scoreboardPageSize); this.applyDeptPaging(1, this.data.deptPageSize) })
    })
  },

  openStatDetail(e) {
    const stat = this.data.categoryStatsPaged[e.currentTarget.dataset.index]
    if (!stat) return
    this.setData({
      statDetail: {
        visible: true,
        loading: true,
        name: stat.name,
        parentName: stat.parentName || '',
        score: stat.score || 0,
        records: [],
        totalScore: 0,
        keyword: '',
        page: 1,
        pageSize: 10
      },
      statDetailView: buildStatDetailView({ visible: true, loading: true, parentName: stat.parentName || '', records: [], totalScore: 0, keyword: '', page: 1, pageSize: 10 })
    })
    if (getApp().globalData.demoMode) {
      const records = (incentiveRecords || [])
        .filter((item) => (item.categoryName || item.category || item.type) === stat.name)
        .map(normalizeStatDetailRecord)
      const statDetail = {
        ...this.data.statDetail,
        loading: false,
        records,
        totalScore: records.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
      }
      this.setData({
        'statDetail.loading': false,
        'statDetail.records': records,
        'statDetail.totalScore': statDetail.totalScore,
        statDetailView: buildStatDetailView(statDetail)
      })
      return
    }
    const params = {}
    if (stat.parentId) params.parentCategoryId = stat.parentId
    // 【M19 2026-10-04】请求序号守卫: 快速切换分类时只采纳最新一次请求,
    // 迟到的旧响应不再覆盖新弹层(含 finally 关 loading, 避免旧请求提前关掉新弹层的加载态)
    const requestSeq = this._statDetailSeq = (this._statDetailSeq || 0) + 1
    getRecords(params).then((rows) => {
      if (requestSeq !== this._statDetailSeq) return
      const source = Array.isArray(rows) ? rows : (rows && (rows.records || rows.rows || rows.list || rows.data)) || []
      const records = source
        .filter((item) => {
          const itemId = item.categoryId || item.childId || item.categoryCode || item.category
          const itemName = item.categoryName || item.category || item.type
          return String(itemId || '') === String(stat.id || '') || itemName === stat.name
        })
        .map(normalizeStatDetailRecord)
      const totalScore = records.reduce((sum, item) => sum + (Number(item.score) || 0), 0)
      const statDetail = { ...this.data.statDetail, records, totalScore }
      this.setData({
        'statDetail.records': records,
        'statDetail.totalScore': totalScore,
        statDetailView: buildStatDetailView(statDetail)
      })
    }).catch((error) => {
      if (requestSeq === this._statDetailSeq) showError(error, '统计明细加载失败')
    }).finally(() => {
      if (requestSeq !== this._statDetailSeq) return
      const statDetail = { ...this.data.statDetail, loading: false }
      this.setData({ 'statDetail.loading': false, statDetailView: buildStatDetailView(statDetail) })
    })
  },

  closeStatDetail() {
    const statDetail = { ...this.data.statDetail, visible: false, records: [] }
    this.setData({ 'statDetail.visible': false, 'statDetail.records': [], statDetailView: buildStatDetailView(statDetail), previewVideoPath: '' })
  },

  noop() {},

  /** 分类统计明细搜索：本地过滤全量明细，输入即筛选并回到第 1 页 */
  onStatDetailKeywordInput(e) {
    const keyword = (e && e.detail && e.detail.value) || ''
    const statDetail = { ...this.data.statDetail, keyword, page: 1 }
    this.setData({ statDetail, statDetailView: buildStatDetailView(statDetail) })
  },

  clearStatDetailKeyword() {
    const statDetail = { ...this.data.statDetail, keyword: '', page: 1 }
    this.setData({ statDetail, statDetailView: buildStatDetailView(statDetail) })
  },

  /** 分类统计明细分页：10/30 条切换回第 1 页，每页只渲染当页记录 */
  onStatDetailPagerChange(e) {
    const detail = (e && e.detail) || {}
    const currentSize = this.data.statDetail.pageSize || 10
    const pageSize = Number(detail.pageSize) || currentSize
    const page = pageSize === currentSize ? (Number(detail.page) || 1) : 1
    const statDetail = { ...this.data.statDetail, page, pageSize }
    this.setData({ statDetail, statDetailView: buildStatDetailView(statDetail) })
  },

  sort() {
    // 分类筛选统一走 categoryNames 名字集合(picker 单选一项;分类树点击含子分类),
    // 客户组卡片上的 category 只是"最近一条"的分类; 命中子记录分类也必须保留该客户组,
    // 否则点「涂层类」时最近一条是「密封类」的客户会被整组过滤掉(点开显示筛选中却没有条目)。
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
    let records = this.applyLocalRecordFilters(this.data.allRecords).filter(childMatches)
    // 【setData 瘦身】视图对象直接排序(全部筛选字段在视图顶层), 重建会丢懒加载的子记录明细
    if (this.data.order === 'score-asc') records.sort((a, b) => (Number(a.score) || 0) - (Number(b.score) || 0))
    if (this.data.order === 'score-desc') records.sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0))
    if (this.data.order === 'time-asc') records.sort((a, b) => coverTimeOf(a).localeCompare(coverTimeOf(b)))
    if (this.data.order === 'time-desc') records.sort((a, b) => coverTimeOf(b).localeCompare(coverTimeOf(a)))
    // 【分页 2026-10-18】排序/筛选/加载后一律回第 1 页; 分页切片与 total 同一次 setData 落定
    const paged = paginate(records, 1, this.data.pageSize)
    this.setData({
      records,
      pagedRecords: paged.rows,
      page: paged.page,
      pageSize: paged.pageSize,
      pagerTotal: paged.total,
      summary: buildSummary(records)
    }, () => this.refreshIncentiveView())
  },

  // 【分页 2026-10-18】客户端分页: 页码/页大小变化时重算当页切片; 用 paginate 返回的 page
  // 写回 data, 数据变少(删记录/筛选收紧)时自动 clamp 到最后一页
  applyPaging(page, pageSize) {
    const paged = paginate(this.data.records, page === undefined ? this.data.page : page, pageSize || this.data.pageSize)
    this.setData({
      pagedRecords: paged.rows,
      page: paged.page,
      pageSize: paged.pageSize,
      pagerTotal: paged.total
    })
  },

  onPagerChange(e) {
    const detail = e.detail || {}
    this.applyPaging(Number(detail.page) || 1, Number(detail.pageSize) || this.data.pageSize)
  },

  // 分类统计 / 个人与部门排行 / 阅读统计与排行：均为客户端分页，10/30 一页
  applyCategoryStatsPaging(page, pageSize) {
    const paged = paginate(this.data.categoryStats, page, pageSize)
    this.setData({ categoryStatsPaged: paged.rows, categoryStatsPage: paged.page, categoryStatsPageSize: paged.pageSize, categoryStatsTotal: paged.total })
  },
  onCategoryStatsPagerChange(e) {
    const detail = e.detail || {}
    const current = this.data.categoryStatsPageSize || 10
    const size = Number(detail.pageSize) || current
    this.applyCategoryStatsPaging(size === current ? (Number(detail.page) || 1) : 1, size)
  },
  applyScoreboardPaging(page, pageSize) {
    const paged = paginate(this.data.scoreboard, page, pageSize)
    this.setData({ scoreboardPaged: paged.rows, scoreboardPage: paged.page, scoreboardPageSize: paged.pageSize, scoreboardTotal: paged.total })
  },
  onScoreboardPagerChange(e) {
    const detail = e.detail || {}
    const current = this.data.scoreboardPageSize || 10
    const size = Number(detail.pageSize) || current
    this.applyScoreboardPaging(size === current ? (Number(detail.page) || 1) : 1, size)
  },
  applyDeptPaging(page, pageSize) {
    const paged = paginate(this.data.deptRanking, page, pageSize)
    this.setData({ deptPaged: paged.rows, deptPage: paged.page, deptPageSize: paged.pageSize, deptTotal: paged.total })
  },
  onDeptPagerChange(e) {
    const detail = e.detail || {}
    const current = this.data.deptPageSize || 10
    const size = Number(detail.pageSize) || current
    this.applyDeptPaging(size === current ? (Number(detail.page) || 1) : 1, size)
  },
  applyReadStatsPaging(page, pageSize) {
    const paged = paginate(this.data.readStatsView, page, pageSize)
    this.setData({ readStatsPaged: paged.rows, readStatsPage: paged.page, readStatsPageSize: paged.pageSize, readStatsTotal: paged.total })
  },
  onReadStatsPagerChange(e) {
    const detail = e.detail || {}
    const current = this.data.readStatsPageSize || 10
    const size = Number(detail.pageSize) || current
    this.applyReadStatsPaging(size === current ? (Number(detail.page) || 1) : 1, size)
  },
  applyReadRankingPaging(page, pageSize) {
    const paged = paginate(this.data.readRankingView, page, pageSize)
    this.setData({ readRankingPaged: paged.rows, readRankingPage: paged.page, readRankingPageSize: paged.pageSize, readRankingTotal: paged.total })
  },
  onReadRankingPagerChange(e) {
    const detail = e.detail || {}
    const current = this.data.readRankingPageSize || 10
    const size = Number(detail.pageSize) || current
    this.applyReadRankingPaging(size === current ? (Number(detail.page) || 1) : 1, size)
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key || 'records'
    this.setData({ activeTab, tabs: buildTabs(activeTab) }, () => this.refreshIncentiveView())
    // 【分页 2026-10-18】切回列表 tab 时回第 1 页, 避免停留在旧页码
    if (activeTab === 'records') this.applyPaging(1)
  },

  chooseCategory(e) {
    this.applyCategoryFilter(this.data.categories[e.detail.value] || '全部')
  },

  applyCategoryFilter(label) {
    const names = label && label !== '全部' ? [label] : []
    this.setData({ category: label || '全部', categoryNames: names }, () => this.refreshIncentiveView())
    this.sort()
  },

  /** 分类树「全部分类」入口：清除分类筛选并回到客户记录 */
  onCategoryAllTap() {
    this.applyCategoryFilter('全部')
    this.switchTab({ currentTarget: { dataset: { key: 'records' } } })
  },

  // 【真机UAT修复 2026-09-22】分类树行点击筛选:选中该分类(父分类含直接子类名集合),
  // 再次点击同一行取消恢复全部;与筛选栏分类 picker 同一过滤机制(PC 端分类即筛选条件)
  onCategoryRowTap(e) {
    const node = (this.data.categoryTreeView || [])[Number(e.currentTarget.dataset.index)]
    if (!node || !node.name) return
    if (this.data.category === node.name) {
      this.applyCategoryFilter('全部')
      this.switchTab({ currentTarget: { dataset: { key: 'records' } } })
      return
    }
    const names = [node.name]
    ;(this.data.categoryTree || []).forEach((row) => {
      if (String(row.parentId) === String(node.id) && names.indexOf(row.name) === -1) names.push(row.name)
    })
    this.setData({ category: node.name, categoryNames: names }, () => this.refreshIncentiveView())
    this.sort()
    // 筛完直接落到客户记录 tab，用户能立刻看到对应条目，而不是停在树上只有「筛选中」标签
    this.switchTab({ currentTarget: { dataset: { key: 'records' } } })
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

  chooseIndustry(e) {
    this.setData({ selectedIndustryIndex: Number(e.detail.value) || 0 }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  chooseDevType(e) {
    this.setData({ selectedDevTypeIndex: Number(e.detail.value) || 0 }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  chooseProduct(e) {
    this.setData({ selectedProductIndex: Number(e.detail.value) || 0 }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  chooseOrg(e) {
    this.setData({ selectedOrgIndex: Number(e.detail.value) || 0 }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  chooseStartDate(e) {
    this.setData({ startDate: e.detail.value || '' }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  chooseEndDate(e) {
    this.setData({ endDate: e.detail.value || '' }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  resetRecordFilters() {
    this.setData({
      keyword: '',
      selectedIndustryIndex: 0,
      selectedDevTypeIndex: 0,
      selectedProductIndex: 0,
      selectedOrgIndex: 0,
      selectedUserIndex: 0,
      selectedOrderIndex: 0,
      order: 'time-desc',
      // 【I3】清空必须连分类筛选一起复位: 否则 sort() 仍按旧 categoryNames 过滤, 重新拉取也恢复不了完整列表
      category: '全部',
      categoryNames: [],
      startDate: '',
      endDate: ''
    }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  // 【D32 2026-10-04】排序 picker 由无宾语的 choose 改名 chooseOrder(与 chooseUser/chooseOrg 命名一致);
  // 不用 sort 是因本页已有记录排序方法 sort(), 避免覆盖
  chooseOrder(e) {
    const index = Number(e.detail.value) || 0
    this.setData({ selectedOrderIndex: index, order: ORDER_VALUES[index] || 'time-desc' }, () => this.refreshIncentiveView())
    this.sort()
  },

  // 【对齐 PC 2026-09-20】显式上传入口: 与 PC「子分类→上传文件」同口径, 常驻页头(权限 incentive:add)
  goUpload() {
    if (!this.data.canUploadEvidence) return wx.showToast({ title: '没有四项服务上传权限', icon: 'none' })
    wx.navigateTo({ url: '/pages/incentive/upload/index' })
  },

  // 【需求C 2026-10-16】封面卡点入 → 客户激励详情页: 子记录视图此刻现构建(单客户量级,setData 无压力),
  // 已读/追加/删除的落地全在详情页(read 事件回写本卡免整页刷新, 变更走 forceRefresh 既有契约)
  openCustomerDetail(e) {
    const index = Number(e.currentTarget.dataset.index)
    // 【分页 2026-10-18】data-index 对应列表实际渲染的当页数组; 子记录仍按 record.id 索引, 与页码无关
    const record = this.data.pagedRecords[index]
    if (!record) return
    const children = ((this._childRawById && this._childRawById[record.id]) || []).map(buildChildRecordView)
    wx.navigateTo({
      url: '/pages/incentive/customer-detail/index',
      events: {
        read: (payload) => {
          const id = payload && payload.id
          const rowIndex = this.data.records.findIndex((item) => item.id === id)
          if (rowIndex < 0 || !this.data.records[rowIndex].unread) return
          const upd = {
            [`records[${rowIndex}].unread`]: false,
            [`records[${rowIndex}].readStatusText`]: '已读',
            [`records[${rowIndex}].readStatusClass`]: 'tag-read'
          }
          // 列表渲染的是 pagedRecords, 同一客户在当前页时同步刷新未读点
          const pagedIndex = this.data.pagedRecords.findIndex((item) => item.id === id)
          if (pagedIndex >= 0) {
            upd[`pagedRecords[${pagedIndex}].unread`] = false
            upd[`pagedRecords[${pagedIndex}].readStatusText`] = '已读'
            upd[`pagedRecords[${pagedIndex}].readStatusClass`] = 'tag-read'
          }
          this.setData(upd)
        }
      },
      success: (res) => {
        res.eventChannel.emit('payload', { record, children })
      }
    })
  },

  // 【需求C 2026-10-16】列表页巨卡拆分后, removeRecord/removeChildRecord/appendRecord/
  // appendChildRecord/previewAttachment/downloadAttachment/findRecordFile/findChildRecord(File)/
  // openCustomer/toggleCustomerRecords/markRead 系列全部下沉到 customer-detail 详情页,
  // 本页仅保留 openCustomerDetail 入口与统计面板的附件处理(findStatDetailFile 系)。

  findStatDetailFile(event) {
    const record = this.data.statDetail.records[event.currentTarget.dataset.detailIndex]
    const groupIndex = event.currentTarget.dataset.groupIndex
    const fileIndex = event.currentTarget.dataset.fileIndex
    if (record && record.fileGroups && groupIndex !== undefined && fileIndex !== undefined) {
      const group = record.fileGroups[Number(groupIndex)]
      return group && group.files ? group.files[Number(fileIndex)] : null
    }
    return record && record.files ? record.files[event.currentTarget.dataset.fileIndex] : null
  },

  previewStatDetailAttachment(event) {
    const file = this.findStatDetailFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可打开', icon: 'none' })
    return this.fetchAttachment(file, 'preview')
  },

  downloadStatDetailAttachment(event) {
    const file = this.findStatDetailFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可下载', icon: 'none' })
    return this.fetchAttachment(file, 'download')
  },

  // 【E5 去重】下载/预览/保存共用 utils/attachment.js; 本页差异: busy=previewing(复位需联动
  // 刷新 incentiveView)、视频写 previewVideoPath、保存失败文案"保存失败"、success 内先收 loading
  fetchAttachment(file, mode) {
    return fetchRemoteAttachment({
      url: getDownloadUrl(file),
      mode,
      file,
      isBusy: this.data.previewing,
      setBusy: (busy) => this.setData({ previewing: busy }, () => this.refreshIncentiveView()),
      isImage: (item) => item.type === 'image',
      onVideo: (tempFilePath) => this.setData({ previewVideoPath: tempFilePath })
    })
  },

  // 【D31 2026-10-04】已删除 selected/uploadEvidence/closeDetail 死代码:
  // wxml 无对应绑定(上传入口早已由页头 goUpload 承接), updateRecord/uploadFile import 一并移除

  // 【#4+#7 2026-10-12】照片墙缩略图凭证: 幂等取一次写模块缓存;失败不阻断列表(token 空→退化面卡)
  ensureDlToken() {
    if (getApp().globalData.demoMode) return Promise.resolve()
    // 【F21 2026-10-13】缓存按用户隔离: 换账号后旧缓存作废;且早退前把有效 token 补写进
    // 当前页面实例的 data(thumb.wxs 读 data.dlToken 拼缩略图 URL, 旧早退分支不 setData
    // 导致换账号后新页面实例缩略图全空)。
    const user = getApp().globalData.currentUser
    const userId = user ? (user.id != null ? user.id : user.username) : null
    const cacheUsable = Boolean(dlTokenCache) && dlTokenCacheUserId != null && dlTokenCacheUserId === userId
    if (!cacheUsable && dlTokenCache) {
      setIncentiveDlToken('', null)
      this._dlTokenPromise = null
    }
    if (cacheUsable && !this.data.dlToken) this.setData({ dlToken: dlTokenCache })
    if (cacheUsable || this._dlTokenPromise) return this._dlTokenPromise || Promise.resolve()
    this._dlTokenPromise = getDownloadToken().then((result) => {
      this._dlTokenPromise = null
      const token = result && (result.token || (result.data && result.data.token)) || ''
      setIncentiveDlToken(token, userId)
      // token 进 data 一份: wxml 经 thumb.wxs 用它拼每个瓦片的缩略图 URL, 晚到自动补齐
      this.setData({ dlToken: token })
    }).catch(() => {
      this._dlTokenPromise = null
    }).then(() => {})
    return this._dlTokenPromise
  },

  refresh() {
    this.loadRecords()
    this.loadCategories()
    this.loadUsers()
    this.loadOrgs()
    this.loadScoreboard()
    this.loadReadStats()
    this.loadReadRanking()
    this.setData({ previewVideoPath: '' })
  }
})
