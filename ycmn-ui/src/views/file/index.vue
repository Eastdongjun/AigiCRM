<template>
  <div class="file-manage">
    <!-- 标题行 -->
    <div class="page-header">
      <h2>文件管理</h2>
      <div class="header-actions">
        <el-input v-model="keyword" placeholder="搜索文件名称..." style="width: 220px" clearable prefix-icon="Search" @keyup.enter="loadFiles" />
        <el-button v-if="activeTab !== 'incentive' && activeTab !== 'shared_to_me'" type="primary" :icon="Upload" @click="uploadVisible = true">上传文件</el-button>
      </div>
    </div>

    <!-- 主卡片 -->
    <el-card shadow="never" class="main-card">
      <!-- Tab栏 -->
      <div class="tab-bar">
        <div
          v-for="tab in tabs"
          :key="tab.value"
          class="tab-item"
          :class="{ active: activeTab === tab.value }"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}
          <span class="tab-num">{{ tab.count }}</span>
        </div>
      </div>

      <div class="content-body">
        <!-- 左侧: 文件夹树（四项激励和分享给我Tab不显示） -->
        <template v-if="activeTab !== 'incentive' && activeTab !== 'shared_to_me'">
          <div class="folder-panel">
            <h4 class="folder-title">文件夹</h4>

            <!-- 全部文件 -->
            <div class="folder-root" :class="{ active: !selectedFolderId }" @click="selectedFolderId = null; loadFiles()">
              <el-icon color="#409eff"><FolderOpened /></el-icon>
              <span class="folder-root-name">全部文件</span>
              <span class="folder-count-badge">{{ fileTotal }}</span>
            </div>

            <!-- 文件夹列表 -->
            <div
              v-for="folder in currentFolders"
              :key="folder.id"
              class="folder-item"
              :class="{ active: selectedFolderId === folder.id }"
              @click="selectFolder(folder)"
            >
              <el-icon><Folder /></el-icon>
              <span class="folder-name">{{ folder.folderName }}</span>
              <span class="folder-count">{{ folder.fileCount || 0 }}</span>
              <span v-if="canManageFolder(folder)" class="folder-actions">
                <el-tooltip content="重命名" placement="top">
                  <el-icon class="folder-act" @click.stop="openRenameFolder(folder)"><EditPen /></el-icon>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-icon class="folder-act folder-act-danger" @click.stop="handleDeleteFolder(folder)"><Delete /></el-icon>
                </el-tooltip>
              </span>
            </div>

            <!-- 新建文件夹（公共Tab仅管理员，私人Tab仅自己） -->
            <el-button v-if="canCreateFolder" class="new-folder-btn" @click="createFolderVisible = true">
              <el-icon><FolderAdd /></el-icon> 新建文件夹
            </el-button>

            <!-- 存储空间（仅私人Tab显示） -->
            <div v-if="activeTab === 'private'" class="storage-info">
              <div class="storage-label">个人存储空间</div>
              <el-progress :percentage="storagePercent" :stroke-width="4" :show-text="false" />
              <div class="storage-text">{{ storageUsed }} / {{ storageQuota }}</div>
            </div>
          </div>

          <!-- 分隔线 -->
          <div class="divider"></div>
        </template>

        <!-- 右侧: 文件列表 -->
        <div class="file-list-panel">
          <el-table :data="files" style="width: 100%" size="small">
            <el-table-column label="文件名称" min-width="100" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="file-name-cell">
                  <span class="file-icon" :class="'icon-' + getFileExt(row.fileName)">{{ getFileExtLabel(row.fileName) }}</span>
                  <span>{{ row.fileName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="文件大小" min-width="80" align="center">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="类型" min-width="60" align="center">
              <template #default="{ row }">
                <span class="type-tag" :class="'type-' + getFileExt(row.fileName)">{{ getFileType(row.fileName) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="uploaderName" label="上传者" min-width="70" align="center" />
            <el-table-column label="更新时间" min-width="95" align="center">
              <template #default="{ row }">{{ (row.createdAt || '').substring(0, 10) }}</template>
            </el-table-column>
            <el-table-column label="操作" min-width="100" align="center">
              <template #default="{ row }">
                <div class="action-icons">
                  <el-tooltip content="下载" placement="top">
                    <el-icon class="act-icon" @click.stop="handleDownload(row)"><Download /></el-icon>
                  </el-tooltip>
                  <el-tooltip v-if="activeTab !== 'public' && activeTab !== 'shared_to_me'" content="分享" placement="top">
                    <el-icon class="act-icon" @click.stop="openShareDialog(row)"><Share /></el-icon>
                  </el-tooltip>
                  <el-tooltip v-if="canDelete(row)" content="删除" placement="top">
                    <el-icon class="act-icon act-danger" @click.stop="handleDelete(row)"><Delete /></el-icon>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="file-footer">
            <span class="file-total">共 {{ fileTotal }} 个文件</span>
            <el-pagination
              v-model:current-page="query.current"
              v-model:page-size="query.size"
              :total="fileTotal"
              :page-sizes="[10, 20, 50]"
              layout="prev, pager, next, sizes"
              small
              @change="loadFiles"
            />
          </div>
        </div>
      </div>
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传文件" width="500px">
      <el-upload
        drag
        :action="'/api/file/upload'"
        :headers="{ Authorization: 'Bearer ' + token }"
        :data="{ folderId: selectedFolderId, scope: activeTab }"
        :on-success="handleUploadSuccess"
        multiple
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            {{ activeTab === 'public' ? '公共文件所有人可见' : '私人文件仅自己可见，占用个人存储配额' }}
          </div>
        </template>
      </el-upload>
    </el-dialog>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="createFolderVisible" title="新建文件夹" width="400px">
      <el-form>
        <el-form-item label="文件夹名称">
          <el-input v-model="newFolderName" placeholder="请输入文件夹名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateFolder">创建</el-button>
      </template>
    </el-dialog>

    <!-- 重命名文件夹对话框 -->
    <el-dialog v-model="renameFolderVisible" title="重命名文件夹" width="400px">
      <el-form>
        <el-form-item label="文件夹名称">
          <el-input v-model="renameFolderName" placeholder="请输入文件夹名称" @keyup.enter="handleRenameFolder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameFolderVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRenameFolder">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分享文件对话框 -->
    <el-dialog v-model="shareDialogVisible" title="分享文件" width="450px">
      <p style="margin-bottom:12px;color:#999;font-size:13px">
        分享 <b>{{ shareFileTarget?.fileName }}</b> 给其他用户（只读权限）
      </p>
      <el-select v-model="shareUserIds" multiple filterable placeholder="选择用户..." style="width:100%">
        <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmShare">确认分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getFolderTree, getFileList, createFolder, renameFolder, deleteFolder, deleteFile, getStorageInfo, shareFile, getSharedToMe, getSharedToMeCount } from '@/api/file'
import { getSimpleUserList } from '@/api/customer'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const activeTab = ref('public')
const files = ref([])
const fileTotal = ref(0)
const keyword = ref('')
const selectedFolderId = ref(null)
const uploadVisible = ref(false)
const createFolderVisible = ref(false)
const newFolderName = ref('')
const renameFolderVisible = ref(false)
const renameFolderName = ref('')
const renameFolderTarget = ref(null)
const storageUsed = ref('0MB')
const storageQuota = ref('10GB')
const storagePercent = ref(0)
const token = localStorage.getItem('token')
const allFolders = ref([])

// Tab 文件计数
const publicCount = ref(0)
const privateCount = ref(0)
const incentiveCount = ref(0)
const sharedToMeCount = ref(0)

// 分享相关
const shareDialogVisible = ref(false)
const shareFileTarget = ref(null)
const shareUserIds = ref([])
const allUsers = ref([])

const query = ref({ current: 1, size: 10 })

const tabs = computed(() => [
  { label: '公共', value: 'public', count: publicCount.value },
  { label: '私人', value: 'private', count: privateCount.value },
  { label: '四项激励', value: 'incentive', count: incentiveCount.value },
  { label: '分享给我', value: 'shared_to_me', count: sharedToMeCount.value }
])

const isAdmin = computed(() => userStore.roles?.includes('admin'))

// 当前 Tab 下的文件夹
const currentFolders = computed(() => {
  return allFolders.value.filter(f => f.folderType === activeTab.value)
})

// 公共文件夹仅管理员可创建，私人文件夹任何人可创建
const canCreateFolder = computed(() => {
  if (activeTab.value === 'public') return isAdmin.value
  if (activeTab.value === 'private') return true
  return false
})

// 文件夹管理权限（重命名/删除）：公共文件夹仅管理员，私人文件夹仅所有者本人
const canManageFolder = (folder) => {
  if (folder.folderType === 'public') return isAdmin.value
  if (folder.folderType === 'private') return folder.ownerId === userStore.userId
  return false
}

// 删除权限：公共文件仅管理员，私人文件仅上传者本人，激励/分享不可删
const canDelete = (row) => {
  if (activeTab.value === 'incentive' || activeTab.value === 'shared_to_me') return false
  if (isAdmin.value) return true
  if (activeTab.value === 'public' || row.fileScope === 'public') return false
  return row.uploaderId === userStore.userId
}

const switchTab = (tab) => {
  activeTab.value = tab
  selectedFolderId.value = null
  query.value.current = 1
  if (tab !== 'incentive' && tab !== 'shared_to_me') loadFolders()
  loadFiles()
}

const selectFolder = (folder) => {
  selectedFolderId.value = folder.id
  loadFiles()
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

const loadFiles = async () => {
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
  } catch (e) {}
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

// === 操作按钮 ===

const handleDownload = (row) => {
  const url = row.ossUrl || `/api/file/download/${row.ossKey}`
  fetch(url, { headers: { Authorization: `Bearer ${token}` } })
    .then(res => res.blob())
    .then(blob => {
      const blobUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = blobUrl
      link.download = row.fileName
      link.click()
      URL.revokeObjectURL(blobUrl)
    })
}

const openShareDialog = (row) => {
  shareFileTarget.value = row
  shareUserIds.value = []
  shareDialogVisible.value = true
  if (allUsers.value.length === 0) loadAllUsers()
}

const loadAllUsers = async () => {
  try {
    const res = await getSimpleUserList()
    allUsers.value = res.data || []
  } catch (e) {}
}

const confirmShare = async () => {
  if (!shareUserIds.value.length) return
  try {
    await shareFile(shareFileTarget.value.id, shareUserIds.value)
    ElMessage.success('文件已分享')
    shareDialogVisible.value = false
    loadTabCounts()
  } catch (e) {}
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除 "${row.fileName}"？`, '删除确认', { type: 'warning' })
    await deleteFile(row.id)
    ElMessage.success('删除成功')
    loadFiles()
    loadFolders()
    loadTabCounts()
    loadStorage()
  } catch (e) {}
}

const handleUploadSuccess = () => {
  ElMessage.success('上传成功')
  uploadVisible.value = false
  loadFiles()
  loadFolders()
  loadTabCounts()
  loadStorage()
}

const handleCreateFolder = async () => {
  if (!newFolderName.value.trim()) return
  try {
    await createFolder({
      folderName: newFolderName.value,
      folderType: activeTab.value,
      parentId: selectedFolderId.value || 0
    })
    ElMessage.success('文件夹创建成功')
    createFolderVisible.value = false
    newFolderName.value = ''
    loadFolders()
  } catch (e) {}
}

const openRenameFolder = (folder) => {
  renameFolderTarget.value = folder
  renameFolderName.value = folder.folderName
  renameFolderVisible.value = true
}

const handleRenameFolder = async () => {
  const name = renameFolderName.value.trim()
  if (!name) return
  try {
    await renameFolder(renameFolderTarget.value.id, name)
    ElMessage.success('重命名成功')
    renameFolderVisible.value = false
    loadFolders()
  } catch (e) {}
}

const handleDeleteFolder = async (folder) => {
  try {
    await ElMessageBox.confirm(`确定删除文件夹 "${folder.folderName}"？`, '删除确认', { type: 'warning' })
    await deleteFolder(folder.id)
    ElMessage.success('删除成功')
    if (selectedFolderId.value === folder.id) {
      selectedFolderId.value = null
    }
    loadFolders()
    loadFiles()
  } catch (e) {}
}

// === 工具函数 ===

const formatSize = (bytes) => {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}

const getFileExt = (name) => {
  if (!name) return 'other'
  const ext = name.split('.').pop()?.toLowerCase()
  if (['doc', 'docx'].includes(ext)) return 'doc'
  if (['xls', 'xlsx'].includes(ext)) return 'xls'
  if (ext === 'pdf') return 'pdf'
  if (['ppt', 'pptx'].includes(ext)) return 'ppt'
  if (['png', 'jpg', 'jpeg', 'gif'].includes(ext)) return 'img'
  return 'other'
}

const getFileExtLabel = (name) => {
  const map = { doc: 'W', xls: 'X', pdf: 'P', ppt: 'P', img: 'I', other: 'F' }
  return map[getFileExt(name)] || 'F'
}

const getFileType = (name) => {
  if (!name) return '—'
  return name.split('.').pop()?.toUpperCase() || '—'
}

onMounted(() => {
  loadFolders()
  loadFiles()
  loadTabCounts()
  loadStorage()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 18px; font-weight: 600; }
.header-actions { display: flex; gap: 10px; }

/* 主卡片 */
.main-card { border-radius: 10px; }
.main-card :deep(.el-card__body) { padding: 0; }

/* Tab 栏 */
.tab-bar {
  display: flex;
  gap: 24px;
  padding: 16px 24px 0;
  border-bottom: 1px solid #ebeef5;
}
.tab-item {
  padding-bottom: 12px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color 0.2s;
}
.tab-item:hover { color: #409eff; }
.tab-item.active {
  color: #409eff;
  font-weight: 500;
  border-bottom-color: #409eff;
}
.tab-num {
  margin-left: 4px;
  color: #409eff;
  font-weight: 500;
}
.tab-item:not(.active) .tab-num { color: #999; }

/* 内容区 */
.content-body {
  display: flex;
  min-height: calc(100vh - 200px);
}

/* 左侧文件夹面板 */
.folder-panel {
  width: 240px;
  flex-shrink: 0;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}
.folder-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
}
.folder-root {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.folder-root:hover { background: #f5f7fa; }
.folder-root.active { background: #ecf5ff; }
.folder-root-name { flex: 1; font-size: 13px; color: #409eff; font-weight: 500; }
.folder-count-badge {
  background: #409eff;
  color: #fff;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 10px;
  font-weight: 600;
}
.folder-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #333;
  transition: background 0.15s;
}
.folder-item:hover { background: #f5f7fa; }
.folder-item.active { background: #ecf5ff; color: #409eff; }
.folder-name { flex: 1; }
.folder-count {
  background: #e6e8eb;
  color: #666;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 500;
}
.folder-item.active .folder-count { background: #d0e4ff; color: #409eff; }
.folder-actions { display: none; align-items: center; gap: 8px; }
.folder-item:hover .folder-actions { display: inline-flex; }
.folder-item:hover .folder-count { display: none; }
.folder-act {
  font-size: 14px;
  color: #999;
  cursor: pointer;
  transition: color 0.15s;
}
.folder-act:hover { color: #409eff; }
.folder-act-danger:hover { color: #f56c6c; }
.new-folder-btn {
  width: 100%;
  margin-top: 16px;
  border-style: dashed;
}
.storage-info { margin-top: auto; padding-top: 16px; }
.storage-label { font-size: 12px; color: #999; margin-bottom: 4px; }
.storage-text { font-size: 11px; color: #999; margin-top: 4px; }
.divider {
  width: 1px;
  background: #ebeef5;
  flex-shrink: 0;
}

/* 右侧文件列表 */
.file-list-panel {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
}
.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.icon-doc { background: #4a90d9; }
.icon-xls { background: #34a853; }
.icon-pdf { background: #ea4335; }
.icon-ppt { background: #e6a23c; }
.icon-img { background: #9b59b6; }
.icon-other { background: #909399; }
.type-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 3px;
}
.type-doc { color: #4a90d9; background: #eaf2fc; }
.type-xls { color: #34a853; background: #e8f5e9; }
.type-pdf { color: #ea4335; background: #fce8e6; }
.type-ppt { color: #e6a23c; background: #fdf6ec; }
.type-img { color: #9b59b6; background: #f3e8ff; }
.type-other { color: #909399; background: #f4f4f5; }

/* 操作图标 */
.action-icons {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.act-icon {
  font-size: 16px;
  color: #999;
  cursor: pointer;
  transition: color 0.15s;
}
.act-icon:hover { color: #409eff; }
.act-danger:hover { color: #f56c6c; }
.file-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
}
.file-total { font-size: 12px; color: #999; }
</style>
