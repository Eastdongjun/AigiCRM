<template>
  <el-dialog v-model="shareDialogVisible" title="分享文件" width="450px">
    <p style="margin-bottom:12px;color: var(--text-muted);font-size:13px">
      分享 <b>{{ shareFileTarget?.fileName }}</b> 给其他用户（只读权限）
    </p>
    <el-select v-model="shareUserIds" multiple filterable placeholder="选择用户..." style="width:100%">
      <el-option v-for="u in allUsers" :key="u.id" :label="u.realName" :value="u.id" />
    </el-select>
    <template #footer>
      <el-button @click="shareDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmShare">确认分享</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { shareFile } from '@/api/file'
import { getSimpleUserList } from '@/api/customer'
import { injectFilePage } from '@/composables/useFilePage'

const { shareDialogVisible, shareFileTarget, loadTabCounts } = injectFilePage()
const allUsers = ref([])
const shareUserIds = ref([])

// 打开时清空已选并懒加载用户列表（用户列表仅分享弹窗使用，留在组件内）
watch(shareDialogVisible, async (v) => {
  if (v) {
    shareUserIds.value = []
    if (!allUsers.value.length) {
      try { allUsers.value = (await getSimpleUserList()).data || [] } catch (e) {}
    }
  }
})

const confirmShare = async () => {
  if (!shareUserIds.value.length) return
  try {
    await shareFile(shareFileTarget.value.id, shareUserIds.value)
    ElMessage.success('文件已分享')
    shareDialogVisible.value = false
    loadTabCounts()
  } catch (e) {}
}
</script>
