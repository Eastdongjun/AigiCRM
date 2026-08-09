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
            <el-tag size="small" :type="{ all: 'danger', custom: 'success', region: 'warning', office: 'warning', dept: 'warning', team: 'info', self: 'info' }[row.dataScope] || 'info'">
              {{ scopeLabel(row.dataScope) }}
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

    <!-- 提示：角色管理包含【菜单权限】（点「权限」按钮分配菜单）+【数据权限】（data_scope 下拉选择数据范围） -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑角色' : '新增角色'" width="600px">
      <el-form :model="editForm" label-width="110px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="角色名称" required>
          <el-input v-model="editForm.roleName" placeholder="如：大区经理" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="editForm.roleCode" :disabled="!!editForm.id" placeholder="英文标识，如：area_manager" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="角色职责说明" />
        </el-form-item>

        <el-divider content-position="left">权限设置</el-divider>
        <el-form-item label="数据范围" prop="dataScope" required>
          <el-select v-model="editForm.dataScope" style="width:100%" placeholder="选择数据可见范围" @change="onScopeChange">
            <el-option label="仅本人" value="self" />
            <el-option label="本人及下级" value="team" />
            <el-option label="本部门及以下" value="dept" />
            <el-option label="本办事处及以下" value="office" />
            <el-option label="本大区及以下" value="region" />
            <el-option label="全部数据(本集团)" value="all" />
            <el-option label="自定义组织" value="custom" />
          </el-select>
        </el-form-item>

        <!-- 自定义数据范围 → 组织矩阵 -->
        <template v-if="editForm.dataScope === 'custom'">
          <el-divider content-position="left">组织矩阵</el-divider>
          <el-alert type="info" :closable="false" style="margin-bottom:12px">
            勾选角色可访问的组织。每个组织可独立设置"包含下级"。
          </el-alert>
          <div class="org-matrix">
            <div v-for="org in flatOrgs" :key="org.id" class="org-matrix-row">
              <el-checkbox
                :model-value="orgMatrix[org.id] !== undefined"
                @change="(checked) => toggleOrg(org.id, checked)">
                <span :style="{ paddingLeft: (org._level || 0) * 16 + 'px' }">{{ org.deptName }}</span>
              </el-checkbox>
              <el-checkbox
                v-if="orgMatrix[org.id] !== undefined"
                v-model="orgMatrix[org.id]"
                :true-label="1" :false-label="0"
                size="small"
                style="margin-left:8px">
                包含下级
              </el-checkbox>
            </div>
          </div>
        </template>

        <el-form-item label="归属组织">
          <el-select v-model="editForm.orgId" style="width:100%" clearable placeholder="留空=全局角色，不限组织">
            <el-option v-for="org in flatOrgs" :key="org.id" :label="org.deptName" :value="org.id" />
          </el-select>
          <div class="form-tip">选择后该角色只在指定组织范围内可用；留空则全局通用</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
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
import { getRoleList, getRole, createRole, updateRole, deleteRole, getMenuTree, getDeptTree, getRoleOrgs, updateRoleOrgs } from '@/api/system'

const roles = ref([])
const menuTree = ref([])
const orgTree = ref([])
const flatOrgs = ref([])
const dialogVisible = ref(false)
const permDialogVisible = ref(false)
const menuTreeRef = ref(null)
const checkedMenuIds = ref([])
const currentRoleId = ref(null)
const saving = ref(false)

/** 组织矩阵: { [orgId]: 0|1 }  0=仅本组织, 1=包含下级 */
const orgMatrix = reactive({})

const editForm = reactive({ id: null, roleName: '', roleCode: '', description: '', dataScope: 'self', orgId: null, sortOrder: 0 })

/** 数据范围中文标签映射（与后端 DataScopeType 六级范围一致） */
const scopeMap = { self: '仅本人', team: '本人及下级', dept: '本部门及以下', office: '本办事处及以下', region: '本大区及以下', all: '全部', custom: '自定义' }
const scopeLabel = s => scopeMap[s] || s || '仅本人'

const loadData = async () => {
  try {
    const res = await getRoleList()
    roles.value = res.data || []
  } catch (e) {}
}

/** 加载组织树 */
const loadOrgs = async () => {
  try {
    const res = await getDeptTree()
    flattenOrg(res.data || [], 0)
  } catch (e) {}
}

/** 递归拍平组织树，添加 _level */
const flattenOrg = (nodes, level) => {
  for (const node of nodes) {
    node._level = level
    flatOrgs.value.push(node)
    if (node.children && node.children.length) {
      flattenOrg(node.children, level + 1)
    }
  }
}

const onScopeChange = (val) => {
  if (val !== 'custom') {
    // 清空组织矩阵
    Object.keys(orgMatrix).forEach(k => delete orgMatrix[k])
  }
}

const toggleOrg = (orgId, checked) => {
  if (checked) {
    orgMatrix[orgId] = 0 // 默认不包含下级
  } else {
    delete orgMatrix[orgId]
  }
}

const openDialog = async (row) => {
  if (row) {
    Object.assign(editForm, row)
    // 如果是自定义，加载已配置的组织矩阵
    if (row.dataScope === 'custom') {
      try {
        const res = await getRoleOrgs(row.id)
        const orgs = res.data || []
        Object.keys(orgMatrix).forEach(k => delete orgMatrix[k])
        orgs.forEach(o => { orgMatrix[o.orgId] = o.includeChildren || 0 })
      } catch (e) {}
    }
  } else {
    Object.assign(editForm, { id: null, roleName: '', roleCode: '', description: '', dataScope: 'self', orgId: null, sortOrder: 0 })
    Object.keys(orgMatrix).forEach(k => delete orgMatrix[k])
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  saving.value = true
  try {
    if (editForm.id) {
      await updateRole(editForm.id, editForm)
    } else {
      await createRole(editForm)
    }
    // 自定义数据范围时，保存组织矩阵
    if (editForm.dataScope === 'custom' && editForm.id) {
      const orgIds = Object.entries(orgMatrix).map(([orgId, includeChildren]) => ({
        orgId: Number(orgId),
        includeChildren
      }))
      await updateRoleOrgs(editForm.id, orgIds)
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

onMounted(() => {
  loadData()
  loadOrgs()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
.org-matrix { max-height: 320px; overflow-y: auto; border: 1px solid #e5e5e5; border-radius: 6px; padding: 8px; }
.org-matrix-row { display: flex; align-items: center; padding: 4px 0; }
.org-matrix-row:hover { background: #f5f7fa; }
</style>
