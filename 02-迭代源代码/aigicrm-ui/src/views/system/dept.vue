<template>
  <div class="dept-manage">
    <div class="section-box" style="display:flex;align-items:center;justify-content:space-between;padding:12px 20px">
      <span style="font-size:15px;font-weight:700;color:#1f2329">组织架构</span>
      <el-button v-if="canEditDept" type="primary" :icon="Plus" @click="openDialog()">新增部门</el-button>
    </div>

    <div class="section-box table-box">
      <div style="flex:1;overflow:auto">
      <el-table :data="deptTree" row-key="id" default-expand-all :tree-props="{ children: 'children' }" size="small">
        <el-table-column prop="deptName" label="部门名称" min-width="160" />
        <el-table-column label="类型" width="75" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="{ 1: 'danger', 2: 'warning', 3: 'primary', 4: 'info' }[row.deptType] || 'info'">
              {{ { 1: '总部', 2: '大区', 3: '办事处', 4: '部门' }[row.deptType] || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大区编码" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.deptType === 2 && row.deptCode">{{ row.deptCode }}</span>
            <span v-else-if="row.deptType === 2" style="color: #f56c6c">未设置</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="80" align="center">
          <template #default="{ row }"><span v-if="row.leaderName">{{ row.leaderName }}</span><span v-else style="color:#c0c4cc">—</span></template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
        <el-table-column label="状态" width="65" align="center">
          <template #default="{ row }"><el-tag :type="row.status===1?'success':'danger'" size="small">{{ row.status===1?'正常':'禁用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center">
          <template #default="{ row }">
            <el-button v-if="canEditDept" link type="primary" size="small" @click="showAdmins(row)">管理{{ adminCounts[row.id] ? ` (${adminCounts[row.id]})` : '' }}</el-button>
            <el-button v-if="canEditDept" link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canEditDept" link type="primary" size="small" @click="openDialog(null, row.id)">添加子部门</el-button>
            <el-button v-if="canEditDept" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑部门' : '新增部门'" width="500px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="上级部门">
          <el-tree-select v-model="editForm.parentId" :data="deptTree" :props="{ label: 'deptName', value: 'id', children: 'children' }" check-strictly placeholder="根节点" style="width:100%" clearable />
        </el-form-item>
        <el-form-item label="部门名称"><el-input v-model="editForm.deptName" /></el-form-item>
        <el-form-item label="集团归属" prop="groupId" required>
          <el-select v-model="editForm.groupId" placeholder="选择所属集团" style="width:100%">
            <el-option v-for="g in groupList" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="组织类型" prop="deptType" required>
          <el-select v-model="editForm.deptType" style="width:100%">
            <el-option label="公司/总部" :value="1" />
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

    <!-- 组织管理员弹窗 -->
    <el-dialog v-model="adminDialogVisible" title="组织管理员" width="500px">
      <el-form :inline="true">
        <el-form-item label="添加用户">
          <el-select v-model="selectedUserId" filterable placeholder="搜索用户"
            :filter-method="searchUsers" @focus="searchUsers('')">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
          <el-button type="primary" @click="doAddAdmin" style="margin-left: 8px">添加</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="currentAdmins" border stripe>
        <el-table-column prop="userName" label="用户名" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定移除？" @confirm="doRemoveAdmin(row)">
              <template #reference>
                <el-button link type="danger" size="small">移除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeptTree, createDept, updateDept, deleteDept, getUserList, getGroupList, getDeptAdmins, addDeptAdmin, removeDeptAdmin } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canEditDept = computed(() => userStore.hasPermission('system:dept:edit'))

const deptTree = ref([])
const userList = ref([])
const groupList = ref([])
const dialogVisible = ref(false)
const editForm = reactive({ id: null, parentId: 0, groupId: null, deptName: '', deptType: 1, deptCode: '', leaderId: null, sortOrder: 0 })

// 组织管理员
const adminDialogVisible = ref(false)
const currentOrg = ref(null)
const currentAdmins = ref([])
const selectedUserId = ref(null)
const userOptions = ref([])
const adminCounts = reactive({})

const loadData = async () => {
  try {
    const res = await getDeptTree()
    deptTree.value = res.data || []
    // 加载管理员计数
    // 【性能 2026-10-04】各组织的管理员数互不依赖，串行 for 改为 Promise.all 并发（参照 reminder 页 2026-08-08 改法）；
    // 单组织失败记 0，不影响其余组织
    const nodes = flattenTree(deptTree.value)
    await Promise.all(nodes.map(async (node) => {
      try {
        const r = await getDeptAdmins(node.id)
        adminCounts[node.id] = (r.data || []).length
      } catch (e) { adminCounts[node.id] = 0 }
    }))
  } catch (e) {}
}

const openDialog = (row, parentId) => {
  if (row) {
    Object.assign(editForm, { id: row.id, parentId: row.parentId, groupId: row.groupId ?? null, deptName: row.deptName, deptType: row.deptType, deptCode: row.deptCode || '', leaderId: row.leaderId, sortOrder: row.sortOrder })
  } else {
    // 新建：按父节点类型预选下级类型（根节点默认为公司/总部）
    const pid = parentId || 0
    const parent = flattenTree(deptTree.value).find(n => n.id === pid)
    const defaultType = parent ? (parent.deptType + 1) : 1
    Object.assign(editForm, { id: null, parentId: pid, groupId: parent?.groupId ?? null, deptName: '', deptType: defaultType, deptCode: '', leaderId: null, sortOrder: 0 })
  }
  dialogVisible.value = true
}

// 组织层级校验：1=公司/总部 2=大区 3=办事处 4=部门；子级类型必须等于父级类型+1
const typeRank = { 1: 0, 2: 1, 3: 2, 4: 3 }
const flattenTree = (nodes, acc = []) => {
  for (const n of nodes || []) {
    acc.push(n)
    if (n.children?.length) flattenTree(n.children, acc)
  }
  return acc
}
const validateDeptHierarchy = () => {
  const pid = editForm.parentId
  if (!pid) {
    // 根节点必须是公司/总部
    return editForm.deptType === 1
  }
  const parent = flattenTree(deptTree.value).find(n => n.id === pid)
  if (!parent) return true // 父节点未找到（异常数据），交由后端处理
  return (typeRank[editForm.deptType] ?? -1) === (typeRank[parent.deptType] ?? -1) + 1
}

const handleSave = async () => {
  if (!editForm.deptType) {
    ElMessage.warning('请选择组织类型')
    return
  }
  if (!editForm.groupId) {
    ElMessage.warning('请选择集团归属')
    return
  }
  if (!validateDeptHierarchy()) {
    ElMessage.warning('组织类型层级不合法：根节点须为公司/总部，大区须挂在公司/总部下，办事处须挂在大区下，部门须挂在办事处下')
    return
  }
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
    // 【M1 2026-10-04】负责人下拉整装一页，size 上限 500（与 usePermissionData.loadAllUsers 同口径）。
    // 超过 500 人时该下拉可能选不全——后端 /system/users 支持 keyword 分页，如需支持超大组织再改远程检索。
    const res = await getUserList({ current: 1, size: 500 })
    userList.value = res.data?.records || []
  } catch (e) {}
}

