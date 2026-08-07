<template>
  <div class="user-manage">

    <!-- 框1: 页头 + 筛选 -->
    <div class="section-box filter-box">
      <div class="filter-head">
        <div class="filter-title">
          <span class="filter-title-text">人员管理</span>
          <span class="filter-title-count">共 {{ total }} 人</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
      </div>
      <div class="filter-row">
        <el-input v-model="query.keyword" placeholder="搜索姓名 / 账号 / 手机号..." clearable prefix-icon="Search" class="filter-search" @keyup.enter="search" />
        <el-tree-select v-model="query.deptId" :data="deptTree" :props="{ label: 'deptName', value: 'id', children: 'children' }" check-strictly :render-after-expand="false" default-expand-all clearable filterable placeholder="全部部门" class="filter-select" @change="onDeptFilterChange" />
        <el-select v-model="query.status" placeholder="全部状态" clearable class="filter-select filter-status" @change="search">
          <el-option label="正常" :value="1" /><el-option label="禁用" :value="0" />
        </el-select>
      </div>
    </div>

    <!-- 框2: 表格 -->
    <div class="section-box table-box">
      <div ref="tableWrapRef" style="flex:1;overflow:auto">
      <el-table :data="tableData" stripe style="width: 100%" size="small" :height="tableHeight">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column label="编号" min-width="100">
          <template #default="{ row }">
            <span class="user-no">{{ row.username }}</span>
          </template>
        </el-table-column>
        <el-table-column label="姓名" min-width="110">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-avatar">{{ (row.realName || '?').slice(0, 1) }}</span>
              <span class="user-name">{{ row.realName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="115" />
        <el-table-column prop="managerName" label="直接上级" width="80">
          <template #default="{ row }">
            <span :class="{ 'no-value': !row.managerName }">{{ row.managerName || '未设置' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="105" show-overflow-tooltip />
        <el-table-column prop="officeName" label="办事处" width="120" show-overflow-tooltip />
        <el-table-column prop="deptName" label="部门" width="105" show-overflow-tooltip />
        <el-table-column label="角色" min-width="90">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.roleName)" size="small" effect="light" class="role-tag">
              {{ row.roleName || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="存储空间" min-width="120">
          <template #default="{ row }">
            <div class="storage-cell">
              <el-progress :percentage="storagePercent(row)" :show-text="false" :stroke-width="4" :color="storageColor(row)" />
              <span class="storage-text">{{ formatSize(row.storageUsed) }} / {{ formatSize(row.storageQuota) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="create-time">{{ (row.createdAt || '').slice(0, 10) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
    </div>

    <!-- 框3: 翻页 -->
    <div class="section-box" style="padding:6px 20px">
      <div class="pagination" style="padding:0;border:none">
        <span>{{ total > 0 ? (query.current - 1) * query.size + 1 : 0 }}-{{ Math.min(query.current * query.size, total) }}条 / 共{{ total }}条</span>
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" :page-sizes="[30, 50, 100]" layout="prev, pager, next" size="small" @change="loadData" />
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑用户' : '新增用户'" width="680px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="90px" class="user-form">
        <el-form-item label="所属组织">
          <el-cascader
            v-model="orgPath"
            :options="deptTree"
            :props="cascaderProps"
            placeholder="选择组织(总部 / 大区 / 办事处)"
            clearable filterable style="width:100%"
            @change="onOrgChange"
          />
          <div class="org-hint">
            <template v-if="editForm.officeId">已归属 {{ orgHintText }}</template>
            <template v-else>选到办事处层级才会归属公海池</template>
          </div>
        </el-form-item>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="账号" prop="username" v-if="!editForm.id">
              <div class="acct-hint" v-if="latestAccountHint">{{ latestAccountHint }}</div>
              <el-input v-model="editForm.username" placeholder="大区缩写+流水号" />
            </el-form-item>
            <el-form-item label="账号" v-else>
              <el-input :model-value="editForm.username" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="editForm.realName" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="editForm.phone" placeholder="手机号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="editForm.email" placeholder="邮箱地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="角色" prop="roleId">
              <el-select v-model="editForm.roleId" style="width:100%" placeholder="选择角色">
                <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="直接上级">
              <el-select v-model="editForm.managerId" style="width:100%" placeholder="选择上级" clearable filterable>
                <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="数据范围">
              <el-select v-model="editForm.dataScopeOverride" clearable style="width:100%" placeholder="默认跟随角色">
                <el-option label="跟随角色" value="" />
                <el-option label="仅本人" value="self" />
                <el-option label="本人及下级" value="team" />
                <el-option label="本部门及以下" value="dept" />
                <el-option label="本办事处及以下" value="office" />
                <el-option label="本大区及以下" value="region" />
                <el-option label="全部(本集团)" value="all" />
                <el-option label="自定义组织" value="custom" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="禁用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, createUser, updateUser, resetPassword, deleteUser } from '@/api/system'
import { getDeptList, getRoleList } from '@/api/system'
import { getSimpleUserList } from '@/api/customer'

const tableData = ref([])
const total = ref(0)
const depts = ref([])
const deptTree = ref([])
const orgPath = ref([])
let deptMap = new Map()
const cascaderProps = { label: 'deptName', value: 'id', children: 'children', checkStrictly: true, emitPath: true }
const roles = ref([])
const userOptions = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const query = reactive({ current: 1, size: 15, keyword: '', deptId: null, status: null })
const editForm = reactive({ id: null, username: '', realName: '', phone: '', email: '', deptId: null, officeId: null, roleId: null, managerId: null, dataScopeOverride: '', status: 1 })
const formRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
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
// 搜索/状态筛选回到第 1 页（分页 @change 仍用 loadData 保持当前页）
const search = () => { query.current = 1; loadData() }

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

// 根据所选组织建议账号前缀
const suggestedUsername = computed(() => {
  if (!orgPath.value?.length) return ''
  const ids = orgPath.value
  // 路径中最深的节点
  const lastId = ids[ids.length - 1]
  const node = deptMap.get(lastId)
  if (!node?.deptCode) {
    // 没有 dept_code 则尝试找上级大区
    for (let i = ids.length - 1; i >= 0; i--) {
      const n = deptMap.get(ids[i])
      if (n?.deptCode) return n.deptCode + '0'
    }
    return ''
  }
  return node.deptCode + '0'
})

const orgHintText = computed(() => {
  const node = deptMap.get(editForm.officeId)
  return node ? (node.deptCode || node.deptName) : ''
})

// 根据所选组织查找当前最大账号
const latestAccountHint = computed(() => {
  const officeId = editForm.officeId || (orgPath.value?.length && orgPath.value[orgPath.value.length - 1])
  if (!officeId) return ''
  // 同级办事处的用户
  const sameOffice = tableData.value.filter(u => u.officeId === officeId && u.username)
  if (!sameOffice.length) return ''
  // 取第一个用户的账号前缀（去掉末尾数字）
  const sample = sameOffice[0].username
  const prefix = sample.replace(/\d+$/, '')
  if (!prefix) return ''
  // 找最大编号
  const nums = sameOffice
    .map(u => parseInt(u.username.replace(prefix, '')))
    .filter(n => !isNaN(n))
  const maxNum = nums.length > 0 ? Math.max(...nums) : 0
  const node = deptMap.get(officeId)
  const deptName = node?.deptName || ''
  return `当前 ${deptName} 最大账号: ${prefix}${String(maxNum).padStart(2, '0')}`
})

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
    Object.assign(editForm, { id: row.id, username: row.username, realName: row.realName, phone: row.phone, email: row.email || '', deptId: row.deptId, officeId: row.officeId, roleId: row.roleId, managerId: row.managerId || null, dataScopeOverride: row.dataScopeOverride || '', status: row.status })
    orgPath.value = pathOf(row.deptId)
    editForm.officeId = officeFromPath(orgPath.value)
  } else {
    Object.assign(editForm, { id: null, username: '', realName: '', phone: '', email: '', deptId: null, officeId: null, roleId: null, managerId: null, dataScopeOverride: '', status: 1 })
    orgPath.value = []
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editForm.id) {
      await updateUser(editForm.id, editForm)
    } else {
      await createUser(editForm)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {} finally { saving.value = false }
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

// 角色标签色：按角色名映射，未识别用默认
const roleTagType = (name) => {
  if (!name) return 'info'
  if (name.includes('管理员')) return 'danger'
  if (name.includes('总监')) return 'success'
  if (name.includes('行政')) return 'warning'
  return 'primary'
}

// 存储使用率（0-100，上限 100）
const storagePercent = (row) => {
  const used = row.storageUsed || 0
  const quota = row.storageQuota || 0
  if (!quota) return 0
  return Math.min(100, Math.round((used / quota) * 100))
}
const storageColor = (row) => {
  const p = storagePercent(row)
  if (p >= 90) return '#f56c6c'
  if (p >= 70) return '#e6a23c'
  return '#67c23a'
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
  try {
    const res = await getSimpleUserList()
    userOptions.value = res.data || []
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
.user-manage { height: calc(100vh - 80px); display: flex; flex-direction: column; gap: 10px }
.section-box { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 14px 20px; flex-shrink: 0 }
.section-box.table-box { flex: 1; padding: 0; display: flex; flex-direction: column; overflow: hidden }
.section-box.table-box > div:first-child { flex: 1; overflow: auto }
.org-hint { font-size: 12px; color: #909399; line-height: 1.5; margin-top: 2px; }
.acct-hint { font-size: 11px; color: #67c23a; margin-bottom: 6px }
.user-form :deep(.el-form-item) { margin-bottom: 18px }
.section-box.table-box :deep(.el-table th) { font-size: 14px; font-weight: 700; color: #1f2329 }
.section-box.table-box :deep(.el-table td) { font-size: 13px; padding: 6px 0 }
.section-box.table-box :deep(.el-table__row) { height: 44px }
.create-time { color: #909399; font-size: 12px }
.pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #ebeef5;
  color: #999;
  font-size: 12px;
  flex-shrink: 0;
}

/* ===== 人员管理美化（2026-08-05） ===== */
.filter-box { display: flex; flex-direction: column; gap: 12px; padding: 16px 20px }
.filter-head { display: flex; align-items: center; justify-content: space-between }
.filter-title { display: flex; align-items: baseline; gap: 10px }
.filter-title-text { font-size: 17px; font-weight: 700; color: #1f2329; letter-spacing: 0.5px }
.filter-title-count { font-size: 12px; color: #909399; background: #f4f4f5; border-radius: 20px; padding: 2px 10px }
.filter-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap }
.filter-search { width: 260px }
.filter-select { width: 200px }
.filter-status { width: 120px }
.filter-box :deep(.el-input__wrapper) { border-radius: 8px }
.filter-box :deep(.el-select__wrapper) { border-radius: 8px }

/* 编号列 */
.user-no {
  font-family: 'SF Mono', 'Menlo', Consolas, monospace;
  font-weight: 600;
  color: #409eff;
  font-size: 12.5px;
  background: #ecf5ff;
  border-radius: 4px;
  padding: 1px 6px;
}

/* 姓名列：首字头像 */
.user-cell { display: flex; align-items: center; gap: 8px }
.user-avatar {
  width: 26px; height: 26px; border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff; font-size: 12px; font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.user-name { font-weight: 500; color: #303133 }

/* 直接上级空值 */
.no-value { color: #c0c4cc }

/* 角色标签 */
.role-tag { border-radius: 4px }

/* 存储列：迷你进度条 + 文本 */
.storage-cell { display: flex; flex-direction: column; gap: 3px; min-width: 110px }
.storage-text { font-size: 11px; color: #909399 }
</style>
