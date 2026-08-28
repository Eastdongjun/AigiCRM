<template>
  <div class="file-manage">
    <!-- 框1: 统计 -->
    <div class="section-box" style="display:flex;align-items:center;gap:32px">
      <div class="stat-item">
        <div class="stat-icon c-blue"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg></div>
        <div><div class="s-num">{{ storageUsed || '0' }}</div><div class="s-lbl">已使用</div></div>
      </div>
      <div class="stat-item">
        <div class="stat-icon c-green"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg></div>
        <div><div class="s-num">{{ storageQuota || '10GB' }}</div><div class="s-lbl">总容量</div></div>
      </div>
      <el-input v-model="keyword" placeholder="搜索文件名称..." style="width:220px;margin-left:auto" clearable prefix-icon="Search" @keyup.enter="searchFiles" />
      <el-button v-if="activeTab!=='incentive'&&activeTab!=='shared_to_me' && folderPerms.includes('file:upload')" type="primary" :icon="Upload" @click="openUpload">上传文件</el-button>
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
          <FolderPanel />
          <!-- 分隔线 -->
          <div class="divider"></div>
        </template>

        <!-- 右侧: 文件列表 -->
        <FileTable />
      </div>
    </el-card>
    </div>

    <!-- 弹窗 -->
    <UploadDialog />
    <CreateFolderDialog />
    <RenameFolderDialog />
    <ShareDialog />
    <AclDialog />
  </div>
</template>

<script setup>
import { FilePageKey, useFilePage } from '@/composables/useFilePage'
import { Upload } from '@element-plus/icons-vue'
import { onMounted, provide } from 'vue'
import AclDialog from './AclDialog.vue'
import CreateFolderDialog from './CreateFolderDialog.vue'
import FileTable from './FileTable.vue'
import FolderPanel from './FolderPanel.vue'
import RenameFolderDialog from './RenameFolderDialog.vue'
import ShareDialog from './ShareDialog.vue'
import UploadDialog from './UploadDialog.vue'

// 共享上下文: 父级实例化一次并 provide,各面板/弹窗 inject
const filePage = useFilePage()
provide(FilePageKey, filePage)

const {
  activeTab, keyword, tabs, folderPerms, storageUsed, storageQuota,
  switchTab, searchFiles, openUpload,
  loadFolders, loadFolderPerms, loadFiles, loadTabCounts, loadStorage,
} = filePage

onMounted(() => {
  loadFolders()
  loadFolderPerms()
  loadFiles()
  loadTabCounts()
  loadStorage()
})
</script>

<style scoped>
.file-manage { height: calc(100vh - 40px); display: flex; flex-direction: column; gap: 10px }
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
  min-height: calc(100vh - 160px);
}
.divider {
  width: 1px;
  background: #ebeef5;
  flex-shrink: 0;
}
</style>
