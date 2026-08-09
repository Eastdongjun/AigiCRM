<template>
  <div>
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
      <span style="font-size:15px;font-weight:600">公海池列表</span>
      <el-tag size="small" type="info">{{ seaPools.length }} 个池</el-tag>
      <el-button type="primary" size="small" :icon="Plus" @click="handleAddPool">新增公海池</el-button>
    </div>
    <el-row :gutter="16">
      <el-col v-for="pool in seaPools" :key="pool.id" :span="8" style="margin-bottom:16px">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
            <span style="font-weight:600;font-size:14px">{{ pool.poolName }}</span>
            <el-tag :type="pool.status===1?'success':'info'" size="small">{{ pool.status===1?'启用':'禁用' }}</el-tag>
          </div>
          <div style="color:#909399;font-size:12px;line-height:1.8">
            <div><el-icon style="vertical-align:middle"><OfficeBuilding /></el-icon> {{ pool.regionNames || '未设置' }}</div>
            <div><el-icon style="vertical-align:middle"><View /></el-icon> {{ pool.visibleNames || '未设置' }}</div>
            <div><el-icon style="vertical-align:middle"><Sort /></el-icon> 排序: {{ pool.sortOrder }}</div>
          </div>
          <div style="margin-top:12px;display:flex;gap:8px">
            <el-button size="small" @click="handleEditPool(pool)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDeletePool(pool)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="seaLoadError" description="无权限查看公海配置（缺少 sea:config:view 权限）或加载失败" />
    <el-empty v-else-if="!seaPools.length" description="暂无公海池，点击上方按钮创建" />

    <!-- 公海专项权限(按组织架构层级勾选) -->
    <div style="display:flex;align-items:center;gap:12px;margin:24px 0 12px">
      <span style="font-size:15px;font-weight:600">公海专项权限</span>
      <el-tag size="small" type="info">按组织层级勾选，勾选上级含下级</el-tag>
    </div>
    <el-table :data="seaPermRows" size="small" border style="width:100%">
      <el-table-column label="权限" width="140">
        <template #default="{ row }">
          <span style="font-weight:600">{{ row.label }}</span>
        </template>
      </el-table-column>
      <el-table-column label="授权范围">
        <template #default="{ row }">
          <template v-if="row.deptNames || row.userNames">
            <el-tag v-for="n in splitNames(row.deptNames)" :key="'d'+n" size="small" type="warning" style="margin:1px 3px 1px 0">{{ n }}</el-tag>
            <el-tag v-for="n in splitNames(row.userNames)" :key="'u'+n" size="small" type="primary" style="margin:1px 3px 1px 0">{{ n }}</el-tag>
          </template>
          <span v-else style="color:#c0c4cc">未授权</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="openPermDialog(row)">配置授权</el-button>
          <el-button size="small" type="danger" link @click="clearSeaPerm(row)">清空</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-divider />

    <el-dialog v-model="poolDialogVisible" :title="poolForm.id ? '编辑公海池' : '新增公海池'" width="700px" destroy-on-close top="5vh">
      <el-form :model="poolForm" label-width="100px" label-position="right">
        <el-form-item label="公海池名称" required>
          <el-input v-model="poolForm.poolName" placeholder="如：内蒙办公海" maxlength="30" show-word-limit />
        </el-form-item>

        <el-divider content-position="left">
          <el-icon><View /></el-icon> 可见范围
          <el-tag size="small" type="warning" style="margin-left:8px">{{ poolForm.visibleKeys.length }} 项已选</el-tag>
        </el-divider>

        <el-row :gutter="16" style="margin-bottom:8px">
          <el-col :span="14">
            <div class="tree-card">
              <div class="tree-card-header">
                <el-icon><OfficeBuilding /></el-icon> 组织架构
              </div>
              <el-tree
                ref="seaTreeRef"
                :data="orgTree"
                show-checkbox
                node-key="id"
                :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
                default-expand-all
                check-strictly
                @check="onSeaTreeCheck"
                style="max-height:360px;overflow:auto;padding:8px"
              />
              <div class="tree-card-tip">总部不可选；大区/办事处/部门/人员均可选</div>
            </div>
          </el-col>
          <el-col :span="10">
            <div class="tree-card">
              <div class="tree-card-header">
                <el-icon><Select /></el-icon> 已选范围
                <el-button link size="small" @click="clearSeaSelection" style="margin-left:auto">清空</el-button>
              </div>
              <div style="max-height:360px;overflow:auto;padding:8px">
                <el-tag
                  v-for="item in selectedSeaNodes"
                  :key="item.key"
                  :type="item.type"
                  closable
                  size="small"
                  @close="removeSeaNode(item)"
                  style="margin:2px;display:block;text-align:left"
                >
                  <el-icon style="vertical-align:middle"><component :is="item.key.startsWith('d_') ? 'OfficeBuilding' : 'User'" /></el-icon>
                  {{ item.label }}
                </el-tag>
                <el-empty v-if="!selectedSeaNodes.length" description="未选择" :image-size="40" />
              </div>
            </div>
          </el-col>
        </el-row>

        <el-divider content-position="left"><el-icon><Setting /></el-icon> 其他设置</el-divider>
        <el-form-item label="启用状态">
          <el-switch v-model="poolForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
          <div style="font-size:12px;color:#909399;margin-top:4px">禁用的公海池不在公海页面展示，也不再接收新释放的客户。<span v-if="poolForm.status === 0" style="color:#e6a23c">⚠️ 池内已有的客户记录将不可见且不可删除，重新启用后恢复。</span></div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="poolForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poolDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPool">确定</el-button>
      </template>
    </el-dialog>

    <!-- 公海专项授权弹窗(按组织架构层级勾选) -->
    <el-dialog v-model="permDialogVisible" :title="`配置「${permForm.label}」授权`" width="680px" destroy-on-close top="6vh">
      <div style="margin-bottom:8px;color:#606266">
        <el-icon style="vertical-align:middle"><OfficeBuilding /></el-icon> 勾选组织/人员（勾选上级自动含下级）
        <el-tag size="small" type="warning" style="margin-left:8px">{{ permForm.keys.length }} 项已选</el-tag>
      </div>
      <el-tree
        ref="permTreeRef"
        :data="orgTree"
        show-checkbox
        node-key="id"
        :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
        default-expand-all
        check-strictly
        style="max-height:400px;overflow:auto;border:1px solid #e4e7ed;border-radius:8px;padding:8px"
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPerm">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, OfficeBuilding, View, Sort, Select, Setting, User } from '@element-plus/icons-vue'
import { getSeaConfigs, createSeaConfig, updateSeaConfig, deleteSeaConfig, getOrgTree,
         getSeaPermissionList, grantSeaPermission, revokeSeaPermission } from '@/api/sea'

