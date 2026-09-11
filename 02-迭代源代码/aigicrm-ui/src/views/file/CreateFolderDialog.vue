<template>
  <el-dialog v-model="createFolderVisible" title="新建文件夹" width="400px">
    <el-form>
      <el-form-item label="文件夹名称">
        <el-input v-model="newFolderName" placeholder="请输入文件夹名称" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createFolderVisible = false">取消</el-button>
      <el-button type="primary" @click="handleCreateFolder">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createFolder } from '@/api/file'
import { injectFilePage } from '@/composables/useFilePage'

const { createFolderVisible, activeTab, selectedFolderId, loadFolders } = injectFilePage()
const newFolderName = ref('')

const handleCreateFolder = async () => {
  if (!newFolderName.value.trim()) return
  try {
    await createFolder({
      folderName: newFolderName.value,
      folderType: activeTab.value,
      parentId: selectedFolderId.value || 0
    })
    ElMessage.success('文件夹创建成功')
    createFolderVisible.value = false
    newFolderName.value = ''
    loadFolders()
  } catch (e) {}
}
</script>
