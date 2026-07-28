<template>
  <div class="user-manage">
    <div class="page-header">
      <h2>人员管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input v-model="query.keyword" placeholder="搜索姓名/账号..." clearable prefix-icon="Search" style="width: 200px" @keyup.enter="loadData" />
        <el-tree-select
          v-model="query.deptId"
          :data="deptTree"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          check-strictly
          :render-after-expand="false"
          default-expand-all
          clearable
          filterable
          placeholder="全部部门"
          style="width: 200px"
          @change="onDeptFilterChange"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="loadData">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>
    </div>

    <!-- 表格区域 -->
    <div class="table-area">
      <div class="table-wrap" ref="tableWrapRef">
      <el-table :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
        <el-table-column prop="username" label="账号" min-width="100" />
        <el-table-column prop="realName" label="姓名" min-width="80" />
        <el-table-column prop="phone" label="手机号" min-width="110" />
        <el-table-column prop="email" label="邮箱" min-width="140" show-overflow-tooltip />
        <el-table-column prop="officeName" label="办事处" min-width="80" />
        <el-table-column prop="deptName" label="部门" min-width="90" />
        <el-table-column prop="roleName" label="角色" min-width="80" />
        <el-table-column label="状态" min-width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="存储空间" min-width="110">
          <template #default="{ row }">
            {{ formatSize(row.storageUsed) }} / {{ formatSize(row.storageQuota) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="130" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="[15, 30, 50]" layout="prev, pager, next" small @change="loadData" />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑用户' : '新增用户'" width="500px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="80px">
        <el-form-item label="账号" v-if="!editForm.id">
          <el-input v-model="editForm.username" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="所属组织">
          <el-cascader
            v-model="orgPath"
            :options="deptTree"
            :props="cascaderProps"
            placeholder="选择组织(总部 / 大区 / 办事处)"
            clearable
            filterable
            style="width:100%"
            @change="onOrgChange"
          />
          <div class="org-hint">
            <template v-if="editForm.officeId">已归属办事处,将参与公海池路由与客户归属</template>
            <template v-else>选到办事处层级才会归属公海池;大区/总部岗位可只选到上级</template>
          </div>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roleId" style="width:100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" />
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
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, createUser, updateUser, resetPassword, deleteUser } from '@/api/system'
import { getDeptList, getRoleList } from '@/api/system'

const tableData = ref([])
const total = ref(0)
const depts = ref([])
const deptTree = ref([])
const orgPath = ref([])
let deptMap = new Map()
const cascaderProps = { label: 'deptName', value: 'id', children: 'children', checkStrictly: true, emitPath: true }
const roles = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)

const query = reactive({ current: 1, size: 15, keyword: '', deptId: null, status: null })
const editForm = reactive({ id: null, username: '', realName: '', phone: '', email: '', deptId: null, officeId: null, roleId: null, status: 1 })
const formRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

// 选中部门时匹配其自身 + 所有下级,便于按大区/办事处一键筛选整棵子树
const descendantIds = (rootId) => {
  const ids = [rootId]
  const stack = [rootId]
  while (stack.length) {
    const cur = stack.pop()
    for (const d of depts.value) {
      if (Number(d.parentId) === Number(cur)) { ids.push(d.id); stack.push(d.id) }
    }
  }
  return ids
}

const onDeptFilterChange = () => {
  query.current = 1
  loadData()
}

