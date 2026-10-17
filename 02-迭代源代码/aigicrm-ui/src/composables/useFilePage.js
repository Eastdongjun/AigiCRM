// 文件管理页共享态（C4 拆组件 2026-08-09）
// 页面级 provide/inject（per-route，避免跨用户/跨页状态残留）：file/index.vue 实例化一次并 provide，
// 文件夹面板/文件表格与 5 个弹窗 inject 同一上下文。
// 共享内容：Tab/选中文件夹/权限态 + 列表与计数数据 + 加载刷新动作 + 弹窗显隐与目标 + 下载工具。
// 弹窗内部工作态（待上传列表、表单值、ACL 列表等）留在各弹窗组件内，仅注入共享态与刷新回调。
import { ref, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getFolderTree, getFileList, deleteFile, deleteFolder,
  getStorageInfo, getSharedToMe, getSharedToMeCount, getFolderPermissions,
} from '@/api/file'
import { useUserStore } from '@/store/user'
import { downloadFile } from '@/utils/download'
import { confirmAction } from '@/utils/confirm'
import { getToken, getDownloadToken, refreshDownloadToken } from '@/utils/token'
import { formatSize } from '@/utils/format'
import { previewKindOf } from '@/utils/media-type'

export const FilePageKey = Symbol('filePage')

/** 子组件注入父级 file/index.vue 提供的共享上下文 */
export function injectFilePage() {
  const ctx = inject(FilePageKey)
  if (!ctx) throw new Error('injectFilePage 必须在 file/index.vue 的组件内使用')
  return ctx
}

