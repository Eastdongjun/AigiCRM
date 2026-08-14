<template>
  <el-dialog v-model="renameFolderVisible" title="重命名文件夹" width="400px">
    <el-form>
      <el-form-item label="文件夹名称">
        <el-input v-model="renameFolderName" placeholder="请输入文件夹名称" @keyup.enter="handleRenameFolder" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="renameFolderVisible = false">取消</el-button>
      <el-button type="primary" @click="handleRenameFolder">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { renameFolder } from '@/api/file'
import { injectFilePage } from '@/composables/useFilePage'

const { renameFolderVisible, renameFolderTarget, loadFolders } = injectFilePage()
const renameFolderName = ref('')

// 打开时同步当前文件夹名称（目标/显隐任一变化都触发，覆盖连续重命名同一文件夹的场景）
watch([renameFolderVisible, renameFolderTarget], () => {
  if (renameFolderVisible.value && renameFolderTarget.value) {
    renameFolderName.value = renameFolderTarget.value.folderName || ''
  }
})

const handleRenameFolder = async () => {
  const name = renameFolderName.value.trim()
  if (!name) return
  try {
    await renameFolder(renameFolderTarget.value.id, name)
    ElMessage.success('重命名成功')
    renameFolderVisible.value = false
    loadFolders()
  } catch (e) {}
}
</script>
