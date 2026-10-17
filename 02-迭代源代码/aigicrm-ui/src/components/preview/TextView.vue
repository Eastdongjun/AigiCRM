<template>
  <!-- 纯文本（txt/md/log/json/xml）：文本插值渲染（Vue 转义，无 HTML 注入面）；超长文本截断提示下载。 -->
  <div class="pv-text">
    <PreviewStatus :loading="loading" :error="error" :too-large="tooLarge" loading-text="正在加载文本…" />
    <template v-if="text !== null">
      <div v-if="truncated" class="pv-text-note">文本过长，仅显示前 {{ maxCharsText }} 字符；完整内容请下载查看</div>
      <pre class="pv-text-body">{{ text }}</pre>
    </template>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useFileBlob } from '@/composables/useFileBlob'
import PreviewStatus from './PreviewStatus.vue'

const props = defineProps({
  url: { type: String, default: '' },
  fileName: { type: String, default: '' },
  sizeHint: { type: Number, default: 0 },
})

const MAX_CHARS = 2_000_000
const maxCharsText = '200 万'
const { loading, error, tooLarge, load } = useFileBlob()
const text = ref(null)
const truncated = ref(false)

async function render() {
  text.value = null
  truncated.value = false
  const t = await load(props.url, { expect: 'text', sizeHint: props.sizeHint })
  if (t === null) return
  if (t.length > MAX_CHARS) {
    truncated.value = true
    text.value = t.slice(0, MAX_CHARS)
  } else {
    text.value = t
  }
}

watch(() => props.url, render, { immediate: true })
</script>

<style scoped>
.pv-text { width: 100%; height: 100%; min-height: 60vh; overflow: auto; background: #fff; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; text-align: left; }
.pv-text-note { padding: 8px 16px; font-size: 12px; color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.pv-text-body { margin: 0; padding: 20px 24px; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; line-height: 1.8; color: #1f2937; white-space: pre-wrap; word-break: break-word; }
</style>
