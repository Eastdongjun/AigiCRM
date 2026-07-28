<template>
  <div class="dept-manage">
    <div class="page-header">
      <h2>组织架构</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增部门</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="deptTree" stripe border row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="{ 1: 'danger', 2: 'warning', 3: 'primary', 4: 'info' }[row.deptType] || 'info'">
              {{ { 1: '总部', 2: '大区', 3: '办事处', 4: '部门' }[row.deptType] || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大区编码" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.deptType === 2 && row.deptCode">{{ row.deptCode }}</span>
            <span v-else-if="row.deptType === 2" style="color: #f56c6c">未设置</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.leaderName">{{ row.leaderName }}</span>
            <span v-else style="color: #c0c4cc">未设置</span>
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
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openDialog(null, row.id)">添加子部门</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑部门' : '新增部门'" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="上级部门">
          <el-tree-select v-model="editForm.parentId" :data="deptTree" :props="{ label: 'deptName', value: 'id', children: 'children' }" check-strictly placeholder="根节点" style="width:100%" clearable />
        </el-form-item>
        <el-form-item label="部门名称"><el-input v-model="editForm.deptName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="editForm.deptType" style="width:100%">
            <el-option label="总部" :value="1" />
            <el-option label="大区" :value="2" />
            <el-option label="办事处" :value="3" />
            <el-option label="部门" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.deptType === 2" label="大区编码">
          <el-input v-model="editForm.deptCode" placeholder="大区首字母，用作客户编号前缀，如 SX / NM / HB" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="editForm.leaderId" placeholder="选择负责人" style="width:100%" clearable filterable>
            <el-option v-for="u in userList" :key="u.id" :label="u.realName" :value="u.id">
              <span>{{ u.realName }}</span>
              <span style="color:#999;margin-left:8px;font-size:12px">{{ u.roleName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editForm.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeptTree, createDept, updateDept, deleteDept, getUserList } from '@/api/system'

const deptTree = ref([])
const userList = ref([])
const dialogVisible = ref(false)
const editForm = reactive({ id: null, parentId: 0, deptName: '', deptType: 3, deptCode: '', leaderId: null, sortOrder: 0 })

const loadData = async () => {
  try {
    const res = await getDeptTree()
    deptTree.value = res.data || []
  } catch (e) {}
}

const openDialog = (row, parentId) => {
  if (row) {
    Object.assign(editForm, { id: row.id, parentId: row.parentId, deptName: row.deptName, deptType: row.deptType, deptCode: row.deptCode || '', leaderId: row.leaderId, sortOrder: row.sortOrder })
  } else {
    Object.assign(editForm, { id: null, parentId: parentId || 0, deptName: '', deptType: 3, deptCode: '', leaderId: null, sortOrder: 0 })
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (editForm.id) {
      await updateDept(editForm.id, editForm)
    } else {
      await createDept(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {}
}

const handleDelete = async (row) => {
  if (row.children && row.children.length > 0) {
    ElMessage.warning('该部门下存在子部门，请先删除或转移子部门')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除部门「${row.deptName}」？`, '提示', { type: 'warning' })
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

const loadUsers = async () => {
  try {
    const res = await getUserList({ current: 1, size: 100 })
    userList.value = res.data?.records || []
  } catch (e) {}
}

onMounted(() => {
  loadData()
  loadUsers()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
</style>
