<template>
  <div class="folder-panel">
    <h4 class="folder-title">文件夹</h4>

    <!-- 全部文件 -->
    <div class="folder-root" :class="{ active: !selectedFolderId }" @click="selectFolder(null)">
      <!-- 【2026-10-17 客户反馈】细线图标换方块底色应用式图标(圆角方块+白色符号) -->
      <span class="sq-icon sq-icon--all"><el-icon><FolderOpened /></el-icon></span>
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
      <span class="sq-icon sq-icon--folder"><el-icon><Folder /></el-icon></span>
      <span class="folder-name">{{ folder.folderName }}</span>
      <span class="folder-count">{{ folder.fileCount || 0 }}</span>
      <!-- 【2026-10-27 深度检查修复】重命名/删除再叠加全局权限码 file:folder:manage(与后端 @PreAuthorize 同口径);
           权限设置走 ACL 端点不受该码管辖, 维持原显隐 -->
      <span v-if="canManageFolders && (folder.perms?.includes('edit') || folder.perms?.includes('delete'))" class="folder-actions">
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

    <!-- 【2026-10-17 客户反馈】新建文件夹按钮移至顶部操作区(views/file/index.vue st-actions),此处不再渲染 -->

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
  selectedFolderId, fileTotal, currentFolders, canCreateFolder, canManageFolders,
  activeTab, storageUsed, storageQuota, storagePercent,
  selectFolder, openRenameFolder, openAclDialog, handleDeleteFolder,
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
/* 【2026-10-17 客户反馈】方块底色应用式图标: 圆角方块渐变底 + 白色符号
   (全部文件=品牌蓝, 文件夹=琥珀黄), 取代原先的细线轮廓图标; 【二轮】整体放大一档 */
.sq-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  font-size: 18px;
  box-shadow: inset 0 -2px 5px rgba(0, 0, 0, .14);
}
.sq-icon--all { background: linear-gradient(135deg, #2563eb, #4285f4); }
.sq-icon--folder { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.folder-root {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.folder-root:hover { background: #f5f7fa; }
.folder-root.active { background: var(--el-color-primary-light-9); }
.folder-root-name { flex: 1; font-size: 14px; color: var(--el-color-primary); font-weight: 600; }
.folder-count-badge {
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-md);
  font-weight: 600;
}
.folder-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 14px;
  color: #333;
  transition: background 0.15s;
}
.folder-item:hover { background: #f5f7fa; }
.folder-item.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.folder-name { flex: 1; }
.folder-count {
  background: #e6e8eb;
  color: #666;
  font-size: 12px;
  padding: 2px 7px;
  border-radius: 8px;
  font-weight: 500;
}
.folder-item.active .folder-count { background: var(--el-color-primary-light-7); color: var(--el-color-primary); }
.folder-actions { display: none; align-items: center; gap: 8px; }
.folder-item:hover .folder-actions { display: inline-flex; }
.folder-item:hover .folder-count { display: none; }
.folder-act {
  font-size: 14px;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.15s;
}
.folder-act:hover { color: var(--el-color-primary); }
.folder-act-danger:hover { color: var(--el-color-danger); }
.storage-info { margin-top: auto; padding-top: 16px; }
.storage-label { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
.storage-text { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
</style>
