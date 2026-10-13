const { deleteRecord, getCategoryStats, getCategoryTree, getCustomerRecords, getGrantCheck, getRanking, getReadRanking, getReadStats, getRecords, getScoreboard, getUsers, markIncentiveRead } = require('../../api/incentive')
const { getDownloadUrl, getDownloadToken } = require('../../api/file')
const { getDepts } = require('../../api/system')
const { incentiveRecords } = require('../../data/demo')
const { hasPermission, isAdmin, requireLogin } = require('../../utils/auth')
const { fetchRemoteAttachment } = require('../../utils/attachment')
const { showError, showErrorWithRetry } = require('../../utils/feedback')
const { buildIncentiveFileGroups, buildIncentiveFiles } = require('../../utils/incentive-files')
const { buildDeptLevelOptions } = require('../../utils/tree')

// 【#4+#7 2026-10-12】列表页佐证照片墙: 缩略图凭证模块级缓存(取一次复用), 未就绪时图片退化为
// 文档面卡仍可点开大图; TAG_COLORS 与详情页同款槽位配色
let dlTokenCache = ''
function setIncentiveDlToken(token) { dlTokenCache = token || '' }
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
      isNew: Boolean(customer.isNew),
      unread: Boolean(customer.unread || records.some((item) => item.unread)),
      summary: latest ? latest.description || latest.remark || latest.product || '暂无详情描述' : '暂无详情描述',
      files: latest ? buildIncentiveFiles(latest) : [],
      fileGroups: latest ? buildIncentiveFileGroups(latest) : [],
      records: records.map((item) => ({ ...item, files: buildIncentiveFiles(item), fileGroups: buildIncentiveFileGroups(item) })),
      expanded: false,
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

