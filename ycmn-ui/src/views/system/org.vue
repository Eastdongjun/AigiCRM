<template>
  <div class="org-container">
    <el-card>
      <div class="toolbar">
        <el-button type="primary" @click="handleAdd(null)" v-if="hasPerm('system:org:add')">
          <el-icon><Plus /></el-icon> 添加组织
        </el-button>
        <el-alert type="info" :closable="false" style="margin-left: 12px; flex: 1">
          <template #title>
            层级：总部 → 大区 → 办事处 → 部门（任何层级下都可以添加部门）
          </template>
        </el-alert>
      </div>

      <el-table :data="treeData" row-key="id" default-expand-all border stripe style="margin-top: 12px">
        <el-table-column prop="orgName" label="组织名称" min-width="200">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.orgType)" size="small" style="margin-right: 8px">
              {{ typeLabel(row.orgType) }}
            </el-tag>
            {{ row.orgName }}
          </template>
        </el-table-column>
        <el-table-column prop="orgCode" label="编码" width="100" />
        <el-table-column label="管理员" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showAdmins(row)">
              管理 ({{ adminCounts[row.id] || 0 }})
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="60" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleAdd(row)">添加子级</el-button>
            <el-button link type="warning" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？子组织将一并删除" @confirm="handleDelete(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 组织表单弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="上级组织">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item label="组织名称" required>
          <el-input v-model="form.orgName" placeholder="如：华东分公司" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.orgCode" placeholder="如：HD" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.orgType">
            <el-option label="🏢 总部" value="HQ" />
            <el-option label="🏭 大区" value="BRANCH" />
            <el-option label="🏬 办事处" value="OFFICE" />
            <el-option label="📂 部门" value="DEPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 管理员弹窗 -->
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrgTree, createOrg, updateOrg, deleteOrg, getOrgAdmins, addOrgAdmin, removeOrgAdmin } from '@/api/system'
import { getUserList } from '@/api/system'

const treeData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const parentName = ref('')
const form = ref({ parentId: null, orgName: '', orgCode: '', orgType: 'DEPT', sortOrder: 0 })
const editId = ref(null)

const adminDialogVisible = ref(false)
const currentOrg = ref(null)
const currentAdmins = ref([])
const userOptions = ref([])
const selectedUserId = ref(null)
const adminCounts = ref({})

onMounted(() => loadTree())

async function loadTree() {
  const res = await getOrgTree()
  treeData.value = res.data || []
  // 加载管理员计数
  for (const org of flatten(treeData.value)) {
    try {
      const r = await getOrgAdmins(org.id)
      adminCounts.value[org.id] = (r.data || []).length
    } catch (e) { adminCounts.value[org.id] = 0 }
  }
}

function flatten(nodes) {
  let result = []
  for (const n of nodes) {
    result.push(n)
    if (n.children) result = result.concat(flatten(n.children))
  }
  return result
}

function typeLabel(type) {
  const map = { HQ: '总部', BRANCH: '大区', OFFICE: '办事处', DEPT: '部门' }
  return map[type] || type
}

function typeTag(type) {
  const map = { HQ: '', BRANCH: 'warning', SUBSIDIARY: 'success', OFFICE: 'info', DEPT: '' }
  return map[type] || ''
}

function handleAdd(parent) {
  editId.value = null
  form.value = { parentId: parent ? parent.id : 0, orgName: '', orgCode: '', orgType: 'DEPT', sortOrder: 0 }
  parentName.value = parent ? parent.orgName : '根级'
  dialogTitle.value = '添加组织'
  dialogVisible.value = true
}

function handleEdit(row) {
  editId.value = row.id
  form.value = { ...row }
  parentName.value = row.parentId ? '已有上级' : '根级'
  dialogTitle.value = '编辑组织'
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.orgName) return ElMessage.warning('请输入组织名称')
  try {
    if (editId.value) {
      await updateOrg(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createOrg(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadTree()
  } catch (e) { ElMessage.error('操作失败') }
}

async function handleDelete(row) {
  try {
    await deleteOrg(row.id)
    ElMessage.success('删除成功')
    loadTree()
  } catch (e) { ElMessage.error('删除失败') }
}

async function showAdmins(org) {
  currentOrg.value = org
  adminDialogVisible.value = true
  const res = await getOrgAdmins(org.id)
  currentAdmins.value = res.data || []
}

async function searchUsers(query) {
  try {
    const res = await getUserList({ keyword: query, size: 50 })
    userOptions.value = (res.data?.records || []).map(u => ({ id: u.id, realName: u.realName }))
  } catch (e) {}
}

async function doAddAdmin() {
  if (!selectedUserId.value) return
  try {
    await addOrgAdmin(currentOrg.value.id, selectedUserId.value)
    ElMessage.success('添加成功')
    selectedUserId.value = null
    showAdmins(currentOrg.value)
  } catch (e) { ElMessage.error('添加失败') }
}

async function doRemoveAdmin(row) {
  try {
    await removeOrgAdmin(currentOrg.value.id, row.id)
    ElMessage.success('移除成功')
    showAdmins(currentOrg.value)
  } catch (e) { ElMessage.error('移除失败') }
}

function hasPerm(perm) {
  return true  // 临时全开放，后续接入指令
}
</script>

<style scoped>
.org-container { padding: 16px; }
.toolbar { display: flex; align-items: center; }
</style>
