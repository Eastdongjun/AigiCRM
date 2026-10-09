<template>
  <div v-if="incentiveRecords.length" class="incentive-list">
    <div v-for="r in incentiveRecords" :key="r.id" class="incentive-item">
      <div class="inc-top">
        <span class="inc-category">{{ getMainCategory(r) }} · {{ getSubCategory(r) }}</span>
        <span class="inc-score" :style="{color: scoreColor(r)}">+{{ r.score }}分</span>
      </div>
      <div class="inc-meta">
        <span>{{ r.period }}</span><span>·</span><span>{{ r.userName }}</span>
        <span v-if="r.customerName">· {{ r.customerName }}</span>
        <span class="inc-time">{{ fmtDate(r.createdAt) }}</span>
      </div>
      <div v-if="r.specificProduct" class="inc-meta" style="margin-top:2px">
        <span style="color:var(--el-color-info)">具体产品：</span><span>{{ r.specificProduct }}</span>
      </div>
      <div v-if="r.preInstallQuestion" class="inc-remark" style="margin-top:6px">
        <span style="color:var(--el-color-info)">{{ detailLabelOf(r) }}：</span><span>{{ r.preInstallQuestion }}</span>
      </div>
      <div class="inc-files" v-if="getFiles(r).length">
        <div v-for="f in getFiles(r)" :key="f.id" class="inc-file" @click="previewIncentiveFile(f)">
          <span class="inc-file-tag">{{ f.tag }}</span>
          <span class="inc-file-name">{{ f.label }}</span>
          <span class="inc-file-type">{{ f.type === 'image' ? '🖼' : f.type === 'video' ? '🎬' : '📄' }}</span>
        </div>
      </div>
      <div v-if="r.remark && isTrialRec(r)" class="inc-remark">
        <span style="color:var(--el-color-info)">{{ getSubCategory(r) || r.categoryName }}安装备注：</span><span>{{ r.remark }}</span>
      </div>
    </div>
  </div>
  <div v-else class="empty-text" style="padding:40px">该客户暂无四项激励记录</div>
  <FilePreview v-model:visible="filePreviewVisible" :url="filePreviewUrl" :type="filePreviewType" :file-name="filePreviewName" />
</template>

<script setup>
import { injectCustomerDetail } from '@/composables/useCustomerDetail'
import FilePreview from '@/components/FilePreview.vue'

const {
  incentiveRecords, getMainCategory, getSubCategory, getFiles, detailLabelOf,
  isTrialRec, scoreColor, fmtDate, previewIncentiveFile,
  filePreviewVisible, filePreviewUrl, filePreviewType, filePreviewName,
} = injectCustomerDetail()
</script>

<style scoped>
.incentive-list { display:flex; flex-direction:column; gap:12px }
.incentive-item { background:#fafbfc; border:1px solid #f0f1f3; border-radius: var(--radius-md); padding:16px 18px }
.inc-top { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px }
.inc-category { font-size:14px; font-weight:600; color:#333 }
.inc-score { font-size:18px; font-weight:700 }
.inc-meta { display:flex; gap:8px; font-size:12px; color:var(--el-color-info); margin-bottom:10px }
.inc-time { margin-left:auto }
.inc-files { display:flex; gap:8px; flex-wrap:wrap }
.inc-file { display:flex; align-items:center; gap:6px; padding:6px 12px; background:#fff; border:1px solid #e8e8e8; border-radius: var(--radius-sm); cursor:pointer; font-size:13px; transition:all .15s }
.inc-file:hover { border-color:var(--el-color-primary); background:var(--el-color-primary-light-9) }
.inc-file-tag { font-size: 12px; padding:1px 6px; border-radius:3px; background:var(--el-color-primary-light-9); color:var(--el-color-primary); font-weight:500 }
.inc-file-name { color:#333; max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
.inc-file-type { font-size:14px }
.inc-remark { font-size:12px; color:var(--el-color-info); margin-top:8px; padding:8px; background:#fff; border-radius: var(--radius-sm) }
.empty-text { color: var(--text-placeholder); text-align:center; padding:24px 0; font-size:13px }
</style>
