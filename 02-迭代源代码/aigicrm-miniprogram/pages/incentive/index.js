const { deleteRecord, getCategoryStats, getCategoryTree, getCustomerRecords, getGrantCheck, getRanking, getReadRanking, getReadStats, getRecords, getScoreboard, getUsers, markIncentiveRead, updateRecord } = require('../../api/incentive')
const { getDownloadUrl, uploadFile } = require('../../api/file')
const { getDepts } = require('../../api/system')
const { incentiveRecords } = require('../../data/demo')
const { hasPermission, isAdmin, requireLogin, getToken } = require('../../utils/auth')
const { showError } = require('../../utils/feedback')
const { buildIncentiveFileGroups, buildIncentiveFiles } = require('../../utils/incentive-files')

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

function flattenCategoryTree(nodes = [], level = 0, result = []) {
  nodes.forEach((item) => {
    result.push({
      id: item.id,
      name: item.categoryName || item.name || '未命名分类',
      score: item.score || 0,
      level,
      count: item.count || item.fileCount || 0
    })
    if (item.children && item.children.length) flattenCategoryTree(item.children, level + 1, result)
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
    fileCount += (item.files || []).length
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
    metaText: `${item.score || 0}分 · ${item.count || 0}条`
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
    files: (group.files || []).map((file, fileIndex) => ({
      ...file,
      fileIndex
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
  const childRecordsView = (record.records || []).map(buildChildRecordView)
  return {
    ...record,
    scoreText: `${record.score || 0} 分`,
    countText: `${record.count || 0}条`,
    fileCountText: `${(record.files || []).length}文件`,
    readStatusText: record.unread ? '未读' : '已读',
    readStatusClass: record.unread ? 'tag-unread' : 'tag-read',
    showNewTag: Boolean(record.isNew),
    showFiles: fileGroupsView.length > 0,
    fileGroupsView,
    slotPills: fileGroupsView.map((group) => group.slotText),
    showExpandAction: childRecordsView.length > 1,
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
    showCategoryEmpty: (data.categoryTree || []).length === 0,
    showCategoryStatsEmpty: (data.categoryStats || []).length === 0,
    showScoreboardEmpty: (data.scoreboard || []).length === 0,
    showDeptRankingEmpty: (data.deptRanking || []).length === 0,
    showReadStatsEmpty: (data.readStats || []).length === 0,
    showReadRankingEmpty: (data.readRanking || []).length === 0,
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
    selected: null,
    previewing: false,
    previewVideoPath: ''
  },

  refreshIncentiveView() {
    this.setData({ incentiveView: buildIncentiveView(this.data) })
  },

  onLoad() {
    if (!requireLogin()) return
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
    this.loadRecords()
    this.loadCategories()
    this.loadUsers()
    this.loadOrgs()
    this.loadCategoryStats()
    this.loadScoreboard()
    this.loadReadStats()
    this.loadReadRanking()
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
    const decorate = (item) => buildRecordView({ ...item, canDelete: this.canDeleteRecord(item), canEdit: this.canEditRecord(item), records: (item.records || []).map((record) => ({ ...record, canDelete: this.canDeleteRecord(record), canEdit: this.canEditRecord(record) })) })
    const allRecords = (this.data.allRecords || []).map(decorate)
    const records = (this.data.records || []).map(decorate)
    this.setData({ allRecords, records }, () => this.refreshIncentiveView())
  },

  loadRecords() {
    const app = getApp()
    if (app.globalData.demoMode) {
      const records = incentiveRecords.map((item) => buildRecordView({ category: item.category || item.type || item.categoryName || '未分类', count: item.count || 1, files: buildIncentiveFiles(item), fileGroups: buildIncentiveFileGroups(item), canDelete: false, ...item }))
      const filtered = this.applyLocalRecordFilters(records)
      this.setData({ allRecords: records, records: filtered, categories: ['全部', ...new Set(records.map((item) => item.category))], summary: buildSummary(filtered) }, () => this.refreshIncentiveView())
      this.sort()
      return
    }

    this.setData({ loading: true }, () => this.refreshIncentiveView())
    const params = this.buildRecordParams()
    getCustomerRecords(params).then((rows) => {
      const records = normalizeRecords(rows).map((item) => buildRecordView({ ...item, canDelete: this.canDeleteRecord(item), canEdit: this.canEditRecord(item), records: (item.records || []).map((record) => ({ ...record, canDelete: this.canDeleteRecord(record), canEdit: this.canEditRecord(record) })) }))
      this.setData({ allRecords: records, records, categories: ['全部', ...new Set(records.map((item) => item.category))], summary: buildSummary(records) }, () => this.refreshIncentiveView())
      this.sort()
    }).catch((error) => showError(error, '四项激励加载失败')).finally(() => {
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
    }).catch((error) => showError(error, '人员筛选加载失败'))
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
      showError(error, '组织列表加载失败，请重试')
      this.applyOrgs([])
    })
  },

  applyOrgs(list) {
    const source = list || []
    const filtered = source.filter((item) => item && item.deptType !== 1)
    const byParent = {}
    filtered.forEach((item) => {
      const parentId = filtered.some((node) => String(node.id) === String(item.parentId)) ? item.parentId : 0
      const key = String(parentId || 0)
      if (!byParent[key]) byParent[key] = []
      byParent[key].push(item)
    })
    const result = [{ id: '', deptName: '全部组织', label: '全部组织' }]
    const walk = (parentId, level) => {
      ;(byParent[String(parentId || 0)] || [])
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
        .forEach((item) => {
          result.push({ ...item, label: `${'  '.repeat(level)}${item.deptName || item.name || '未命名组织'}` })
          walk(item.id, level + 1)
        })
    }
    walk(0, 0)
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
    }).catch((error) => showError(error, '四项激励分类加载失败'))
  },

  loadCategoryStats() {
    if (getApp().globalData.demoMode) {
      this.setData({ categoryStats: [{ id: 'trial', name: '产品试点', count: 2, score: 45 }, { id: 'visit', name: '交流拜访', count: 3, score: 28 }] }, () => this.refreshIncentiveView())
      return
    }
    getCategoryStats().then((payload) => {
      const rows = payload && (payload.children || payload.rows || payload.list || payload.data || [])
      const categoryStats = Array.isArray(rows) ? rows.map((item) => ({
        id: item.categoryId || item.childId || item.id || item.code || item.categoryCode || item.name || item.childName,
        parentId: item.parentCategoryId || item.parentId || item.parentCode || '',
        parentName: item.parentCategoryName || item.parentName || '',
        name: item.categoryName || item.name || item.childName || '未命名分类',
        count: item.fileCount || item.count || item.totalFiles || 0,
        score: item.totalScore || item.score || 0
      })) : []
      this.setData({ categoryStats }, () => this.refreshIncentiveView())
    }).catch((error) => showError(error, '四项激励统计加载失败'))
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
      showError(error, '已读统计加载失败')
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
        showError(error, '客户积分榜加载失败')
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
    getRecords(params).then((rows) => {
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
    }).catch((error) => showError(error, '统计明细加载失败')).finally(() => {
      const statDetail = { ...this.data.statDetail, loading: false }
      this.setData({ 'statDetail.loading': false, statDetailView: buildStatDetailView(statDetail) })
    })
  },

  closeStatDetail() {
    const statDetail = { ...this.data.statDetail, visible: false, records: [] }
    this.setData({ 'statDetail.visible': false, 'statDetail.records': [], statDetailView: buildStatDetailView(statDetail), previewVideoPath: '' })
  },

  sort() {
    let records = this.applyLocalRecordFilters(this.data.allRecords).filter((item) => this.data.category === '全部' || item.category === this.data.category)
    records = records.map(buildRecordView)
    if (this.data.order === 'asc') records.sort((a, b) => (Number(a.score) || 0) - (Number(b.score) || 0))
    if (this.data.order === 'desc') records.sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0))
    this.setData({ records, summary: buildSummary(records) }, () => this.refreshIncentiveView())
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key || 'records'
    this.setData({ activeTab, tabs: buildTabs(activeTab) }, () => this.refreshIncentiveView())
  },

  chooseCategory(e) {
    this.setData({ category: this.data.categories[e.detail.value] || '全部' }, () => this.refreshIncentiveView())
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

  choose(e) {
    const orderMap = ['', 'desc', 'asc']
    const index = Number(e.detail.value) || 0
    this.setData({ selectedOrderIndex: index, order: orderMap[index] || '' }, () => this.refreshIncentiveView())
    this.sort()
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
    ;(record.records || []).forEach((_, childIndex) => {
      updates[`records[${recordIndex}].records[${childIndex}].unread`] = false
    })
    this.setData(updates)

    if (!getApp().globalData.demoMode && record.recordIds && record.recordIds.length) {
      Promise.all(record.recordIds.map((id) => markIncentiveRead(id))).then(() => {
        this.loadReadStats()
      }).catch((error) => {
        const rollback = { [`records[${recordIndex}].unread`]: true }
        ;(record.records || []).forEach((item, childIndex) => {
          rollback[`records[${recordIndex}].records[${childIndex}].unread`] = Boolean(item.unread)
        })
        this.setData(rollback)
        showError(error, '标记失败')
      })
    }
  },

  markChildRecordRead(recordIndex, childIndex) {
    const parentIndex = Number(recordIndex)
    const itemIndex = Number(childIndex)
    const parent = this.data.records[parentIndex]
    const record = parent && parent.records ? parent.records[itemIndex] : null
    if (!record) return
    this.setData({ [`records[${parentIndex}].records[${itemIndex}].unread`]: false })

    if (!getApp().globalData.demoMode && record.id) {
      markIncentiveRead(record.id).then(() => {
        this.loadReadStats()
      }).catch((error) => {
        this.setData({ [`records[${parentIndex}].records[${itemIndex}].unread`]: Boolean(record.unread) })
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
    this.setData({ [`records[${index}]`]: buildRecordView({ ...record, expanded: !record.expanded }) })
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
    return parent && parent.records ? parent.records[event.currentTarget.dataset.childIndex] : null
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

  fetchAttachment(file, mode) {
    if (this.data.previewing) return
    const url = getDownloadUrl(file)
    if (!url) return wx.showToast({ title: '附件地址缺失', icon: 'none' })
    this.setData({ previewing: true }, () => this.refreshIncentiveView())
    wx.showLoading({ title: mode === 'download' ? '下载中' : '准备预览' })
    return new Promise((resolve) => {
      wx.downloadFile({
        url,
        header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {},
        success: (result) => {
          if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
          if (mode === 'download') return this.saveDownloadedFile(result.tempFilePath)
          this.openDownloadedFile(file, result.tempFilePath)
        },
        fail: (error) => showError(error, '文件下载失败'),
        complete: () => { wx.hideLoading(); this.setData({ previewing: false }, () => this.refreshIncentiveView()); resolve() }
      })
    })
  },

  openDownloadedFile(file, tempFilePath) {
    if (file.type === 'image') {
      wx.previewImage({ urls: [tempFilePath], fail: (error) => showError(error, '图片预览失败') })
      return
    }
    if (file.type === 'video') {
      this.setData({ previewVideoPath: tempFilePath })
      return
    }
    wx.openDocument({
      filePath: tempFilePath,
      showMenu: true,
      fail: (error) => showError(error, '无法直接预览，可下载后查看')
    })
  },

  saveDownloadedFile(tempFilePath) {
    const options = {
      tempFilePath,
      success: () => wx.showToast({ title: '已保存到本机', icon: 'success' }),
      fail: (error) => showError(error, '保存失败')
    }
    const fs = typeof wx.getFileSystemManager === 'function' ? wx.getFileSystemManager() : null
    if (fs && typeof fs.saveFile === 'function') return fs.saveFile(options)
    return wx.saveFile(options)
  },

  openCustomer(e) {
    const record = this.data.records[e.currentTarget.dataset.index]
    if (!record || !record.customerId) return
    wx.navigateTo({ url: `/pages/customer/detail/index?id=${record.customerId}&from=incentive` })
  },

  closeDetail() { this.setData({ selected: null }) },

  uploadEvidence() {
    const selected = this.data.selected
    if (!this.data.canUploadEvidence || !selected || !selected.id || getApp().globalData.demoMode) {
      wx.showToast({ title: '演示记录不可上传', icon: 'none' }); return
    }
    wx.chooseMessageFile({ count: 1, type: 'file', success: (res) => {
      const file = res.tempFiles && res.tempFiles[0]
      if (!file) return
      wx.showLoading({ title: '上传中...' })
      uploadFile(file.path, null, 'incentive').then((uploaded) => {
        const fileId = uploaded && uploaded.id
        if (!fileId) throw new Error('上传结果缺少文件 ID')
        return updateRecord(selected.id, { fileId: JSON.stringify([fileId]) })
      }).then(() => {
        wx.showToast({ title: '上传成功', icon: 'success' })
        this.closeDetail(); this.loadRecords()
      }).catch((error) => showError(error, '上传失败')).finally(() => wx.hideLoading())
    } })
  },

  refresh() {
    this.loadRecords()
    this.loadCategories()
    this.loadUsers()
    this.loadOrgs()
    this.loadCategoryStats()
    this.loadScoreboard()
    this.loadReadStats()
    this.loadReadRanking()
    this.setData({ previewVideoPath: '' })
  }
})
