const { getCategoryStats, getCategoryTree, getCustomerRecords, getGrantCheck, getRanking, getReadRanking, getReadStats, getRecords, getScoreboard, getUsers } = require('../../api/incentive')
const { getDownloadUrl, getDownloadToken } = require('../../api/file')
const { getCustomerList } = require('../../api/customer')
const { getDepts, getDictData } = require('../../api/system')
const { incentiveRecords } = require('../../data/demo')
const { hasPermission, isAdmin, requireLogin } = require('../../utils/auth')
const { fetchRemoteAttachment } = require('../../utils/attachment')
const { showError, showErrorWithRetry } = require('../../utils/feedback')
const { buildIncentiveFileGroups, buildIncentiveFiles } = require('../../utils/incentive-files')
const { paginate } = require('../../utils/paging')
const { buildDeptLevelOptions } = require('../../utils/tree')
const incentivePanes = require('../../utils/incentive-panes.js')

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

// 【2026-11-02 用户令·PC 同构表格】查询区客户封面卡 → 横向表格(每行一条记录, 列=客户/分类/产品/
// 时间/上传人/文件/学分, 横滚看全部列)。行数据=客户聚合 records[] 拉平, 字段口径与轻量页
// buildFlatRow 同源(同 API 同映射); 点行直达记录详情(拨盘页 detail?id=)。
function buildRecordRow(record, customerName) {
  const files = record.files || buildIncentiveFiles(record)
  const time = String(record.createdAt || record.createTime || '')
  const categoryName = record.categoryName || record.category || record.type || ''
  return {
    id: record.id,
    customerName: record.customerName || customerName || '未命名客户',
    categoryName: categoryName || '未分类',
    productText: [record.specificProduct, record.relatedProducts, record.product].filter(Boolean).join(' · '),
    uploaderName: record.userName || record.updatedByName || record.createByName || '',
    score: Number(record.score) || 0,
    timeText: time.replace('T', ' ').slice(0, 16),
    timeRaw: time,
    fileCount: (files || []).length
  }
}

function buildRecordTableRows(customerGroups, childRawById) {
  const rows = []
  ;(customerGroups || []).forEach((group) => {
    // 【修复 2026-11-03】入参是 buildRecordView 瘦身后的组(2026-10-13 性能批已剔除 records/files/
    // fileGroups 大数组, 原始子记录存页面内存 _childRawById)——与轻量页 buildFlatRecordRows 同源,
    // 从 _childRawById 取子记录; 组自带 records(未来兼容/演示路径)优先
    const children = (group && group.records) || (childRawById && group && childRawById[group.id]) || []
    children.forEach((record) => {
      if (record && record.id !== undefined && record.id !== null) {
        rows.push(buildRecordRow(record, group.name || group.customerName))
      }
    })
  })
  return rows
}

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
    // 记录分类只取前两级; 第三级自归树批起是产品目录(防腐/水泵等), 不进分类浏览
    if (level < 2 && item.children && item.children.length) flattenCategoryTree(item.children, level + 1, result, { id: item.id, name })
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

