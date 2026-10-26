// 【2026-10-23 四项服务拆分·用户令】tabBar「四项服务」= 轻量客户记录快查视图。
// 【2026-10-26 用户令】轻量页与完整版(四项服务应用)排法分工: 完整版=按客户归集封面卡(原地不动);
// 轻量页默认=打散记录流(不绑客户, 一次上传=一条记录, 按上传时间排), 「按客户」可切回归集视图。
// 数据链路同源(getCustomerRecords 按客户分组 + 本地筛选排序), 打散=分组结果拉平, 不加接口。
const { getCustomerRecords, getGrantCheck, getUsers } = require('../../../api/incentive')
const { getDownloadToken } = require('../../../api/file')
const { getDepts } = require('../../../api/system')
const { incentiveRecords } = require('../../../data/demo')
const { hasPermission, requireLogin } = require('../../../utils/auth')
const { showError, showErrorWithRetry } = require('../../../utils/feedback')
const { buildIncentiveFiles, buildIncentiveFileGroups } = require('../../../utils/incentive-files')
const { paginate } = require('../../../utils/paging')
const { buildDeptLevelOptions } = require('../../../utils/tree')

// 缩略图凭证: 模块级缓存(与完整版同款机制, 本页独立一份); 失败不阻断列表(退化为首字面卡)
let dlTokenCache = ''
let dlTokenCacheUserId = null
function setHomeDlToken(token, userId) {
  dlTokenCache = token || ''
  dlTokenCacheUserId = token ? (userId ?? null) : null
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
  // 与完整版同款: 原始 records/files/fileGroups 大数组不进 data(存页面内存), 视图只带封面/计数字段
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
  return {
    refreshLoading: isLoading,
    refreshText: isLoading ? '刷新中' : '刷新',
    searchLoading: isLoading,
    searchText: isLoading ? '查询中' : '查询',
    showLoading: isLoading,
    pagerLoading: isLoading,
    loadingText: '加载中...',
    industryText: `${pickOption(data.industries, data.selectedIndustryIndex, '全部行业')}⌄`,
    devTypeText: `${pickOption(data.devTypes, data.selectedDevTypeIndex, '全部类型')}⌄`,
    productText: `${pickOption(data.productOptions, data.selectedProductIndex, '全部产品')}⌄`,
    orgText: `${pickOption(data.orgOptions, data.selectedOrgIndex, '全部组织', 'label')}⌄`,
    startDateText: `${data.startDate || '开始日期'}⌄`,
    endDateText: `${data.endDate || '截止日期'}⌄`,
    userText: `${pickOption(data.userNames, data.selectedUserIndex, '全部人员')}⌄`,
    orderText: `${pickOption(data.orderOptions, data.selectedOrderIndex, '最新记录在前')}⌄`,
    showRecordEmpty: !isLoading && ((data.listMode === 'time' ? data.flatRecords : data.records) || []).length === 0
  }
}

