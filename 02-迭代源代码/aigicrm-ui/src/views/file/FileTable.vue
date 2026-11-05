<template>
  <div class="file-list-panel">
    <!-- 【C12/D13 2026-10-08 二轮】列表请求期间 v-loading，与其他 18 处列表加载反馈对齐 -->
    <el-table v-loading="filesLoading" :data="files" style="width: 100%" size="small">
      <!-- 【2026-10-17 客户反馈】文件名必须看全: 去 show-overflow-tooltip 截断,列内自动换行(word-break) -->
      <el-table-column label="文件名称" min-width="220" class-name="name-col">
        <template #default="{ row }">
          <div class="file-name-cell">
            <span class="file-icon" :class="'icon-' + getFileExt(row.fileName)">{{ getFileExtLabel(row.fileName) }}</span>
            <span class="file-name-text">{{ row.fileName }}</span>
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
  </div>
</template>

<script setup>
// 【2026-11-05 用户令"替换文件功能删除"】行内替换入口/隐藏选择器/替换提交链已随后端端点一并退役
import { injectFilePage } from '@/composables/useFilePage'
import { pageSizes } from '@/utils/pagination'

const {
  files, fileTotal, filesLoading, query, activeTab, folderPerms, folderDownloadable,
  loadFiles, loadFolders, loadTabCounts,
  handlePreview, handleDownload, openShareDialog, handleDelete,
  formatSize, getFileExt, getFileExtLabel, getFileType,
} = injectFilePage()
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
  align-items: flex-start;
  gap: 8px;
}
/* 【2026-10-17】文件名全显: 允许两行内换行(超两行才省略),图标对齐首行 */
.file-name-text {
  min-width: 0;
  word-break: break-all;
  white-space: normal;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  line-height: 1.5;
  padding-top: 4px;
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
  margin-top: 2px;
}
.icon-doc { background: #4a90d9; }
.icon-xls { background: #34a853; }
.icon-pdf { background: #ea4335; }
.icon-ppt { background: var(--el-color-warning); }
.icon-img { background: #9b59b6; }
.icon-video { background: #10b981; }
.icon-other { background: var(--el-color-info); }
.type-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 3px;
}
.type-doc { color: #4a90d9; background: #eaf2fc; }
.type-xls { color: #34a853; background: #e8f5e9; }
.type-pdf { color: #ea4335; background: #fce8e6; }
.type-ppt { color: var(--el-color-warning); background: #fdf6ec; }
.type-img { color: #9b59b6; background: #f3e8ff; }
.type-video { color: #10b981; background: #ecfdf5; }
.type-other { color: var(--el-color-info); background: #f4f4f5; }

/* 操作图标 */
.action-icons {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.act-icon {
  font-size: 16px;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.15s;
}
.act-icon:hover { color: var(--el-color-primary); }
.act-danger:hover { color: var(--el-color-danger); }
.file-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 12px;
}
.file-total { font-size: 12px; color: var(--text-muted); }
</style>
