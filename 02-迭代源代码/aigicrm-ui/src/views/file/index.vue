<template>
  <div class="file-manage">
    <!-- 存储概览 · 深蓝条(与工作台主视觉同语言,含真实配额用量) -->
    <div class="storage-strip">
      <div class="st-stat">
        <div class="st-num">{{ storageUsed || '0' }}</div>
        <div class="st-lbl">已使用</div>
      </div>
      <div class="st-divider"></div>
      <div class="st-stat">
        <div class="st-num">{{ storageQuota || '10GB' }}</div>
        <div class="st-lbl">总容量</div>
      </div>
      <div class="st-usage" v-if="usagePercent !== null">
        <div class="st-usage-track"><div class="st-usage-fill" :class="{ warn: usagePercent >= 80 }" :style="{ width: usagePercent + '%' }"></div></div>
        <span class="st-usage-text">{{ usagePercent }}% 已用</span>
      </div>
      <div class="st-actions">
        <el-input v-model="keyword" placeholder="搜索文件名称..." class="st-search" clearable prefix-icon="Search" @keyup.enter="searchFiles" />
        <el-button v-if="activeTab!=='incentive'&&activeTab!=='shared_to_me' && folderPerms.includes('upload')"
                   class="st-upload" :icon="Upload" @click="openUpload">上传文件</el-button>
      </div>
    </div>

    <!-- 主内容 -->
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
    <FilePreview v-model:visible="previewVisible" :url="previewUrl" :type="previewType" :file-name="previewName" />
  </div>
</template>

<script setup>
import { provide, onMounted, computed } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { useFilePage, FilePageKey } from '@/composables/useFilePage'
import FolderPanel from './FolderPanel.vue'
import FileTable from './FileTable.vue'
import UploadDialog from './UploadDialog.vue'
import CreateFolderDialog from './CreateFolderDialog.vue'
import RenameFolderDialog from './RenameFolderDialog.vue'
import ShareDialog from './ShareDialog.vue'
import AclDialog from './AclDialog.vue'
import FilePreview from '@/components/FilePreview.vue'

// 共享上下文: 父级实例化一次并 provide,各面板/弹窗 inject
const filePage = useFilePage()
provide(FilePageKey, filePage)

const {
  activeTab, keyword, tabs, folderPerms, storageUsed, storageQuota,
  previewVisible, previewUrl, previewType, previewName,
  switchTab, searchFiles, openUpload,
  loadFolders, loadFolderPerms, loadFiles, loadTabCounts, loadStorage,
} = filePage

// ——— 配额用量百分比("2.3GB"/"512MB"/"10GB" → %),解析失败则不显示进度条 ———
const parseSizeToMB = (s) => {
  const m = String(s || '').trim().match(/^([\d.]+)\s*(GB|MB|KB|B)$/i)
  if (!m) return null
  const n = parseFloat(m[1])
  const unit = m[2].toUpperCase()
  return unit === 'GB' ? n * 1024 : unit === 'MB' ? n : unit === 'KB' ? n / 1024 : n / (1024 * 1024)
}
const usagePercent = computed(() => {
  const used = parseSizeToMB(storageUsed.value)
  const total = parseSizeToMB(storageQuota.value || '10GB')
  if (used === null || !total) return null
  return Math.min(100, Math.round(used / total * 100))
})

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

/* —— 存储概览 · 深蓝渐变条 —— */
.storage-strip {
  display: flex; align-items: center; gap: 24px;
  background: linear-gradient(135deg, #16337a 0%, #1d4ed8 58%, #2563eb 100%);
  border-radius: 12px; padding: 16px 24px; flex-shrink: 0;
  box-shadow: 0 6px 20px rgba(22, 51, 122, .14);
}
.st-stat { min-width: 72px }
.st-num { font-size: 22px; font-weight: 700; color: #fff; line-height: 1.2; letter-spacing: .2px }
.st-lbl { font-size: 12px; color: rgba(255, 255, 255, .66); margin-top: 2px }
.st-divider { width: 1px; height: 34px; background: rgba(255, 255, 255, .18) }
.st-usage { flex: 1; max-width: 420px; display: flex; align-items: center; gap: 12px }
.st-usage-track { flex: 1; height: 6px; background: rgba(255, 255, 255, .18); border-radius: 3px; overflow: hidden }
.st-usage-fill { height: 100%; background: #fcd34d; border-radius: 3px; transition: width .4s }
.st-usage-fill.warn { background: #f87171 }
.st-usage-text { font-size: 12px; color: rgba(255, 255, 255, .78); white-space: nowrap }
.st-actions { margin-left: auto; display: flex; align-items: center; gap: 12px }
.st-search { width: 240px }
.st-search :deep(.el-input__wrapper) { background: rgba(255, 255, 255, .14); box-shadow: 0 0 0 1px rgba(255, 255, 255, .28) inset }
.st-search :deep(.el-input__inner) { color: #fff }
.st-search :deep(.el-input__inner::placeholder) { color: rgba(255, 255, 255, .55) }
.st-search :deep(.el-input__prefix) { color: rgba(255, 255, 255, .75) }
.st-upload {
  background: #fff; color: #1d4ed8; border: none; font-weight: 600;
  box-shadow: 0 4px 12px rgba(0, 0, 0, .12);
}
.st-upload:hover, .st-upload:focus { background: #eff6ff; color: #16337a }

.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }

.main-card { border-radius: 10px; }
.main-card :deep(.el-card__body) { padding: 0; }

/* Tab 栏(深蓝主色) */
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
.tab-item:hover { color: #1d4ed8; }
.tab-item.active {
  color: #1d4ed8;
  font-weight: 600;
  border-bottom-color: #1d4ed8;
}
.tab-num {
  margin-left: 4px;
  font-size: 12px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef4ff;
  color: #1d4ed8;
  font-weight: 600;
}
.tab-item:not(.active) .tab-num { background: #f5f6f8; color: #909399; }

/* 内容区 */
.content-body {
  display: flex;
  min-height: calc(100vh - 200px);
}
.divider {
  width: 1px;
  background: #ebeef5;
  flex-shrink: 0;
}
</style>
