<template>
  <div class="group-manage">
    <div class="page-header">
      <h2>集团管理</h2>
      <el-button v-if="canAddGroup" type="primary" :icon="Plus" @click="openDialog()">新增集团</el-button>
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
            <el-button v-if="canEditGroup" link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canDeleteGroup" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑集团' : '新增集团'" width="560px">
<!-- 【UI 整改 2026-10-08】字段级校验：:rules + required 星标，报错落在字段下方，替代全局 toast -->
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-form-item label="集团名称" prop="groupName">
          <el-input v-model="editForm.groupName" placeholder="如：青阳耀东集团" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="集团编码" prop="groupCode">
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
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { getGroupList, createGroup, updateGroup, deleteGroup } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canAddGroup = computed(() => userStore.hasPermission('system:group:add'))
const canEditGroup = computed(() => userStore.hasPermission('system:group:edit'))
const canDeleteGroup = computed(() => userStore.hasPermission('system:group:delete'))

const groups = ref([])
const dialogVisible = ref(false)
const saving = ref(false)

const editForm = reactive({
  id: null, groupName: '', groupCode: '', description: '', sortOrder: 0, status: 1
})
// 【UI 整改 2026-10-08】名称类必填字段级校验（与 customer 表单同规范）
const formRef = ref(null)
const formRules = {
  groupName: [{ required: true, message: '请输入集团名称', trigger: 'blur' }],
  groupCode: [
    { required: true, message: '请输入集团编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码须为字母开头的字母/数字/下划线', trigger: 'blur' },
  ],
}

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
  // 打开弹窗清掉上一次校验残留的红字
  nextTick(() => formRef.value?.clearValidate())
}

const handleSave = async () => {
  // 【UI 整改 2026-10-08】改字段级校验，报错定位到字段，弃全局 toast
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
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
    // 【UI 整改 2026-10-08】删除类确认走 confirmAction：红色确认钮 + 取消左置
    await confirmAction(`确定删除集团「${row.groupName}」？删除后不可恢复。`, '删除确认', { confirmText: '删除' })
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
.form-tip { font-size: 12px; color: var(--el-color-info); margin-top: 4px; }
</style>