const seaPools = ref([])
const orgTree = ref([])
const poolDialogVisible = ref(false)
const poolForm = reactive({ id: null, poolName: '', visibleKeys: [], sortOrder: 0, status: 1 })
const seaLoadError = ref(false)

const loadSeaPools = async () => {
  seaLoadError.value = false
  try {
    const res = await getSeaConfigs()
    seaPools.value = res.data || []
  } catch (e) { seaLoadError.value = true }
}

const loadOrgTree = async () => {
  try {
    const res = await getOrgTree()
    orgTree.value = res.data || []
  } catch (e) {}
}

// ===== 公海专项权限(按组织架构层级勾选) =====
const seaPermRows = ref([])
const permDialogVisible = ref(false)
const permForm = reactive({ permType: '', label: '', keys: [], existingDeptIds: [], existingUserIds: [] })
const permTreeRef = ref(null)
const PERM_LABELS = { view: '查看公海', edit: '编辑公海', delete: '删除公海', claim: '捞取公海' }

const loadSeaPerms = async () => {
  try {
    const res = await getSeaPermissionList()
    seaPermRows.value = (res.data || []).map(r => ({ ...r, label: PERM_LABELS[r.permType] || r.permType }))
  } catch (e) {}
}

const splitNames = (csv) => (csv || '').split(',').filter(Boolean)

const openPermDialog = (row) => {
  Object.assign(permForm, {
    permType: row.permType,
    label: row.label,
    keys: buildPermKeys(row.deptIds, row.userIds),
    existingDeptIds: (row.deptIds || '').split(',').filter(Boolean),
    existingUserIds: (row.userIds || '').split(',').filter(Boolean)
  })
  permDialogVisible.value = true
  nextTick(() => {
    if (permTreeRef.value) permTreeRef.value.setCheckedKeys(permForm.keys)
  })
}

const buildPermKeys = (deptIds, userIds) => {
  const keys = []
  if (deptIds) deptIds.split(',').filter(Boolean).forEach(id => keys.push('d_' + id.trim()))
  if (userIds) userIds.split(',').filter(Boolean).forEach(id => keys.push('u_' + id.trim()))
  return keys
}

const submitPerm = async () => {
  try {
    const keys = permTreeRef.value ? permTreeRef.value.getCheckedKeys().map(String) : []
    const deptIds = keys.filter(k => k.startsWith('d_')).map(k => k.slice(2))
    const userIds = keys.filter(k => k.startsWith('u_')).map(k => k.slice(2))
    await grantSeaPermission({ permType: permForm.permType, deptIds, userIds })
    ElMessage.success('授权已保存')
    permDialogVisible.value = false
    loadSeaPerms()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  }
}

