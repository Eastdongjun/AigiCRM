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
      <!-- 【缩略图墙 2026-10-17】图片附件直接显示缩略图(≤300px 秒载), 点击放大看原图(el-image 内建查看器);
           视频与文档不再和图片挤一行, 走下方文字条点开预览 -->
      <div v-if="imageFiles(r).length" class="inc-thumbs">
        <div v-for="f in imageFiles(r)" :key="'thumb-' + f.id" class="inc-thumb-item">
          <el-image
            class="inc-thumb"
            :src="thumbUrl(f.id)"
            :preview-src-list="[fileUrl(f.id)]"
            :initial-index="0"
            fit="cover"
            lazy
            preview-teleported
          >
            <template #error>
              <div class="inc-thumb-na"><el-icon><PictureFilled /></el-icon></div>
            </template>
          </el-image>
          <span v-if="f.tag" class="inc-thumb-tag">{{ f.tag }}</span>
        </div>
      </div>
      <div class="inc-files" v-if="otherFiles(r).length">
        <div v-for="f in otherFiles(r)" :key="f.id" class="inc-file" @click="previewIncentiveFile(f)">
          <span class="inc-file-tag" v-if="f.tag">{{ f.tag }}</span>
          <span class="inc-file-name">{{ f.label }}</span>
          <span class="inc-file-type">{{ f.type === 'video' ? '🎬' : '📄' }}</span>
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
import { PictureFilled } from '@element-plus/icons-vue'
import FilePreview from '@/components/FilePreview.vue'

const {
  incentiveRecords, getMainCategory, getSubCategory, getFiles, detailLabelOf,
  isTrialRec, scoreColor, fmtDate, previewIncentiveFile,
  filePreviewVisible, filePreviewUrl, filePreviewType, filePreviewName, fileUrl, thumbUrl,
} = injectCustomerDetail()

// 图片走缩略图墙; 其余(视频/文档)保留文字条(点开=阅读器预览)
const imageFiles = (r) => getFiles(r).filter((f) => f.type === 'image' && f.id)
const otherFiles = (r) => getFiles(r).filter((f) => f.type !== 'image')
</script>

<style scoped>
.incentive-list { display:flex; flex-direction:column; gap:12px }
.incentive-item { background:#fafbfc; border:1px solid #f0f1f3; border-radius: var(--radius-md); padding:16px 18px }
.inc-top { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px }
.inc-category { font-size:14px; font-weight:600; color:#333 }
.inc-score { font-size:18px; font-weight:700 }
.inc-meta { display:flex; gap:8px; font-size:12px; color:var(--el-color-info); margin-bottom:10px }
.inc-time { margin-left:auto }
.inc-thumbs { display:flex; flex-wrap:wrap; gap:8px; margin:4px 0 10px }
.inc-thumb-item { position:relative; width:88px; height:88px; border-radius:8px; overflow:hidden; border:1px solid #eceef2; background:#f5f7fa }
.inc-thumb { width:100%; height:100%; display:block; cursor:zoom-in }
.inc-thumb-na { width:100%; height:100%; display:flex; align-items:center; justify-content:center; color:var(--el-text-color-placeholder) }
.inc-thumb-tag { position:absolute; left:0; bottom:0; right:0; font-size:11px; color:#fff; background:rgba(31,45,61,.62); padding:1px 6px; text-align:center }
.inc-files { display:flex; gap:8px; flex-wrap:wrap }
.inc-file { display:flex; align-items:center; gap:6px; padding:6px 12px; background:#fff; border:1px solid #e8e8e8; border-radius: var(--radius-sm); cursor:pointer; font-size:13px; transition:all .15s }
.inc-file:hover { border-color:var(--el-color-primary); background:var(--el-color-primary-light-9) }
.inc-file-tag { font-size: 12px; padding:1px 6px; border-radius:3px; background:var(--el-color-primary-light-9); color:var(--el-color-primary); font-weight:500 }
.inc-file-name { color:#333; max-width:200px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap }
.inc-file-type { font-size:14px }
.inc-remark { font-size:12px; color:var(--el-color-info); margin-top:8px; padding:8px; background:#fff; border-radius: var(--radius-sm) }
.empty-text { color: var(--text-placeholder); text-align:center; padding:24px 0; font-size:13px }
</style>
