<template>
  <div class="group-manage">
    <div class="page-header">
      <h2>集团管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增集团</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="groups" stripe border>
        <el-table-column prop="groupName" label="集团名称" width="200" show-overflow-tooltip />
        <el-table-column prop="groupCode" label="集团编码" width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑集团' : '新增集团'" width="560px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="集团名称" required>
          <el-input v-model="editForm.groupName" placeholder="如：青阳耀东集团" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="集团编码" required>
          <el-input v-model="editForm.groupCode" :disabled="!!editForm.id" placeholder="唯一编码，如：QYYD" maxlength="50" />
          <div class="form-tip">唯一编码，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="集团说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getGroupList, createGroup, updateGroup, deleteGroup } from '@/api/system'

const groups = ref([])
const dialogVisible = ref(false)
const saving = ref(false)

const editForm = reactive({
  id: null, groupName: '', groupCode: '', description: '', sortOrder: 0, status: 1
})

const loadData = async () => {
  try {
    const res = await getGroupList()
    groups.value = res.data || []
  } catch (e) {}
}

const openDialog = (row) => {
  if (row) {
    Object.assign(editForm, row)
  } else {
    Object.assign(editForm, {
      id: null, groupName: '', groupCode: '', description: '', sortOrder: 0, status: 1
    })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!editForm.groupName.trim()) { ElMessage.warning('请填写集团名称'); return }
  if (!editForm.groupCode.trim()) { ElMessage.warning('请填写集团编码'); return }
  saving.value = true
  try {
    if (editForm.id) {
      await updateGroup(editForm.id, editForm)
    } else {
      await createGroup(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除集团 ${row.groupName}？`, '提示')
    await deleteGroup(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
