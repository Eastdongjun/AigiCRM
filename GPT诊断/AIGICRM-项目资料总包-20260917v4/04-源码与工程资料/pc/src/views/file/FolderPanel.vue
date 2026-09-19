<template>
  <div class="folder-panel">
    <h4 class="folder-title">文件夹</h4>

    <!-- 全部文件 -->
    <div class="folder-root" :class="{ active: !selectedFolderId }" @click="selectFolder(null)">
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
    <el-button v-if="canCreateFolder" class="new-folder-btn" @click="openCreateFolder">
      <el-icon><FolderAdd /></el-icon> 新建文件夹
    </el-button>

    <!-- 存储空间（仅私人Tab显示） -->
    <div v-if="activeTab === 'private'" class="storage-info">
      <div class="storage-label">个人存储空间</div>
      <el-progress :percentage="storagePercent" :stroke-width="4" :show-text="false" />
      <div class="storage-text">{{ storageUsed }} / {{ storageQuota }}</div>
    </div>
  </div>
</template>

<script setup>
import { injectFilePage } from '@/composables/useFilePage'

const {
  selectedFolderId, fileTotal, currentFolders, canCreateFolder,
  activeTab, storageUsed, storageQuota, storagePercent,
  selectFolder, openRenameFolder, openAclDialog, openCreateFolder, handleDeleteFolder,
} = injectFilePage()
</script>

<style scoped>
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
</style>
