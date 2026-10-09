<template>
  <!-- 文件预览器:图片/PDF/视频优先直接预览,Office 优先浏览器直开,失败再下载。 -->
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="文件预览"
    width="80%"
    top="4vh"
    :close-on-click-modal="true"
    destroy-on-close
  >
    <div class="preview-body">
      <el-image
        v-if="type === 'image'"
        :src="url"
        :preview-src-list="[url]"
        :initial-index="0"
        fit="contain"
        style="max-width:100%;max-height:72vh"
        preview-teleported
      />
      <video
        v-else-if="type === 'video'"
        :src="url"
        controls
        autoplay
        style="max-width:100%;max-height:72vh;background:#000;border-radius:8px"
      />
      <iframe
        v-else-if="type === 'pdf'"
        class="pdf-frame"
        :src="url"
        :title="fileName || 'PDF 文件'"
      />
      <div v-else class="doc-fallback">
        <el-icon :size="48"><Document /></el-icon>
        <div style="margin:14px 0 6px;color:#303133;font-size:14px;word-break:break-all">{{ fileName || '文件' }}</div>
        <div style="font-size:12px">{{ type === 'office' ? 'Office 文件将优先在新窗口打开，浏览器不支持时请下载查看' : '当前格式暂不支持内嵌预览，可下载查看' }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
      <el-button v-if="type === 'office' || type === 'file'" @click="openInBrowser"><el-icon><Document /></el-icon> 直接打开</el-button>
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

async function openInBrowser() {
  if (!props.url) return
  // 【M11 2026-10-04】此前 window.open(url, '_blank', 'noopener')：按 HTML 规范，
  // 第三参 features 含 noopener 时返回值恒为 null，"被拦截"判断永远成立，
  // 导致每次都弹假警告并强制重复下载。改为不带 features 调用——只有真被弹窗拦截
  // 时才返回 null；打开成功后手动置空 opener，补回 noopener 的防逆向标签页引用语义。
  const opened = window.open(props.url, '_blank')
  if (opened) {
    opened.opener = null
  } else {
    ElMessage.warning('浏览器拦截了新窗口，已改为下载文件')
    await download()
  }
}
</script>

<style scoped>
.preview-body {
  text-align: center;
}

.pdf-frame {
  width: 100%;
  height: 72vh;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.doc-fallback {
  padding: 50px 0;
  color: var(--el-color-info);
}
</style>
