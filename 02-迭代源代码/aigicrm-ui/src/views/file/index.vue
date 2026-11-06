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
      <!-- 扩容申请入口：canApply 时可用；否则禁用并以 tooltip 说明原因（无授权/已有在途申请等） -->
      <el-tooltip
        v-if="storageApplyInfo"
        :content="storageApplyReason"
        :disabled="storageApplyInfo.canApply"
        placement="bottom"
      >
        <span class="st-apply-wrap">
          <el-button
            class="st-apply"
            :icon="Plus"
            :disabled="!storageApplyInfo.canApply"
            @click="openStorageApply"
          >申请扩容</el-button>
        </span>
      </el-tooltip>
      <div class="st-actions">
        <el-input v-model="keyword" placeholder="搜索文件名/上传人/所在文件夹..." class="st-search" clearable prefix-icon="Search" @keyup.enter="searchFiles" />
        <!-- 【2026-10-17 客户反馈】新建文件夹从左侧文件夹树底部移到顶部操作区(原位置不合适) -->
        <el-button v-if="canCreateFolder" class="st-folder-add" :icon="FolderAdd" @click="openCreateFolder">新建文件夹</el-button>
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
        <!-- 左侧: 文件夹树（四项服务和分享给我Tab不显示） -->
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
    <FilePreview v-model:visible="previewVisible" :url="previewUrl" :type="previewType" :file-name="previewName" :file-size="previewSize" />
    <StorageApplyDialog />
  </div>
</template>

<script setup>
import { provide, onMounted, computed } from 'vue'
import { Upload, FolderAdd, Plus } from '@element-plus/icons-vue'
import { useFilePage, FilePageKey } from '@/composables/useFilePage'
import FolderPanel from './FolderPanel.vue'
import FileTable from './FileTable.vue'
import UploadDialog from './UploadDialog.vue'
import CreateFolderDialog from './CreateFolderDialog.vue'
import RenameFolderDialog from './RenameFolderDialog.vue'
import ShareDialog from './ShareDialog.vue'
import AclDialog from './AclDialog.vue'
import StorageApplyDialog from './StorageApplyDialog.vue'
import FilePreview from '@/components/FilePreview.vue'

// 共享上下文: 父级实例化一次并 provide,各面板/弹窗 inject
const filePage = useFilePage()
provide(FilePageKey, filePage)

const {
  activeTab, keyword, tabs, folderPerms, storageUsed, storageQuota, storageApplyInfo,
  previewVisible, previewUrl, previewType, previewName, previewSize,
  canCreateFolder, switchTab, searchFiles, openUpload, openCreateFolder, openStorageApply,
  loadFolders, loadFolderPerms, loadFiles, loadTabCounts, loadStorage, loadStorageApplyInfo,
} = filePage

// 后端已返回可申请状态与不可申请原因；reason 为空时给中性兜底，避免 tooltip 空白
const storageApplyReason = computed(() => storageApplyInfo.value?.reason || '暂不可申请扩容')

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
  loadStorageApplyInfo()
})
</script>

<style scoped>
.file-manage { height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 10px }

/* —— 存储概览 · 深蓝渐变条 —— */
.storage-strip {
  display: flex; align-items: center; gap: 24px;
  background: var(--hero-gradient);
  border-radius: 12px; padding: 16px 24px; flex-shrink: 0;
  box-shadow: var(--hero-shadow);
}
.st-stat { min-width: 72px }
.st-num { font-size: 22px; font-weight: 700; color: #fff; line-height: 1.2; letter-spacing: .2px }
/* 【对比度 2026-10-17】深蓝 hero 最亮端 #2563eb：白字 alpha .66 仅 3.14:1 → .92 为 4.65:1，达 AA */
.st-lbl { font-size: 12px; color: rgba(255, 255, 255, .92); margin-top: 2px }
.st-divider { width: 1px; height: 34px; background: rgba(255, 255, 255, .18) }
.st-usage { flex: 1; max-width: 420px; display: flex; align-items: center; gap: 12px }
.st-usage-track { flex: 1; height: 6px; background: rgba(255, 255, 255, .18); border-radius: 3px; overflow: hidden }
.st-usage-fill { height: 100%; background: #fcd34d; border-radius: 3px; transition: width .4s }
.st-usage-fill.warn { background: #f87171 }
/* 【对比度 2026-10-17】#2563eb 上 .78 仅 3.79:1 → .92 为 4.65:1，达 AA */
.st-usage-text { font-size: 12px; color: rgba(255, 255, 255, .92); white-space: nowrap }
.st-actions { margin-left: auto; display: flex; align-items: center; gap: 12px }
.st-search { width: 240px }
.st-search :deep(.el-input__wrapper) { background: rgba(255, 255, 255, .14); box-shadow: 0 0 0 1px rgba(255, 255, 255, .28) inset }
.st-search :deep(.el-input__inner) { color: #fff }
/* 【对比度 2026-10-17】占位符按项目占位符口径提升：.55 → .85；输入框半透明底上仍低于正文阈值 */
.st-search :deep(.el-input__inner::placeholder) { color: rgba(255, 255, 255, .85) }
.st-search :deep(.el-input__prefix) { color: rgba(255, 255, 255, .75) }
.st-upload {
  background: #fff; color: #1d4ed8; border: none; font-weight: 600;
  box-shadow: 0 4px 12px rgba(0, 0, 0, .12);
}
.st-upload:hover, .st-upload:focus { background: #eff6ff; color: var(--brand-navy) }
/* 【2026-10-17】新建文件夹(自树底移入): 描边次级样式,与上传文件主次分明 */
.st-folder-add {
  background: rgba(255, 255, 255, .12); color: #fff; font-weight: 600;
  border: 1px solid rgba(255, 255, 255, .4);
}
.st-folder-add:hover, .st-folder-add:focus { background: rgba(255, 255, 255, .22); color: #fff }
/* 【2026-10-18】申请扩容：紧随配额/进度信息的三级操作，描边弱化避免与上传主 CTA 抢焦 */
.st-apply-wrap { display: inline-flex; flex-shrink: 0; }
.st-apply {
  background: rgba(255, 255, 255, .12); color: #fff; font-weight: 600;
  border: 1px solid rgba(255, 255, 255, .4);
}
.st-apply:hover, .st-apply:focus { background: rgba(255, 255, 255, .22); color: #fff }
.st-apply.is-disabled {
  background: rgba(255, 255, 255, .08);
  color: rgba(255, 255, 255, .85);
  border-color: rgba(255, 255, 255, .26);
}

.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: var(--radius-md); padding: 14px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }

.main-card { border-radius: var(--radius-md); }
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
  border-radius: var(--radius-pill);
  background: #eef4ff;
  color: #1d4ed8;
  font-weight: 600;
}
.tab-item:not(.active) .tab-num { background: #f5f6f8; color: var(--el-color-info); }

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
