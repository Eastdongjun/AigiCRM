<template>
  <el-dialog v-model="visible" :title="`历史版本 — ${fileName}`" width="720px" :close-on-click-modal="false">
    <el-table :data="versions" v-loading="loading" size="small" max-height="420">
      <el-table-column label="版本" width="70" align="center">
        <template #default="{ row }">v{{ row.versionNo }}</template>
      </el-table-column>
      <el-table-column label="文件名" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.fileName || '—' }}</template>
      </el-table-column>
      <el-table-column label="大小" width="90" align="center">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="归档时间" min-width="120" align="center">
        <template #default="{ row }">{{ (row.createdAt || '').substring(0, 16).replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="doDownload(row)">下载</el-button>
          <el-button size="small" link type="warning" @click="doRollback(row)">回滚</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <span class="vh-note">当前使用版本不在列表中；回滚前系统会先把当前内容归档，回滚不丢数据。</span>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listFileVersions, downloadFileVersion, rollbackFileVersion } from '@/api/file'

const emit = defineEmits(['rolled-back'])

const visible = ref(false)
const loading = ref(false)
const fileId = ref(null)
const fileName = ref('')
const versions = ref([])

const open = (row) => {
  fileId.value = row.id
  fileName.value = row.fileName
  visible.value = true
  load()
}

const load = async () => {
  loading.value = true
  try {
    const res = await listFileVersions(fileId.value)
    versions.value = res.data || []
  } catch (e) {
    versions.value = []
  } finally {
    loading.value = false
  }
}

const formatSize = (n) => {
  if (n == null) return '—'
  if (n >= 1073741824) return (n / 1073741824).toFixed(2) + ' GB'
  if (n >= 1048576) return (n / 1048576).toFixed(1) + ' MB'
  if (n >= 1024) return (n / 1024).toFixed(0) + ' KB'
  return n + ' B'
}

const saveBlob = (res, fallbackName) => {
  // request.js blob 直通后返回 axios response; 文件名从 Content-Disposition 解析(RFC5987)
  const headers = res?.headers || {}
  const cd = headers['content-disposition'] || headers['Content-Disposition'] || ''
  const m = cd.match(/filename\*=UTF-8''([^;]+)/i)
  let name = fallbackName
  if (m) name = decodeURIComponent(m[1])
  const url = URL.createObjectURL(new Blob([res?.data || res]))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

const doDownload = async (row) => {
  const res = await downloadFileVersion(fileId.value, row.versionNo)
  saveBlob(res, `v${row.versionNo}-${fileName.value}`)
  ElMessage.success('已开始下载')
}

const doRollback = async (row) => {
  await ElMessageBox.confirm(
    `回滚到 v${row.versionNo}？当前内容会先归档为历史版本，不会丢失。`,
    '回滚确认', { type: 'warning' }
  )
  const res = await rollbackFileVersion(fileId.value, row.versionNo)
  ElMessage.success(`已回滚到 v${row.versionNo}（当前为 v${res?.data?.version || '?'}）`)
  load()
  emit('rolled-back')
}

defineExpose({ open })
</script>

<style scoped>
.vh-note { font-size: 12px; color: #909399; margin-right: 16px }
</style>
