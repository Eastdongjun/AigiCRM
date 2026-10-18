<template>
  <el-dialog v-model="showImportDialog" title="批量导入客户" width="500px" class="import-dialog" :close-on-click-modal="false" @close="closeImportDialog">
    <div class="import-step"><div class="step-label">1. 下载模板</div>
      <el-button type="primary" plain size="small" @click="downloadTemplate">下载导入模板 (.xlsx)</el-button></div>
    <div class="import-step"><div class="step-label">2. 选择文件</div>
      <el-upload ref="uploadRef" :auto-upload="false" :limit="1" :on-exceed="handleExceed" accept=".xlsx,.xls" :on-change="handleFileChange" :on-remove="handleFileRemove" drag>
        <el-icon class="el-icon--upload"><Upload /></el-icon><div class="el-upload__text">拖拽文件或<em>点击上传</em></div>
      </el-upload></div>
    <div v-if="importSummary" class="import-result">
      <el-alert :title="importSummary.title" :type="importSummary.type" :closable="false" show-icon />
      <div v-if="importSummary.failures.length" class="fail-list">
        <div class="fail-header">失败明细</div>
        <div v-for="(item, idx) in importSummary.failures" :key="idx" class="fail-item">
          <span class="fail-row">第{{ item.row }}行</span><span class="fail-name">{{ item.name }}</span><span class="fail-reason">{{ item.reason }}</span>
        </div>
      </div>
      <!-- 【D3】不阻断导入的兜底提示（如注册日缺失按导入日计算保护期） -->
      <div v-if="importSummary.warnings.length" class="fail-list">
        <div class="fail-header">提示</div>
        <div v-for="(item, idx) in importSummary.warnings" :key="'w' + idx" class="fail-item">
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
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { importCustomers, downloadImportTemplate } from '@/api/customer'
import { injectCustomerList } from '@/composables/useCustomerList'

const { showImportDialog, refreshAll } = injectCustomerList()

const importFile = ref(null)
const importLoading = ref(false)
const importResult = ref(null)
const uploadRef = ref(null)

// 【D3 2026-10-18】后端返回 {successCount,failCount,totalCount,failures,warnings}；
// 原页面读 title/type/failList 与后端字段不一致，导致导入结果不展示。这里统一映射，
// 兼容旧 failList 字段，并把“注册日缺失已兜底”等提示与失败明细分区展示。
const importSummary = computed(() => {
  const r = importResult.value
  if (!r) return null
  const failures = (r.failures || r.failList || []).map(item => ({
    row: item.rowNum ?? item.row,
    name: item.customerName ?? item.name ?? '',
    reason: item.reason || ''
  }))
  const warnings = (r.warnings || []).map(item => ({
    row: item.rowNum ?? item.row,
    name: item.customerName ?? item.name ?? '',
    reason: item.reason || ''
  }))
  const success = Number(r.successCount) || 0
  const fail = Number(r.failCount) || failures.length
  const total = Number(r.totalCount) || success + fail
  return {
    failures,
    warnings,
    title: `导入完成：成功 ${success} 条，失败 ${fail} 条，共 ${total} 条${warnings.length ? `；另有 ${warnings.length} 条提示` : ''}`,
    type: fail > 0 || warnings.length > 0 ? 'warning' : 'success'
  }
})

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