const loadData = async () => {
  try {
    const params = { current: query.current, size: query.size, keyword: query.keyword, status: query.status }
    if (query.deptId != null) params.deptIds = descendantIds(query.deptId).join(',')
    const res = await getUserList(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {}
}

// 由扁平部门列表构建级联树(去掉空 children 以免出现空展开箭头)
const buildTree = (list, parentId = 0) =>
  list
    .filter(d => Number(d.parentId) === Number(parentId))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map(d => {
      const children = buildTree(list, d.id)
      return children.length ? { ...d, children } : { ...d }
    })

// 由 dept_id 沿 parentId 反推出级联路径 [总部, 大区, 办事处]
const pathOf = (deptId) => {
  const path = []
  let cur = deptId
  let guard = 0
  while (cur != null && deptMap.has(cur) && guard++ < 20) {
    const node = deptMap.get(cur)
    path.unshift(node.id)
    cur = node.parentId
  }
  return path
}

// 从级联路径取办事处:路径中最浅的 type=3 节点(直属大区/总部的那个办事处)。
// 子部门(无论 type=4 部门,还是被建成 type=3 的子办事处)都不覆盖顶层办事处,保证公海池路由稳定。
const officeFromPath = (ids) => {
  for (const id of (ids || [])) {
    const node = deptMap.get(id)
    if (node && node.deptType === 3) return id
  }
  return null
}

// 选择变化时派生:最深节点为部门(dept_id),办事处取路径中的办事处节点(office_id)
const onOrgChange = (path) => {
  const ids = Array.isArray(path) ? path : []
  editForm.deptId = ids.length ? ids[ids.length - 1] : null
  editForm.officeId = officeFromPath(ids)
}

const openDialog = (row) => {
  if (row) {
    Object.assign(editForm, { id: row.id, username: row.username, realName: row.realName, phone: row.phone, email: row.email || '', deptId: row.deptId, officeId: row.officeId, roleId: row.roleId, status: row.status })
    orgPath.value = pathOf(row.deptId)
    // 以级联路径为唯一事实源,按 dept 层级重算办事处,自动修正历史不一致数据(下次保存即对齐)
    editForm.officeId = officeFromPath(orgPath.value)
  } else {
    Object.assign(editForm, { id: null, username: '', realName: '', phone: '', email: '', deptId: null, officeId: null, roleId: null, status: 1 })
    orgPath.value = []
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (editForm.id) {
      await updateUser(editForm.id, editForm)
    } else {
      await createUser(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {}
}

const handleResetPwd = async (row) => {
  try {
    await ElMessageBox.confirm(`确定重置 ${row.realName} 的密码为 123456？`, '提示')
    await resetPassword(row.id)
    ElMessage.success('密码已重置')
  } catch (e) {}
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定删除用户 ${row.realName}？该用户名下的客户将自动释放到公海池。`,
      '删除用户',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await deleteUser(row.id)
    ElMessage.success('用户已删除，客户已释放到公海')
    loadData()
  } catch (e) {}
}

const formatSize = (bytes) => {
  if (!bytes) return '0'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + 'KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(0) + 'MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + 'GB'
}

// 表格高度：动态计算可用高度，保证分页器完整显示、不被裁切
const tableWrapRef = ref(null)
const tableHeight = ref(300)
let resizeObserver = null
const updateTableHeight = () => {
  if (tableWrapRef.value) tableHeight.value = tableWrapRef.value.clientHeight
}

onMounted(async () => {
  loadData()
  try {
    const res = await getDeptList()
    depts.value = res.data || []
    deptMap = new Map(depts.value.map(d => [d.id, d]))
    deptTree.value = buildTree(depts.value)
  } catch (e) {}
  try {
    const res = await getRoleList()
    roles.value = res.data || []
  } catch (e) {}
  nextTick(updateTableHeight)
  if (tableWrapRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableWrapRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
})
</script>

<style scoped>
.user-manage { height: calc(100vh - 100px); display: flex; flex-direction: column; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #333; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar-left { display: flex; gap: 8px; }
.org-hint { font-size: 12px; color: #909399; line-height: 1.5; margin-top: 2px; }
.table-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.table-wrap { flex: 1; min-height: 0; }
.table-area :deep(.el-table td.el-table__cell),
.table-area :deep(.el-table th.el-table__cell) {
  height: 40px;
  padding: 4px 0;
  box-sizing: border-box;
}
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}
</style>
