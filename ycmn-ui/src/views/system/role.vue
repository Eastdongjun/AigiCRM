<template>
  <div class="role-manage">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增角色</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="roles" stripe border>
        <el-table-column prop="roleName" label="角色名称" width="150" />
        <el-table-column prop="roleCode" label="角色编码" width="150" />
        <el-table-column prop="description" label="描述" min-width="150" />
        <el-table-column label="数据范围" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="{ all: 'danger', dept_and_children: 'warning', dept: '', self: 'info' }[row.dataScope] || 'info'">
              {{ { all: '全部数据', dept_and_children: '本部门及下级', dept: '本部门', self: '仅自己' }[row.dataScope] || '仅自己' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openPermDialog(row)">权限</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑角色' : '新增角色'" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="角色名称"><el-input v-model="editForm.roleName" /></el-form-item>
        <el-form-item label="角色编码"><el-input v-model="editForm.roleCode" :disabled="!!editForm.id" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="editForm.dataScope" style="width:100%">
            <el-option label="全部数据" value="all" />
            <el-option label="本部门及下级" value="dept_and_children" />
            <el-option label="本部门" value="dept" />
            <el-option label="仅自己" value="self" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配对话框 -->
    <el-dialog v-model="permDialogVisible" title="权限分配" width="500px">
      <el-tree
        ref="menuTreeRef"
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        show-checkbox
        node-key="id"
        default-expand-all
        :default-checked-keys="checkedMenuIds"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePerm">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleList, getRole, createRole, updateRole, deleteRole, getMenuTree } from '@/api/system'

const roles = ref([])
const menuTree = ref([])
const dialogVisible = ref(false)
const permDialogVisible = ref(false)
const menuTreeRef = ref(null)
const checkedMenuIds = ref([])
const currentRoleId = ref(null)

const editForm = reactive({ id: null, roleName: '', roleCode: '', description: '', dataScope: 'self', sortOrder: 0 })

const loadData = async () => {
  try {
    const res = await getRoleList()
    roles.value = res.data || []
  } catch (e) {}
}

const openDialog = (row) => {
  if (row) {
    Object.assign(editForm, row)
  } else {
    Object.assign(editForm, { id: null, roleName: '', roleCode: '', description: '', dataScope: 'self', sortOrder: 0 })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (editForm.id) {
      await updateRole(editForm.id, editForm)
    } else {
      await createRole(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {}
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除角色 ${row.roleName}？`, '提示')
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

const openPermDialog = async (row) => {
  currentRoleId.value = row.id
  try {
    const res = await getRole(row.id)
    checkedMenuIds.value = res.data?.menuIds || []
  } catch (e) {}
  try {
    const res = await getMenuTree()
    menuTree.value = res.data || []
  } catch (e) {}
  permDialogVisible.value = true
}

const handleSavePerm = async () => {
  const checkedKeys = menuTreeRef.value.getCheckedKeys()
  const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys()
  const menuIds = [...checkedKeys, ...halfCheckedKeys]
  try {
    await updateRole(currentRoleId.value, { menuIds })
    ElMessage.success('权限保存成功')
    permDialogVisible.value = false
  } catch (e) {}
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
</style>
