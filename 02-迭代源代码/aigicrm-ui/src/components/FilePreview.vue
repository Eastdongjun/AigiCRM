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
      <el-image
        v-if="kind === 'image'"
        :src="url"
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
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Document, VideoCamera, Headset } from '@element-plus/icons-vue'
import { getToken } from '@/utils/token'
import { previewKindOf, unsupportedHintOf } from '@/utils/media-type'

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

// 【2026-10-17 客户需求】点"下载文件"→ 下载完成的那一刻立即在系统里打开给用户看:
//   · 浏览器可直显格式(图片/PDF/文本): 保存的同时,把文件写进预开的新标签页立即展示——
//     不做 tab.location=blob 导航(无头/部分环境静默失败),用同源 document.write 注入
//     img/embed/iframe(经典打印窗模式, 真实浏览器必渲染; 预开空白标签规避异步链弹窗拦截)
//   · Word/Excel 等: 弹窗内本来就是在线预览的,下载仅保存本地并给出明确成功提示
//   · 浏览器打不开的格式(pptx 等): 保存 + 提示从浏览器下载列表用本地程序打开
//   · 后端对文档类统一 Content-Disposition: attachment,故必须走前端 blob 展示
const BROWSER_VIEWABLE = new Set(['image', 'pdf', 'text'])
const downloading = ref(false)
const openObjectUrls = []
watch(() => props.visible, (v) => { if (!v) releaseObjectUrls() })
onBeforeUnmount(releaseObjectUrls)
function releaseObjectUrls() {
  while (openObjectUrls.length) URL.revokeObjectURL(openObjectUrls.pop())
}
function escapeHtml(text) {
  return String(text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function showInTab(tab, objectUrl) {
  const title = escapeHtml(props.fileName || '文件')
  const body =
    kind.value === 'image'
      ? `<body style="margin:0;background:#f5f7fa;display:flex;align-items:center;justify-content:center;min-height:100vh"><img src="${objectUrl}" alt="${title}" style="max-width:100%;max-height:100vh;object-fit:contain"></body>`
      : kind.value === 'pdf'
        ? `<body style="margin:0"><embed src="${objectUrl}" type="application/pdf" style="width:100vw;height:100vh"></body>`
        : `<body style="margin:0"><iframe src="${objectUrl}" title="${title}" style="width:100vw;height:100vh;border:0"></iframe></body>`
  const doc = tab.document
  doc.open()
  doc.write(`<!doctype html><meta charset="utf-8"><title>${title}</title>${body}`)
  doc.close()
}

async function download() {
  if (!props.url || downloading.value) return
  const viewable = BROWSER_VIEWABLE.has(kind.value)
  const tab = viewable ? window.open('about:blank', '_blank') : null
  downloading.value = true
  try {
    const res = await fetch(props.url, { headers: { Authorization: 'Bearer ' + (getToken() || '') } })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const blob = await res.blob()
    const objectUrl = URL.createObjectURL(blob)
    openObjectUrls.push(objectUrl)
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = props.fileName || 'file'
    a.click()
    if (tab) {
      showInTab(tab, objectUrl)
    } else {
      ElMessage.success(`已下载：${props.fileName || '文件'}（浏览器打不开此格式时，可从右上角下载列表用本地程序打开）`)
    }
  } catch (e) {
    if (tab) tab.close()
    ElMessage.error('下载失败：' + (e && e.message ? e.message : '网络异常'))
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
