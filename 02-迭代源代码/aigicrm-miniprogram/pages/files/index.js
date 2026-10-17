const { getFolders, getStorageInfo, getSharedToMe, getSharedToMeCount, getFileList, uploadFile, replaceFile, deleteFile, deleteFolder, getFolderAcl, shareFile: shareFileApi, getDownloadUrl, createFolder } = require('../../api/file')
const { getCustomerSimpleUsers } = require('../../api/customer')
const { showError, showErrorWithRetry } = require('../../utils/feedback')
const { hasPermission, requireLogin, getToken } = require('../../utils/auth')
const { readDemoFolders, removeDemoFolder } = require('../../utils/demo-folders')
const { isTextFileName, openTextView } = require('../../utils/attachment')

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

function buildFileRowView(item, data) {
  const canMutateOwnTab = data.activeTab !== '分享给我' && data.activeTab !== '四项激励'
  const showDownload = item.canDownload !== false
  const showReplace = Boolean(data.canReplace && canMutateOwnTab)
  const showDelete = Boolean(data.canDelete && canMutateOwnTab)
  const showShare = Boolean(data.canShare && canMutateOwnTab)
  // 【需求A】行内按钮全部收进「⋯」半屏面板;打开保留为整行首选操作(点行=打开)
  const sheetActions = [{ key: 'open', label: '打开' }]
  if (showDownload) sheetActions.push({ key: 'download', label: '下载' })
  if (showShare) sheetActions.push({ key: 'share', label: '分享' })
  if (showReplace) sheetActions.push({ key: 'replace', label: '替换' })
  if (showDelete) sheetActions.push({ key: 'delete', label: '删除', danger: true })
  return {
    ...item,
    typeText: item.type || 'FILE',
    nameText: item.name || '未命名文件',
    folderText: item.folder || '未分类',
    sizeText: item.size || '-',
    uploaderNameText: item.uploaderName || '—',
    updatedAtText: item.updatedAt || '-',
    // 【需求A】meta 拆两段: 行内只放 大小·时间(与名字同区), 文件夹·上传人放次要位
    metaBrief: `${item.size || '-'} · ${item.updatedAt || '-'}`,
    metaExtra: `${item.folder || '未分类'} · ${item.uploaderName || '—'}`,
    bucket: parseUpdatedBucket(item.updatedAt),
    sheetActions,
    replaceLoading: String(data.replacingId || '') === String(item.id || '')
  }
}

/** 演示模式判定（页面模块级使用，app 未就绪时按非演示处理） */
function isDemoMode() {
  const app = typeof getApp === 'function' && getApp()
  return Boolean(app && app.globalData && app.globalData.demoMode)
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
  const showUploadAction = Boolean(data.canUpload) && (data.activeTab === '公共' || data.activeTab === '私人')
  // 【需求A】时间分组(今天/本周/更早),每组带 flatIndex 锚回 filteredFiles 供既有处理器复用
  const fileGroups = FILE_GROUP_ORDER.map((label) => {
    const groupFiles = files.filter((item) => item.bucket === label)
    return { key: label, label, count: groupFiles.length, files: groupFiles }
  }).filter((group) => group.count > 0)
  return {
    showUploadAction,
    tabs,
    folderTitle: data.activeFolderName || '常用文件夹',
    folderCountText: `${folders.length} 个文件夹`,
    showFolderList: !hasActiveFolder,
    showClearFolder: hasActiveFolder,
    breadcrumbText: hasActiveFolder ? `全部文件 › ${data.activeFolderName}` : '',
    listTitle: hasActiveFolder ? '' : '最近文件',
    folders,
    // 【PR-04】兜底文件夹卡片（客户文档/团队共享）仅演示模式显示；真实模式空就是空
    showFallbackFolders: !folders.length && isDemoMode(),
    showManageFolders: hasManageFolders,
    showManageEmpty: !hasManageFolders,
    fileGroups,
    files,
    fileCountText: `${files.length} 个文件`,
    showFileList: files.length > 0,
    showEmpty: files.length === 0 && !data.previewVideoPath,
    aclRows: data.aclRows || [],
    showAclRows: Boolean(data.aclRows && data.aclRows.length)
  }
}

