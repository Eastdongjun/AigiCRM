<template>
  <!-- 文件预览器(FEAT 0804):图片可放大预览(el-image 内置预览器,支持缩放/旋转),视频可播放,文档显示下载入口;所有类型均可下载 -->
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="文件预览"
    width="80%"
    top="4vh"
    :close-on-click-modal="true"
    destroy-on-close
  >
    <div style="text-align:center">
      <!-- 图片:el-image 点击放大(自带缩放/旋转工具栏) -->
      <el-image
        v-if="type === 'image'"
        :src="url"
        :preview-src-list="[url]"
        :initial-index="0"
        fit="contain"
        style="max-width:100%;max-height:72vh"
        preview-teleported
      />
      <!-- 视频 -->
      <video
        v-else-if="type === 'video'"
        :src="url"
        controls
        autoplay
        style="max-width:100%;max-height:72vh;background:#000;border-radius:8px"
      />
      <!-- 文档:显示文件名,提供下载 -->
      <div v-else style="padding:50px 0;color:#909399">
        <el-icon :size="48"><Document /></el-icon>
        <div style="margin:14px 0 6px;color:#303133;font-size:14px;word-break:break-all">{{ fileName || '文件' }}</div>
        <div style="font-size:12px">点击下方"下载文件"保存</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button type="primary" @click="download"><el-icon><Download /></el-icon> 下载文件</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Download, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/token'
import { downloadFile } from '@/utils/download'

const props = defineProps({
  visible: { type: Boolean, default: false },
  url: { type: String, default: '' },
  type: { type: String, default: 'image' },
  fileName: { type: String, default: '' }
})
const emit = defineEmits(['update:visible'])

// 【需求】文件下载(FEAT 0804):window.open 在新标签闪开/被拦截时表现为"没反应";
// 改为 fetch blob + a.download 强制保存（F4: 统一走 downloadFile，带超时/失败提示）
async function download() {
  if (!props.url) return
  await downloadFile(props.url, { name: props.fileName || 'file', headers: { 'Authorization': 'Bearer ' + (getToken() || '') } })
}
</script>
