<template>
  <div class="file-manage">
    <!-- 框1: 统计 -->
    <div class="section-box" style="display:flex;align-items:center;gap:32px">
      <div class="stat-item">
        <div class="stat-icon c-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
        <div><div class="s-num">{{ storage?.usedFormatted || '0' }}</div><div class="s-lbl">已使用</div></div>
      </div>
      <div class="stat-item">
        <div class="stat-icon c-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg></div>
        <div><div class="s-num">{{ storage?.quotaFormatted || '10GB' }}</div><div class="s-lbl">总容量</div></div>
      </div>
      <el-input v-model="keyword" placeholder="搜索文件名称..." style="width:220px;margin-left:auto" clearable prefix-icon="Search" @keyup.enter="searchFiles" />
      <el-button v-if="activeTab!=='incentive'&&activeTab!=='shared_to_me' && folderPerms.includes('upload')" type="primary" :icon="Upload" @click="uploadVisible=true">上传文件</el-button>
    </div>

    <!-- 框2: 主内容 -->
    <div class="section-box table-box">
      <el-card shadow="never" class="main-card" style="border:none;border-radius:0;box-shadow:none;flex:1;display:flex;flex-direction:column">
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
            <div class="folder-root" :class="{ active: !selectedFolderId }" @click="selectedFolderId = null; query.value.current = 1; loadFolderPerms(); loadFiles()">
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
              <span v-if="folder.perms?.includes('edit') || folder.perms?.includes('delete')" class="folder-actions">
                <el-tooltip v-if="folder.perms?.includes('edit')" content="重命名" placement="top">
                  <el-icon class="folder-act" @click.stop="openRenameFolder(folder)"><EditPen /></el-icon>
                </el-tooltip>
                <el-tooltip v-if="folder.perms?.includes('edit')" content="权限设置" placement="top">
                  <el-icon class="folder-act" @click.stop="openAclDialog(folder)"><Setting /></el-icon>
                </el-tooltip>
                <el-tooltip v-if="folder.perms?.includes('delete')" content="删除" placement="top">
                  <el-icon class="folder-act folder-act-danger" @click.stop="handleDeleteFolder(folder)"><Delete /></el-icon>
                </el-tooltip>
              </span>
            </div>

            <!-- 新建文件夹（公共/私人 Tab 按当前文件夹 upload 权限显示） -->
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
                  <el-tooltip v-if="folderPerms.includes('download') && folderDownloadable === 1" content="下载" placement="top">
                    <el-icon class="act-icon" @click.stop="handleDownload(row)"><Download /></el-icon>
                  </el-tooltip>
                  <el-tooltip v-if="activeTab !== 'public' && activeTab !== 'shared_to_me' && folderPerms.includes('share')" content="分享" placement="top">
                    <el-icon class="act-icon" @click.stop="openShareDialog(row)"><Share /></el-icon>
                  </el-tooltip>
                  <el-tooltip v-if="folderPerms.includes('edit')" content="替换" placement="top">
                    <el-icon class="act-icon" @click.stop="openReplaceFile(row)"><Refresh /></el-icon>
                  </el-tooltip>
                  <el-tooltip v-if="folderPerms.includes('delete')" content="删除" placement="top">
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
    </div>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传文件" width="500px" @close="pendingFiles = []">
      <div
        class="upload-dropzone"
        @click="pickFiles"
        @dragover.prevent
        @drop.prevent="handleDrop"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <div v-if="pendingFiles.length" class="upload-pending">
          <div v-for="(f, i) in pendingFiles" :key="i" class="upload-pending-item">
            <span>{{ f.name }}</span><span class="upload-pending-size">{{ formatSize(f.size) }}</span>
          </div>
        </div>
        <div class="el-upload__tip">
          {{ activeTab === 'public' ? '公共文件所有人可见' : '私人文件仅自己可见，占用个人存储配额' }}
        </div>
        <input ref="fileInputRef" type="file" multiple style="display:none" @change="handleFilesChosen" />
      </div>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!pendingFiles.length" @click="startUploads">
          开始上传{{ pendingFiles.length ? `（${pendingFiles.length} 个）` : '' }}
        </el-button>
      </template>
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

    <!-- 权限设置对话框 -->
    <el-dialog v-model="aclDialogVisible" :title="'权限设置：' + (aclFolder?.folderName || '')" width="560px">
      <div class="acl-block">
        <div class="acl-block-title">授权角色</div>
        <el-select v-model="aclSelectedRoles" multiple filterable placeholder="选择要授权的角色..." style="width:100%">
          <el-option v-for="r in allRoles" :key="r.id" :label="r.roleName" :value="r.id" />
        </el-select>
        <div class="acl-perms">
          <el-checkbox-group v-model="aclSelectedPerms">
            <el-checkbox v-for="p in PERM_OPTIONS" :key="p.value" :value="p.value">{{ p.label }}</el-checkbox>
          </el-checkbox-group>
        </div>
        <div>
          <el-button type="primary" :loading="aclSaving" :disabled="!aclSelectedRoles.length || !aclSelectedPerms.length" @click="saveAcl">
            保存授权
          </el-button>
        </div>
      </div>
      <el-divider />
      <div class="acl-block">
        <div class="acl-block-title">下载开关</div>
        <div class="acl-download-row">
          <span>允许下载该文件夹下的文件</span>
          <el-switch v-model="aclDownloadable" @change="handleDownloadableChange" />
        </div>
      </div>
      <el-divider />
      <div class="acl-block">
        <div class="acl-block-title">已授权角色</div>
        <div v-if="aclList.length" class="acl-list">
          <div v-for="row in aclList" :key="row.roleId" class="acl-item">
            <span class="acl-role">{{ row.roleName }}</span>
            <span class="acl-tags">
              <el-tag v-for="p in row.perms" :key="p" size="small" type="info">{{ permLabel(p) }}</el-tag>
            </span>
            <el-button type="danger" link size="small" @click="removeAcl(row)">移除</el-button>
          </div>
        </div>
        <el-empty v-else description="暂无授权角色" :image-size="50" />
      </div>
    </el-dialog>

    <!-- 替换文件隐藏选择器 -->
    <input ref="replaceFileInputRef" type="file" style="display:none" @change="handleReplaceChosen" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getFolderTree, getFileList, createFolder, renameFolder, deleteFolder, deleteFile, getStorageInfo, shareFile, getSharedToMe, getSharedToMeCount, getFolderAcl, saveFolderAcl, removeFolderAcl, setFolderDownloadable, getFolderPermissions, replaceFile } from '@/api/file'
