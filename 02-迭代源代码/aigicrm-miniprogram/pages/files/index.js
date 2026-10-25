const { getFolders, getStorageInfo, getStorageApplyInfo, getSharedToMe, getSharedToMeCount, getFileList, uploadFile, replaceFile, deleteFile, deleteFolder, getFolderAcl, shareFile: shareFileApi, getDownloadUrl, createFolder } = require('../../api/file')
const { getCustomerRecords } = require('../../api/incentive')
const { buildIncentiveFiles } = require('../../utils/incentive-files')
const { getCustomerSimpleUsers } = require('../../api/customer')
const { submitApproval } = require('../../api/approval')
const { showError, showErrorWithRetry } = require('../../utils/feedback')
const { hasPermission, requireLogin, getToken, isAdmin } = require('../../utils/auth')
const { readDemoFolders, removeDemoFolder } = require('../../utils/demo-folders')
const { isTextFileName, openTextView } = require('../../utils/attachment')
const { whenAuthReady } = require('./permission-ready')

const demoFiles = [
  { id: 'file-001', name: '华东智能制造集团项目方案.pdf', type: 'PDF', size: '8.6 MB', folder: '激励资料', uploaderName: '张三', updatedAt: '今天 10:24', tone: 'red' },
  { id: 'file-002', name: '客户需求调研记录.docx', type: 'DOC', size: '2.4 MB', folder: '客户文档', uploaderName: '李四', updatedAt: '昨天 16:40', tone: 'blue' },
  { id: 'file-003', name: 'Q3 激励复盘.xlsx', type: 'XLS', size: '1.1 MB', folder: '团队共享', uploaderName: '销售运营', updatedAt: '08-28 09:12', tone: 'green' },
  { id: 'file-004', name: '激励佐证图片素材.zip', type: 'ZIP', size: '24.8 MB', folder: '激励资料', uploaderName: '服务工程师', updatedAt: '08-26 18:05', tone: 'orange' }
]

const demoCapacity = { used: '3.8 GB', total: '10 GB', percent: 38, hint: '演示空间，真实账号登录后同步' }

function bytesToSize(value) {
  const bytes = Number(value || 0)
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  const fixed = size >= 10 || index === 0 ? 0 : 1
  return `${size.toFixed(fixed)} ${units[index]}`
}

/** 四项服务记录 → 可检索文件行：客户名/上传人/记录时间来自激励记录，文件来自各槽位 */
function buildIncentiveFileRows(groups) {
  const map = new Map()
  ;(groups || []).forEach((group) => {
    const customerName = group.customerName || group.name || '未命名客户'
    ;(group.records || []).forEach((record) => {
      const recordTime = record.createdAt || record.createTime || ''
      const files = buildIncentiveFiles(record)
      files.forEach((file) => {
        if (!file || !file.id) return
        const name = file.name || file.fileName || '未命名文件'
        const extension = name.includes('.') ? name.split('.').pop().toUpperCase() : 'FILE'
        const row = {
          id: file.id,
          name,
          type: extension,
          size: typeof file.size === 'number' ? bytesToSize(file.size) : (file.size || '-'),
          folder: customerName,
          customerName,
          uploaderName: record.userName || record.updatedByName || '',
          updatedAt: recordTime,
          fileScope: 'incentive',
          canDownload: true,
          recordCategory: record.categoryName || record.category || '',
          tag: file.tag || '',
          ossKey: file.ossKey || '',
          fileUrl: file.fileUrl || ''
        }
        const existing = map.get(file.id)
        if (!existing || String(recordTime) > String(existing.updatedAt)) map.set(file.id, row)
      })
    })
  })
  return Array.from(map.values()).sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)))
}

