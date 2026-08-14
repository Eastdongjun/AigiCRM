<template>
  <el-dialog v-model="uploadVisible" title="上传文件" width="500px" @close="pendingFiles = []">
    <div
      class="upload-dropzone"
      @click="pickFiles"
      @dragover.prevent
      @drop.prevent="handleDrop"
    >
      <el-icon class="el-icon--upload"><Upload /></el-icon>
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <div v-if="pendingFiles.length" class="upload-pending">
        <div v-for="(f, i) in pendingFiles" :key="i" class="upload-pending-item">
          <span>{{ f.name }}</span><span class="upload-pending-size">{{ formatSize(f.size) }}</span>
        </div>
      </div>
      <div class="el-upload__tip">
        {{ activeTab === 'public' ? '公共文件所有人可见' : '私人文件仅自己可见，占用个人存储配额' }}
      </div>
      <input ref="fileInputRef" type="file" multiple style="display:none" @change="handleFilesChosen" />
    </div>
    <template #footer>
      <el-button @click="uploadVisible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!pendingFiles.length" @click="startUploads">
        开始上传{{ pendingFiles.length ? `（${pendingFiles.length} 个）` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { injectFilePage } from '@/composables/useFilePage'

const { uploadVisible, activeTab, selectedFolderId, formatSize, refreshAll } = injectFilePage()

// 自定义上传状态（不依赖 el-upload 内部回调链——其 on-success 在此环境不触发）
const fileInputRef = ref(null)
const pendingFiles = ref([])
const uploading = ref(false)

const pickFiles = () => fileInputRef.value?.click()
const handleDrop = (e) => {
  const files = e.dataTransfer?.files
  if (files?.length) pendingFiles.value = [...pendingFiles.value, ...Array.from(files)]
}
const handleFilesChosen = (e) => {
  const files = e.target.files
  if (files?.length) pendingFiles.value = [...pendingFiles.value, ...Array.from(files)]
  e.target.value = ''
}

const uploadOne = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('scope', activeTab.value)
  // folderId 为 null 时不携带（后端 Long 类型收到字符串 "null" 会 400）
  if (selectedFolderId.value != null) formData.append('folderId', selectedFolderId.value)
  const res = await request.post('/file/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
  if (!res.data?.id) throw new Error(res.data?.message || '上传失败')
}

const startUploads = async () => {
  if (!pendingFiles.value.length) return
  uploading.value = true
  let ok = 0
  const failedNames = []
  for (const f of pendingFiles.value) {
    try { await uploadOne(f); ok++ }
    catch { failedNames.push(f.name) }
  }
  uploading.value = false
  if (failedNames.length) ElMessage.error(`上传完成：成功 ${ok} 个，失败 ${failedNames.length} 个（${failedNames.join('、')}）`)
  else ElMessage.success(`上传成功 ${ok} 个文件`)
  pendingFiles.value = []
  uploadVisible.value = false
  refreshAll()
}
</script>

<style scoped>
/* 上传拖拽区（自定义实现，替代 el-upload） */
.upload-dropzone {
  border: 1px dashed #c0c4cc;
  border-radius: 8px;
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color .2s, background .2s;
  background: #fafbfc;
}
.upload-dropzone:hover { border-color: #409eff; background: #f5f9ff }
.upload-dropzone .el-icon--upload { font-size: 44px; color: #c0c4cc; margin-bottom: 8px }
.upload-dropzone .el-upload__text { color: #606266; font-size: 13px; margin-bottom: 6px }
.upload-dropzone .el-upload__text em { color: #409eff; font-style: normal }
.upload-dropzone .el-upload__tip { color: #909399; font-size: 12px; margin-top: 8px }
.upload-pending { margin-top: 10px; text-align: left; max-height: 160px; overflow: auto }
.upload-pending-item { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; color: #303133; padding: 4px 8px; background: #f0f2f5; border-radius: 4px; margin-bottom: 4px }
.upload-pending-size { color: #909399; flex-shrink: 0 }
</style>