const clearSeaPerm = async (row) => {
  try {
    await ElMessageBox.confirm(`确定清空「${row.label}」的全部授权？`, '提示', { type: 'warning' })
    await revokeSeaPermission(row.permType)
    ElMessage.success('已清空')
    loadSeaPerms()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.message || e?.message || '清空失败')
  }
}

// 由 visibleDeptIds/visibleUserIds 还原树节点 key (d_<id> / u_<id>)
const buildVisibleKeys = (deptIds, userIds) => {
  const keys = []
  if (deptIds) deptIds.split(',').filter(Boolean).forEach(id => keys.push('d_' + id.trim()))
  if (userIds) userIds.split(',').filter(Boolean).forEach(id => keys.push('u_' + id.trim()))
  return keys
}

const seaTreeRef = ref(null)
// 扁平化组织树用于 key → 标签映射
const flattenOrgTree = (nodes, out = []) => {
  for (const n of nodes) { out.push(n); if (n.children) flattenOrgTree(n.children, out) }
  return out
}
// 已选范围标签（同步树勾选态）
const selectedSeaNodes = computed(() => {
  const all = flattenOrgTree(orgTree.value)
  return (poolForm.visibleKeys || []).map(k => {
    const n = all.find(x => String(x.id) === k)
    if (n) return { key: k, label: n.label, type: String(k).startsWith('d_') ? 'warning' : 'primary' }
    return { key: k, label: k, type: 'info' }
  })
})
// 树勾选变化 → 同步 visibleKeys
const onSeaTreeCheck = () => {
  if (!seaTreeRef.value) return
  poolForm.visibleKeys = seaTreeRef.value.getCheckedKeys().map(String)
}
const removeSeaNode = (item) => {
  poolForm.visibleKeys = (poolForm.visibleKeys || []).filter(k => k !== item.key)
  if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys(poolForm.visibleKeys)
}
const clearSeaSelection = () => {
  poolForm.visibleKeys = []
  if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys([])
}

const handleAddPool = () => {
  Object.assign(poolForm, { id: null, poolName: '', visibleKeys: [], sortOrder: 0, status: 1 })
  poolDialogVisible.value = true
}

const handleEditPool = (row) => {
  Object.assign(poolForm, {
    id: row.id,
    poolName: row.poolName,
    visibleKeys: buildVisibleKeys(row.visibleDeptIds, row.visibleUserIds),
    sortOrder: row.sortOrder || 0,
    status: row.status === 0 ? 0 : 1
  })
  poolDialogVisible.value = true
  nextTick(() => { if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys(poolForm.visibleKeys) })
}

const handleDeletePool = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除公海池「${row.poolName}」？`, '提示', { type: 'warning' })
    await deleteSeaConfig(row.id)
    ElMessage.success('删除成功')
    loadSeaPools()
  } catch (e) {
    // 【修复 2026-08-09 B8】区分"用户取消"与"后端拒绝",不再静默吞错
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
    }
  }
}

const submitPool = async () => {
  if (!poolForm.poolName) { ElMessage.warning('请输入公海池名称'); return }
  if (!poolForm.visibleKeys.length) { ElMessage.warning('请选择可见部门/人员'); return }
  try {
    // 拆分树节点 key 为可见部门ID / 可见人员ID
    const deptIds = poolForm.visibleKeys.filter(k => k.startsWith('d_')).map(k => k.slice(2))
    const userIds = poolForm.visibleKeys.filter(k => k.startsWith('u_')).map(k => k.slice(2))
    const payload = {
      poolName: poolForm.poolName,
      visibleDeptIds: deptIds.join(','),
      visibleUserIds: userIds.join(','),
      sortOrder: poolForm.sortOrder,
      status: poolForm.status
    }
    if (poolForm.id) {
      await updateSeaConfig(poolForm.id, payload)
    } else {
      await createSeaConfig(payload)
    }
    ElMessage.success('保存成功')
    poolDialogVisible.value = false
    loadSeaPools()
  } catch (e) {
    // 【修复 2026-08-09 B8】后端错误(如办事处重叠/权限不足)显式提示,不再静默
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  }
}

onMounted(() => { loadSeaPools(); loadOrgTree(); loadSeaPerms() })
</script>

<style scoped>
.tree-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 10px 12px; background: #fafbfc }
.tree-card-header { display: flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 6px }
.tree-card-tip { font-size: 11px; color: #909399; margin-top: 6px; line-height: 1.5 }
</style>