const INDUSTRIES = ['全部行业', '冶金', '煤矿', '化工', '电力', '有色金属', '机械', '工程公司', '维修厂', '其他终端客户']
const DEV_TYPES = ['全部类型', '成熟创新', '突破开拓', '铺垫宣传']
const PRODUCT_OPTIONS = ['全部产品', '防腐', '加固', '钢结构', '煤气管道', '公共区', '装置区', '水泵', '渣泵', '介质泵', '静密封', '动密封', '橡胶密封', '润滑油', '润滑脂', '润滑剂']
// 【2026-10-26 用户令"要有按时间排序"】原三档(默认/分值高低)改四档: 时间两档显式化,
// 「默认排序」即接口顺序=最新在前, 由首档承接; 值 time-*/score-* 交 sort() 分派
const ORDER_OPTIONS = ['最新记录在前', '最早记录在前', '分值从高到低', '分值从低到高']
const ORDER_VALUES = ['time-desc', 'time-asc', 'score-desc', 'score-asc']
// 封面卡时间 = 其最新一条记录的 createdAt(normalizeRecords 的 detail 即最新子记录)
function coverTimeOf(item) {
  return String((item && item.detail && item.detail.createdAt) || item.createdAt || '')
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
const FALLBACK_ORGS = [
  { id: 1, deptName: '内蒙古大区', deptType: 2, parentId: 0 },
  { id: 11, deptName: '内蒙办', deptType: 3, parentId: 1 },
  { id: 2, deptName: '山西大区', deptType: 2, parentId: 0 },
  { id: 21, deptName: '山西办', deptType: 3, parentId: 2 }
]

Page({
  data: {
    incentiveView: buildIncentiveView({
      industries: INDUSTRIES,
      devTypes: DEV_TYPES,
      productOptions: PRODUCT_OPTIONS,
      orgOptions: [{ id: '', deptName: '全部组织', label: '全部组织' }],
      orderOptions: ORDER_OPTIONS,
      userNames: ['全部人员'],
      records: [],
      loading: false
    }),
    order: 'time-desc',
    orderOptions: ORDER_OPTIONS,
    selectedOrderIndex: 0,
    // 【2026-10-26 打散视图】轻量页默认 time=打散记录流(用户令), customer=按客户归集封面卡
    listMode: 'time',
    flatRecords: [],
    pagedFlatRecords: [],
    flatPagerTotal: 0,
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
    allRecords: [],
    records: [],
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
    this.ensureDlToken().then(() => {
      this.loadRecords()
    })
    this.loadUsers()
    this.loadOrgs()
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
      const records = this.rebuildRecordViews(decorated)
      const filtered = this.applyLocalRecordFilters(records)
      this.setData({ allRecords: records, records: filtered, categories: ['全部', ...new Set(records.map((item) => item.category))] }, () => this.sort())
      return
    }

    this.setData({ loading: true }, () => this.refreshIncentiveView())
    const params = this.buildRecordParams()
    getCustomerRecords(params).then((rows) => {
      const decorated = normalizeRecords(rows)
      const records = this.rebuildRecordViews(decorated)
      this.setData({ allRecords: records, records, categories: ['全部', ...new Set(records.map((item) => item.category))] }, () => this.sort())
    }).catch((error) => showErrorWithRetry(error, '四项服务加载失败', () => this.loadRecords())).finally(() => {
      this.setData({ loading: false }, () => this.refreshIncentiveView())
    })
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
    getDepts().then((rows) => {
      this.applyOrgs(rows || [])
    }).catch((error) => {
      showErrorWithRetry(error, '组织列表加载失败，请重试', () => this.loadOrgs())
      this.applyOrgs([])
    })
  },

  applyOrgs(list) {
    const result = buildDeptLevelOptions(list, { rootLabel: '全部组织', fallbackName: '未命名组织' })
    this.setData({ orgOptions: result, selectedOrgIndex: Math.min(this.data.selectedOrgIndex, result.length - 1) }, () => this.refreshIncentiveView())
  },

  sort() {
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
    if (this.data.order === 'score-asc') records.sort((a, b) => (Number(a.score) || 0) - (Number(b.score) || 0))
    if (this.data.order === 'score-desc') records.sort((a, b) => (Number(b.score) || 0) - (Number(a.score) || 0))
    if (this.data.order === 'time-asc') records.sort((a, b) => coverTimeOf(a).localeCompare(coverTimeOf(b)))
    if (this.data.order === 'time-desc') records.sort((a, b) => coverTimeOf(b).localeCompare(coverTimeOf(a)))
    const paged = paginate(records, 1, this.data.pageSize)
    // 【打散视图】记录流与封面卡共用筛选(分组已过服务端+本地筛选, 拉平即继承),
    // 分类/关键词/日期在行上二次过滤, 排序四档作用于单条记录(时间=行自身 createdAt)
    const keywordFlat = (this.data.keyword || '').trim().toLowerCase()
    let flat = buildFlatRecordRows(this.data.allRecords, this._childRawById).filter((row) => {
      if (categoryNames.length && categoryNames.indexOf(row.categoryName) === -1) return false
      if (keywordFlat) {
        const haystack = `${row.customerName} ${row.categoryName} ${row.productText} ${row.uploaderName} ${row.timeText}`.toLowerCase()
        if (!haystack.includes(keywordFlat)) return false
      }
      const dateValue = row.createdAt.slice(0, 10)
      if (this.data.startDate && dateValue && dateValue < this.data.startDate) return false
      if (this.data.endDate && dateValue && dateValue > this.data.endDate) return false
      return true
    })
    if (this.data.order === 'score-asc') flat.sort((a, b) => a.score - b.score)
    if (this.data.order === 'score-desc') flat.sort((a, b) => b.score - a.score)
    if (this.data.order === 'time-asc') flat.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
    if (this.data.order === 'time-desc') flat.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    const pagedFlat = paginate(flat, 1, this.data.pageSize)
    this.setData({
      records,
      pagedRecords: paged.rows,
      page: paged.page,
      pageSize: paged.pageSize,
      pagerTotal: paged.total,
      flatRecords: flat,
      pagedFlatRecords: pagedFlat.rows,
      flatPagerTotal: pagedFlat.total
    }, () => this.refreshIncentiveView())
  },

  applyPaging(page, pageSize) {
    // 打散视图与客户视图各自分页, pager 组件按 listMode 取对应总数
    const source = this.data.listMode === 'time' ? this.data.flatRecords : this.data.records
    const paged = paginate(source, page === undefined ? this.data.page : page, pageSize || this.data.pageSize)
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

  chooseCategory(e) {
    this.applyCategoryFilter(this.data.categories[e.detail.value] || '全部')
  },

  applyCategoryFilter(label) {
    const names = label && label !== '全部' ? [label] : []
    this.setData({ category: label || '全部', categoryNames: names }, () => this.refreshIncentiveView())
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
      order: 'time-desc',
      category: '全部',
      categoryNames: [],
      startDate: '',
      endDate: ''
    }, () => this.refreshIncentiveView())
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
      this.applyPaging(1)
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
    const cacheUsable = Boolean(dlTokenCache) && dlTokenCacheUserId != null && dlTokenCacheUserId === userId
    if (!cacheUsable && dlTokenCache) {
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
    }).then(() => {})
    return this._dlTokenPromise
  },

  refresh() {
    this.loadRecords()
    this.loadUsers()
    this.loadOrgs()
  }
})
