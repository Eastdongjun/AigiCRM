<template>
  <!-- 四个渲染器共用的状态块：加载中 / 失败 / 超过体积阈值（文件阅读器 2026-10-16） -->
  <div v-if="loading" class="pv-status">
    <el-icon class="is-loading" :size="22"><Loading /></el-icon>
    <span>{{ loadingText }}</span>
  </div>
  <div v-else-if="tooLarge" class="pv-status pv-status--warn">
    <el-icon :size="30"><WarningFilled /></el-icon>
    <div class="pv-status-title">文件超过 {{ limitText }}，不做在线预览</div>
    <div class="pv-status-hint">浏览器内解析大文件会长时间卡顿，请使用下方「下载文件」后本地打开</div>
  </div>
  <div v-else-if="error" class="pv-status pv-status--error">
    <el-icon :size="30"><CircleCloseFilled /></el-icon>
    <div class="pv-status-title">{{ error }}</div>
    <div class="pv-status-hint">可尝试重新打开，或下载后查看</div>
  </div>
</template>

<script setup>
import { Loading, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { previewSizeLimitText } from '@/composables/useFileBlob'

defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  tooLarge: { type: Boolean, default: false },
  loadingText: { type: String, default: '正在加载文件…' },
})
const limitText = previewSizeLimitText()
</script>

<style scoped>
.pv-status { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 48px 20px; color: var(--el-text-color-secondary); font-size: 13px; }
.pv-status--warn { color: var(--el-color-warning); }
.pv-status--error { color: var(--el-color-danger); }
.pv-status-title { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.pv-status-hint { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
