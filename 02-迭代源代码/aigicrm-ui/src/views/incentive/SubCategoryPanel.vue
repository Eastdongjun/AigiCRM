<template>
  <!-- 左侧：子分类列表 -->
  <div class="left-panel">
    <div class="sub-hint">
      <el-tag type="danger" size="small" effect="plain">{{ activeParent?.categoryName }}</el-tag>
      <span>— 选择子项上传文件</span>
    </div>

    <div v-for="group in groupedChildren" :key="group.id" class="sub-group">
      <template v-if="group.items">
        <div class="group-header">{{ group.categoryName }}</div>
        <div v-for="item in group.items" :key="item.id" class="sub-item">
          <span class="sub-name">{{ item.categoryName }}</span>
          <div class="sub-actions">
            <span class="score-badge" :class="'score-' + Math.min(item.score, 3)">{{ item.score }}分</span>
            <el-button v-if="userStore.hasPermission('incentive:add')" size="small" plain :icon="Upload" @click="startUpload(item)">上传文件</el-button>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="sub-item flat">
          <span class="sub-name">{{ group.categoryName }}</span>
          <div class="sub-actions">
            <span class="score-badge" :class="'score-' + Math.min(group.score, 3)">{{ group.score }}分</span>
            <el-button v-if="userStore.hasPermission('incentive:add')" size="small" plain :icon="Upload" @click="startUpload(group)">上传文件</el-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { Upload } from '@element-plus/icons-vue'
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'

const { userStore, activeParent, groupedChildren, startUpload } = injectIncentiveUpload()
</script>

<style scoped>
.left-panel { flex:0 0 240px; background:#fafbfd; padding:16px; border-right:1px solid #f0f2f5; overflow-y:auto }
.sub-hint { display:flex; align-items:center; gap:8px; margin-bottom:12px; color:var(--el-color-info); font-size:13px }
.sub-group { margin-bottom:4px }
.group-header { font-size:13px; font-weight:700; color:#1f2329; padding:10px 0 6px 12px; border-left:3px solid var(--el-color-primary); margin-top:8px }
.sub-item { display:flex; justify-content:space-between; align-items:center; padding:10px 0 10px 12px; border-bottom:1px solid #f5f5f5; transition:background .15s }
.sub-item:hover { background:#f0f4ff }
.sub-item.flat { padding-left:0 }
.sub-name { font-size:13px; color:#333; font-weight:500 }
.sub-actions { display:flex; align-items:center; gap:10px }
.score-badge { display:inline-block; min-width:28px; text-align:center; padding:2px 8px; border-radius: var(--radius-md); font-size:12px; font-weight:600 }
.score-0 { color:#bbb; background:#f5f5f5 } .score-1 { color:var(--el-color-warning); background:#fff7ed } .score-2 { color:var(--el-color-success); background:#f0f9eb } .score-3 { color:var(--el-color-danger); background:#fef0f0 }
</style>