function buildStatDetailView(statDetail) {
  const records = statDetail.records || []
  return {
    ...statDetail,
    metaText: `${statDetail.parentName || '全部分类'} · ${records.length} 条 · ${statDetail.totalScore || 0} 分`,
    showEmpty: !statDetail.loading && records.length === 0
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
  // 【setData 瘦身 2026-10-13】子记录明细懒构造(点展开才生成, 见 toggleCustomerRecords);
  // 原始 records/files/fileGroups 大数组不进 data(存页面内存 _childRawById/_allDecorated),
  // 68 客户组曾因此把 setData 顶到 1470KB(超 1MB 真机硬限)
  const childCount = (record.records || []).length
  const childRecordsView = record.expanded ? (record.records || []).map(buildChildRecordView) : []
  const { records: _omitRecords, files: _omitFiles, fileGroups: _omitGroups, ...rest } = record
  return {
    ...rest,
    scoreText: `${record.score || 0} 分`,
    countText: `${record.count || 0}条`,
    fileCountText: `${(record.files || []).length}文件`,
    fileCountTotal: fileGroupsView.reduce((sum, group) => sum + (group.files || []).length, 0),
    readStatusText: record.unread ? '未读' : '已读',
    readStatusClass: record.unread ? 'tag-unread' : 'tag-read',
    showNewTag: Boolean(record.isNew),
    showFiles: fileGroupsView.length > 0,
    fileGroupsView,
    slotPills: fileGroupsView.map((group) => group.slotText),
    childCount,
    showExpandAction: childCount > 1,
    expandText: record.expanded ? '收起记录' : '全部记录',
    showCustomerAction: Boolean(record.customerId),
    showAppendAction: Boolean(record.canEdit),
    showDeleteAction: Boolean(record.canDelete),
    childRecordsView
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
    loadingText: '加载中...',
    previewLoading: Boolean(data.previewing),
    industryText: `${pickOption(data.industries, data.selectedIndustryIndex, '全部行业')}⌄`,
    devTypeText: `${pickOption(data.devTypes, data.selectedDevTypeIndex, '全部类型')}⌄`,
    productText: `${pickOption(data.productOptions, data.selectedProductIndex, '全部产品')}⌄`,
    orgText: `${pickOption(data.orgOptions, data.selectedOrgIndex, '全部组织', 'label')}⌄`,
    startDateText: `${data.startDate || '开始日期'}⌄`,
    endDateText: `${data.endDate || '截止日期'}⌄`,
    userText: `${pickOption(data.userNames, data.selectedUserIndex, '全部人员')}⌄`,
    orderText: `${pickOption(data.orderOptions, data.selectedOrderIndex, '默认排序')}⌄`,
    readPeriodText: `${pickOption(data.readPeriodOptions, data.selectedReadPeriodIndex, data.readPeriod || currentMonth(), 'label')}⌄`,
    showRecordEmpty: !isLoading && (data.records || []).length === 0,
    // 【工作流复核确认修复】空态统一加 !isLoading 守卫(与 records tab 同口径, 弱网首屏不显示"暂无"假空态)
    showCategoryEmpty: !isLoading && (data.categoryTree || []).length === 0,
    showCategoryStatsEmpty: !isLoading && (data.categoryStats || []).length === 0,
    showScoreboardEmpty: !isLoading && (data.scoreboard || []).length === 0,
    showDeptRankingEmpty: !isLoading && (data.deptRanking || []).length === 0,
    showReadStatsEmpty: !isLoading && (data.readStats || []).length === 0,
    showReadRankingEmpty: !isLoading && (data.readRanking || []).length === 0,
    showReadStatsPanel: Boolean(data.canViewReadStats),
    showReadRankingPanel: Boolean(data.canViewReadRanking),
    showNoReadPermission: !data.canViewReadStats && !data.canViewReadRanking
  }
}

const INDUSTRIES = ['全部行业', '冶金', '煤矿', '化工', '电力', '有色金属', '机械', '工程公司', '维修厂', '其他终端客户']
const DEV_TYPES = ['全部类型', '成熟创新', '突破开拓', '铺垫宣传']
const PRODUCT_OPTIONS = ['全部产品', '防腐', '加固', '钢结构', '煤气管道', '公共区', '装置区', '水泵', '渣泵', '介质泵', '静密封', '动密封', '橡胶密封', '润滑油', '润滑脂', '润滑剂']
const SCORE_ORDER_OPTIONS = ['默认排序', '分值从高到低', '分值从低到高']
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
      orderOptions: SCORE_ORDER_OPTIONS,
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
    order: '',
    orderOptions: SCORE_ORDER_OPTIONS,
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
    statDetail: {
      visible: false,
      loading: false,
      name: '',
      parentName: '',
      score: 0,
      records: [],
      totalScore: 0
    },
    statDetailView: buildStatDetailView({ visible: false, loading: false, records: [], totalScore: 0 }),
    allRecords: [],
    records: [],
    summary: { customers: 0, records: 0, categories: 0, files: 0, unread: 0, score: 0 },
    loading: false,
    readStats: [],
    readStatsView: [],
    readRanking: [],
    readRankingView: [],
    readPeriod: currentMonth(),
    readPeriodOptions: buildReadPeriodOptions(),
    selectedReadPeriodIndex: 0,
    scoreboard: [],
    deptRanking: [],
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
      wx.showToast({ title: '没有四项激励查看权限', icon: 'none' })
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
    // grant 到达只影响两个操作位, 就地局部更新
    const upd = {}
    ;['allRecords', 'records'].forEach((key) => {
      ;(this.data[key] || []).forEach((item, i) => {
        upd[`${key}[${i}].canDelete`] = this.canDeleteRecord(item)
        upd[`${key}[${i}].canEdit`] = this.canEditRecord(item)
        upd[`${key}[${i}].showAppendAction`] = Boolean(this.canEditRecord(item))
        upd[`${key}[${i}].showDeleteAction`] = Boolean(this.canDeleteRecord(item))
      })
    })
    this.setData(upd, () => this.refreshIncentiveView())
  },

  /** 【setData 瘦身】装饰后的组数据(含原始 records)只存页面内存, 视图按需构建; 子记录原始数据按组 id 索引供懒展开 */
  rebuildRecordViews(decorated) {
    this._allDecorated = decorated
    this._childRawById = {}
    ;(decorated || []).forEach((group) => {
      if (group && group.id !== undefined && group.id !== null) this._childRawById[group.id] = group.records || []
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
    }).catch((error) => showErrorWithRetry(error, '四项激励加载失败', () => this.loadRecords())).finally(() => {
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
      if (devType && devType !== '全部类型' && item.devType !== devType) return false
      if (product && product !== '全部产品' && !text.includes(product.toLowerCase())) return false
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
    }).catch((error) => showErrorWithRetry(error, '四项激励分类加载失败', () => this.loadCategories()))
  },

  loadCategoryStats() {
    if (getApp().globalData.demoMode) {
      this.setData({ categoryStats: [{ id: 'trial', name: '产品试点', count: 2, score: 45 }, { id: 'visit', name: '交流拜访', count: 3, score: 28 }] }, () => this.refreshIncentiveView())
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
      this.setData({ categoryStats }, () => this.refreshIncentiveView())
    }).catch((error) => showErrorWithRetry(error, '四项激励统计加载失败', () => this.loadCategoryStats()))
  },

  loadReadStats() {
    if (!this.data.canViewReadStats) return
    if (getApp().globalData.demoMode) {
      const readStats = [{ userId: 1, realName: '张三', readCount: 6, unreadCount: 2 }]
      this.setData({ readStats, readStatsView: buildReadStatsView(readStats) }, () => this.refreshIncentiveView())
      return
    }
    getReadStats({ period: this.data.readPeriod }).then((rows) => {
      const readStats = rows || []
      this.setData({ readStats, readStatsView: buildReadStatsView(readStats) }, () => this.refreshIncentiveView())
    }).catch((error) => {
      this.setData({ readStats: [], readStatsView: [] }, () => this.refreshIncentiveView())
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
      this.setData({ readRanking, readRankingView: buildReadRankingView(readRanking) }, () => this.refreshIncentiveView())
    }).catch(() => this.setData({ readRanking: [], readRankingView: [] }, () => this.refreshIncentiveView()))
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
      }, () => this.refreshIncentiveView())
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
      }, () => this.refreshIncentiveView())
    })
  },

  openStatDetail(e) {
    const stat = this.data.categoryStats[e.currentTarget.dataset.index]
    if (!stat) return
    this.setData({
      statDetail: {
        visible: true,
        loading: true,
        name: stat.name,
        parentName: stat.parentName || '',
        score: stat.score || 0,
        records: [],
        totalScore: 0
      },
      statDetailView: buildStatDetailView({ visible: true, loading: true, parentName: stat.parentName || '', records: [], totalScore: 0 })
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

  sort() {
    // 分类筛选统一走 categoryNames 名字集合(picker 单选一项;分类树点击含子分类),
    // 记录的 category 字段是分类名(子分类名),父分类需按"自身+直接子类名"集合匹配
    const categoryNames = this.data.categoryNames || []
    let records = this.applyLocalRecordFilters(this.data.allRecords)
      .filter((item) => !categoryNames.length || categoryNames.indexOf(item.category) !== -1)
    // 【setData 瘦身】视图对象直接排序(全部筛选字段在视图顶层), 重建会丢懒加载的子记录明细
    if (this.data.order === 'asc') records.sort((a, b) => (Number(a.score) || 0) - (Number(b.score) || 0))
    if (this.data.order === 'desc') records.sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0))
    this.setData({ records, summary: buildSummary(records) }, () => this.refreshIncentiveView())
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key || 'records'
    this.setData({ activeTab, tabs: buildTabs(activeTab) }, () => this.refreshIncentiveView())
  },

  chooseCategory(e) {
    this.applyCategoryFilter(this.data.categories[e.detail.value] || '全部')
  },

  applyCategoryFilter(label) {
    const names = label && label !== '全部' ? [label] : []
    this.setData({ category: label || '全部', categoryNames: names }, () => this.refreshIncentiveView())
    this.sort()
  },

  // 【真机UAT修复 2026-09-22】分类树行点击筛选:选中该分类(父分类含直接子类名集合),
  // 再次点击同一行取消恢复全部;与筛选栏分类 picker 同一过滤机制(PC 端分类即筛选条件)
  onCategoryRowTap(e) {
    const node = (this.data.categoryTreeView || [])[Number(e.currentTarget.dataset.index)]
    if (!node || !node.name) return
    if (this.data.category === node.name) {
      this.applyCategoryFilter('全部')
      return
    }
    const names = [node.name]
    ;(this.data.categoryTree || []).forEach((row) => {
      if (String(row.parentId) === String(node.id) && names.indexOf(row.name) === -1) names.push(row.name)
    })
    this.setData({ category: node.name, categoryNames: names }, () => this.refreshIncentiveView())
    this.sort()
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
      order: '',
      startDate: '',
      endDate: ''
    }, () => this.refreshIncentiveView())
    this.loadRecords()
  },

  // 【D32 2026-10-04】排序 picker 由无宾语的 choose 改名 chooseOrder(与 chooseUser/chooseOrg 命名一致);
  // 不用 sort 是因本页已有记录排序方法 sort(), 避免覆盖
  chooseOrder(e) {
    const orderMap = ['', 'desc', 'asc']
    const index = Number(e.detail.value) || 0
    this.setData({ selectedOrderIndex: index, order: orderMap[index] || '' }, () => this.refreshIncentiveView())
    this.sort()
  },

  // 【对齐 PC 2026-09-20】显式上传入口: 与 PC「子分类→上传文件」同口径, 常驻页头(权限 incentive:add)
  goUpload() {
    if (!this.data.canUploadEvidence) return wx.showToast({ title: '没有四项激励上传权限', icon: 'none' })
    wx.navigateTo({ url: '/pages/incentive/upload/index' })
  },

  markRead(e) {
    const index = e.currentTarget.dataset.index
    this.markRecordRead(index)
  },

  markRecordRead(index) {
    const recordIndex = Number(index)
    const record = this.data.records[recordIndex]
    if (!record) return
    const updates = { [`records[${recordIndex}].unread`]: false }
    // 【setData 瘦身】子级未读态更新到视图明细(展开态可见)与页面内存原始数据
    const children = (this._childRawById && this._childRawById[record.id]) || []
    ;(this.data.records[recordIndex].childRecordsView || []).forEach((child, childIndex) => {
      if (child.unread) {
        updates[`records[${recordIndex}].childRecordsView[${childIndex}].unread`] = false
        updates[`records[${recordIndex}].childRecordsView[${childIndex}].readStatusText`] = '已读'
        updates[`records[${recordIndex}].childRecordsView[${childIndex}].readStatusClass`] = 'tag-read inline-read-status'
      }
    })
    children.forEach((child) => { child.unread = false })
    this.setData(updates)

    if (!getApp().globalData.demoMode && record.recordIds && record.recordIds.length) {
      Promise.all(record.recordIds.map((id) => markIncentiveRead(id))).then(() => {
        this.loadReadStats()
      }).catch((error) => {
        const rollback = { [`records[${recordIndex}].unread`]: true }
        this.setData(rollback)
        showError(error, '标记失败')
      })
    }
  },

  markChildRecordRead(recordIndex, childIndex) {
    const parentIndex = Number(recordIndex)
    const itemIndex = Number(childIndex)
    const parent = this.data.records[parentIndex]
    // 【setData 瘦身】原始子记录从页面内存取(视图已不携带), 视图层更新读态
    const record = parent ? ((this._childRawById && this._childRawById[parent.id]) || [])[itemIndex] : null
    if (!record) return
    this.setData({
      [`records[${parentIndex}].childRecordsView[${itemIndex}].unread`]: false,
      [`records[${parentIndex}].childRecordsView[${itemIndex}].readStatusText`]: '已读',
      [`records[${parentIndex}].childRecordsView[${itemIndex}].readStatusClass`]: 'tag-read inline-read-status'
    })

    if (!getApp().globalData.demoMode && record.id) {
      markIncentiveRead(record.id).then(() => {
        this.loadReadStats()
      }).catch((error) => {
        this.setData({ [`records[${parentIndex}].childRecordsView[${itemIndex}].unread`]: Boolean(record.unread) })
        showError(error, '标记失败')
      })
    }
  },

  showDetail(e) {
    const index = e.currentTarget.dataset.index
    const record = this.data.records[index]
    if (!record) return
    this.markRecordRead(index)
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${record.id}` })
  },

  showChildDetail(e) {
    this.markChildRecordRead(e.currentTarget.dataset.recordIndex, e.currentTarget.dataset.childIndex)
    const record = this.findChildRecord(e)
    if (!record || !record.id) return
    wx.navigateTo({ url: `/pages/incentive/detail/index?id=${record.id}` })
  },

  toggleCustomerRecords(e) {
    const index = Number(e.currentTarget.dataset.index)
    const record = this.data.records[index]
    if (!record) return
    // 【setData 瘦身】子记录明细点开才构造(从页面内存的原始数据), 收起即清空
    const expanded = !record.expanded
    const children = (this._childRawById && this._childRawById[record.id]) || []
    const childRecordsView = expanded ? children.map(buildChildRecordView) : []
    this.setData({
      [`records[${index}].expanded`]: expanded,
      [`records[${index}].expandText`]: expanded ? '收起记录' : '全部记录',
      [`records[${index}].childRecordsView`]: childRecordsView
    })
  },

  removeRecord(e) {
    const record = this.data.records[e.currentTarget.dataset.index]
    if (!record || !record.id || !record.canDelete) return wx.showToast({ title: '没有四项激励删除权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可删除', icon: 'none' })
    wx.showModal({
      title: '删除四项激励记录',
      content: `确认删除“${record.name || record.category || '该记录'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteRecord(record.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          this.refresh()
        }).catch((error) => showError(error, '删除四项激励记录失败'))
      }
    })
  },

  removeChildRecord(e) {
    const record = this.findChildRecord(e)
    if (!record || !record.id || !record.canDelete) return wx.showToast({ title: '没有四项激励删除权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示记录不可删除', icon: 'none' })
    wx.showModal({
      title: '删除四项激励记录',
      content: `确认删除“${record.categoryName || record.category || '该记录'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteRecord(record.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          this.refresh()
        }).catch((error) => showError(error, '删除四项激励记录失败'))
      }
    })
  },

  appendRecord(e) {
    const record = this.data.records[e.currentTarget.dataset.index]
    if (!record || !record.id || !record.canEdit) return wx.showToast({ title: '没有四项激励追加权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/incentive/upload/index?id=${record.id}` })
  },

  appendChildRecord(e) {
    const record = this.findChildRecord(e)
    if (!record || !record.id || !record.canEdit) return wx.showToast({ title: '没有四项激励追加权限', icon: 'none' })
    wx.navigateTo({ url: `/pages/incentive/upload/index?id=${record.id}` })
  },

  findRecordFile(event) {
    const record = this.data.records[event.currentTarget.dataset.recordIndex]
    const groupIndex = event.currentTarget.dataset.groupIndex
    const fileIndex = event.currentTarget.dataset.fileIndex
    if (record && record.fileGroups && groupIndex !== undefined && fileIndex !== undefined) {
      const group = record.fileGroups[Number(groupIndex)]
      return group && group.files ? group.files[Number(fileIndex)] : null
    }
    return record && record.files ? record.files[event.currentTarget.dataset.fileIndex] : null
  },

  findChildRecord(event) {
    const parent = this.data.records[event.currentTarget.dataset.recordIndex]
    // 【setData 瘦身】原始子记录从页面内存取(视图已不携带 records 数组)
    return parent ? ((this._childRawById && this._childRawById[parent.id]) || [])[event.currentTarget.dataset.childIndex] : null
  },

  findChildRecordFile(event) {
    const record = this.findChildRecord(event)
    const groupIndex = event.currentTarget.dataset.groupIndex
    const fileIndex = event.currentTarget.dataset.fileIndex
    if (record && record.fileGroups && groupIndex !== undefined && fileIndex !== undefined) {
      const group = record.fileGroups[Number(groupIndex)]
      return group && group.files ? group.files[Number(fileIndex)] : null
    }
    return record && record.files ? record.files[event.currentTarget.dataset.fileIndex] : null
  },

  previewAttachment(event) {
    const file = event.currentTarget.dataset.childIndex !== undefined ? this.findChildRecordFile(event) : this.findRecordFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可打开', icon: 'none' })
    return this.fetchAttachment(file, 'preview')
  },

  downloadAttachment(event) {
    const file = event.currentTarget.dataset.childIndex !== undefined ? this.findChildRecordFile(event) : this.findRecordFile(event)
    if (!file) return
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示附件不可下载', icon: 'none' })
    return this.fetchAttachment(file, 'download')
  },

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

  openCustomer(e) {
    const record = this.data.records[e.currentTarget.dataset.index]
    if (!record || !record.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${record.customerId}&from=incentive` })
  },

  // 【D31 2026-10-04】已删除 selected/uploadEvidence/closeDetail 死代码:
  // wxml 无对应绑定(上传入口早已由页头 goUpload 承接), updateRecord/uploadFile import 一并移除

  // 【#4+#7 2026-10-12】照片墙缩略图凭证: 幂等取一次写模块缓存;失败不阻断列表(token 空→退化面卡)
  ensureDlToken() {
    if (getApp().globalData.demoMode) return Promise.resolve()
    if (dlTokenCache || this._dlTokenPromise) return this._dlTokenPromise || Promise.resolve()
    this._dlTokenPromise = getDownloadToken().then((result) => {
      this._dlTokenPromise = null
      const token = result && (result.token || (result.data && result.data.token)) || ''
      setIncentiveDlToken(token)
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