export function useFilePage() {
  const userStore = useUserStore()

  // ---- 页面导航态 ----
  const activeTab = ref('public')
  const selectedFolderId = ref(null)
  const keyword = ref('')
  // 【2026-09-23 UI】默认每页 30:整页高度表格按 10 条只填三分之一,与客户列表口径一致
  const query = ref({ current: 1, size: 30 })

  // ---- 列表/数据态 ----
  const files = ref([])
  const fileTotal = ref(0)
  const allFolders = ref([])
  const folderPerms = ref([])
  const folderDownloadable = ref(1)
  const publicCount = ref(0)
  const privateCount = ref(0)
  const incentiveCount = ref(0)
  const sharedToMeCount = ref(0)
  const storageUsed = ref('0MB')
  const storageQuota = ref('10GB')
  const storagePercent = ref(0)

  // ---- 弹窗显隐与目标（从多个入口打开，放共享态）----
  const uploadVisible = ref(false)
  const createFolderVisible = ref(false)
  const renameFolderVisible = ref(false)
  const renameFolderTarget = ref(null)
  const shareDialogVisible = ref(false)
  const shareFileTarget = ref(null)
  const aclDialogVisible = ref(false)
  const aclFolder = ref(null)
  const previewVisible = ref(false)
  const previewUrl = ref('')
  const previewType = ref('file')
  const previewName = ref('')
  const previewSize = ref(0)

  const isAdmin = computed(() => userStore.isAdmin())

  const tabs = computed(() => [
    { label: '公共', value: 'public', count: publicCount.value },
    { label: '私人', value: 'private', count: privateCount.value },
    { label: '四项激励', value: 'incentive', count: incentiveCount.value },
    { label: '分享给我', value: 'shared_to_me', count: sharedToMeCount.value }
  ])

  // 当前 Tab 下的文件夹
  const currentFolders = computed(() => {
    return allFolders.value.filter(f => f.folderType === activeTab.value)
  })

  // 新建文件夹：公共/私人 Tab 按当前文件夹 upload 权限显示（不再仅管理员/本人写死）。
  // 公共根目录对齐后端"仅管理员可建夹"门槛（非管理员公共根 upload=传文件权限,不授予建夹）；
  // 子夹按当前文件夹 upload 权限（ACL 判定）。
  const canCreateFolder = computed(() => {
    if (activeTab.value === 'incentive' || activeTab.value === 'shared_to_me') return false
    if (activeTab.value === 'public' && selectedFolderId.value == null) return isAdmin.value
    return folderPerms.value.includes('upload')
  })

  // 当前文件夹权限加载：
  //  - 激励/分享给我 Tab 无文件夹树：只读 + 可下载（保持原有行为）
  //  - 根目录（全部文件）无 ACL：公共根目录保持"所有人可上传/下载、管理操作仅管理员"，
  //    私人根目录本人全量
  //  - 选中文件夹：调 permissions 接口（管理员/本人/ACL 角色/公共兜底由后端判定）
  const loadFolderPerms = async () => {
    if (activeTab.value === 'incentive' || activeTab.value === 'shared_to_me') {
      folderPerms.value = ['view', 'download']
      folderDownloadable.value = 1
      return
    }
    const id = selectedFolderId.value
    if (id == null) {
      folderPerms.value = isAdmin.value
        ? ['view', 'upload', 'edit', 'delete', 'share', 'download']
        : activeTab.value === 'public'
          ? ['view', 'upload', 'download']
          : ['view', 'upload', 'edit', 'delete', 'share', 'download']
      folderDownloadable.value = 1
      return
    }
    try {
      const res = await getFolderPermissions(id)
      folderPerms.value = res.data?.perms || []
      folderDownloadable.value = res.data?.downloadable ?? 1
    } catch (e) {}
  }

  const loadFolders = async () => {
    try {
      const res = await getFolderTree(activeTab.value)
      const raw = res.data || []
      const flat = []
      const flatten = (list) => {
        for (const item of list) {
          flat.push(item)
          if (item.children) flatten(item.children)
        }
      }
      flatten(raw)
      allFolders.value = flat
    } catch (e) {}
  }

  // 【C12/D13 2026-10-08 二轮】文件列表补加载态：fetch 期间 FileTable v-loading，不再空白易误读为"暂无数据"
  const filesLoading = ref(false)
  const loadFiles = async () => {
    filesLoading.value = true
    try {
      if (activeTab.value === 'shared_to_me') {
        const res = await getSharedToMe({ ...query.value, keyword: keyword.value })
        files.value = res.data?.records || []
        fileTotal.value = res.data?.total || 0
        return
      }
      const params = {
        ...query.value,
        keyword: keyword.value,
        folderId: selectedFolderId.value,
        scope: activeTab.value
      }
      const res = await getFileList(params)
      files.value = res.data?.records || []
      fileTotal.value = res.data?.total || 0
    } catch (e) {
      // 错误已由 request 拦截器提示
    } finally {
      filesLoading.value = false
    }
  }

  const loadTabCounts = async () => {
    try {
      const [pub, pri, inc, shared] = await Promise.all([
        getFileList({ current: 1, size: 1, scope: 'public' }),
        getFileList({ current: 1, size: 1, scope: 'private' }),
        getFileList({ current: 1, size: 1, scope: 'incentive' }),
        getSharedToMeCount()
      ])
      publicCount.value = pub.data?.total || 0
      privateCount.value = pri.data?.total || 0
      incentiveCount.value = inc.data?.total || 0
      sharedToMeCount.value = shared.data || 0
    } catch (e) {}
  }

  const loadStorage = async () => {
    try {
      const res = await getStorageInfo()
      storageUsed.value = res.data?.usedFormatted || '0MB'
      storageQuota.value = res.data?.quotaFormatted || '10GB'
      const used = res.data?.used || 0
      const quota = res.data?.quota || 1
      storagePercent.value = Math.round((used / quota) * 100)
    } catch (e) {}
  }

  // 各弹窗/操作成功后统一刷新（上传/删除/替换/分享等）
  const refreshAll = async () => {
    await Promise.allSettled([loadFiles(), loadFolders(), loadTabCounts(), loadStorage()])
  }

  // ---- 导航 ----
  const switchTab = (tab) => {
    activeTab.value = tab
    selectedFolderId.value = null
    query.value.current = 1
    if (tab !== 'incentive' && tab !== 'shared_to_me') loadFolders()
    loadFolderPerms()
    loadFiles()
  }

  const selectFolder = (folder) => {
    selectedFolderId.value = folder?.id ?? null
    query.value.current = 1
    loadFolderPerms()
    loadFiles()
  }

  // 关键字搜索回到第 1 页（分页 @change 仍用 loadFiles 保持当前页）
  const searchFiles = () => { query.value.current = 1; loadFiles() }

  // ---- 弹窗打开动作 ----
  const openUpload = () => { uploadVisible.value = true }
  const openCreateFolder = () => { createFolderVisible.value = true }
  const openRenameFolder = (folder) => {
    renameFolderTarget.value = folder
    renameFolderVisible.value = true
  }
  const openShareDialog = (row) => {
    shareFileTarget.value = row
    shareDialogVisible.value = true
  }
  const openAclDialog = (folder) => {
    aclFolder.value = folder
    aclDialogVisible.value = true
  }

  // ---- 下载 ----
  const appendToken = (url, token) => {
    if (!url || !token || url.startsWith('http')) return url
    return `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
  }

  const downloadUrlOf = (row, tokenValue = '') => {
    const url = row?.id ? `/api/file/download/${row.id}` : (row?.ossUrl || `/api/file/download/${row?.ossKey}`)
    return appendToken(url, tokenValue)
  }

  const handlePreview = async (row) => {
    if (!row) return
    await refreshDownloadToken()
    previewUrl.value = downloadUrlOf(row, getDownloadToken())
    previewName.value = row.fileName || '文件'
    previewSize.value = Number(row.fileSize) || 0
    // 【文件阅读器 2026-10-16】种类判定收口到 utils/media-type.previewKindOf（Word/Excel/PDF 等全部内嵌预览）；
    // 此前本页自持 getFileExt 四分类表与 FilePreview 口径分叉（mov 本页认视频、灯箱不认等）
    previewType.value = previewKindOf(row.fileName)
    previewVisible.value = true
  }

  const handleDownload = async (row) => {
    if (!row) return
    const url = downloadUrlOf(row)
    // 【C3 2026-10-04】token 改为下载时实时获取（对齐 FilePreview）：
    // 切换集团会重签 token 且不刷新页面（store/user.js switchGroup），此前组合式函数
    // 初始化时一次性捕获的 token 会一直是旧集团 JWT，切换后下载仍走旧集团上下文。
    // 【2026-10-17 客户口径】下载完成即用系统文件阅读器打开(与「眼睛」预览同款弹窗);
    // pptx 等浏览器渲染不了的格式,弹窗会给出"暂不支持"说明,文件本体已存本地。
    await downloadFile(url, { name: row.fileName, headers: { Authorization: `Bearer ${getToken() || ''}` } })
    handlePreview(row)
  }

  // ---- 删除（确认 + 刷新，含共享态回写）----
  const handleDelete = async (row) => {
    try {
      // 【UI 整改 2026-10-08】删除类确认走 confirmAction：红色确认钮 + 取消左置（统一 utils/confirm.js 约定）
      await confirmAction(`确定删除「${row.fileName}」？删除后不可恢复。`, '删除确认', { confirmText: '删除' })
      await deleteFile(row.id)
      ElMessage.success('删除成功')
      refreshAll()
    } catch (e) {}
  }

  const handleDeleteFolder = async (folder) => {
    try {
      // 【体检 2026-10-12】文案与后端行为对齐: deleteFolder 对非空夹(含文件/子夹)一律拒绝
      // ("文件夹内还有文件，请先清空后再删除"), 原文案承诺级联删除与实际相反
      await confirmAction(`确定删除文件夹「${folder.folderName}」？仅空文件夹可删除，夹内有文件或子文件夹时需先清空。`, '删除确认', { confirmText: '删除' })
      await deleteFolder(folder.id)
      ElMessage.success('删除成功')
      if (selectedFolderId.value === folder.id) {
        selectedFolderId.value = null
      }
      loadFolders()
      // 删除选中文件夹后回到根目录：刷新权限，避免 edit/delete 按钮状态过期
      loadFolderPerms()
      loadFiles()
    } catch (e) {}
  }

  // === 工具函数 ===
  // 【去重 2026-10-04】formatSize 统一提取到 utils/format.js（与人员管理页共用同一口径，
  // 契约见该文件头注释）；本页原内联实现即该统一口径，行为不变
  const getFileExt = (name) => {
    if (!name) return 'other'
    const ext = name.split('.').pop()?.toLowerCase()
    if (['doc', 'docx'].includes(ext)) return 'doc'
    if (['xls', 'xlsx'].includes(ext)) return 'xls'
    if (ext === 'pdf') return 'pdf'
    if (['ppt', 'pptx'].includes(ext)) return 'ppt'
    if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) return 'img'
    if (['mp4', 'webm', 'mov'].includes(ext)) return 'video'
    return 'other'
  }

  const getFileExtLabel = (name) => {
    const map = { doc: 'W', xls: 'X', pdf: 'P', ppt: 'P', img: 'I', video: 'V', other: 'F' }
    return map[getFileExt(name)] || 'F'
  }

  const getFileType = (name) => {
    if (!name) return '—'
    return name.split('.').pop()?.toUpperCase() || '—'
  }

  return {
    activeTab, selectedFolderId, keyword, query,
    files, fileTotal, filesLoading, allFolders, folderPerms, folderDownloadable,
    publicCount, privateCount, incentiveCount, sharedToMeCount,
    storageUsed, storageQuota, storagePercent,
    uploadVisible, createFolderVisible, renameFolderVisible, renameFolderTarget,
    shareDialogVisible, shareFileTarget, aclDialogVisible, aclFolder,
    previewVisible, previewUrl, previewType, previewName, previewSize,
    isAdmin, tabs, currentFolders, canCreateFolder,
    loadFolderPerms, loadFolders, loadFiles, loadTabCounts, loadStorage, refreshAll,
    switchTab, selectFolder, searchFiles,
    openUpload, openCreateFolder, openRenameFolder, openShareDialog, openAclDialog,
    handlePreview, handleDownload, handleDelete, handleDeleteFolder,
    formatSize, getFileExt, getFileExtLabel, getFileType,
  }
}
