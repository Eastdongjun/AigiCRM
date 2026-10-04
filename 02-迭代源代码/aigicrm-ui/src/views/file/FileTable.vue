<template>
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
            <el-tooltip content="打开" placement="top">
              <el-icon class="act-icon" @click.stop="handlePreview(row)"><View /></el-icon>
            </el-tooltip>
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
        :page-sizes="pageSizes(query.size)"
        layout="sizes, prev, pager, next, jumper"
        @size-change="query.current = 1"
        size="small"
        @change="loadFiles"
      />
    </div>

    <!-- 替换文件隐藏选择器 -->
    <input ref="replaceFileInputRef" type="file" style="display:none" @change="handleReplaceChosen" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
// 【D16】View 图标已由 main.js menuIconList 全局注册(app.component)，本地 import 冗余已删；模板 <View/> 仍可解析
import { replaceFile } from '@/api/file'
import { injectFilePage } from '@/composables/useFilePage'
import { pageSizes } from '@/utils/pagination'

const {
  files, fileTotal, query, activeTab, folderPerms, folderDownloadable,
  loadFiles, loadFolders, loadTabCounts,
  handlePreview, handleDownload, openShareDialog, handleDelete,
  formatSize, getFileExt, getFileExtLabel, getFileType,
} = injectFilePage()

// 文件替换（行操作触发，独立隐藏选择器）
const replaceFileTarget = ref(null)
const replaceFileInputRef = ref(null)
const replacing = ref(false)

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
</script>

<style scoped>
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
.icon-video { background: #10b981; }
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
.type-video { color: #10b981; background: #ecfdf5; }
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
