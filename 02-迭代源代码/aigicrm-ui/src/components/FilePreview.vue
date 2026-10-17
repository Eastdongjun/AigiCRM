<template>
  <!-- 文件阅读器（2026-10-16 升级）：图片/视频/音频/PDF/Word(.docx)/Excel(.xlsx|.xls|.csv)/文本 全部内嵌预览；
       其余格式给出具体原因并引导下载。渲染器按需懒加载，不进首屏包。
       种类以文件名扩展名判定（previewKindOf 单一出处）；调用方传的 type 仅在文件名无法判定时兜底，
       兼容旧口径 image/video/pdf/office/file/doc。 -->
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="fileName || '文件预览'"
    width="90%"
    top="2vh"
    :close-on-click-modal="true"
    destroy-on-close
    class="file-preview-dlg"
  >
    <div class="preview-body" :class="'kind-' + kind">
      <!-- 【图片秒开 2026-10-17】展示用 ≤300px 缩略图(18MB 原图 14.8s → 缩略亚秒);
           点击放大(preview-src-list)才加载原图看全细节 -->
      <el-image
        v-if="kind === 'image'"
        :src="thumbSrcOf(url)"
        :preview-src-list="[url]"
        :initial-index="0"
        fit="contain"
        class="pv-image"
        preview-teleported
      />
      <template v-else-if="kind === 'video'">
        <video v-if="!mediaError" :src="url" controls autoplay class="pv-video" @error="mediaError = true" />
        <div v-else class="doc-fallback">
          <el-icon :size="48"><VideoCamera /></el-icon>
          <div class="fallback-name">{{ fileName || '视频' }}</div>
          <div class="fallback-hint">浏览器无法播放该视频编码（如 .mov 的 HEVC），可下载后用播放器打开</div>
        </div>
      </template>
      <div v-else-if="kind === 'audio'" class="pv-audio-wrap">
        <el-icon :size="48"><Headset /></el-icon>
        <div class="fallback-name">{{ fileName || '音频' }}</div>
        <audio :src="url" controls class="pv-audio" />
      </div>
      <PdfFrame v-else-if="kind === 'pdf'" :url="url" :file-name="fileName" :size-hint="fileSize" />
      <DocxView v-else-if="kind === 'docx'" :url="url" :file-name="fileName" :size-hint="fileSize" />
      <SheetView v-else-if="kind === 'sheet'" :url="url" :file-name="fileName" :size-hint="fileSize" />
      <TextView v-else-if="kind === 'text'" :url="url" :file-name="fileName" :size-hint="fileSize" />
      <div v-else class="doc-fallback">
        <el-icon :size="48"><Document /></el-icon>
        <div class="fallback-name">{{ fileName || '文件' }}</div>
        <div class="fallback-hint">{{ unsupportedHintOf(fileName) }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button type="primary" :loading="downloading" @click="download"><el-icon><Download /></el-icon> 下载文件</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Document, VideoCamera, Headset } from '@element-plus/icons-vue'
import { getToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'
import { previewKindOf, unsupportedHintOf, thumbSrcOf } from '@/utils/media-type'

// 重型渲染器按需加载：docx-preview / SheetJS 各自在渲染器内部动态 import，首屏零成本
const PdfFrame = defineAsyncComponent(() => import('./preview/PdfFrame.vue'))
const DocxView = defineAsyncComponent(() => import('./preview/DocxView.vue'))
const SheetView = defineAsyncComponent(() => import('./preview/SheetView.vue'))
const TextView = defineAsyncComponent(() => import('./preview/TextView.vue'))

const props = defineProps({
  visible: { type: Boolean, default: false },
  url: { type: String, default: '' },
  type: { type: String, default: 'image' },
  fileName: { type: String, default: '' },
  fileSize: { type: Number, default: 0 },
})
const emit = defineEmits(['update:visible'])

const TYPE_FALLBACK = new Set(['image', 'video', 'audio', 'pdf', 'docx', 'sheet', 'text'])
const kind = computed(() => {
  const byName = previewKindOf(props.fileName)
  if (byName !== 'unsupported') return byName
  return TYPE_FALLBACK.has(props.type) ? props.type : 'unsupported'
})

const mediaError = ref(false)
watch(() => props.url, () => { mediaError.value = false })

// 【2026-10-17 客户口径】文件在系统里打开=本弹窗(点击文件/下载图标即唤起)。
// 弹窗内的「下载文件」只负责存本地副本: 文件已在系统阅读器里展示着,保存成功给明确提示即可
// (此前一度做过"下载后新标签页打开",按用户口径"要在系统里面打开"收敛回系统阅读器本身)。
const downloading = ref(false)

async function download() {
  if (!props.url || downloading.value) return
  downloading.value = true
  try {
    await downloadFile(props.url, { name: props.fileName || 'file', headers: { Authorization: 'Bearer ' + (getToken() || '') } })
    ElMessage.success(`已下载：${props.fileName || '文件'}`)
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.preview-body {
  height: 82vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  text-align: center;
}
/* 文档类渲染器自己撑满高度并内部滚动；媒体类居中展示 */
.preview-body.kind-pdf,
.preview-body.kind-docx,
.preview-body.kind-sheet,
.preview-body.kind-text {
  align-items: stretch;
  overflow: hidden;
}
.preview-body.kind-pdf > *,
.preview-body.kind-docx > *,
.preview-body.kind-sheet > *,
.preview-body.kind-text > * {
  width: 100%;
}
.pv-image { max-width: 100%; max-height: 80vh; }
.pv-video { max-width: 100%; max-height: 80vh; background: #000; border-radius: 8px; }
.pv-audio-wrap { display: flex; flex-direction: column; align-items: center; gap: 14px; color: var(--el-color-info); }
.pv-audio { width: min(560px, 90%); }
.doc-fallback { padding: 50px 0; color: var(--el-color-info); }
.fallback-name { margin: 14px 0 6px; color: #303133; font-size: 14px; word-break: break-all; }
.fallback-hint { font-size: 12px; max-width: 480px; margin: 0 auto; line-height: 1.7; }
</style>

<style>
/* 对话框整体放大，内容区铺满；非 scoped 因 el-dialog 渲染在组件根之外 */
.file-preview-dlg .el-dialog__body { padding: 10px 16px 0; }
.file-preview-dlg .el-dialog__header { padding-bottom: 8px; }
.file-preview-dlg .el-dialog__title { font-size: 14px; word-break: break-all; }
</style>
