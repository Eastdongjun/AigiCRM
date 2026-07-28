<template>
  <div class="incentive-category-manage">
    <div class="page-header">
      <h2>激励类别管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增类别</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="categoryTree" stripe border row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="categoryName" label="类别名称" min-width="200" />
        <el-table-column prop="categoryCode" label="编码" width="140" />
        <el-table-column label="分值" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.score > 0">{{ row.score }}分</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" size="small" v-if="row.parentId === 0" @click="openDialog(null, row.id)">添加子项</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑类别' : '新增类别'" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="上级类别">
          <el-tree-select
            v-model="editForm.parentId"
            :data="parentOptions"
            :props="{ label: 'categoryName', value: 'id', children: 'children' }"
            check-strictly
            placeholder="无（作为顶级类别）"
            style="width:100%"
            clearable
          />
        </el-form-item>
        <el-form-item label="类别名称">
          <el-input v-model="editForm.categoryName" placeholder="如：外出参观、涂层类" />
        </el-form-item>
        <el-form-item label="类别编码">
          <el-input v-model="editForm.categoryCode" placeholder="如：visit、trial_coating" />
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="editForm.score" :min="0" :max="100" />
          <span class="form-tip">顶级类别分值为0，子项设置实际分值</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '@/api/incentive'

const categoryTree = ref([])
const dialogVisible = ref(false)
const editForm = reactive({
  id: null,
  parentId: 0,
  categoryName: '',
  categoryCode: '',
  score: 0,
  sortOrder: 0,
  status: 1
})

// 上级选择只显示顶级类别（不允许三级嵌套）
const parentOptions = computed(() =>
  categoryTree.value.map(c => ({ id: c.id, categoryName: c.categoryName }))
)

const loadData = async () => {
  try {
    const res = await getCategoryTree()
    categoryTree.value = res.data || []
  } catch (e) {}
}

const openDialog = (row, parentId) => {
  if (row) {
    Object.assign(editForm, {
      id: row.id,
      parentId: row.parentId,
      categoryName: row.categoryName,
      categoryCode: row.categoryCode,
      score: row.score,
      sortOrder: row.sortOrder,
      status: row.status
    })
  } else {
    Object.assign(editForm, {
      id: null,
      parentId: parentId || 0,
      categoryName: '',
      categoryCode: '',
      score: parentId ? 1 : 0,
      sortOrder: 0,
      status: 1
    })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!editForm.categoryName.trim()) {
    ElMessage.warning('请输入类别名称')
    return
  }
  try {
    if (editForm.id) {
      await updateCategory(editForm.id, editForm)
    } else {
      await createCategory(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {}
}

const handleDelete = async (row) => {
  const hasChildren = row.children && row.children.length > 0
  const msg = hasChildren
    ? `"${row.categoryName}" 下有 ${row.children.length} 个子项，确认删除？`
    : `确认删除 "${row.categoryName}"？`
  try {
    await ElMessageBox.confirm(msg, '删除确认', { type: 'warning' })
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 20px; }
.text-muted { color: #c0c4cc; }
.form-tip { color: #999; font-size: 12px; margin-left: 8px; }
</style>