// 【2026-11-05 用户令"这一页按照一级 二级 这种层级统计"】分类统计按一级→二级层级分组:
// 基座=rankParents(树序「大类→子类」结构, 与 loadCategories 同源), 统计行按子类 id 挂靠,
// 一级行聚合子类文件数/学分; 树上找不到归属的统计行兜底进「其他分类」尾组, 不丢数。
function buildCategoryStatsGroups(rows, rankParents) {
  const byChildId = {}
  ;(rows || []).forEach((row) => { byChildId[String(row.id)] = row })
  const used = new Set()
  const groups = (rankParents || []).map((parent) => {
    const children = []
    let count = 0
    let score = 0
    ;(parent.children || []).forEach((child) => {
      const row = byChildId[String(child.id)]
      if (!row) return
      used.add(String(row.id))
      children.push(row)
      count += Number(row.count) || 0
      score += Number(row.score) || 0
    })
    return { parentId: parent.id, parentName: parent.categoryName, children, count, score }
  }).filter((group) => group.children.length > 0)
  const orphans = (rows || []).filter((row) => !used.has(String(row.id)))
  if (orphans.length > 0) {
    groups.push({
      parentId: '',
      parentName: '其他分类',
      children: orphans,
      count: orphans.reduce((sum, row) => sum + (Number(row.count) || 0), 0),
      score: orphans.reduce((sum, row) => sum + (Number(row.score) || 0), 0)
    })
  }
  return groups
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
    scoreText: `${item.totalScore || item.score || item.points || 0} 分`,
    // 【2026-11-02 用户令·PC 同款排行】表格化字段: 姓名首字头像/办事处/分数/合计/均分(与 PC 同构)
    avatarChar: String(item.realName || item.userName || item.deptName || item.name || '?').trim().charAt(0).toUpperCase(),
    officeText: item.officeName || item.deptName || '—',
    scoreNum: Number(item.totalScore || item.score || item.points) || 0,
    recordCountText: `${item.recordCount || item.count || item.totalRecords || 0} 条`,
    avgText: item.avgScore !== undefined ? `${item.avgScore}` : (item.participantCount ? `${Math.round(((Number(item.totalScore) || 0) / item.participantCount) * 10) / 10}` : '—'),
    // 【2026-11-02 用户令·子类列】保留原始 byCategory 映射(childId → [count, score]), 供表格子类列渲染
    byCategory: item.byCategory || {}
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

// 【2026-11-03 用户令"查询和统计分开"】应用中心「激励查询/激励统计」两个入口不再共用同一套
// 页签(原五 tab 同页互切=两个应用混在一页): 按入口模式锁定页签集——
// query=客户记录/分类树/阅读(查询侧), stats=统计/排行(统计侧); 标题随模式分开
const ALL_TABS = [
  { key: 'records', label: '客户记录' },
  { key: 'categories', label: '分类树' },
  { key: 'stats', label: '统计' },
  { key: 'ranking', label: '排行' },
  { key: 'reads', label: '阅读' }
]
const TAB_SETS = {
  query: ['records', 'categories', 'reads'],
  stats: ['stats', 'ranking']
}
const TAB_MODE_DEFAULTS = { query: 'records', stats: 'stats' }
const TAB_MODE_TITLES = { query: '激励查询', stats: '激励统计' }

function buildTabs(activeTab, mode = 'query', canStatsPanes) {
  let keys = TAB_SETS[mode] || TAB_SETS.query
  // 【2026-11-04 逐功能建码】stats 模式页签按各 pane 注册码过滤(无权页签不渲染)
  if (mode === 'stats' && canStatsPanes) keys = keys.filter((key) => canStatsPanes[key])
  return ALL_TABS.filter((item) => keys.indexOf(item.key) >= 0).map((item) => ({
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

// 【2026-11-03 用户令】排行 tab 内三页签: 客户/个人积分榜/部门(2026-11-04 用户令"更新字 个人积分榜")(原三块纵向堆叠改切换显示);
// 页签文案以字面量写在 WXML(契约钉 个人积分榜/部门排行), 激活态由视图归一化 className 提供
const RANK_TAB_KEYS = ['customer', 'scoreboard', 'dept']

function buildIncentiveView(data) {
  const isLoading = Boolean(data.loading)
  const rankTab = data.rankTab || 'customer'
  const tabMode = data.tabMode || 'query'
  return {
    heroTitle: TAB_MODE_TITLES[tabMode] || TAB_MODE_TITLES.query,
    showRecords: data.activeTab === 'records',
    showCategories: data.activeTab === 'categories',
    // 【2026-11-04 逐功能建码】pane 渲染=页签命中+注册码放行(无权 pane 不渲染)
    showStats: data.activeTab === 'stats' && (data.canStatsPanes ? data.canStatsPanes.stats !== false : true),
    showRanking: data.activeTab === 'ranking' && (data.canStatsPanes ? data.canStatsPanes.ranking !== false : true),
    showReads: data.activeTab === 'reads',
    customerTabClass: rankTab === 'customer' ? 'active' : '',
    scoreboardTabClass: rankTab === 'scoreboard' ? 'active' : '',
    deptTabClass: rankTab === 'dept' ? 'active' : '',
    showRankCustomer: rankTab === 'customer',
    showRankScoreboard: rankTab === 'scoreboard',
    showRankDept: rankTab === 'dept',
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
    showCustomerRankEmpty: !isLoading && (data.customerRanking || []).length === 0,
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

// 【2026-10-29 字典接线】行业选项=系统字典 industry 优先(loadIndustryOptions 拉取覆盖 data.industries),
// 本常量降级为字典缺行/接口失败兜底; 兜底已含字典现量「新行业」(原硬编码缺该项致筛不出)。
const INDUSTRIES = ['全部行业', '冶金', '煤矿', '化工', '电力', '有色金属', '机械', '工程公司', '维修厂', '其他终端客户', '新行业']
// 【2026-11-01 审计修复】兜底补第四类(新建改建扩建大修), 真实选项由 loadDevTypeOptions 字典覆盖
const DEV_TYPES = ['全部类型', '成熟创新', '突破开拓', '铺垫宣传', '新建改建扩建大修']
// 【2026-10-29 归树批】产品筛选选项=分类树"产品试点"分支派生(loadCategories 树就绪后覆盖), 本常量降级为
// 兜底; 兜底补上此前手抄漏掉的「其他」(与 PC 目录对齐——旧 MP 扁平清单只有 15 个小类)。
const PRODUCT_OPTIONS = ['全部产品', '防腐', '加固', '钢结构', '煤气管道', '其他', '公共区', '装置区', '水泵', '渣泵', '介质泵', '静密封', '动密封', '橡胶密封', '润滑油', '润滑脂', '润滑剂']
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
    // 【2026-11-02 应用中心拆分】初始 tab 可被 onLoad 的 ?tab= 参数覆盖(激励统计直达 stats)
    activeTab: 'records',
    tabs: buildTabs('records'),
    tabMode: 'query', // 【2026-11-03 分开批】查询/统计入口模式(锁页签集+定标题)
    rankTab: 'customer', // 【2026-11-03 用户令】排行 tab 内三页签, 默认客户排行
    incentiveView: buildIncentiveView({
      activeTab: 'records',
      tabMode: 'query',
      rankTab: 'customer',
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
    rankParents: [],
    categoryStats: [],
    categoryStatsGroups: [],
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
    customerRanking: [],
    customerRankPage: 1,
    customerRankPageSize: 10,
    customerRankTotal: 0,
    customerRankPaged: [],
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

  onLoad(options) {
    if (!requireLogin()) return
    // 【2026-11-03 用户令"查询和统计分开"】入口模式锁定页签集: ?mode=query|stats(应用中心两入口
    // 分别带); 兼容旧 ?tab= 深链(tab∈统计侧推导 stats 模式)。tab 不属当前模式集时归位模式默认。
    let tabMode = 'query'
    if (options && options.mode === 'stats') tabMode = 'stats'
    else if (options && options.mode === 'query') tabMode = 'query'
    else if (options && ['stats', 'ranking'].indexOf(options.tab) >= 0) tabMode = 'stats'
    const modeKeys = TAB_SETS[tabMode]
    let initialTab = TAB_MODE_DEFAULTS[tabMode]
    if (options && options.tab && modeKeys.indexOf(options.tab) >= 0) initialTab = options.tab
    // 【2026-11-04 用户令"逐功能建码"】stats 模式双 pane 各绑注册码(统计=incentive:stats:view/
    // 排行=incentive:rank:view, 见 utils/incentive-panes.js): 入口=注册码并集(任一命中可进),
    // 默认 pane=第一个有权 pane, 无权 pane 不渲染不发请求。单元测试: scripts/test-miniprogram-incentive-panes.js。
    let canStatsPanes = { stats: true, ranking: true }
    if (tabMode === 'stats') {
      const demo = Boolean(getApp().globalData.demoMode)
      canStatsPanes = incentivePanes.computeStatsPanes(hasPermission, demo)
      const firstAllowed = incentivePanes.firstAllowedStatsPane(canStatsPanes)
      if (!firstAllowed) {
        wx.showToast({ title: '没有统计排行权限', icon: 'none' })
        wx.switchTab({ url: '/pages/home/index' })
        return
      }
      if (!canStatsPanes[initialTab]) initialTab = firstAllowed
    }
    this._tabMode = tabMode
    this.setData({ tabMode, activeTab: initialTab, tabs: buildTabs(initialTab, tabMode, canStatsPanes), canStatsPanes })
    wx.setNavigationBarTitle({ title: TAB_MODE_TITLES[tabMode] })
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
    this.loadIndustryOptions()
    this.loadDevTypeOptions()
    this.loadUsers()
    this.loadOrgs()
    this.loadScoreboard()
    this.loadReadStats()
    this.loadReadRanking()
    // 【2026-11-03 分开批】初始 tab/页签集已在 onLoad 头部落定(带模式), 原 _pendingTab 尾段机制移除
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

  // 【2026-11-01 审计修复】开发类型选项=系统字典 dev_type(原硬编码3项缺第四类, 与行业接线同模式)
  loadDevTypeOptions() {
    if (getApp().globalData.demoMode) return
    getDictData('dev_type').then((rows) => {
      const labels = (Array.isArray(rows) ? rows : [])
        .filter((r) => r && r.status !== 0)
        .map((r) => r && r.dictValue)
        .filter(Boolean)
      if (!labels.length) return
      const devTypes = ['全部类型', ...labels]
      const selectedDevTypeIndex = Math.min(this.data.selectedDevTypeIndex || 0, devTypes.length - 1)
      this.setData({ devTypes, selectedDevTypeIndex }, () => this.refreshIncentiveView())
    }).catch(() => {})
  },

  loadCategories() {
    if (getApp().globalData.demoMode) {
      const categoryTree = [
        { id: 'trial', name: '产品试点', score: 3, level: 0, count: 2 },
        { id: 'talk', name: '两谈两做', score: 2, level: 0, count: 1 },
        { id: 'visit', name: '交流拜访', score: 1, level: 0, count: 3 },
        { id: 'tour', name: '外出参观', score: 1, level: 0, count: 1 }
      ]
      // 【2026-11-06 整体检查 P3】演示模式补 rankParents(与分类树同源)并串联分类统计,
      // 此前 demo 早退不调 loadCategoryStats → 统计分支重写了也永远不可达(演示恒"暂无统计")
      const rankParents = categoryTree.map((item) => ({ id: item.id, categoryName: item.name }))
      this.setData({
        categoryTree,
        categoryTreeView: buildCategoryTreeView(categoryTree),
        rankParents
      }, () => { this.refreshIncentiveView(); this.loadCategoryStats() })
      return
    }
    getCategoryTree().then((rows) => {
      const categoryTree = flattenCategoryTree(rows || [])
      // 【2026-11-02 用户令·排行子类列】大类→子类分组结构(与 PC rankParents 同构), 供排行表格展开列
      const rankParents = (rows || [])
        .filter((n) => n && ((n.parentId !== undefined ? n.parentId === 0 : !n.parent)))
        .map((parent) => ({
          id: parent.id,
          categoryName: parent.categoryName || parent.name || '',
          children: ((parent.children || [])).map((c) => ({ id: c.id, categoryName: c.categoryName || c.name || '' }))
        }))
        .filter((g) => g.children.length > 0 && g.categoryName)
      this.setData({ categoryTree, categoryTreeView: buildCategoryTreeView(categoryTree), rankParents }, () => this.refreshIncentiveView())
      // 【2026-10-29 归树批】产品筛选选项随树"产品试点"分支派生(大类→小类拉平); 无三级结构保持兜底
      const trial = (rows || []).find((n) => n && ((n.categoryCode || '') === 'trial' || (n.categoryName || n.name) === '产品试点'))
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
      this.loadCategoryStats()
    }).catch((error) => showErrorWithRetry(error, '四项服务分类加载失败', () => this.loadCategories()))
  },

  loadCategoryStats() {
    if (getApp().globalData.demoMode) {
      // 【2026-11-06 整体检查 P3】演示统计与演示记录对齐(3 条记录各归一类, 分值一致):
      // 此前 demoStats 计数/分值与记录对不上, 且明细弹层按 childNames 过滤恒 0 行
      const demoStats = [
        { id: 'trial', name: '产品试点', count: 1, score: 32 },
        { id: 'visit', name: '交流拜访', count: 1, score: 45 },
        { id: 'talk', name: '两谈两做', count: 1, score: 18 }
      ]
      this.setData({ categoryStats: demoStats, categoryStatsGroups: buildCategoryStatsGroups(demoStats, this.data.rankParents) }, () => this.refreshIncentiveView())
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
      this.setData({ categoryStats, categoryStatsGroups: buildCategoryStatsGroups(categoryStats, this.data.rankParents) }, () => this.refreshIncentiveView())
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
    // 【2026-11-04 逐功能建码】排行数据面守卫(rank:view∨stats:view): 两者皆无则跳过(不发起必败请求)
    const demo = Boolean(getApp().globalData.demoMode)
    if (!demo && !hasPermission('incentive:rank:view') && !hasPermission('incentive:stats:view')) return
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
    // 【2026-11-05 层级化】WXML 传 data-id; 兼容 automator 老通道 dataset.index(平铺数组定位)
    const dataset = e.currentTarget.dataset || {}
    const rows = this.data.categoryStats || []
    const stat = dataset.id !== undefined && dataset.id !== null && dataset.id !== ''
      ? rows.find((row) => String(row.id) === String(dataset.id))
      : rows[Number(dataset.index) || 0]
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
    this._fetchStatDetailRecords(params, (item) => {
      const itemId = item.categoryId || item.childId || item.categoryCode || item.category
      const itemName = item.categoryName || item.category || item.type
      return String(itemId || '') === String(stat.id || '') || itemName === stat.name
    })
  },

  closeStatDetail() {
    const statDetail = { ...this.data.statDetail, visible: false, records: [] }
    this.setData({ 'statDetail.visible': false, 'statDetail.records': [], statDetailView: buildStatDetailView(statDetail), previewVideoPath: '' })
  },

  noop() {},

  // 【M19 2026-10-04】请求序号守卫: 快速切换分类时只采纳最新一次请求,
  // 迟到的旧响应不再覆盖新弹层(含 finally 关 loading, 避免旧请求提前关掉新弹层的加载态)
  // 【2026-11-06 整体检查 P3】统计明细取数尾部抽取共用——此前 openStatDetail/openGroupStatDetail
  // 各抄一份约 40 行(解包/过滤/合计/回填/异常/loading 收口), 历史上出过"修一处漏一处"
  _fetchStatDetailRecords(params, filterFn) {
    const requestSeq = this._statDetailSeq = (this._statDetailSeq || 0) + 1
    getRecords(params).then((rows) => {
      if (requestSeq !== this._statDetailSeq) return
      const source = Array.isArray(rows) ? rows : (rows && (rows.records || rows.rows || rows.list || rows.data)) || []
      const records = source.filter(filterFn).map(normalizeStatDetailRecord)
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

  // 【2026-11-05 用户令"数据要能点开查看的"】一级分组头可点开: 明细=组内全部子类记录合并(不按单子类过滤);
  // 请求口径与子级明细同源(getRecords, 大组带 parentCategoryId 直取, 「其他分类」兜底组全量拉回后按子类 id 收口)
  openGroupStatDetail(e) {
    const dataset = e.currentTarget.dataset || {}
    const group = (this.data.categoryStatsGroups || []).find((item) => String(item.parentId) === String(dataset.parentId))
    if (!group) return
    const subtitle = `${(group.children || []).length} 个子类合计`
    this.setData({
      statDetail: {
        visible: true,
        loading: true,
        name: group.parentName,
        parentName: subtitle,
        score: group.score || 0,
        records: [],
        totalScore: 0,
        keyword: '',
        page: 1,
        pageSize: 10
      },
      statDetailView: buildStatDetailView({ visible: true, loading: true, parentName: subtitle, records: [], totalScore: 0, keyword: '', page: 1, pageSize: 10 })
    })
    const childIds = new Set((group.children || []).map((item) => String(item.id)))
    const childNames = new Set((group.children || []).map((item) => item.name))
    if (getApp().globalData.demoMode) {
      const records = (incentiveRecords || [])
        .filter((item) => childNames.has(item.categoryName || item.category || item.type))
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
    const params = group.parentId ? { parentCategoryId: group.parentId } : {}
    // 【2026-11-06 整体检查 P3】补名字兜底通道: 与子级明细双通道同口径, 数据缺 id 仅有名时不再吞数
    this._fetchStatDetailRecords(params, (item) => {
      const itemName = item.categoryName || item.category || item.type
      return childIds.has(String(item.categoryId || item.childId || item.categoryCode || item.category || '')) || childNames.has(itemName)
    })
  },

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
    // 【2026-11-02 用户令·PC 同构表格】客户聚合拉平为记录行(列=客户/分类/产品/时间/上传人/文件/学分),
    // 排序作用在记录行上(与 PC 表格同构: 分值=单条记录分, 时间=记录 createdAt)
    // 【修复 2026-11-03】瘦身组不带 records 子数组, 原始子记录从 _childRawById 取(同轻量页)
    let tableRows = buildRecordTableRows(records, this._childRawById)
    // 排序: 与 PC 表格同构(分值=单条记录分, 时间=记录 createdAt), 默认时间倒序
    if (this.data.order === 'score-asc') tableRows.sort((a, b) => a.score - b.score)
    else if (this.data.order === 'score-desc') tableRows.sort((a, b) => b.score - a.score)
    else if (this.data.order === 'time-asc') tableRows.sort((a, b) => String(a.timeRaw).localeCompare(String(b.timeRaw)))
    else if (this.data.order === 'time-desc') tableRows.sort((a, b) => String(b.timeRaw).localeCompare(String(a.timeRaw)))
    // 【分页 2026-10-18】排序/筛选/加载后一律回第 1 页; 分页切片与 total 同一次 setData 落定
    const paged = paginate(tableRows, 1, this.data.pageSize)
    this.setData({
      records,
      pagedTableRows: paged.rows,
      page: paged.page,
      pageSize: paged.pageSize,
      pagerTotal: paged.total,
      summary: buildSummary(records)
    }, () => { this.refreshIncentiveView(); this.buildCustomerRanking() })
  },

  // 【2026-11-02 用户令·表格】点行直达记录详情(拨盘页, 与轻量页同款跳转)
  openRecordDetail(e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    wx.navigateTo({ url: '../incentive/detail/index?id=' + id })
  },

  // 【分页 2026-10-18】客户端分页: 页码/页大小变化时重算当页切片; 用 paginate 返回的 page
  // 写回 data, 数据变少(删记录/筛选收紧)时自动 clamp 到最后一页
  applyPaging(page, pageSize) {
    // 【2026-11-02 表格】翻页切片对象=排序后的记录行(与 sort() 落定的同序, 此处按当前序重建)
    // 【修复 2026-11-03】同 sort(): 瘦身组子记录从 _childRawById 取
    const rows = buildRecordTableRows(this.data.records, this._childRawById)
    if (this.data.order === 'score-asc') rows.sort((a, b) => a.score - b.score)
    else if (this.data.order === 'score-desc') rows.sort((a, b) => b.score - a.score)
    else if (this.data.order === 'time-asc') rows.sort((a, b) => String(a.timeRaw).localeCompare(String(b.timeRaw)))
    else if (this.data.order === 'time-desc') rows.sort((a, b) => String(b.timeRaw).localeCompare(String(a.timeRaw)))
    const paged = paginate(rows, page === undefined ? this.data.page : page, pageSize || this.data.pageSize)
    this.setData({
      pagedTableRows: paged.rows,
      page: paged.page,
      pageSize: paged.pageSize,
      pagerTotal: paged.total
    })
  },

  onPagerChange(e) {
    const detail = e.detail || {}
    this.applyPaging(Number(detail.page) || 1, Number(detail.pageSize) || this.data.pageSize)
  },

  // 分类统计层级化(2026-11-05 用户令)后不再分页, 全量分组渲染; 其余排行仍为客户端分页 10/30 一页
  applyScoreboardPaging(page, pageSize) {
    const paged = paginate(this.data.scoreboard, page, pageSize)
    this.setData({ scoreboardPaged: paged.rows, scoreboardPage: paged.page, scoreboardPageSize: paged.pageSize, scoreboardTotal: paged.total })
  },
  // 【2026-11-02 用户令】客户排行=客户聚合按总学分降序, 双栏冻结表格(与个人排行同款);
  // 数据源=this.data.records(客户维度聚合, 已含子记录), byCategory 从子记录聚合
  // 【2026-11-04 用户令"客户排行只显示当前权限账号范围内的客户"】可见客户 id 集:
  // /api/customer/list 本身按数据范围过滤, 取回 id 集作客户排行的收口基线(与后端
  // customerRankings.visibleCustomers 客户可见范围口径一致); 60s 缓存; 取失败则维持原状不过滤。
  ensureVisibleCustomerIds() {
    const cached = this._visibleCustomerIds
    if (cached && Date.now() - (this._visibleCustomerIdsTs || 0) < 60000) {
      return Promise.resolve(cached)
    }
    const collected = new Set()
    const fetchPage = (page) => {
      return getCustomerList({ current: page, size: 500 }).then((res) => {
        const records = (res && res.records) || []
        records.forEach((c) => collected.add(c.id))
        const total = (res && res.total) || 0
        if (collected.size < total && page < 20) return fetchPage(page + 1)
        return collected
      })
    }
    return fetchPage(1).then((set) => {
      this._visibleCustomerIds = set
      this._visibleCustomerIdsTs = Date.now()
      return set
    }).catch(() => null)
  },

  buildCustomerRanking() {
    const demo = Boolean(getApp().globalData.demoMode)
    this.ensureVisibleCustomerIds().then((visibleIds) => {
      let rows = (this.data.records || []).map((c) => {
        const byCategory = {}
        // 【修复 2026-11-05】瘦身组不带 records 子数组(2026-10-13 性能批剥离, 689b07fc 同款漏网):
        // 子记录从 _childRawById 取, 组自带 records 优先(未来兼容/演示路径), 与 buildRecordTableRows 同习惯
        const children = (c && c.records) || (this._childRawById && this._childRawById[c.id]) || []
        ;(children).forEach((r) => {
          const cid = r.categoryId || r.childId
          if (!cid) return
          if (!byCategory[cid]) byCategory[cid] = [0, 0]
          byCategory[cid][0] += 1
          byCategory[cid][1] += Number(r.score) || 0
        })
        return {
          id: c.id,
          customerId: c.customerId,
          name: c.name || c.customerName || '未命名客户',
          industryText: c.industry || '—',
          scoreNum: Number(c.score) || 0,
          recordCountText: `${children.length} 条`,
          byCategory
        }
      })
      // 【2026-11-04 用户令】范围收口: 只保留当前权限账号可见范围内的客户(演示模式不过滤;
      // 可见集取失败时维持原状, 不虚增不误删)
      if (!demo && visibleIds) {
        rows = rows.filter((row) => visibleIds.has(row.customerId))
      }
      rows.sort((a, b) => b.scoreNum - a.scoreNum)
      rows = rows.map((row, idx) => ({ ...row, rank: idx + 1 }))
      this.setData({ customerRanking: rows }, () => this.applyCustomerRankPaging(1, this.data.customerRankPageSize || 10))
    })
  },
  applyCustomerRankPaging(page, pageSize) {
    const paged = paginate(this.data.customerRanking || [], page, pageSize)
    this.setData({ customerRankPaged: paged.rows, customerRankPage: paged.page, customerRankPageSize: paged.pageSize, customerRankTotal: paged.total })
  },
  onCustomerRankPagerChange(e) {
    const detail = e.detail || {}
    const current = this.data.customerRankPageSize || 10
    const size = Number(detail.pageSize) || current
    this.applyCustomerRankPaging(size === current ? (Number(detail.page) || 1) : 1, size)
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
    // 【2026-11-04 逐功能建码】stats 模式无权 pane 拒绝切换
    if (this._tabMode === 'stats' && this.data.canStatsPanes && this.data.canStatsPanes[activeTab] === false) return
    this.setData({ activeTab, tabs: buildTabs(activeTab, this._tabMode || 'query', this.data.canStatsPanes) }, () => this.refreshIncentiveView())
    // 【分页 2026-10-18】切回列表 tab 时回第 1 页, 避免停留在旧页码
    if (activeTab === 'records') this.applyPaging(1)
  },

  // 【2026-11-03 用户令】排行 tab 内三页签切换(客户/个人/部门): 数据已加载, 纯显示切换不发请求
  switchRankTab(e) {
    const rankTab = e.currentTarget.dataset.tab || 'customer'
    if (!RANK_TAB_KEYS.includes(rankTab) || rankTab === this.data.rankTab) return
    this.setData({ rankTab }, () => this.refreshIncentiveView())
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