import { getRoleList } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'
import { useUserStore } from '@/store/user'
import request from '@/utils/request'

const userStore = useUserStore()
const activeTab = ref('public')
const files = ref([])
const fileTotal = ref(0)
const keyword = ref('')
const selectedFolderId = ref(null)
// 自定义上传状态（不依赖 el-upload 内部回调链——其 on-success 在此环境不触发）
const fileInputRef = ref(null)
const pendingFiles = ref([])
const uploading = ref(false)
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

// 当前文件夹权限（按钮显隐控制）
const folderPerms = ref([])
const folderDownloadable = ref(1)

// 权限设置弹窗
const aclDialogVisible = ref(false)
const aclFolder = ref(null)
const aclList = ref([])
const allRoles = ref([])
const aclSelectedRoles = ref([])
const aclSelectedPerms = ref([])
const aclDownloadable = ref(true)
const aclSaving = ref(false)

// 文件替换
const replaceFileTarget = ref(null)
const replaceFileInputRef = ref(null)
const replacing = ref(false)

// 6 项权限选项
const PERM_OPTIONS = [
  { value: 'view', label: '查看' },
  { value: 'upload', label: '上传' },
  { value: 'edit', label: '编辑（重命名/替换）' },
  { value: 'delete', label: '删除' },
  { value: 'share', label: '分享' },
  { value: 'download', label: '下载' }
]
const permLabel = (v) => PERM_OPTIONS.find(p => p.value === v)?.label || v

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