// 【D1-A】配额展示保留一位小数(10.5 GB 不能显示成 11 GB);文件大小仍走上面的既有口径
function formatQuotaSize(value) {
  const bytes = Number(value || 0)
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(Number.isInteger(size) ? 0 : 1)} ${units[index]}`
}

function normalizeCapacity(info) {
  const used = Number(info && info.used) || 0
  const quota = Number(info && info.quota) || 0
  const percent = quota > 0 ? Math.min(100, Math.round((used / quota) * 100)) : 0
  return {
    used: (info && info.usedFormatted) || bytesToSize(used),
    total: (info && info.quotaFormatted) || (quota > 0 ? bytesToSize(quota) : '未配置'),
    percent,
    hint: quota > 0 ? `已使用 ${percent}%，剩余 ${bytesToSize(Math.max(quota - used, 0))}` : '暂未配置存储配额'
  }
}

const GB_BYTES = 1024 * 1024 * 1024

function toFiniteNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

// 【D1-A 2026-10-18】扩容申请信息(bytes):只有 canApply 才露出入口,
// 不可申请原因(未授权/在途/已上限)按后端 reason 原样展示, 缺失时给短兜底
function normalizeStorageApplyInfo(info) {
  const source = info || {}
  const enabled = source.enabled !== false
  // enabled 是管理端开关原值: 管理员不受开关限制, 资格只认后端 canApply
  const canApply = source.canApply === true
  const quota = toFiniteNumber(source.quota)
  const maxQuota = toFiniteNumber(source.maxQuota)
  let reason = String(source.reason || '').trim()
  if (!reason && !canApply) reason = enabled ? '当前暂不可申请' : '扩容申请未启用'
  return {
    enabled,
    canApply,
    reason,
    used: toFiniteNumber(source.used) || 0,
    quota: quota == null ? 0 : quota,
    maxQuota: maxQuota == null ? 0 : maxQuota,
    allowDecrease: source.allowDecrease === true
  }
}

function buildStorageApplyView(data) {
  const info = (data && data.storageApplyInfo) || {}
  const form = (data && data.storageApplyForm) || {}
  const reasonInput = String(form.reasonInput || '')
  const quota = Number(info.quota) || 0
  const maxQuota = Number(info.maxQuota) || 0
  return {
    showApplyAction: Boolean(info.canApply),
    showReason: !info.canApply && Boolean(info.reason),
    reasonText: info.reason || '',
    visible: Boolean(form.visible),
    submitting: Boolean(form.submitting),
    quotaInput: String(form.quotaInput || ''),
    reasonInput,
    reasonCountText: `${reasonInput.length}/200`,
    errorText: form.errorText || '',
    currentQuotaText: formatQuotaSize(quota),
    maxQuotaText: maxQuota > 0 ? formatQuotaSize(maxQuota) : '未配置',
    quotaHint: maxQuota > 0
      ? `需大于当前 ${formatQuotaSize(quota)}，且不超过上限 ${formatQuotaSize(maxQuota)}`
      : '扩容上限信息暂不可用',
    quotaPlaceholder: quota > 0 ? `例如 ${Math.ceil(quota / GB_BYTES + 1)}` : '请输入数字'
  }
}

// 申请的是"申请后总配额"(GB):必须为数字、大于当前配额、不超过上限;理由必填且 ≤200 字
function validateStorageApplyInput(form, info) {
  const quotaInput = String((form && form.quotaInput) || '').trim()
  const reason = String((form && form.reasonInput) || '').trim()
  if (!quotaInput) return { error: '请输入申请后的总配额' }
  if (!/^\d+(\.\d+)?$/.test(quotaInput) || Number(quotaInput) <= 0) return { error: '申请配额需为大于 0 的数字' }
  const maxQuota = toFiniteNumber(info && info.maxQuota)
  if (maxQuota == null || maxQuota <= 0) return { error: '扩容上限信息暂不可用，请稍后重试' }
  const currentQuota = toFiniteNumber(info && info.quota) || 0
  const quota = Math.round(Number(quotaInput) * GB_BYTES)
  if (quota <= currentQuota) return { error: `申请配额需大于当前 ${formatQuotaSize(currentQuota)}` }
  if (quota > maxQuota) return { error: `申请配额不能超过上限 ${formatQuotaSize(maxQuota)}` }
  if (!reason) return { error: '请填写申请理由' }
  if (reason.length > 200) return { error: '申请理由不能超过 200 字' }
  return { quota, reason }
}

const permissionLabels = {
  view: '查看',
  upload: '上传',
  edit: '编辑',
  delete: '删除',
  share: '分享',
  download: '下载'
}

function normalizeAclRows(rows) {
  return (Array.isArray(rows) ? rows : []).map((row) => {
    const perms = Array.isArray(row.perms) ? row.perms : String(row.perms || '').split(',').filter(Boolean)
    return {
      roleId: row.roleId,
      roleName: row.roleName || '未命名角色',
      permLabels: perms.map((item) => permissionLabels[item] || item)
    }
  })
}

function pageRecords(response) {
  return response?.data?.records || response?.records || []
}

function pageTotal(response) {
  const value = response?.data?.total ?? response?.total
  return Number(value || 0)
}

const ALL_FOLDER_PERMS = ['view', 'upload', 'edit', 'delete', 'share', 'download']

function normalizeDownloadable(item) {
  if (item.canDownload === false) return false
  if (item.downloadable === 0 || item.downloadable === false) return false
  if (item.folderDownloadable === 0 || item.folderDownloadable === false) return false
  const perms = Array.isArray(item.perms) ? item.perms : String(item.perms || '').split(',').filter(Boolean)
  return !perms.length || perms.includes('download')
}

// 【需求A 2026-10-16】时间分组桶: 今天/本周/更早;解析失败一律归"更早"保证文件不丢
const FILE_GROUP_ORDER = ['今天', '本周', '更早']
function parseUpdatedBucket(text) {
  const raw = String(text || '')
  if (!raw) return '更早'
  if (raw.includes('今天')) return '今天'
  if (raw.includes('昨天')) return '本周'
  const match = raw.match(/(\d{4})[-/](\d{1,2})[-/](\d{1,2})/)
  if (match) {
    const d = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
    if (!Number.isNaN(d.getTime())) {
      const now = new Date()
      const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
      const diff = startOfToday - d.getTime()
      if (diff <= 0) return '今天'
      if (diff < 7 * 86400000) return '本周'
    }
  }
  return '更早'
}

// 【F2】后端 /file/list 与 /shared-to-me 只回 folderId(不回 folderName):
// 用已加载文件夹树的 rawName 反查真实夹名; 根目录才是"未分类",
// 引用了当前不可见/已删除的文件夹时显示明确占位, 不冒充"未分类"
function folderNameLookup(folders) {
  const lookup = {}
  ;(folders || []).forEach((folder) => {
    if (folder && folder.id !== undefined && folder.id !== null) {
      lookup[String(folder.id)] = folder.rawName || folder.name || ''
    }
  })
  return lookup
}

function resolveFolderText(folderId, folderNames) {
  if (!folderId) return '未分类'
  return (folderNames && folderNames[String(folderId)]) || '未知文件夹'
}

/** 接口时间 2026-08-28T16:44:34 → 2026-08-28 16:44；演示数据的「今天 10:24」原样保留 */
function formatDateTimeText(value) {
  const raw = String(value || '').trim()
  const match = raw.match(/^(\d{4})-(\d{1,2})-(\d{1,2})[T\s](\d{1,2}):(\d{2})/)
  if (!match) return raw || '-'
  const pad = (part) => String(part).padStart(2, '0')
  return `${match[1]}-${pad(match[2])}-${pad(match[3])} ${pad(match[4])}:${match[5]}`
}

function buildFileRowView(item, data) {
  const canMutateOwnTab = data.activeTab !== '分享给我' && data.activeTab !== '四项服务'
  // 【mp-4 2026-10-17】行操作按当前文件夹 ACL + downloadable 收敛（与 PC FileTable 同口径）；
  // 后端列表不返回 canDownload/isEvidence，downloadable/perms 来自文件夹树（getFolderTree 逐夹回填）。
  const folderPerms = Array.isArray(data.folderPerms) ? data.folderPerms : []
  const downloadable = data.folderDownloadable !== 0
  // 行级 canDownload=false 仍然优先；再叠加当前文件夹的下载开关与 download 权限
  const showDownload = item.canDownload !== false && downloadable && folderPerms.includes('download')
  // 激励文件（scope=incentive，folderId=null）是计分佐证，后端 replaceFile 直接拒绝；
  // 列表未返回 isEvidence，按 scope 保守隐藏替换。
  const showReplace = Boolean(data.canReplace && canMutateOwnTab && folderPerms.includes('edit') && item.fileScope !== 'incentive')
  const showDelete = Boolean(data.canDelete && canMutateOwnTab && folderPerms.includes('delete'))
  const showShare = Boolean(data.canShare && canMutateOwnTab && data.activeTab !== '公共' && folderPerms.includes('share'))
  // 【需求A】行内按钮全部收进「⋯」半屏面板;打开保留为整行首选操作(点行=打开)
  const sheetActions = [{ key: 'open', label: '打开' }]
  if (showDownload) sheetActions.push({ key: 'download', label: '下载' })
  if (showShare) sheetActions.push({ key: 'share', label: '分享' })
  if (showReplace) sheetActions.push({ key: 'replace', label: '替换' })
  if (showDelete) sheetActions.push({ key: 'delete', label: '删除', danger: true })
  const sizePrefix = item.size && item.size !== '-' ? `${item.size} · ` : ''
  return {
    ...item,
    typeText: item.type || 'FILE',
    nameText: item.name || '未命名文件',
    folderText: item.folder || '未分类',
    sizeText: item.size || '-',
    uploaderNameText: item.uploaderName || '—',
    updatedAtText: formatDateTimeText(item.updatedAt),
    // 【需求A】meta 拆两段: 行内只放 大小·时间(与名字同区), 文件夹·上传人放次要位
    metaBrief: `${sizePrefix}${formatDateTimeText(item.updatedAt)}`,
    metaExtra: `${item.folder || '未分类'} · ${item.uploaderName || '—'}`,
    bucket: parseUpdatedBucket(item.updatedAt),
    canDownload: showDownload,
    sheetActions,
    replaceLoading: String(data.replacingId || '') === String(item.id || '')
  }
}

/** 演示模式判定（页面模块级使用，app 未就绪时按非演示处理） */
function isDemoMode() {
  const app = typeof getApp === 'function' && getApp()
  return Boolean(app && app.globalData && app.globalData.demoMode)
}

// 【F3/F6】演示模式 Tab 归属规则: 列表过滤与 Tab 计数共用同一判定,
// 不再出现"公共计数 0 但列表明明有条目"。演示数据没有 scope/分享关系:
// 公共与私人共用非激励文件, 分享给我没有可展示的分享数据
function demoFileMatchesTab(item, activeTab) {
  if (activeTab === '四项服务') return item.folder === '激励资料'
  if (activeTab === '分享给我') return false
  return item.folder !== '激励资料'
}

function buildFileView(data) {
  const tabs = (data.tabs || []).map((item) => ({
    key: item,
    label: item,
    activeClass: data.activeTab === item ? 'active' : '',
    countText: String((data.tabCounts && data.tabCounts[item]) || 0)
  }))
  const files = (data.filteredFiles || []).map((item, index) => ({ ...buildFileRowView(item, data), flatIndex: index }))
  const folders = data.folders || []
  const hasActiveFolder = Boolean(data.activeFolderName)
  const hasManageFolders = folders.length > 0
  // 【mp-4】上传入口同样按当前夹 upload 权限收敛（后端 upload 会校验文件夹 P_UPLOAD）
  const showUploadAction = Boolean(data.canUpload && (data.folderPerms || []).includes('upload') && (data.activeTab === '公共' || data.activeTab === '私人'))
  const loading = Boolean(data.loading)
  // 【需求A】时间分组(今天/本周/更早),每组带 flatIndex 锚回 filteredFiles 供既有处理器复用
  const fileGroups = FILE_GROUP_ORDER.map((label) => {
    const groupFiles = files.filter((item) => item.bucket === label)
    return { key: label, label, count: groupFiles.length, files: groupFiles }
  }).filter((group) => group.count > 0)
  // 【分页 2026-10-18】条数优先用服务端 total；拿不到时退回本页条数
  const totalFiles = Number(data.total)
  const fileCount = Number.isFinite(totalFiles) && totalFiles > 0 ? totalFiles : files.length
  const isIncentiveTab = data.activeTab === '四项服务'
  const showFolderCard = !isIncentiveTab && data.activeTab !== '分享给我'
  return {
    showUploadAction,
    tabs,
    folderTitle: data.activeFolderName || '常用文件夹',
    folderCountText: `${folders.length} 个文件夹`,
    showFolderList: !hasActiveFolder,
    showClearFolder: hasActiveFolder,
    breadcrumbText: hasActiveFolder ? `全部文件 › ${data.activeFolderName}` : '',
    listTitle: isIncentiveTab ? '四项服务文件' : (hasActiveFolder ? '' : '最近文件'),
    showFolderCard,
    folders,
    // 【PR-04】兜底文件夹卡片（客户文档/团队共享）仅演示模式显示；真实模式空就是空
    showFallbackFolders: !folders.length && isDemoMode(),
    showManageFolders: hasManageFolders,
    showManageEmpty: !hasManageFolders,
    fileGroups,
    files,
    fileCountText: `共 ${fileCount} 个文件`,
    showFileList: files.length > 0,
    showEmpty: files.length === 0 && !data.previewVideoPath && !loading,
    // 首屏加载文案；翻页状态由 <pager loading> 承载
    showLoading: loading && files.length === 0,
    pagerLoading: loading,
    loadingText: '加载中...',
    aclRows: data.aclRows || [],
    showAclRows: Boolean(data.aclRows && data.aclRows.length)
  }
}

Page({
  data: { keyword: '', activeTab: '公共', activeFolderId: '', activeFolderName: '', tabs: ['公共', '私人', '四项服务', '分享给我'], tabCounts: { 公共: 0, 私人: 0, 四项服务: 0, 分享给我: 0 }, folders: [], files: [], filteredFiles: [], capacity: { used: '-', total: '-', percent: 0, hint: '存储统计加载中' }, canUpload: false, canReplace: false, canDelete: false, canShare: false, canManageFolder: false, canViewFolderAcl: false, canDevUploadSmoke: false, uploading: false, replacingId: '', loaded: false, loading: false, page: 1, pageSize: 10, total: 0, folderPerms: [], folderDownloadable: 1, aclVisible: false, aclLoading: false, aclFolderName: '', aclRows: [], previewing: false, previewVideoPath: '', actionSheet: { visible: false, index: -1, name: '', actions: [] }, storageApplyInfo: { enabled: false, canApply: false, reason: '', used: 0, quota: 0, maxQuota: 0, allowDecrease: false }, storageApplyForm: { visible: false, submitting: false, quotaInput: '', reasonInput: '', errorText: '' }, storageApplyView: buildStorageApplyView({}), fileView: buildFileView({ tabs: ['公共', '私人', '四项服务', '分享给我'], tabCounts: { 公共: 0, 私人: 0, 四项服务: 0, 分享给我: 0 }, folders: [], filteredFiles: [], activeTab: '公共', activeFolderName: '', previewVideoPath: '', total: 0 }) },

  setFileData(patch) {
    const nextData = { ...this.data, ...patch }
    // 【mp-4】文件夹 ACL/下载开关随数据一起回填到视图，行按钮只按这里的最终权限显隐
    const permissionState = this.resolveFolderPermissions(nextData)
    this.setData({
      ...patch,
      folderPerms: permissionState.perms,
      folderDownloadable: permissionState.downloadable,
      fileView: buildFileView({ ...nextData, ...permissionState }),
      storageApplyView: buildStorageApplyView(nextData)
    })
  },

  // 【D1-A】扩容弹窗表单/入口信息独立更新, 避免输入时重建整个文件列表视图
  setStorageApplyForm(patch) {
    const storageApplyForm = { ...this.data.storageApplyForm, ...patch }
    this.setData({
      storageApplyForm,
      storageApplyView: buildStorageApplyView({ ...this.data, storageApplyForm })
    })
  },

  setStorageApplyInfo(info) {
    const storageApplyInfo = normalizeStorageApplyInfo(info)
    this.setData({
      storageApplyInfo,
      storageApplyView: buildStorageApplyView({ ...this.data, storageApplyInfo })
    })
  },

  // 【D1-A】扩容资格独立加载: 不阻塞文件列表/文件夹树, 失败时隐藏入口(与 PC loadStorageApplyInfo 同口径)
  loadStorageApplyInfo() {
    if (getApp().globalData.demoMode) return
    return getStorageApplyInfo().then((info) => {
      if (info) this.setStorageApplyInfo(info)
    }).catch(() => { /* 无权限/接口异常: 不展示申请入口 */ })
  },

  /** 当前 Tab/文件夹的有效权限：根目录沿用 PC useFilePage 的默认口径，
   *  选中文件夹时使用 getFolderTree 返回的该夹 perms/downloadable（管理员 perms 为全量）。 */
  resolveFolderPermissions(data) {
    if (data.activeTab === '四项服务' || data.activeTab === '分享给我') {
      return { perms: ['view', 'download'], downloadable: 1 }
    }
    if (!data.activeFolderId) {
      if (isDemoMode() || isAdmin()) return { perms: ALL_FOLDER_PERMS, downloadable: 1 }
      return data.activeTab === '公共'
        ? { perms: ['view', 'upload', 'download'], downloadable: 1 }
        : { perms: ALL_FOLDER_PERMS, downloadable: 1 }
    }
    if (isDemoMode()) return { perms: ALL_FOLDER_PERMS, downloadable: 1 }
    const folder = (data.folders || []).find((item) => String(item.id) === String(data.activeFolderId))
    return {
      perms: folder && Array.isArray(folder.perms) ? folder.perms : [],
      downloadable: folder && folder.downloadable === 0 ? 0 : 1
    }
  },

  hasFolderPermission(action) {
    return (this.data.folderPerms || []).includes(action)
  },

  canDownloadItem(item) {
    if (this.data.folderDownloadable === 0 || !this.hasFolderPermission('download')) return false
    return !item || item.canDownload !== false
  },

  onLoad() {
    if (!requireLogin()) return
    // 【F5】冷启动 permissions 由 app.onShow 的 /auth/info 异步回填: 有在途请求先等它落地,
    // 否则 permission 尚未回填时直接判 file:view 会把合法用户误踢回首页
    whenAuthReady().then(() => this.initPage())
  },

  initPage() {
    if (!getApp().globalData.demoMode && !hasPermission('file:view')) {
      wx.showToast({ title: '没有文件查看权限', icon: 'none' })
      wx.switchTab({ url: '/pages/home/index' })
      return
    }
    const globalData = getApp().globalData
    const apiBaseUrl = globalData.apiBaseUrl || ''
    // Dev-only smoke guard — runtime environment whitelist (fail-closed):
    // 1. non-demo mode
    // 2. local/intranet API address whitelist
    // 3. runtime envVersion === 'develop' only (via wx.getAccountInfoSync)
    //    trial / release / undefined / API missing → fail-closed (hidden)
    let isDevelopEnv = false
    try {
      const accountInfo = wx.getAccountInfoSync()
      isDevelopEnv = accountInfo && accountInfo.miniProgram && accountInfo.miniProgram.envVersion === 'develop'
    } catch (_) { /* API missing or unavailable → fail-closed */ }
    const isLocalApi = /^https?:\/\/(127\.0\.0\.1|localhost|192\.168\.|10\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(apiBaseUrl)
    const canDevUploadSmoke = !globalData.demoMode && isLocalApi && isDevelopEnv
    this.setFileData({ canUpload: globalData.demoMode || hasPermission('file:upload'), canReplace: !globalData.demoMode && hasPermission('file:edit'), canDelete: !globalData.demoMode && hasPermission('file:delete'), canShare: !globalData.demoMode && hasPermission('file:share'), canManageFolder: globalData.demoMode || hasPermission('file:folder:manage'), canViewFolderAcl: !globalData.demoMode && hasPermission('file:acl:manage'), canDevUploadSmoke })
    this.loadFiles()
    this.loadStorageApplyInfo()
  },

  onShow() {
    // 【2026-10-24 自定义 tabBar】每页独立实例, onShow 同步选中态(勿删, 否则选中滞留旧页)
    if (typeof this.getTabBar === 'function' && this.getTabBar()) this.getTabBar().setData({ selected: 2 })
    // 【M20】onShow 防重入：列表请求进行中则跳过本次刷新，避免每次返回页面叠加并发请求
    // 【容量止血 2026-10-05】再加 30s 节流：回流 3 个请求的放大器之一；30s 内回流不重拉，
    // 上传/删除成功后的显式刷新路径不受影响（只拦 onShow 重入）。
    // 【PR-12 契约修复 2026-10-07】写后回流的强制刷新: folder-form 重命名成功后置
    // globalData.forceRefresh.files, 此处命中即清标记并把 _lastShowTs 归零 —— 绕过 30s 节流一次。
    // "写完回来立刻见结果"是 PR-12 契约, 节流只拦纯回流的流量放大,不拦写后回流。
    const forceRefresh = getApp().globalData.forceRefresh
    if (forceRefresh && forceRefresh.files) {
      forceRefresh.files = false
      this._lastShowTs = 0 // 归零使下方 throttled 判定为 false(绕过节流一次), 刷新后 _lastShowTs 重新起算
    }
    const now = Date.now()
    const throttled = now - (this._lastShowTs || 0) < 30000
    if (!throttled) this._lastShowTs = now
    if (this.data.loaded && !this._filesLoading && !throttled) this.loadFiles()
  },

  async loadFiles(options = {}) {
    // 【分页 2026-10-18】最近文件改为显式分页：默认回到第 1 页（筛选/上传/删除后刷新），
    // pager 翻页时显式传 page/pageSize；每次只替换当前页数据，不再触底追加。
    const requestedPage = Number(options.page)
    const page = Number.isFinite(requestedPage) && requestedPage > 0 ? Math.floor(requestedPage) : 1
    const requestedSize = Number(options.pageSize)
    const pageSize = Number.isFinite(requestedSize) && requestedSize > 0 ? Math.floor(requestedSize) : (Number(this.data.pageSize) || 10)
    if (getApp().globalData.demoMode) {
      const folders = readDemoFolders()
      // 演示模式没有服务端分页：先按当前文件夹/Tab/关键字过滤全量，再本地切当前页
      const baseFiles = this.data.activeFolderName
        ? demoFiles.filter((item) => item.folder === this.data.activeFolderName)
        : demoFiles
      const matched = this.filterFiles(baseFiles, this.data)
      const total = matched.length
      const totalPages = Math.max(1, Math.ceil(total / pageSize))
      const safePage = Math.min(page, totalPages)
      const start = (safePage - 1) * pageSize
      const files = matched.slice(start, start + pageSize)
      this.setFileData({ folders, files, filteredFiles: files, capacity: demoCapacity, tabCounts: this.demoTabCounts(demoFiles), loaded: true, loading: false, page: safePage, pageSize, total })
      return
    }
    // 四项服务 tab：文件行要带客户/人员/时间做检索，改用激励记录接口摊平文件；
    // 无 incentive:view 时退回通用文件接口（仅文件名检索），避免空 tab。
    if (this.data.activeTab === '四项服务' && hasPermission('incentive:view')) {
      return this.loadIncentiveFiles(page, pageSize)
    }
    // 【M20】请求序号守卫：Tab/筛选/翻页快速切换时，慢返回的旧响应直接丢弃，防止覆盖新列表
    const requestSeq = (this._filesReqSeq = (this._filesReqSeq || 0) + 1)
    this._filesLoading = true
    let folders = this.data.folders
    let files = []
    let total = 0
    let capacity = this.data.capacity
    let failed = false
    this.setFileData({ loading: true, page, pageSize })
    try {
      const folderScope = this.activeFileScope()
      const [folderResponse, storageInfo] = await Promise.all([
        // 【体检 P1 2026-10-12】带 scope 请求: 后端缺省只回公共树, 私人 Tab 此前恒空
        getFolders(this.data.activeTab === '公共' || this.data.activeTab === '私人' ? folderScope : undefined),
        getStorageInfo().catch((error) => {
          showError(error, '存储统计加载失败')
          return null
        })
      ])
      if (storageInfo) capacity = normalizeCapacity(storageInfo)
      // request 已解包 Result.data；兼容数组与旧式 {data|records} 两种返回形态
      const rawFolders = Array.isArray(folderResponse) ? folderResponse : (folderResponse?.data || folderResponse?.records || [])
      // 【体检 P1 2026-10-12】树只映射顶层会丢子夹(公共子文件夹不可见)——递归展平为芯片列表,
      // 子夹名前加「·」标识层级, 点按仍按各自 id 进入
      // 【mp-4】保留每个夹的 perms/downloadable，选中夹后用于收敛行内操作
      const flattenFolders = (list, depth, out) => {
        (list || []).forEach((f) => {
          // 【F1】展示名(「· 」层级标记)与真实名分离: rawName 用于重命名提交,
          // 否则前缀会被当成名字发到接口, 后端只 trim 会永久落库脏名
          const rawName = f.name || f.folderName || '未命名文件夹'
          out.push({
            id: f.id,
            name: (depth > 0 ? '· ' : '') + rawName,
            rawName,
            perms: Array.isArray(f.perms) ? f.perms : [],
            downloadable: f.downloadable
          })
          if (f.children && f.children.length) flattenFolders(f.children, depth + 1, out)
        })
        return out
      }
      folders = (this.data.activeTab === '公共' || this.data.activeTab === '私人')
        ? flattenFolders(rawFolders.filter((item) => item.folderType === folderScope), 0, [])
        : []
      const fileResponse = this.data.activeTab === '分享给我'
        ? await getSharedToMe(this.buildFileListParams({ current: page, size: pageSize }))
        : await getFileList(this.buildFileListParams({ current: page, size: pageSize }))
      files = pageRecords(fileResponse)
      total = pageTotal(fileResponse)
    } catch (error) {
      failed = true
      folders = []
      files = []
      // 【PR-04】真实模式失败：报错 + 空列表（空态样式），不再显示演示数据
      // 【M20】已被更新请求取代的旧失败不再弹错（新请求的结果会接管界面）
      capacity = { used: '-', total: '-', percent: 0, hint: '存储统计暂未同步' }
      if (requestSeq === this._filesReqSeq) {
        showErrorWithRetry(error, '文件加载失败，请重试', () => this.loadFiles({ page, pageSize }))
      }
    } finally {
      if (requestSeq === this._filesReqSeq) this._filesLoading = false
    }
    if (requestSeq !== this._filesReqSeq) return
    const folderNames = folderNameLookup(folders)
    const normalizedFiles = files.map((item, index) => this.normalizeFile(item, index, folderNames))
    // 分页替换语义：只保留当前页；flatIndex 由 buildFileView 按 filteredFiles 重新编号
    this.setFileData({ folders, files: normalizedFiles, filteredFiles: this.filterFiles(normalizedFiles), capacity, loaded: true, loading: false, page, pageSize, total: failed ? 0 : total })
    if (!failed && page === 1) this.loadTabCounts()
  },

  /** 四项服务文件：一次拉取激励记录，摊平为客户/人员/时间可检索的文件行；分页与搜索均在本地完成 */
  loadIncentiveFiles(page = 1, pageSize = this.data.pageSize || 10) {
    const requestSeq = (this._incentiveReqSeq = (this._incentiveReqSeq || 0) + 1)
    this._filesLoading = true
    this.setFileData({ loading: true, page: 1, folders: [] })
    return getCustomerRecords({}).then((groups) => {
      if (requestSeq !== this._incentiveReqSeq) return
      const rows = buildIncentiveFileRows(Array.isArray(groups) ? groups : [])
      this._incentiveFileRows = rows
      // Tab 徽标与可检索文件行同口径，避免徽标 1727、列表 1829 的错位
      this.setFileData({ tabCounts: { ...this.data.tabCounts, '四项服务': rows.length } })
      this.applyIncentiveFileFilter(page, pageSize)
    }).catch((error) => {
      if (requestSeq !== this._incentiveReqSeq) return
      this._incentiveFileRows = []
      this.setFileData({ loading: false, files: [], filteredFiles: [], total: 0, page: 1 })
      showErrorWithRetry(error, '四项服务文件加载失败', () => this.loadIncentiveFiles(1, pageSize))
    }).finally(() => {
      if (requestSeq === this._incentiveReqSeq) this._filesLoading = false
    })
  },

  applyIncentiveFileFilter(page = 1, pageSize = this.data.pageSize || 10, keywordOverride) {
    const keyword = String(keywordOverride !== undefined ? keywordOverride : (this.data.keyword || '')).trim().toLowerCase()
    const all = this._incentiveFileRows || []
    const filtered = !keyword ? all : all.filter((item) => {
      const haystack = `${item.name || ''} ${item.customerName || ''} ${item.uploaderName || ''} ${item.recordCategory || ''} ${item.tag || ''} ${item.updatedAt || ''} ${formatDateTimeText(item.updatedAt)}`
      return haystack.toLowerCase().includes(keyword)
    })
    const size = pageSize > 0 ? pageSize : 10
    const total = filtered.length
    const totalPages = Math.max(1, Math.ceil(total / size))
    const safePage = Math.min(Math.max(1, page || 1), totalPages)
    const start = (safePage - 1) * size
    const rows = filtered.slice(start, start + size)
    this.setFileData({ folders: [], files: rows, filteredFiles: rows, loaded: true, loading: false, page: safePage, pageSize: size, total })
  },

  // 【分页 2026-10-18】翻页改由 <pager> 显式驱动，触底不再追加请求
  onReachBottom() {},

  // 【分页 2026-10-18】pager 事件 detail={page,pageSize}: 上一页/下一页/切换 10/30 条
  onPagerChange(event) {
    const detail = (event && event.detail) || {}
    const page = Math.max(1, Number(detail.page) || 1)
    const pageSize = Number(detail.pageSize) || this.data.pageSize || 10
    if (this.data.activeTab === '四项服务' && this._incentiveFileRows) {
      this.applyIncentiveFileFilter(page, pageSize)
      return
    }
    this.loadFiles({ page, pageSize })
  },

  activeFileScope() {
    // 【对齐 PC 2026-09-20】公共=public / 私人=private / 四项服务=incentive, 分享给我走独立接口
    if (this.data.activeTab === '公共') return 'public'
    if (this.data.activeTab === '四项服务') return 'incentive'
    return 'private'
  },

  buildFileListParams(extra = {}) {
    const params = {
      current: this.data.page || 1,
      size: this.data.pageSize || 10,
      keyword: this.data.keyword.trim() || undefined,
      ...extra
    }
    if (this.data.activeTab !== '分享给我') params.scope = this.activeFileScope()
    if (this.data.activeFolderId && (this.data.activeTab === '公共' || this.data.activeTab === '私人')) params.folderId = this.data.activeFolderId
    return params
  },

  demoTabCounts(files = demoFiles) {
    // 【F6】计数与 filterFiles 共用 demoFileMatchesTab, 保证"看到几条 = 显示几条"
    return (this.data.tabs || []).reduce((counts, tab) => {
      counts[tab] = files.filter((item) => demoFileMatchesTab(item, tab)).length
      return counts
    }, {})
  },

  async loadTabCounts() {
    if (getApp().globalData.demoMode) {
      // 【分页】Tab 计数始终按演示全量统计，不能只数当前页
      this.setFileData({ tabCounts: this.demoTabCounts(demoFiles) })
      return
    }
    try {
      const [publicFiles, privateFiles, incentiveFiles, sharedCount] = await Promise.all([
        getFileList({ current: 1, size: 1, scope: 'public' }),
        getFileList({ current: 1, size: 1, scope: 'private' }),
        getFileList({ current: 1, size: 1, scope: 'incentive' }),
        getSharedToMeCount()
      ])
      this.setFileData({
        tabCounts: {
          公共: pageTotal(publicFiles),
          私人: pageTotal(privateFiles),
          四项服务: pageTotal(incentiveFiles),
          分享给我: Number((sharedCount && (sharedCount.data ?? sharedCount)) || 0)
        }
      })
    } catch (error) {
      showError(error, '文件数量加载失败')
    }
  },

  normalizeFile(item, index, folderNames) {
    const name = item.name || item.fileName || '未命名文件'
    const extension = name.includes('.') ? name.split('.').pop().toUpperCase() : 'FILE'
    return {
      id: item.id || '',
      name,
      type: extension,
      size: typeof item.fileSize === 'number' ? bytesToSize(item.fileSize) : (item.fileSize || item.size || '-'),
      // 【F2】列表接口只回 folderId: 按已加载文件夹树反查真实夹名(缺失时给明确占位)
      folder: item.folderName || resolveFolderText(item.folderId, folderNames),
      folderId: item.folderId || '',
      fileScope: item.fileScope || '',
      uploaderId: item.uploaderId || '',
      uploaderName: item.uploaderName || item.uploader || item.createByName || '—',
      updatedAt: item.updateTime || item.updatedAt || '',
      ossKey: item.ossKey || item.fileKey || item.path || '',
      fileUrl: item.fileUrl || item.url || '',
      canDownload: normalizeDownloadable(item),
      tone: extension === 'PDF' ? 'red' : extension === 'XLS' ? 'green' : 'blue',
      demoId: item.id || `file-${index}`
    }
  },

  filterFiles(files = this.data.files, context = this.data) {
    const keyword = String(context.keyword || '').trim().toLowerCase()
    // 真实模式列表已按 scope 服务端过滤(onTabTap→loadFiles 带 scope 重拉), 本地不得按文件夹文字二次筛选
    // —— 真实激励文件 folder 多为'未分类', 文字法会把整表筛空; 文字法仅演示模式(无服务端)使用
    const useLabelScope = Boolean(getApp().globalData.demoMode)
    return files.filter(item => {
      // 【F3】Tab/keyword 从 context 读取本次变更后的值: 直接读 this.data 会拿到旧值(慢一拍)
      const matchesTab = !useLabelScope ? true : demoFileMatchesTab(item, context.activeTab)
      return matchesTab && (!keyword || `${item.name}${item.folder}`.toLowerCase().includes(keyword))
    })
  },

  onKeywordInput(event) {
    const keyword = event.detail.value
    if (this.data.activeTab === '四项服务' && this._incentiveFileRows) {
      // 四项服务：客户/人员/时间/文件名都在本地行上，输入即筛选
      this.setFileData({ keyword })
      this.applyIncentiveFileFilter(1, this.data.pageSize, keyword)
      return
    }
    if (getApp().globalData.demoMode) {
      // 【分页】演示模式本地分页: 关键字即时生效, 回到第 1 页按全量重算
      this.setFileData({ keyword })
      this.loadFiles({ page: 1 })
      return
    }
    // 【F3】用新关键词过滤当前页显示: 先 setData 再 filterFiles 会漏掉刚输入的最后一个字符;
    // 真实模式仍等「搜索」确认后再按关键字请求第 1 页
    this.setFileData({ keyword, filteredFiles: this.filterFiles(this.data.files, { ...this.data, keyword }) })
  },
  searchFiles() {
    if (this.data.activeTab === '四项服务' && this._incentiveFileRows) {
      this.applyIncentiveFileFilter(1, this.data.pageSize)
      return
    }
    // 【分页】查询条件变化：重置到第 1 页重新拉取（demo 模式在 loadFiles 内本地分页）
    this.loadFiles({ page: 1 })
  },
  onTabTap(event) {
    const activeTab = event.currentTarget.dataset.tab
    // 【分页】切换 Tab 属于筛选变化：清空文件夹并回到第 1 页
    this.setFileData({ activeTab, activeFolderId: '', activeFolderName: '', page: 1, ...(activeTab === '四项服务' ? { keyword: '' } : {}) })
    this.loadFiles({ page: 1 })
  },
  openFolder(event) {
    const rawFolderId = event.currentTarget.dataset.id
    const folderId = rawFolderId && rawFolderId !== 'undefined' && rawFolderId !== 'null' ? rawFolderId : ''
    const folderName = event.currentTarget.dataset.name || ''
    // 【分页】进入文件夹属于筛选变化：回到第 1 页（demo 模式按 activeFolderName 本地过滤后切页）
    this.setFileData({ activeFolderId: folderId, activeFolderName: folderName, page: 1 })
    this.loadFiles({ page: 1 })
  },
  clearFolder() { this.setFileData({ activeFolderId: '', activeFolderName: '', page: 1 }); this.loadFiles({ page: 1 }) },
  // 【D1-A 2026-10-18】容量卡「申请扩容」:页面内自绘弹窗承载 总配额 + 理由 两个字段,
  // 提交复用审批 submit(type=storage, extraData={quota,reason}); 客户端不传 oldQuota
  openStorageApply() {
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示模式暂不支持扩容申请', icon: 'none' })
    const info = this.data.storageApplyInfo || {}
    if (!info.canApply) return wx.showToast({ title: info.reason || '当前暂不可申请扩容', icon: 'none' })
    if (!info.maxQuota) return wx.showToast({ title: '扩容上限信息暂不可用', icon: 'none' })
    this.setStorageApplyForm({ visible: true, submitting: false, quotaInput: '', reasonInput: '', errorText: '' })
  },
  closeStorageApply() {
    if (this.data.storageApplyForm.submitting) return
    this.setStorageApplyForm({ visible: false, errorText: '' })
  },
  onStorageQuotaInput(event) {
    this.setStorageApplyForm({ quotaInput: event.detail.value, errorText: '' })
  },
  onStorageReasonInput(event) {
    this.setStorageApplyForm({ reasonInput: event.detail.value, errorText: '' })
  },
  submitStorageApply() {
    const form = this.data.storageApplyForm || {}
    if (form.submitting) return
    const info = this.data.storageApplyInfo || {}
    if (!info.canApply) {
      this.setStorageApplyForm({ visible: false })
      return wx.showToast({ title: info.reason || '当前暂不可申请扩容', icon: 'none' })
    }
    const validation = validateStorageApplyInput(form, info)
    if (validation.error) return this.setStorageApplyForm({ errorText: validation.error })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示模式暂不支持扩容申请', icon: 'none' })
    this.setStorageApplyForm({ submitting: true, errorText: '' })
    submitApproval({
      type: 'storage',
      extraData: JSON.stringify({ quota: validation.quota, reason: validation.reason })
    }).then(() => {
      this.setStorageApplyForm({ visible: false, submitting: false, quotaInput: '', reasonInput: '', errorText: '' })
      wx.showToast({ title: '扩容申请已提交', icon: 'success' })
      this.refreshStorageOverview()
    }).catch((error) => {
      this.setStorageApplyForm({ submitting: false, errorText: (error && error.message) || '提交失败，请稍后重试' })
      showError(error, '扩容申请提交失败')
    })
  },
  refreshStorageOverview() {
    if (getApp().globalData.demoMode) return
    getStorageInfo().then((info) => {
      if (info) this.setFileData({ capacity: normalizeCapacity(info) })
    }).catch((error) => showError(error, '存储统计加载失败'))
    this.loadStorageApplyInfo()
  },
  // 【弹窗化 2026-09-20】新建文件夹不再跳整页表单, showModal editable 一步完成; folderType 跟随当前 tab(与 PC 同参)
  openFolderForm() {
    if (this.data.activeTab !== '公共' && this.data.activeTab !== '私人') {
      return wx.showToast({ title: '当前分类不支持文件夹', icon: 'none' })
    }
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示模式不可新建文件夹', icon: 'none' })
    wx.showModal({
      title: '新建文件夹',
      editable: true,
      placeholderText: '输入文件夹名称',
      success: (result) => {
        const name = String(result.content || '').trim()
        if (!result.confirm || !name) return
        wx.showLoading({ title: '创建中' })
        // 【体检 H3 2026-10-12】带当前文件夹上下文(与上传同参): 浏览夹内时建的是子夹;
        // 根级传 0, 后端已把 0 归一化为根级哨兵(此前恒传 0 被 403)
        createFolder({ folderName: name, folderType: this.activeFileScope(), parentId: this.data.activeFolderId || 0 }).then(() => {
          wx.hideLoading()
          wx.showToast({ title: '已创建', icon: 'success' })
          this.data.activeFolderId
            ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } })
            : this.loadFiles()
        }).catch((error) => {
          wx.hideLoading()
          showError(error, '新建文件夹失败')
        })
      }
    })
  },
  openFolderActions(event) {
    const dataset = event.currentTarget.dataset || {}
    const actions = []
    if (this.data.canViewFolderAcl) actions.push({ key: 'acl', label: '权限设置' })
    actions.push({ key: 'rename', label: '重命名' })
    actions.push({ key: 'delete', label: '删除' })
    wx.showActionSheet({
      itemList: actions.map((item) => item.label),
      success: (result) => {
        const action = actions[result.tapIndex]
        if (!action) return
        const forwarded = { currentTarget: { dataset } }
        if (action.key === 'acl') this.viewFolderAcl(forwarded)
        if (action.key === 'rename') this.openFolderEdit(forwarded)
        if (action.key === 'delete') this.removeFolder(forwarded)
      }
    })
  },
  openFolderEdit(event) {
    // 【F1】只提交真实名(rawName): 列表 name 可能带「· 」层级前缀, 前缀仅用于视图
    const dataset = event.currentTarget.dataset || {}
    const name = dataset.rawName ? dataset.rawName : (dataset.name || '')
    wx.navigateTo({ url: `/pages/files/folder-form/index?id=${dataset.id}&name=${encodeURIComponent(name)}` })
  },
  removeFolder(event) {
    if (!this.data.canManageFolder) return wx.showToast({ title: '没有文件夹管理权限', icon: 'none' })
    const id = event.currentTarget.dataset.id
    const name = event.currentTarget.dataset.name || '该文件夹'
    if (!id) return
    wx.showModal({ title: '删除文件夹', content: `确认删除“${name}”？请先确认其中没有需要保留的文件。`, success: (result) => {
      if (!result.confirm) return
      if (getApp().globalData.demoMode) {
        removeDemoFolder(id)
        wx.showToast({ title: '已删除', icon: 'success' })
        return this.loadFiles()
      }
      deleteFolder(id).then(() => { wx.showToast({ title: '已删除', icon: 'success' }); this.loadFiles() }).catch((error) => showError(error, '删除失败'))
    } })
  },
  viewFolderAcl(event) {
    if (!this.data.canViewFolderAcl) return wx.showToast({ title: '没有文件夹权限查看权限', icon: 'none' })
    const id = event.currentTarget.dataset.id
    const name = event.currentTarget.dataset.name || '文件夹'
    if (!id) return
    this.setFileData({ aclVisible: true, aclLoading: true, aclFolderName: name, aclRows: [] })
    getFolderAcl(id).then((response) => {
      this.setFileData({ aclRows: normalizeAclRows(response && (response.data || response.records || response)) })
    }).catch((error) => {
      this.setFileData({ aclVisible: false })
      showError(error, '权限加载失败')
    }).finally(() => this.setFileData({ aclLoading: false }))
  },
  closeFolderAcl() { this.setFileData({ aclVisible: false, aclFolderName: '', aclRows: [] }) },
  noop() {},
  uploadFile() {
    if (!this.data.canUpload) return wx.showToast({ title: '没有文件上传权限', icon: 'none' })
    if (getApp().globalData.demoMode) return wx.showToast({ title: '演示模式暂不上传文件', icon: 'none' })
    if (this.data.uploading) return
    wx.chooseMessageFile({ count: 1, type: 'file', success: (result) => {
      const file = result.tempFiles && result.tempFiles[0]
      if (!file || !file.path) return
      this.setData({ uploading: true })
      wx.showLoading({ title: '上传中' })
      // 【L17】loading 与 toast 共用原生单例：必须先 hideLoading 再 showToast，否则成功提示被 finally 里的 hideLoading 一并关掉
      uploadFile(file.path, this.data.activeFolderId || null, this.activeFileScope()).then(() => { wx.hideLoading(); wx.showToast({ title: '上传成功', icon: 'success' }); this.data.activeFolderId ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } }) : this.loadFiles() }).catch((error) => { wx.hideLoading(); showError(error, '上传失败') }).finally(() => { this.setData({ uploading: false }) })
    }, fail: (error) => { if (error && error.errMsg && !error.errMsg.includes('cancel')) showError(error, '选择文件失败') } })
  },
  runDevUploadSmoke() {
    if (!this.data.canDevUploadSmoke) return wx.showToast({ title: '仅本地开发可用', icon: 'none' })
    if (!this.data.canUpload) return wx.showToast({ title: '没有文件上传权限', icon: 'none' })
    if (this.data.uploading) return
    const fs = wx.getFileSystemManager()
    const filePath = `${wx.env.USER_DATA_PATH}/aigicrm-upload-smoke-${Date.now()}.txt`
    const content = `AIGICRM miniprogram upload smoke ${new Date().toISOString()}`
    this.setData({ uploading: true })
    wx.showLoading({ title: '自测上传中' })
    const cleanupTempFile = () => {
      try { fs.unlink({ filePath, fail: () => {} }) } catch (_) { /* ignore cleanup errors */ }
    }
    try {
      fs.writeFile({
        filePath,
        data: content,
        encoding: 'utf8',
        success: () => {
          // 【L17】先 hideLoading 再 showToast（共用原生单例，见 uploadFile 处注释）
          uploadFile(filePath, this.data.activeFolderId || null, this.activeFileScope())
            .then(() => { wx.hideLoading(); wx.showToast({ title: '自测上传成功', icon: 'success' }); this.data.activeFolderId ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } }) : this.loadFiles() })
            .catch((error) => { wx.hideLoading(); showError(error, '自测上传失败') })
            .finally(() => { cleanupTempFile(); this.setData({ uploading: false }) })
        },
        fail: (error) => { this.setData({ uploading: false }); wx.hideLoading(); showError(error, '自测文件创建失败') }
      })
    } catch (error) {
      this.setData({ uploading: false })
      wx.hideLoading()
      showError(error, '自测文件创建异常')
    }
  },
  openFile(event) {
    const index = Number(event.currentTarget.dataset.index)
    const item = this.data.filteredFiles[index]
    if (!item || ((!item.id || String(item.id).startsWith('file-')) && !item.ossKey && !item.fileUrl)) return wx.showToast({ title: '演示文件暂不可预览', icon: 'none' })
    // 【mp-4】详情页沿用列表行算出的最终门控（文件夹 ACL/downloadable + scope），
    // 不在详情页仅凭全局权限码重新放开替换/下载。
    const rowView = (this.data.fileView.files || [])[index] || {}
    const canReplace = (rowView.sheetActions || []).some((action) => action.key === 'replace')
    wx.navigateTo({ url: `/pages/files/detail/index?id=${item.id || ''}&path=${encodeURIComponent(item.ossKey || item.fileUrl || '')}&name=${encodeURIComponent(item.name)}&type=${item.type}&size=${encodeURIComponent(item.size)}&folder=${encodeURIComponent(item.folder)}&uploaderName=${encodeURIComponent(item.uploaderName || '')}&updatedAt=${encodeURIComponent(item.updatedAt || '')}&scope=${encodeURIComponent(item.fileScope || '')}&canReplace=${canReplace ? 1 : 0}&canDownload=${rowView.canDownload ? 1 : 0}` })
  },
  findFile(event) {
    return this.data.filteredFiles[event.currentTarget.dataset.index]
  },
  previewFile(event) {
    const item = this.findFile(event)
    if (!item || ((!item.id || String(item.id).startsWith('file-')) && !item.ossKey && !item.fileUrl)) return wx.showToast({ title: '演示文件暂不可预览', icon: 'none' })
    this.fetchFile(item, 'preview')
  },
  downloadFile(event) {
    const item = this.findFile(event)
    if (!item || ((!item.id || String(item.id).startsWith('file-')) && !item.ossKey && !item.fileUrl)) return wx.showToast({ title: '演示文件暂不可下载', icon: 'none' })
    if (!this.canDownloadItem(item)) return wx.showToast({ title: '该文件夹已关闭下载', icon: 'none' })
    this.fetchFile(item, 'download')
  },
  removeFile(event) {
    if (!this.data.canDelete || !this.hasFolderPermission('delete')) return wx.showToast({ title: '没有文件删除权限', icon: 'none' })
    const item = this.findFile(event)
    if (!item || !item.id || String(item.id).startsWith('file-')) return wx.showToast({ title: '演示文件暂不可删除', icon: 'none' })
    wx.showModal({
      title: '删除文件',
      content: `确认删除“${item.name || '该文件'}”？`,
      success: (result) => {
        if (!result.confirm) return
        deleteFile(item.id).then(() => {
          wx.showToast({ title: '已删除', icon: 'success' })
          this.data.activeFolderId ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } }) : this.loadFiles()
        }).catch((error) => showError(error, '删除文件失败'))
      }
    })
  },
  replaceListFile(event) {
    // 【mp-4】文件夹 edit ACL + 非激励佐证 scope 才允许替换（后端 replaceFile 对佐证直接拒绝）
    if (!this.data.canReplace || this.data.activeTab === '分享给我' || this.data.activeTab === '四项服务' || !this.hasFolderPermission('edit')) return wx.showToast({ title: '没有文件替换权限', icon: 'none' })
    const item = this.findFile(event)
    if (!item || !item.id || String(item.id).startsWith('file-')) return wx.showToast({ title: '演示文件暂不可替换', icon: 'none' })
    if (item.fileScope === 'incentive') return wx.showToast({ title: '激励佐证文件不能替换，请在激励模块删除后重新上传', icon: 'none' })
    if (this.data.replacingId) return
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0]
        if (!file || !file.path) return
        this.setFileData({ replacingId: item.id })
        wx.showLoading({ title: '替换中...' })
        replaceFile(item.id, file.path).then((result) => {
          const version = result && result.version ? `，版本 ${result.version}` : ''
          // 【L17】先 hideLoading 再 showToast（共用原生单例，见 uploadFile 处注释）
          wx.hideLoading()
          wx.showToast({ title: `替换成功${version}`, icon: 'success' })
          this.data.activeFolderId
            ? this.openFolder({ currentTarget: { dataset: { id: this.data.activeFolderId, name: this.data.activeFolderName } } })
            : this.loadFiles()
          this.loadTabCounts()
        }).catch((error) => { wx.hideLoading(); showError(error, '替换失败') }).finally(() => {
          this.setFileData({ replacingId: '' })
        })
      },
      fail: (error) => { if (error && error.errMsg && !error.errMsg.includes('cancel')) showError(error, '选择文件失败') }
    })
  },
  fetchFile(item, mode) {
    if (this.data.previewing) return
    const downloadUrl = getDownloadUrl({ id: item.id, ossKey: item.ossKey || item.fileUrl })
    if (!downloadUrl) return showError(new Error('缺少下载地址'), '文件下载失败')
    this.setData({ previewing: true })
    // 【下载进度 2026-10-17】同 utils/attachment.js: 整包下载场景把百分比写进 loading,
    // done 守卫防迟到进度回调在 hideLoading 后重新顶出 loading
    let done = false
    wx.showLoading({ title: mode === 'download' ? '下载中' : '准备预览' })
    const task = wx.downloadFile({
      url: downloadUrl,
      header: getToken() ? { Authorization: 'Bearer ' + getToken() } : {},
      success: (result) => {
        if (result.statusCode !== 200) return showError(new Error('文件下载失败'), '文件下载失败')
        if (mode === 'download') return this.saveDownloadedFile(result.tempFilePath)
        this.openDownloadedFile(item, result.tempFilePath)
      },
      fail: (error) => showError(error, '文件下载失败'),
      complete: () => { done = true; wx.hideLoading(); this.setData({ previewing: false }) }
    })
    if (task && typeof task.onProgressUpdate === 'function') {
      task.onProgressUpdate((res) => {
        if (done || !res || !res.progress) return
        wx.showLoading({ title: `${mode === 'download' ? '下载中' : '加载中'} ${Math.min(99, res.progress)}%` })
      })
    }
  },
  openDownloadedFile(item, tempFilePath) {
    const suffix = String(item.name || '').toLowerCase().split('.').pop()
    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(suffix)) {
      wx.previewImage({ urls: [tempFilePath], fail: (error) => showError(error, '图片预览失败') })
      return
    }
    if (['mp4', 'mov', 'm4v', 'webm'].includes(suffix)) {
      this.setData({ previewVideoPath: tempFilePath })
      return
    }
    if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(suffix)) {
      wx.openDocument({ filePath: tempFilePath, showMenu: true, fail: (error) => showError(error, '无法直接预览，可下载后查看') })
      return
    }
    // 【2026-10-17】txt/csv/md/log/json 走文本查看页(openDocument 不支持文本格式)
    if (isTextFileName(item.name)) return openTextView(tempFilePath, item.name)
    wx.showModal({ title: '无法直接打开', content: '当前格式暂不支持直接预览，可下载后查看。', showCancel: false })
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
  shareFile(event) {
    if (!this.data.canShare || this.data.activeTab === '分享给我' || this.data.activeTab === '公共' || this.data.activeTab === '四项服务' || !this.hasFolderPermission('share')) return
    const item = this.data.filteredFiles[event.currentTarget.dataset.index]
    if (!item || !item.id || String(item.id).startsWith('file-')) return wx.showToast({ title: '演示文件暂不可分享', icon: 'none' })
    getCustomerSimpleUsers().then((users) => {
      const list = Array.isArray(users) ? users.filter((user) => user.id != null) : []
      if (!list.length) {
        wx.showToast({ title: '暂无可分享用户', icon: 'none' })
        return
      }
      wx.showActionSheet({
        itemList: list.map((user) => user.realName || user.username || `用户${user.id}`),
        success: (result) => {
          const target = list[result.tapIndex]
          if (!target) return
          shareFileApi(item.id, [target.id]).then(() => {
            wx.showToast({ title: '分享成功', icon: 'success' })
          }).catch((error) => showError(error, '分享失败'))
        }
      })
    }).catch((error) => showError(error, '用户列表加载失败'))
  },
  // 【需求A 2026-10-16】「⋯」/长按 → 自绘半屏操作面板;动作按权限显隐(buildFileRowView 已算好),
  // 分发复用既有处理器(合成 dataset.index 事件),权限/确认/刷新逻辑零改动
  openActionSheet(event) {
    const index = Number(event.currentTarget.dataset.index)
    const item = this.data.filteredFiles[index]
    if (!item) return
    const view = this.data.fileView.files[index] || {}
    this.setData({ actionSheet: { visible: true, index, name: item.name || '未命名文件', actions: view.sheetActions || [{ key: 'open', label: '打开' }] } })
  },
  closeActionSheet() { this.setData({ 'actionSheet.visible': false }) },
  onSheetAction(event) {
    const act = event.currentTarget.dataset.act
    const index = this.data.actionSheet.index
    this.setData({ 'actionSheet.visible': false })
    const synthetic = { currentTarget: { dataset: { index } } }
    if (act === 'open') return this.openFile(synthetic)
    if (act === 'download') return this.downloadFile(synthetic)
    if (act === 'share') return this.shareFile(synthetic)
    if (act === 'replace') return this.replaceListFile(synthetic)
    if (act === 'delete') return this.removeFile(synthetic)
  },
  switchTab(event) { wx.switchTab({ url: event.currentTarget.dataset.url }) }
})
