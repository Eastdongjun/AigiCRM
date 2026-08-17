<template>
  <div class="incentive-page">
    <!-- 分类Tab -->
    <div class="cat-tabs-bar">
      <div class="cat-tabs">
        <div v-for="cat in parentCategories" :key="cat.id" class="cat-tab" :class="{ active: activeTab === String(cat.id) }" @click="activeTab = String(cat.id)">
          <span class="cat-tab-icon"><el-icon><component :is="catIcons[cat.categoryCode] || Box" /></el-icon></span>
          <span class="cat-tab-name">{{ cat.categoryName }}</span>
          <span class="cat-tab-badge">{{ cat._recordCount || 0 }}</span>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <IncentiveFilterBar />

    <!-- 框3: 左右布局 -->
    <div class="main-layout" v-if="groupedChildren.length">
      <!-- 左侧：子分类列表 -->
      <SubCategoryPanel />

      <!-- 右侧：已上传文件表格 -->
      <RecordTable />
    </div>

    <!-- 上传/追加 弹窗 -->
    <UploadDialog />

    <!-- 隐藏的文件上传 (用opacity而非display:none,避免浏览器安全限制) -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      style="position:absolute;width:1px;height:1px;opacity:0;overflow:hidden"
      @change="handleFileSelected"
    />

    <!-- 安装文件预览（图片/视频） -->
    <FilePreview v-model:visible="previewVisible" :url="previewUrl" :type="previewType" :file-name="previewName" />
  </div>
</template>

<script setup>
import { provide, onMounted } from 'vue'
import { Box } from '@element-plus/icons-vue'
import { useIncentiveUpload, IncentiveUploadKey } from '@/composables/useIncentiveUpload'
import FilePreview from '@/components/FilePreview.vue'
import IncentiveFilterBar from './IncentiveFilterBar.vue'
import SubCategoryPanel from './SubCategoryPanel.vue'
import RecordTable from './RecordTable.vue'
import UploadDialog from './UploadDialog.vue'

// 共享上下文: 父级实例化一次并 provide,各区块/弹窗 inject
const upload = useIncentiveUpload()
provide(IncentiveUploadKey, upload)

const {
  activeTab, parentCategories, catIcons, groupedChildren,
  fileInputRef, handleFileSelected,
  previewVisible, previewUrl, previewType, previewName,
  loadCategories, loadIndustries, loadPermission, loadUsers, loadDeptTree,
} = upload

onMounted(() => {
  loadCategories()
  loadIndustries()
  loadPermission()
  loadUsers()
  loadDeptTree()
})
</script>

<style scoped>
.incentive-page { padding: 0; height: calc(100vh - 40px); display: flex; flex-direction: column; gap: 10px }

/* Cat Tabs */
.cat-tabs-bar { flex-shrink:0 }
.cat-tabs { display:flex; gap:0; background:#f2f4f7; border-radius:12px; padding:4px; overflow:hidden }
.cat-tab { flex:1; display:flex; align-items:center; justify-content:center; gap:8px; padding:12px 8px; font-size:14px; color:#606266; cursor:pointer; border-radius:10px; transition:all .25s; font-weight:500; position:relative }
.cat-tab:hover { background:rgba(255,255,255,.6) }
.cat-tab.active { font-weight:700; color:#fff; box-shadow:0 2px 8px rgba(0,0,0,.12) }
.cat-tab:nth-child(1).active { background:#f59e0b }
.cat-tab:nth-child(2).active { background:#3b82f6 }
.cat-tab:nth-child(3).active { background:#8b5cf6 }
.cat-tab:nth-child(4).active { background:#10b981 }
.cat-tab-icon { font-size:16px; display:flex }
.cat-tab-badge { font-size:11px; padding:1px 7px; border-radius:10px; background:rgba(0,0,0,.08); font-weight:600 }
.cat-tab.active .cat-tab-badge { background:rgba(255,255,255,.25) }

.main-layout { display:flex; border:1px solid #e8ecf1; border-radius:12px; overflow:hidden; flex:1; min-height:0; background:#fff }
</style>