const loadGroups = async () => {
  try {
    const res = await getGroupList()
    groupList.value = res.data || []
  } catch (e) {}
}

// ===== 组织管理员 =====
const showAdmins = async (node) => {
  currentOrg.value = node
  adminDialogVisible.value = true
  try {
    const res = await getDeptAdmins(node.id)
    currentAdmins.value = res.data || []
  } catch (e) { currentAdmins.value = [] }
}

const searchUsers = async (query) => {
  try {
    const res = await getUserList({ keyword: query, size: 50 })
    userOptions.value = (res.data?.records || []).map(u => ({ id: u.id, realName: u.realName }))
  } catch (e) {}
}

const doAddAdmin = async () => {
  if (!selectedUserId.value) return
  try {
    await addDeptAdmin(currentOrg.value.id, selectedUserId.value)
    ElMessage.success('添加成功')
    selectedUserId.value = null
    showAdmins(currentOrg.value)
  } catch (e) { ElMessage.error('添加失败') }
}

const doRemoveAdmin = async (row) => {
  try {
    await removeDeptAdmin(currentOrg.value.id, row.id)
    ElMessage.success('移除成功')
    showAdmins(currentOrg.value)
  } catch (e) { ElMessage.error('移除失败') }
}

onMounted(() => {
  loadData()
  loadUsers()
  loadGroups()
})
</script>

<style scoped>
.dept-manage { height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 10px }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 16px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }
.section-box.table-box > div:first-child { flex: 1; overflow: auto; padding: 4px 0 }
.section-box.table-box :deep(.el-table th) { font-size: 14px; font-weight: 700; color: #1f2329; background: #f8f9fb }
.section-box.table-box :deep(.el-table td) { font-size: 13px }
.section-box.table-box :deep(.el-table__row) { height: 44px }
</style>
