<template>
  <el-dialog v-model="visible" title="回收站" width="860px" :close-on-click-modal="false" @open="onOpen">
    <el-tabs v-model="tab">
      <el-tab-pane label="文件" name="files">
        <div class="rb-toolbar">
          <span class="rb-hint">仅显示我删除的项目；管理员在「系统设置 → 回收站」管理全量。</span>
          <div class="rb-batch" v-if="fileSelection.length">
            <el-button size="small" type="primary" @click="doBatchRestore">批量恢复({{ fileSelection.length }})</el-button>
            <el-button v-if="userStore.hasPermission('file:purge')" size="small" type="danger" @click="doBatchPurge">批量彻底删除({{ fileSelection.length }})</el-button>
          </div>
        </div>
        <el-table :data="files" v-loading="loading" size="small" max-height="420" @selection-change="s => fileSelection = s">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column label="删除时间" min-width="110" align="center">
            <template #default="{ row }">{{ (row.deletedAt || '').substring(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="doRestoreFile(row)">恢复</el-button>
              <el-button v-if="userStore.hasPermission('file:purge')" size="small" link type="danger" @click="doPurgeFile(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="fPage" v-model:page-size="fSize" :total="fTotal"
          layout="prev, pager, next" size="small" style="margin-top:8px;justify-content:flex-end"
          @current-change="loadFiles" />
      </el-tab-pane>

      <el-tab-pane label="文件夹" name="folders">
        <div class="rb-toolbar">
          <span class="rb-hint">文件夹删除进入回收站可恢复；彻底删除将同时清除其授权。</span>
        </div>
        <el-table :data="folders" v-loading="loading" size="small" max-height="420" @selection-change="s => folderSelection = s">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="folderName" label="文件夹名" min-width="200" show-overflow-tooltip />
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.folderType === 'public' ? 'primary' : 'info'">
                {{ row.folderType === 'public' ? '公共' : '私人' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" min-width="110" align="center">
            <template #default="{ row }">{{ (row.deletedAt || '').substring(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="doRestoreFolder(row)">恢复</el-button>
              <el-button v-if="userStore.hasPermission('file:purge')" size="small" link type="danger" @click="doPurgeFolder(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="foPage" v-model:page-size="foSize" :total="foTotal"
          layout="prev, pager, next" size="small" style="margin-top:8px;justify-content:flex-end"
          @current-change="loadFolders" />
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <span class="rb-note">回收站项目保留至管理员清理或您彻底删除；恢复后权限与授权同步恢复。</span>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  myRecycleFiles, myRecycleFolders,
  restoreRecycleFile, purgeRecycleFile,
  restoreRecycleFolder, purgeRecycleFolder,
  batchRestoreRecycle, batchPurgeRecycle,
} from '@/api/file'

const emit = defineEmits(['restored'])
const userStore = useUserStore()
// 【V2026.11.14.3 P1】彻底删除=不可逆动作, 独立权限码 file:purge(默认仅 admin, 权限中心按需授权);
// 恢复仍按 file:delete(可逆动作)。无 purge 码者只见"恢复"。

const visible = ref(false)
const tab = ref('files')
const loading = ref(false)

// —— 文件 tab ——
const files = ref([])
const fTotal = ref(0)
const fPage = ref(1)
const fSize = ref(20)
const fileSelection = ref([])

// —— 文件夹 tab ——
const folders = ref([])
const foTotal = ref(0)
const foPage = ref(1)
const foSize = ref(20)
const folderSelection = ref([])

const open = () => {
  visible.value = true
}

const onOpen = () => {
  fPage.value = 1
  foPage.value = 1
  loadFiles()
  loadFolders()
}

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await myRecycleFiles({ current: fPage.value, size: fSize.value })
    files.value = res.data || []
    fTotal.value = res.total || 0
    fileSelection.value = []
  } catch (e) {
    files.value = []
    fTotal.value = 0
  } finally {
    loading.value = false
  }
}

const loadFolders = async () => {
  loading.value = true
  try {
    const res = await myRecycleFolders({ current: foPage.value, size: foSize.value })
    folders.value = res.data || []
    foTotal.value = res.total || 0
    folderSelection.value = []
  } catch (e) {
    folders.value = []
    foTotal.value = 0
  } finally {
    loading.value = false
  }
}

const doRestoreFile = async (row) => {
  await ElMessageBox.confirm(`恢复文件「${row.fileName}」？`, '恢复确认', { type: 'info' })
  const res = await restoreRecycleFile(row.id)
  ElMessage.success(res?.message || '已恢复')
  loadFiles()
  emit('restored')
}

const doPurgeFile = async (row) => {
  await ElMessageBox.confirm(
    `彻底删除「${row.fileName}」？删除后无法恢复（不可找回）。`,
    '彻底删除', { type: 'warning', confirmButtonText: '彻底删除' }
  )
  const res = await purgeRecycleFile(row.id)
  ElMessage.success(res?.message || '已彻底删除')
  loadFiles()
}

const doRestoreFolder = async (row) => {
  await ElMessageBox.confirm(`恢复文件夹「${row.folderName}」？原上级文件夹已删除时将恢复到根目录。`, '恢复确认', { type: 'info' })
  const res = await restoreRecycleFolder(row.id)
  ElMessage.success(res?.message || '已恢复')
  loadFolders()
  emit('restored')
}

const doPurgeFolder = async (row) => {
  await ElMessageBox.confirm(
    `彻底删除文件夹「${row.folderName}」？其授权配置将一并清除，删除后无法恢复。`,
    '彻底删除', { type: 'warning', confirmButtonText: '彻底删除' }
  )
  const res = await purgeRecycleFolder(row.id)
  ElMessage.success(res?.message || '已彻底删除')
  loadFolders()
}

const doBatchRestore = async () => {
  const fs = fileSelection.value
  const fo = folderSelection.value.filter(f => f && f.id && tab.value === 'folders')
  const sel = tab.value === 'folders' ? fo : fs
  if (!sel.length) return
  await ElMessageBox.confirm(`批量恢复选中的 ${sel.length} 项？`, '批量恢复', { type: 'info' })
  const payload = tab.value === 'folders'
    ? { folderIds: sel.map(f => f.id), fileIds: [] }
    : { fileIds: sel.map(f => f.id), folderIds: [] }
  const res = await batchRestoreRecycle(payload)
  ElMessage.success(`恢复完成：${res?.ok ?? 0}/${res?.total ?? sel.length} 项成功`)
  tab.value === 'folders' ? loadFolders() : loadFiles()
  emit('restored')
}

const doBatchPurge = async () => {
  const sel = tab.value === 'folders' ? folderSelection.value : fileSelection.value
  if (!sel.length) return
  await ElMessageBox.confirm(
    `彻底删除选中的 ${sel.length} 项？删除后无法恢复。`,
    '批量彻底删除', { type: 'warning', confirmButtonText: '彻底删除' }
  )
  const payload = tab.value === 'folders'
    ? { folderIds: sel.map(f => f.id), fileIds: [] }
    : { fileIds: sel.map(f => f.id), folderIds: [] }
  const res = await batchPurgeRecycle(payload)
  ElMessage.success(`清理完成：${res?.ok ?? 0}/${res?.total ?? sel.length} 项成功`)
  tab.value === 'folders' ? loadFolders() : loadFiles()
}

defineExpose({ open })
</script>

<style scoped>
.rb-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px }
.rb-hint { font-size: 12px; color: #909399 }
.rb-note { font-size: 12px; color: #909399; margin-right: 16px }
.rb-batch { display: flex; gap: 8px }
</style>