Page({
  data: { keyword: '', activeTab: '公共', activeFolderId: '', activeFolderName: '', tabs: ['公共', '私人', '四项激励', '分享给我'], tabCounts: { 公共: 0, 私人: 0, 四项激励: 0, 分享给我: 0 }, folders: [], files: [], filteredFiles: [], capacity: { used: '-', total: '-', percent: 0, hint: '存储统计加载中' }, canUpload: false, canReplace: false, canDelete: false, canShare: false, canManageFolder: false, canViewFolderAcl: false, canDevUploadSmoke: false, uploading: false, replacingId: '', loaded: false, aclVisible: false, aclLoading: false, aclFolderName: '', aclRows: [], previewing: false, previewVideoPath: '', actionSheet: { visible: false, index: -1, name: '', actions: [] }, fileView: buildFileView({ tabs: ['公共', '私人', '四项激励', '分享给我'], tabCounts: { 公共: 0, 私人: 0, 四项激励: 0, 分享给我: 0 }, folders: [], filteredFiles: [], activeTab: '公共', activeFolderName: '', previewVideoPath: '' }) },

  setFileData(patch) {
    const nextData = { ...this.data, ...patch }
    this.setData({ ...patch, fileView: buildFileView(nextData) })
  },

  onLoad() {
    if (!requireLogin()) return
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
  },

  onShow() {
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

  async loadFiles() {
    if (getApp().globalData.demoMode) {
      const folders = readDemoFolders()
      const files = demoFiles
      this.setFileData({ folders, files, filteredFiles: this.filterFiles(files), capacity: demoCapacity, tabCounts: this.demoTabCounts(files), loaded: true })
      return
    }
    // 【M20】请求序号守卫：Tab/筛选快速切换时，慢返回的旧响应直接丢弃，防止覆盖新列表
    const requestSeq = (this._filesReqSeq = (this._filesReqSeq || 0) + 1)
    this._filesLoading = true
    let folders = []
    let files = []
    let capacity = this.data.capacity
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
      const rawFolders = folderResponse?.data || folderResponse?.records || []
      // 【体检 P1 2026-10-12】树只映射顶层会丢子夹(公共子文件夹不可见)——递归展平为芯片列表,
      // 子夹名前加「·」标识层级, 点按仍按各自 id 进入
      const flattenFolders = (list, depth, out) => {
        (list || []).forEach((f) => {
          out.push({ id: f.id, name: (depth > 0 ? '· ' : '') + (f.name || f.folderName || '未命名文件夹') })
          if (f.children && f.children.length) flattenFolders(f.children, depth + 1, out)
        })
        return out
      }
      folders = (this.data.activeTab === '公共' || this.data.activeTab === '私人')
        ? flattenFolders(rawFolders.filter((item) => item.folderType === folderScope), 0, [])
        : []
      const fileResponse = this.data.activeTab === '分享给我'
        ? await getSharedToMe(this.buildFileListParams())
        : await getFileList(this.buildFileListParams())
      files = pageRecords(fileResponse)
    } catch (error) {
      folders = []
      files = []
      // 【PR-04】真实模式失败：报错 + 空列表（空态样式），不再显示演示数据
      // 【M20】已被更新请求取代的旧失败不再弹错（新请求的结果会接管界面）
      if (requestSeq === this._filesReqSeq) showErrorWithRetry(error, '文件加载失败，请重试', () => this.loadFiles())
      capacity = { used: '-', total: '-', percent: 0, hint: '存储统计暂未同步' }
    } finally {
      this._filesLoading = false
    }
    if (requestSeq !== this._filesReqSeq) return
    const normalizedFiles = files.map((item, index) => this.normalizeFile(item, index))
    this.setFileData({ folders, files: normalizedFiles, filteredFiles: this.filterFiles(normalizedFiles), capacity, loaded: true })
    this.loadTabCounts()
  },

  activeFileScope() {
    // 【对齐 PC 2026-09-20】公共=public / 私人=private / 四项激励=incentive, 分享给我走独立接口
    if (this.data.activeTab === '公共') return 'public'
    if (this.data.activeTab === '四项激励') return 'incentive'
    return 'private'
  },

  buildFileListParams(extra = {}) {
    const params = {
      current: 1,
      size: 50,
      keyword: this.data.keyword.trim() || undefined,
      ...extra
    }
    if (this.data.activeTab !== '分享给我') params.scope = this.activeFileScope()
    if (this.data.activeFolderId && (this.data.activeTab === '公共' || this.data.activeTab === '私人')) params.folderId = this.data.activeFolderId
    return params
  },

  demoTabCounts(files = demoFiles) {
    return {
      公共: 0,
      私人: files.filter((item) => item.folder !== '激励资料').length,
      四项激励: files.filter((item) => item.folder === '激励资料').length,
      分享给我: 0
    }
  },

  async loadTabCounts() {
    if (getApp().globalData.demoMode) {
      this.setFileData({ tabCounts: this.demoTabCounts(this.data.files.length ? this.data.files : demoFiles) })
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
          四项激励: pageTotal(incentiveFiles),
          分享给我: Number((sharedCount && (sharedCount.data ?? sharedCount)) || 0)
        }
      })
    } catch (error) {
      showError(error, '文件数量加载失败')
    }
  },

  normalizeFile(item, index) {
    const name = item.name || item.fileName || '未命名文件'
    const extension = name.includes('.') ? name.split('.').pop().toUpperCase() : 'FILE'
    return {
      id: item.id || '',
      name,
      type: extension,
      size: typeof item.fileSize === 'number' ? bytesToSize(item.fileSize) : (item.fileSize || item.size || '-'),
      folder: item.folderName || '未分类',
      uploaderName: item.uploaderName || item.uploader || item.createByName || '—',
      updatedAt: item.updateTime || item.updatedAt || '',
      ossKey: item.ossKey || item.fileKey || item.path || '',
      fileUrl: item.fileUrl || item.url || '',
      canDownload: normalizeDownloadable(item),
      tone: extension === 'PDF' ? 'red' : extension === 'XLS' ? 'green' : 'blue',
      demoId: item.id || `file-${index}`
    }
  },

  filterFiles(files = this.data.files) {
    const keyword = this.data.keyword.trim().toLowerCase()
    // 真实模式列表已按 scope 服务端过滤(onTabTap→loadFiles 带 scope 重拉), 本地不得按文件夹文字二次筛选
    // —— 真实激励文件 folder 多为'未分类', 文字法会把整表筛空; 文字法仅演示模式(无服务端)使用
    const useLabelScope = Boolean(getApp().globalData.demoMode)
    return files.filter(item => {
      const matchesTab = !useLabelScope ? true : (this.data.activeTab === '四项激励' ? item.folder === '激励资料' : item.folder !== '激励资料')
      return matchesTab && (!keyword || `${item.name}${item.folder}`.toLowerCase().includes(keyword))
    })
  },

  onKeywordInput(event) { const keyword = event.detail.value; this.setFileData({ keyword, filteredFiles: this.filterFiles(this.data.files) }) },
  searchFiles() {
    if (getApp().globalData.demoMode) {
      this.setFileData({ filteredFiles: this.filterFiles(this.data.files) })
      return
    }
    this.loadFiles()
  },
  onTabTap(event) {
    const activeTab = event.currentTarget.dataset.tab
    this.setFileData({ activeTab, activeFolderId: '', activeFolderName: '', filteredFiles: this.filterFiles(this.data.files) })
    if (!getApp().globalData.demoMode) this.loadFiles()
  },
  openFolder(event) {
    const rawFolderId = event.currentTarget.dataset.id
    const folderId = rawFolderId && rawFolderId !== 'undefined' && rawFolderId !== 'null' ? rawFolderId : ''
    const folderName = event.currentTarget.dataset.name || ''
    if (getApp().globalData.demoMode) {
      const files = demoFiles.filter((item) => item.folder === folderName)
      return this.setFileData({ activeFolderId: folderId, activeFolderName: folderName, files, filteredFiles: this.filterFiles(files) })
    }
    this.setFileData({ activeFolderId: folderId, activeFolderName: folderName })
    getFileList(this.buildFileListParams({ folderId })).then((response) => {
      const records = response?.data?.records || response?.records || []
      const files = records.map((item, index) => this.normalizeFile(item, index))
      this.setFileData({ files, filteredFiles: this.filterFiles(files) })
    }).catch((error) => showError(error, '文件夹加载失败'))
  },
  clearFolder() { this.loadFiles() },
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
  openFolderEdit(event) { wx.navigateTo({ url: `/pages/files/folder-form/index?id=${event.currentTarget.dataset.id}&name=${encodeURIComponent(event.currentTarget.dataset.name || '')}` }) },
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
    const item = this.data.filteredFiles[event.currentTarget.dataset.index]
    if (!item || ((!item.id || String(item.id).startsWith('file-')) && !item.ossKey && !item.fileUrl)) return wx.showToast({ title: '演示文件暂不可预览', icon: 'none' })
    wx.navigateTo({ url: `/pages/files/detail/index?id=${item.id || ''}&path=${encodeURIComponent(item.ossKey || item.fileUrl || '')}&name=${encodeURIComponent(item.name)}&type=${item.type}&size=${encodeURIComponent(item.size)}&folder=${encodeURIComponent(item.folder)}&uploaderName=${encodeURIComponent(item.uploaderName || '')}&updatedAt=${encodeURIComponent(item.updatedAt || '')}` })
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
    if (!item.canDownload) return wx.showToast({ title: '该文件夹已关闭下载', icon: 'none' })
    this.fetchFile(item, 'download')
  },
  removeFile(event) {
    if (!this.data.canDelete) return wx.showToast({ title: '没有文件删除权限', icon: 'none' })
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
    if (!this.data.canReplace || this.data.activeTab === '分享给我') return wx.showToast({ title: '没有文件替换权限', icon: 'none' })
    const item = this.findFile(event)
    if (!item || !item.id || String(item.id).startsWith('file-')) return wx.showToast({ title: '演示文件暂不可替换', icon: 'none' })
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
    if (!this.data.canShare || this.data.activeTab === '分享给我') return
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
