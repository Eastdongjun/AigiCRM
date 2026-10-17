<template>
  <!-- PDF：浏览器内建阅读器直连下载 URL 流式渲染(2026-10-17)。
       直链的前提 = 后端 PDF Content-Disposition inline + nginx 下载路径 XFO SAMEORIGIN/CSP frame-ancestors self
       (见 FileService.SAFE_INLINE_EXT 与 nginx 配置下载 location)——浏览器阅读器按 Range 分段边下边看,
       23.5MB 的 PDF 首屏从"全量下载完"降到秒级。
       看门狗: 8s 内 iframe 未触发 load(如部署序错位/头被回退)自动回落旧的"整包下载→blob"路径,预览不坏。 -->
  <div class="pv-pdf">
    <PreviewStatus :loading="loading" :error="error" :too-large="tooLarge" loading-text="正在加载 PDF…" />
    <iframe
      v-if="src"
      :src="src"
      class="pv-pdf-frame"
      :title="fileName || 'PDF 文件'"
      @load="onFrameLoad"
    ></iframe>
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useFileBlob } from '@/composables/useFileBlob'
import PreviewStatus from './PreviewStatus.vue'

const props = defineProps({
  url: { type: String, default: '' },
  fileName: { type: String, default: '' },
  sizeHint: { type: Number, default: 0 },
})

const { loading, error, tooLarge, load } = useFileBlob()
const src = ref('')
const loaded = ref(false)
let objectUrl = ''
let watchdog = null

function clearWatchdog() {
  if (watchdog) { clearTimeout(watchdog); watchdog = null }
}
function revoke() {
  clearWatchdog()
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
  src.value = ''
  loaded.value = false
}

function onFrameLoad() {
  if (!src.value) return
  clearWatchdog()
  loaded.value = true
  loading.value = false
}

// 兜底: 直链迟迟未 load → 回落整包下载→blob(老路径, 任何环境下都可用)
async function fallbackToBlob() {
  if (loaded.value || !props.url) return
  const blob = await load(props.url, { expect: 'blob', sizeHint: props.sizeHint })
  if (!blob || loaded.value) return
  // 后端按扩展名给 Content-Type；万一拿到 octet-stream 则强制 application/pdf，否则浏览器会当下载而非渲染
  const pdfBlob = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' })
  objectUrl = URL.createObjectURL(pdfBlob)
  src.value = objectUrl
}

async function render() {
  revoke()
  if (!props.url) return
  loading.value = false
  error.value = false
  // 主路径: 直链流式(inline + Range; 浏览器阅读器自行分段取数,首屏秒级)
  src.value = props.url
  // 看门狗: 8s 未 load 视为直链不可用(头未生效/被代理改写), 自动回落 blob
  clearWatchdog()
  watchdog = setTimeout(() => { if (!loaded.value) fallbackToBlob() }, 8000)
}

watch(() => props.url, render, { immediate: true })
onBeforeUnmount(revoke)
</script>

<style scoped>
.pv-pdf { width: 100%; height: 100%; min-height: 60vh; }
.pv-pdf-frame { width: 100%; height: 100%; min-height: 60vh; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: #fff; display: block; }
</style>