// 新建文件夹：公共/私人 Tab 按当前文件夹 upload 权限显示（不再仅管理员/本人写死）
const canCreateFolder = computed(() => {
  if (activeTab.value === 'incentive' || activeTab.value === 'shared_to_me') return false
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

const switchTab = (tab) => {
  activeTab.value = tab
  selectedFolderId.value = null
  query.value.current = 1
  if (tab !== 'incentive' && tab !== 'shared_to_me') loadFolders()
  loadFolderPerms()
  loadFiles()
}

const selectFolder = (folder) => {
  selectedFolderId.value = folder.id
  query.value.current = 1
  loadFolderPerms()
  loadFiles()
}
// 关键字搜索回到第 1 页（分页 @change 仍用 loadFiles 保持当前页）
const searchFiles = () => { query.value.current = 1; loadFiles() }

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

// === 权限设置 ===

const openAclDialog = async (folder) => {
  aclFolder.value = folder
  aclSelectedRoles.value = []
  aclSelectedPerms.value = []
  aclDialogVisible.value = true
  if (!allRoles.value.length) {
    try {
      const res = await getRoleList()
      allRoles.value = res.data || []
    } catch (e) {}
  }
  await Promise.all([loadAclList(folder.id), loadAclDownloadable(folder.id)])
}

const loadAclList = async (id) => {
  try {
    const res = await getFolderAcl(id)
    aclList.value = res.data || []
  } catch (e) {}
}

const loadAclDownloadable = async (id) => {
  try {
    const res = await getFolderPermissions(id)
    aclDownloadable.value = (res.data?.downloadable ?? 1) === 1
  } catch (e) {}
}

const saveAcl = async () => {
  if (!aclSelectedRoles.value.length || !aclFolder.value) return
  aclSaving.value = true
  try {
    for (const roleId of aclSelectedRoles.value) {
      await saveFolderAcl(aclFolder.value.id, { roleId, perms: aclSelectedPerms.value })
    }
    ElMessage.success('权限已保存')
    aclSelectedRoles.value = []
    aclSelectedPerms.value = []
    await loadAclList(aclFolder.value.id)
    // 授权变更影响当前选中文件夹时同步刷新列表页按钮状态
    if (selectedFolderId.value === aclFolder.value.id) loadFolderPerms()
  } catch (e) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    aclSaving.value = false
  }
}

const removeAcl = async (row) => {
  if (!aclFolder.value) return
  try {
    await removeFolderAcl(aclFolder.value.id, row.roleId)
    ElMessage.success('已移除该角色授权')
    await loadAclList(aclFolder.value.id)
    // 授权变更影响当前选中文件夹时同步刷新列表页按钮状态
    if (selectedFolderId.value === aclFolder.value.id) loadFolderPerms()
  } catch (e) {}
}

const handleDownloadableChange = async (val) => {
  if (!aclFolder.value) return
  try {
    await setFolderDownloadable(aclFolder.value.id, val ? 1 : 0)
    ElMessage.success(val ? '已允许下载' : '已关闭下载')
    // 若正是当前选中文件夹，同步列表页按钮状态
    if (selectedFolderId.value === aclFolder.value.id) {
      folderDownloadable.value = val ? 1 : 0
    }
  } catch (e) {
    aclDownloadable.value = !val
  }
}

// === 文件替换 ===

const openReplaceFile = (row) => {
  replaceFileTarget.value = row
  replaceFileInputRef.value?.click()
}

const handleReplaceChosen = async (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || !replaceFileTarget.value || replacing.value) return
  const target = replaceFileTarget.value
  replaceFileTarget.value = null
  replacing.value = true
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await replaceFile(target.id, formData)
    ElMessage.success('替换成功，版本 ' + (res.data?.version ?? ''))
    loadFiles()
    loadFolders()
    loadTabCounts()
  } catch (err) {
    // 错误提示由 request 拦截器统一处理
  } finally {
    replacing.value = false
  }
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

