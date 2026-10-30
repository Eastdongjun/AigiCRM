<template>
  <!-- 【2026-10-30 用户令: 同一上传格子内左右翻页】单卡轮播: 槽位只渲染当前文件,
       箭头+页码翻页; [+] 添加入口与删除(×)不进序列; 点卡片本身仍预览大图 -->
  <div class="file-slot-cards">
    <template v-if="cur">
      <span v-if="files.length > 1" class="pager-arrow" role="button" tabindex="0" aria-label="上一个文件" @click.stop="turn(-1)" @keydown.enter.prevent="turn(-1)"><el-icon><ArrowLeft /></el-icon></span>
      <div class="file-card has" @click.stop="previewCardFile(cur)">
        <img v-if="(cur._existing ? (thumbsReady && cur.id) : true) && isImage(cur.name) && !cur._thumbFailed" :src="cur._existing ? existingThumb(cur) : getObjectURL(cur)" class="file-card-img" @error="cur._thumbFailed = true" />
        <div v-else class="file-card-icon"><el-icon :size="cur._existing ? 20 : 24"><Document /></el-icon></div>
        <span v-if="fileType(cur.name) === 'video'" class="file-card-play"><el-icon><VideoPlay /></el-icon></span>
        <!-- 已有文件也可移除（用户裁决 2026-10-10：上传错了需要修改；确认后走后端原子移除） -->
        <span class="file-card-del" role="button" tabindex="0" aria-label="移除文件" @click.stop="handleRemoveSlotFile(slotKey, idx)" @keydown.enter.stop.prevent="handleRemoveSlotFile(slotKey, idx)"><el-icon><Close /></el-icon></span>
        <span class="file-card-name">{{ cur.name?.substring(0, 12) }}{{ cur.name?.length > 12 ? '…' : '' }}</span>
      </div>
      <span v-if="files.length > 1" class="pager-count">{{ idx + 1 }}/{{ files.length }}</span>
      <span v-if="files.length > 1" class="pager-arrow" role="button" tabindex="0" aria-label="下一个文件" @click.stop="turn(1)" @keydown.enter.prevent="turn(1)"><el-icon><ArrowRight /></el-icon></span>
    </template>
    <div v-if="files.length < MAX_FILES" class="file-card add" @click="pick">
      <el-icon :size="28"><Plus /></el-icon>
      <span v-if="trackMode" style="font-size:12px;color:var(--el-color-info);margin-top:4px">选择文件</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { injectIncentiveUpload } from '@/composables/useIncentiveUpload'

const props = defineProps({
  /** 槽位键: pre(单槽与三槽共用)/mid/post/track */
  slotKey: { type: String, required: true },
  /** 该槽位的文件数组(共享上下文 installFiles[slotKey] 或 trackFile) */
  files: { type: Array, required: true },
  /** 跟踪记录槽: 添加入口文案为"选择文件"且走 pickTrackFile */
  trackMode: { type: Boolean, default: false },
})

const { previewCardFile, handleRemoveSlotFile, pickFile, pickTrackFile, isImage, existingThumb, thumbsReady, fileType, getObjectURL, MAX_FILES } = injectIncentiveUpload()

// 翻页态留在本组件实例内: 弹窗重置清空数组后自然回 0, 无需外部协调
const page = ref(0)
const idx = computed(() => Math.min(page.value, props.files.length - 1))
const cur = computed(() => props.files[idx.value])
const turn = (dir) => {
  const n = props.files.length
  if (n > 1) page.value = (idx.value + dir + n) % n
}
const pick = () => (props.trackMode ? pickTrackFile() : pickFile(props.slotKey))
// 新增→跳到最新一张(用户刚选的立即可见); 删除/清空→钳回末位(删的正是当前页时顺势看相邻一张)
watch(() => props.files.length, (n, o) => {
  page.value = n > (o ?? 0) ? n - 1 : Math.min(page.value, Math.max(0, n - 1))
})
</script>

<style scoped>
.file-slot-cards { display:flex; flex-wrap:wrap; gap:8px; align-items:center }
.file-card { width:104px; height:118px; border:2px dashed #dcdfe6; border-radius: var(--radius-md); display:flex; flex-direction:column; align-items:center; justify-content:center; cursor:pointer; transition:all .2s; position:relative; overflow:hidden; background:#fff }
.file-card:hover { border-color:var(--el-color-primary); background:var(--el-color-primary-light-9) }
.file-card.add { gap:2px }
.file-card.add .el-icon { color: var(--text-placeholder) } .file-card.add:hover .el-icon { color:var(--el-color-primary) }
.file-card.has { border-style:solid; border-color:#d4e8d4; background:#f6fdf6; cursor:default }
.file-card.has:hover { border-color:#a3d9a3 }
.file-card-img { width:100%; height:100%; object-fit:cover; border-radius:8px }
.file-card-icon { display:flex; align-items:center; justify-content:center; color:var(--el-color-info) }
.file-card-play { position:absolute; left:50%; top:38%; transform:translate(-50%,-50%); width:24px; height:24px; border-radius:50%; background:rgba(0,0,0,.55); color:#fff; display:flex; align-items:center; justify-content:center; font-size:13px; pointer-events:none }
.file-card-del { position:absolute; top:2px; right:2px; width:20px; height:20px; border-radius:50%; background:rgba(0,0,0,.5); color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; opacity:0; transition:opacity .15s }
.file-card:hover .file-card-del { opacity:1 }
.file-card-name { font-size:12px; color:#606266; text-align:center; margin-top:2px; max-width:96px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0 2px }
.file-card-del:hover { background:var(--el-color-danger) }
/* 【复检修复】触屏设备无稳定 hover, 移除 X 恒可见(原来 opacity:0 只 hover 显) */
@media (hover: none) {
  .file-card-del { opacity: 1 }
}
/* 翻页控件: 与卡片同高的圆形箭头 + 等宽数字页码 */
.pager-arrow { width:24px; height:24px; border-radius:50%; border:1px solid #dcdfe6; background:#fff; color:#606266; display:flex; align-items:center; justify-content:center; cursor:pointer; flex-shrink:0; transition:all .15s }
.pager-arrow:hover { color:var(--el-color-primary); border-color:var(--el-color-primary); background:var(--el-color-primary-light-9) }
.pager-count { font-size:12px; font-weight:600; color:var(--el-color-info); min-width:30px; text-align:center; font-variant-numeric:tabular-nums; flex-shrink:0 }
</style>
