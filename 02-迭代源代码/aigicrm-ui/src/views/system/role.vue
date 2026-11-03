<template>
  <div class="role-manage">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button v-if="canAddRole" type="primary" :icon="Plus" @click="openDialog()">新增角色</el-button>
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
            <el-button v-if="canEditRole" link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canEditRole" link type="primary" size="small" @click="openPermDialog(row)">权限</el-button>
            <el-button v-if="canDeleteRole" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 提示：角色管理包含【菜单权限】（点「权限」按钮分配菜单）+【数据权限】（data_scope 下拉选择数据范围） -->
    <el-dialog v-model="dialogVisible" :title="editForm.id ? '编辑角色' : '新增角色'" width="600px">
<!-- 【UI 整改 2026-10-08】字段级校验：:rules + required 星标，报错落在字段下方，替代保存时静默失败 -->
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="editForm.roleName" placeholder="如：大区经理" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="editForm.roleCode" :disabled="!!editForm.id" placeholder="英文标识，如：area_manager" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="角色职责说明" />
        </el-form-item>

        <el-divider content-position="left">权限设置</el-divider>
        <el-form-item label="数据范围" prop="dataScope">
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
            勾选角色可访问的组织，勾选即包含其全部下级组织。
          </el-alert>
          <div class="org-matrix">
            <div v-for="org in flatOrgs" :key="org.id" class="org-matrix-row">
              <el-checkbox
                :model-value="orgMatrix[org.id] !== undefined"
                @change="(checked) => toggleOrg(org.id, checked)">
                <span :style="{ paddingLeft: (org._level || 0) * 16 + 'px' }">{{ org.deptName }}</span>
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

    <!-- 权限分配对话框(2026-11-03 权限中心收口: 改只读展示 + 跳转, 可写入口唯一=权限中心) -->
    <el-dialog v-model="permDialogVisible" title="权限分配（只读）" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
                title="功能勾选已收口至「权限中心」"
                description="勾选/取消权限请前往 系统管理 → 权限中心 → 功能与范围（保存即生效）；此处仅查看该角色当前绑定的权限码。" />
      <el-tree
        :data="menuTree"
        :props="{ label: 'menuName', children: 'children' }"
        node-key="id"
        default-expand-all
        :default-checked-keys="checkedMenuIds"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="goPermCenter">去权限中心配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { getRoleList, getRole, createRole, updateRole, deleteRole, getMenuTree, getDeptTree, getRoleOrgs } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canAddRole = computed(() => userStore.hasPermission('system:role:add'))
const canEditRole = computed(() => userStore.hasPermission('system:role:edit'))
const canDeleteRole = computed(() => userStore.hasPermission('system:role:delete'))

const roles = ref([])
const menuTree = ref([])
const orgTree = ref([])
const flatOrgs = ref([])
const dialogVisible = ref(false)
const permDialogVisible = ref(false)
const checkedMenuIds = ref([])
const currentRoleId = ref(null)
const saving = ref(false)

/** 组织矩阵: { [orgId]: 1 }  勾选的组织（含下级）
 *  【P1 2026-10-04 契约对齐】后端 saveRoleOrgs 对每个 org 固定写 include_children=1
 *  (SysRoleService.java:356)、GET /system/roles/{id}/orgs 只回 List<Long> 组织ID，
 *  "每组织是否含下级"无任何持久化/回显通道，故移除原"包含下级"勾选，勾选即含下级。
 */
const orgMatrix = reactive({})

const editForm = reactive({ id: null, roleName: '', roleCode: '', description: '', dataScope: 'self', orgId: null, sortOrder: 0 })
// 【UI 整改 2026-10-08】名称类必填字段级校验（与 customer 表单同规范）
const formRef = ref(null)
const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '编码须为字母开头的字母/数字/下划线', trigger: 'blur' },
  ],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
}

/** 数据范围中文标签映射（与后端 DataScopeType 枚举一致：self/team/dept/office/region/all/custom） */
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
    orgMatrix[orgId] = 1 // 后端保存时固定 include_children=1（含下级）
  } else {
    delete orgMatrix[orgId]
  }
}

const openDialog = async (row) => {
  if (row) {
    Object.assign(editForm, row)
    // 如果是自定义，加载已配置的组织矩阵
    if (row.dataScope === 'custom') {
      // 先清空旧状态再加载：加载失败时不携带上一会话的残留勾选（所见即所存）
      Object.keys(orgMatrix).forEach(k => delete orgMatrix[k])
      try {
        // 【P1 2026-10-04 契约对齐】GET /system/roles/{id}/orgs 返回 List<Long> 组织ID（非对象数组），
        // 旧代码按 o.orgId 取值恒 undefined 导致编辑回显勾选全部丢失
        const res = await getRoleOrgs(row.id)
        const orgIds = res.data || []
        orgIds.forEach(id => { orgMatrix[id] = 1 })
      } catch (e) {}
    }
  } else {
    Object.assign(editForm, { id: null, roleName: '', roleCode: '', description: '', dataScope: 'self', orgId: null, sortOrder: 0 })
    Object.keys(orgMatrix).forEach(k => delete orgMatrix[k])
  }
  dialogVisible.value = true
  // 打开弹窗清掉上一次校验残留的红字
  nextTick(() => formRef.value?.clearValidate())
}

const handleSave = async () => {
  // 【UI 整改 2026-10-08】先走字段级校验，报错定位到字段
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    // 【P1 2026-10-04 组织矩阵静默丢失修复】后端 POST/PUT /system/roles 请求体支持 orgIds(List<Long>)，
    // create/update 内部同事务调用 saveRoleOrgs(SysRoleService.java:127/174)。
    // 旧链路有两个断点：
    //   1) 新增时 createRole 返回 Result<Void> 无新 id（SysRoleController.java:50），editForm.id 恒 null，
    //      后续 updateRoleOrgs 永不执行，勾选的组织静默丢失还提示"保存成功"；
    //   2) 编辑时单独调 updateRoleOrgs 传 [{orgId,includeChildren}]，与后端 List<Long> 契约不符必 400，
    //      且 update 先清空 role_org 再补写为两步非原子。
    // 现统一走请求体 orgIds 一次落库；非 custom 传空数组（后端 saveRoleOrgs 对非 custom 清空后直接返回）。
    const payload = { ...editForm, orgIds: Object.keys(orgMatrix).map(Number) }
    if (editForm.id) {
      await updateRole(editForm.id, payload)
    } else {
      await createRole(payload)
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
    await confirmAction(`确定删除角色「${row.roleName}」？删除后不可恢复。`, '删除确认', { confirmText: '删除' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

// 【2026-11-03 权限中心收口】弹窗改只读: 只回显该角色当前绑定, 不再提供可写树
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

// 跳权限中心功能与范围页签(唯一可写入口; 可写勾选链=矩阵 togglePerm → PUT /system/roles)
const goPermCenter = () => {
  permDialogVisible.value = false
  router.push('/system/perm-center')
}

onMounted(() => {
  loadData()
  loadOrgs()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.form-tip { font-size: 12px; color: var(--el-color-info); margin-top: 4px; }
.org-matrix { max-height: 320px; overflow-y: auto; border: 1px solid #e5e5e5; border-radius: var(--radius-sm); padding: 8px; }
.org-matrix-row { display: flex; align-items: center; padding: 4px 0; }
.org-matrix-row:hover { background: #f5f7fa; }
</style>