const pickFiles = () => fileInputRef.value?.click()
const handleDrop = (e) => {
  const files = e.dataTransfer?.files
  if (files?.length) pendingFiles.value = [...pendingFiles.value, ...Array.from(files)]
}
const handleFilesChosen = (e) => {
  const files = e.target.files
  if (files?.length) pendingFiles.value = [...pendingFiles.value, ...Array.from(files)]
  e.target.value = ''
}

const uploadOne = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('scope', activeTab.value)
  // folderId 为 null 时不携带（后端 Long 类型收到字符串 "null" 会 400）
  if (selectedFolderId.value != null) formData.append('folderId', selectedFolderId.value)
  const res = await request.post('/file/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
  if (!res.data?.id) throw new Error(res.data?.message || '上传失败')
}

const startUploads = async () => {
  if (!pendingFiles.value.length) return
  uploading.value = true
  let ok = 0
  const failedNames = []
  for (const f of pendingFiles.value) {
    try { await uploadOne(f); ok++ }
    catch { failedNames.push(f.name) }
  }
  uploading.value = false
  if (failedNames.length) ElMessage.error(`上传完成：成功 ${ok} 个，失败 ${failedNames.length} 个（${failedNames.join('、')}）`)
  else ElMessage.success(`上传成功 ${ok} 个文件`)
  pendingFiles.value = []
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
    // 删除选中文件夹后回到根目录：刷新权限，避免 edit/delete 按钮状态过期
    loadFolderPerms()
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
  loadFolderPerms()
  loadFiles()
  loadTabCounts()
  loadStorage()
})
</script>

<style scoped>
.file-manage { height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 10px }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }
.stat-item { display: flex; align-items: center; gap: 12px }
.stat-icon { width: 42px; height: 42px; border-radius: 10px; display: flex; align-items: center; justify-content: center }
.stat-icon svg { width: 20px; height: 20px }
.stat-icon.c-blue { background: #eff6ff; color: #3b82f6 }
.stat-icon.c-green { background: #ecfdf5; color: #10b981 }
.s-num { font-size: 20px; font-weight: 800; color: #1f2329; line-height: 1.2 }
.s-lbl { font-size: 12px; color: #999 }

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

/* 上传拖拽区（自定义实现，替代 el-upload） */
.upload-dropzone {
  border: 1px dashed #c0c4cc;
  border-radius: 8px;
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color .2s, background .2s;
  background: #fafbfc;
}
.upload-dropzone:hover { border-color: #409eff; background: #f5f9ff }
.upload-dropzone .el-icon--upload { font-size: 44px; color: #c0c4cc; margin-bottom: 8px }
.upload-dropzone .el-upload__text { color: #606266; font-size: 13px; margin-bottom: 6px }
.upload-dropzone .el-upload__text em { color: #409eff; font-style: normal }
.upload-dropzone .el-upload__tip { color: #909399; font-size: 12px; margin-top: 8px }
.upload-pending { margin-top: 10px; text-align: left; max-height: 160px; overflow: auto }
.upload-pending-item { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; color: #303133; padding: 4px 8px; background: #f0f2f5; border-radius: 4px; margin-bottom: 4px }
.upload-pending-size { color: #909399; flex-shrink: 0 }

/* 权限设置弹窗 */
.acl-block { display: flex; flex-direction: column; gap: 10px }
.acl-block-title { font-size: 13px; font-weight: 600; color: #333 }
.acl-perms { display: flex; flex-wrap: wrap; gap: 4px 16px; padding: 4px 0 }
.acl-download-row { display: flex; align-items: center; justify-content: space-between; font-size: 13px; color: #666 }
.acl-list { max-height: 260px; overflow: auto; display: flex; flex-direction: column; gap: 6px }
.acl-item { display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: #f5f7fa; border-radius: 6px }
.acl-role { font-size: 13px; font-weight: 500; color: #303133; min-width: 80px; flex-shrink: 0 }
.acl-tags { flex: 1; display: flex; flex-wrap: wrap; gap: 4px }
</style>
