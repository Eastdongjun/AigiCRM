<template>
  <el-dialog v-model="showImportDialog" title="批量导入客户" width="500px" class="import-dialog" :close-on-click-modal="false" @close="closeImportDialog">
    <div class="import-step"><div class="step-label">1. 下载模板</div>
      <el-button type="primary" plain size="small" @click="downloadTemplate">下载导入模板 (.xlsx)</el-button></div>
    <div class="import-step"><div class="step-label">2. 选择文件</div>
      <el-upload ref="uploadRef" :auto-upload="false" :limit="1" :on-exceed="handleExceed" accept=".xlsx,.xls" :on-change="handleFileChange" :on-remove="handleFileRemove" drag>
        <el-icon class="el-icon--upload"><Upload /></el-icon><div class="el-upload__text">拖拽文件或<em>点击上传</em></div>
      </el-upload></div>
    <div v-if="importResult" class="import-result">
      <el-alert :title="importResult.title" :type="importResult.type" :closable="false" show-icon />
      <div v-if="importResult.failList && importResult.failList.length" class="fail-list">
        <div class="fail-header">失败明细</div>
        <div v-for="(item, idx) in importResult.failList" :key="idx" class="fail-item">
          <span class="fail-row">第{{ item.row }}行</span><span class="fail-name">{{ item.name }}</span><span class="fail-reason">{{ item.reason }}</span>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="showImportDialog = false">取消</el-button>
      <el-button type="primary" @click="doImport" :disabled="!importFile" :loading="importLoading">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { importCustomers, downloadImportTemplate } from '@/api/customer'
import { injectCustomerList } from '@/composables/useCustomerList'

const { showImportDialog, refreshAll } = injectCustomerList()

const importFile = ref(null)
const importLoading = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

const handleFileChange = (file) => {
  importFile.value = file.raw
}

// 【工作流复核确认修复】移除已选文件时清空引用: el-upload 的 on-remove 不触发 on-change,
// 原实现 importFile 残留旧文件, "开始导入"仍可点并把用户已移除的文件导入
const handleFileRemove = () => {
  importFile.value = null
}

// 超出 limit=1 时的提示（原脚本定义了处理函数但模板未接线，本次接上）
const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先移除已选文件')
}

const downloadTemplate = async () => {
  try {
    await downloadImportTemplate()
    ElMessage.success('模板下载成功')
  } catch (e) {
    ElMessage.error('模板下载失败')
  }
}

const doImport = async () => {
  if (!importFile.value) return
  importLoading.value = true
  importResult.value = null
  try {
    const res = await importCustomers(importFile.value)
    importResult.value = res.data
    if (res.data.successCount > 0) {
      refreshAll()
    }
  } catch (e) {
    // 错误已由 request 拦截器提示
  } finally {
    importLoading.value = false
  }
}

// 关闭时重置文件与结果，避免重开时残留上一次状态（原脚本定义了但模板未接线，本次接上）
const closeImportDialog = () => {
  importFile.value = null
  importResult.value = null
  if (uploadRef.value) uploadRef.value.clearFiles()
}
</script>
