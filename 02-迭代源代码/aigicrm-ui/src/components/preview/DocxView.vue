<template>
  <!-- Word(.docx)：docx-preview 在客户端把 OOXML 渲染成分页 DOM（样式/表格/图片基本还原）。
       库只构建 DOM 节点、不执行文档内任何脚本；图片以 data: URL 内嵌（CSP img-src data: 放行）。
       老版 .doc 二进制格式无前端方案，由 previewKindOf 归入 unsupported，不会进到这里。 -->
  <div class="pv-docx">
    <PreviewStatus :loading="loading || parsing" :error="error || parseError" :too-large="tooLarge" loading-text="正在渲染文档…" />
    <div ref="bodyEl" class="pv-docx-body" v-show="rendered"></div>
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

const { loading, error, tooLarge, load, abort } = useFileBlob()
const bodyEl = ref(null)
const rendered = ref(false)
const parsing = ref(false)
const parseError = ref('')
let renderSeq = 0

// docx-preview 渲染参数：分页展示、保留页面宽高、页眉页脚脚注都渲染；修订/批注不渲染（阅读视图）
const RENDER_OPTIONS = {
  className: 'docx',
  inWrapper: true,
  ignoreWidth: false,
  ignoreHeight: false,
  ignoreFonts: false,
  breakPages: true,
  ignoreLastRenderedPageBreak: true,
  experimental: false,
  trimXmlDeclaration: true,
  useBase64URL: true,
  renderHeaders: true,
  renderFooters: true,
  renderFootnotes: true,
  renderEndnotes: true,
  renderChanges: false,
  renderComments: false,
  debug: false,
}

function clear() {
  rendered.value = false
  parseError.value = ''
  if (bodyEl.value) bodyEl.value.replaceChildren()
}

async function render() {
  const seq = ++renderSeq
  clear()
  const blob = await load(props.url, { expect: 'blob', sizeHint: props.sizeHint })
  if (!blob || seq !== renderSeq || !bodyEl.value) return
  parsing.value = true
  try {
    const { renderAsync } = await import('docx-preview')
    if (seq !== renderSeq) return
    await renderAsync(blob, bodyEl.value, undefined, RENDER_OPTIONS)
    if (seq !== renderSeq) return
    rendered.value = true
  } catch (e) {
    if (seq === renderSeq) parseError.value = '文档解析失败：可能不是标准 .docx 文件或已损坏'
  } finally {
    if (seq === renderSeq) parsing.value = false
  }
}

watch(() => props.url, render, { immediate: true })
onBeforeUnmount(() => {
  renderSeq += 1
  abort()
  clear()
})
</script>

<style scoped>
.pv-docx { width: 100%; height: 100%; min-height: 60vh; overflow: auto; background: #e9ebef; border-radius: 8px; }
.pv-docx-body { padding: 16px 0; }
/* docx-preview 产出的页面容器：居中、页间留白、白纸质感 */
.pv-docx-body :deep(.docx-wrapper) { background: transparent; padding: 0 16px; }
.pv-docx-body :deep(.docx-wrapper > section.docx) { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12); margin-bottom: 16px; background: #fff; }
</style>
