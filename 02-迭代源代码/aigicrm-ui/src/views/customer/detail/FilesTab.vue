<template>
  <section class="files-tab">
    <div class="files-head"><div><h3>客户文件</h3><span class="files-hint">仅显示当前客户已关联且当前账号有权访问的文件</span></div><div class="head-actions"><el-button v-if="canEdit && !readonly" size="small" type="primary" @click="openAttach"><el-icon><Plus /></el-icon>关联文件</el-button><el-button size="small" @click="loadFiles"><el-icon><Refresh /></el-icon>刷新</el-button></div></div>
    <el-empty v-if="!loading && !files.length" description="暂无关联文件" />
    <div v-else v-loading="loading" class="file-list"><div v-for="file in files" :key="file.id" class="file-row"><div class="file-main" @click="previewFile(file)"><el-icon class="file-icon"><Document /></el-icon><span class="file-name" :title="file.fileName">{{ file.fileName || '未命名文件' }}</span><span class="file-type">{{ file.fileType || '文件' }}</span></div><div class="file-actions"><el-button link size="small" @click="previewFile(file)"><el-icon><View /></el-icon>预览</el-button><el-button v-if="canEdit && !readonly" link size="small" type="danger" @click="detachFile(file)"><el-icon><Delete /></el-icon>解除关联</el-button></div></div></div>
    <FilePreview v-model:visible="filePreviewVisible" :url="filePreviewUrl" :type="filePreviewType" :file-name="filePreviewName" />
    <el-dialog v-model="attachVisible" title="关联文件" width="480px"><el-select v-model="selectedFileId" filterable placeholder="选择可访问的文件" style="width:100%"><el-option v-for="file in availableFiles" :key="file.id" :label="file.fileName" :value="file.id" /></el-select><template #footer><el-button @click="attachVisible=false">取消</el-button><el-button type="primary" :disabled="!selectedFileId" @click="attachFile">确认关联</el-button></template></el-dialog>
  </section>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Plus, Refresh, View } from '@element-plus/icons-vue'
import { attachCustomerFile, getCustomerFiles, detachCustomerFile } from '@/api/customer'
import { getFileList } from '@/api/file'
import FilePreview from '@/components/FilePreview.vue'
import { injectCustomerDetail } from '@/composables/useCustomerDetail'
const detail = injectCustomerDetail(); const { customerId, readonly, previewIncentiveFile, filePreviewVisible, filePreviewUrl, filePreviewType, filePreviewName } = detail
const files = ref([]); const availableFiles = ref([]); const loading = ref(false); const attachVisible = ref(false); const selectedFileId = ref(''); const canEdit = computed(() => detail.userStore.hasPermission('customer:edit') && detail.userStore.hasPermission('file:view'))
const loadFiles = async () => { loading.value = true; try { files.value = (await getCustomerFiles(customerId.value)).data || [] } catch (error) { ElMessage.error(error?.response?.data?.message || '客户文件加载失败') } finally { loading.value = false } }
const previewFile = (file) => previewIncentiveFile({ id: file.id, label: file.fileName, type: detail.detectType(file.fileName) })
const openAttach = async () => { try { const response = await getFileList({ current: 1, size: 100 }); const candidates = response.data?.records || response.data || response.records || []; const linked = new Set(files.value.map(file => file.id)); availableFiles.value = candidates.filter(file => !linked.has(file.id)); selectedFileId.value = ''; attachVisible.value = true } catch (error) { ElMessage.error(error?.response?.data?.message || '可关联文件加载失败') } }
const attachFile = async () => { try { await attachCustomerFile(customerId.value, selectedFileId.value); ElMessage.success('已关联'); attachVisible.value = false; await loadFiles() } catch (error) { ElMessage.error(error?.response?.data?.message || '关联文件失败') } }
const detachFile = async (file) => { try { await ElMessageBox.confirm(`确认解除“${file.fileName || '该文件'}”与客户的关联吗？`, '解除关联'); await detachCustomerFile(customerId.value, file.id); files.value = files.value.filter(item => item.id !== file.id); ElMessage.success('已解除关联') } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.message || '解除关联失败') } }
onMounted(loadFiles)
</script>
<style scoped>
.files-tab{padding:4px 0}.files-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.head-actions{display:flex;gap:8px}.files-head h3{margin:0 0 4px;font-size:16px;color:#303133}.files-hint{color:#909399;font-size:12px}.file-list{min-height:120px}.file-row{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 4px;border-bottom:1px solid #f0f1f3}.file-main{min-width:0;display:flex;align-items:center;gap:8px;cursor:pointer}.file-icon{color:#409eff;flex-shrink:0}.file-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#303133;font-size:14px}.file-type{color:#909399;font-size:12px;flex-shrink:0}.file-actions{display:flex;flex-shrink:0}
</style>
