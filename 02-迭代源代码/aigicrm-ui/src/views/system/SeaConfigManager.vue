<template>
  <div>
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
      <span style="font-size:15px;font-weight:600">公海池列表</span>
      <el-tag size="small" type="info">{{ seaPools.length }} 个池</el-tag>
      <el-button v-if="canEditSeaConfig" type="primary" size="small" :icon="Plus" @click="handleAddPool">新增公海池</el-button>
    </div>
    <el-row :gutter="16">
      <el-col v-for="pool in seaPools" :key="pool.id" :span="8" style="margin-bottom:16px">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
            <span style="font-weight:600;font-size:14px">{{ pool.poolName }}</span>
            <el-tag :type="pool.status===1?'success':'info'" size="small">{{ pool.status===1?'启用':'禁用' }}</el-tag>
          </div>
          <div style="color:var(--el-color-info);font-size:12px;line-height:1.8">
            <div><el-icon style="vertical-align:middle"><OfficeBuilding /></el-icon> {{ pool.regionNames || '未设置' }}</div>
            <div><el-icon style="vertical-align:middle"><View /></el-icon> {{ pool.visibleNames || '未设置' }}</div>
            <div><el-icon style="vertical-align:middle"><Sort /></el-icon> 排序: {{ pool.sortOrder }}</div>
          </div>
          <div style="margin-top:12px;display:flex;gap:8px">
            <el-button v-if="canEditSeaConfig" size="small" @click="handleEditPool(pool)">编辑</el-button>
            <el-button v-if="canEditSeaConfig" size="small" type="danger" @click="handleDeletePool(pool)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="seaLoadError" description="无权限查看公海配置（缺少 sea:config:view 权限）或加载失败" />
    <el-empty v-else-if="!seaPools.length" description="暂无公海池，点击上方按钮创建" />

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

        <!-- 池级操作权限(2026-08-10): 编辑/删除/导入/捞取 各组织范围 -->
        <el-collapse v-model="permOpenSections" style="margin-top:8px">
          <el-collapse-item v-for="sec in permSections" :key="sec.key" :name="sec.key">
            <template #title>
              <span style="font-weight:600">{{ sec.label }}</span>
              <el-tag size="small" :type="poolForm[sec.keys].length ? 'warning' : 'info'" style="margin-left:8px">{{ poolForm[sec.keys].length }} 项已选</el-tag>
            </template>
            <el-row :gutter="16">
              <el-col :span="14">
                <div class="tree-card">
                  <div class="tree-card-header"><el-icon><OfficeBuilding /></el-icon> 组织架构</div>
                  <el-tree
                    :ref="(el) => bindSecTree(sec.key, el)"
                    :data="orgTree"
                    show-checkbox
                    node-key="id"
                    :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
                    default-expand-all
                    check-strictly
                    @check="(d,c) => onSecTreeCheck(sec.key, d, c)"
                    style="max-height:300px;overflow:auto;padding:8px"
                  />
                  <div class="tree-card-tip">勾选上级组织自动含其下所有人员</div>
                </div>
              </el-col>
              <el-col :span="10">
                <div class="tree-card">
                  <div class="tree-card-header">
                    <el-icon><Select /></el-icon> 已选范围
                    <el-button link size="small" @click="clearSecSelection(sec.key)" style="margin-left:auto">清空</el-button>
                  </div>
                  <div style="max-height:300px;overflow:auto;padding:8px">
                    <el-tag
                      v-for="item in secSelectedNodes(sec.key)"
                      :key="item.key"
                      :type="item.key.startsWith('d_') ? 'warning' : 'primary'"
                      closable
                      size="small"
                      @close="removeSecNode(sec.key, item.key)"
                      style="margin:2px;display:block;text-align:left"
                    >
                      <el-icon style="vertical-align:middle"><component :is="item.key.startsWith('d_') ? 'OfficeBuilding' : 'User'" /></el-icon>
                      {{ item.label }}
                    </el-tag>
                    <el-empty v-if="!poolForm[sec.keys].length" description="未选择" :image-size="32" />
                  </div>
                </div>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>

        <el-divider content-position="left"><el-icon><Setting /></el-icon> 其他设置</el-divider>
        <el-form-item label="启用状态">
          <el-switch v-model="poolForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
          <div style="font-size:12px;color:var(--el-color-info);margin-top:4px">禁用的公海池不在公海页面展示，也不再接收新释放的客户。<span v-if="poolForm.status === 0" style="color:var(--el-color-warning)">⚠️ 池内已有的客户记录将不可见且不可删除，重新启用后恢复。</span></div>
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { Plus, OfficeBuilding, View, Sort, Select, Setting, User } from '@element-plus/icons-vue'
import { getSeaConfigs, createSeaConfig, updateSeaConfig, deleteSeaConfig, getOrgTree } from '@/api/sea'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const canEditSeaConfig = computed(() => userStore.hasPermission('sea:config:edit'))

const seaPools = ref([])
const orgTree = ref([])
const poolDialogVisible = ref(false)
const poolForm = reactive({ id: null, poolName: '', visibleKeys: [], editKeys: [], deleteKeys: [], importKeys: [], claimKeys: [], sortOrder: 0, status: 1 })
const seaLoadError = ref(false)

// 池级操作权限折叠区块配置(2026-08-10): label + poolForm keys 字段 + 后端 dept/user 字段名
const permSections = [
  { key: 'edit', label: '编辑权限', keys: 'editKeys', deptField: 'editDeptIds', userField: 'editUserIds' },
  { key: 'delete', label: '删除权限', keys: 'deleteKeys', deptField: 'deleteDeptIds', userField: 'deleteUserIds' },
  { key: 'import', label: '导入权限', keys: 'importKeys', deptField: 'importDeptIds', userField: 'importUserIds' },
  { key: 'claim', label: '捞取权限', keys: 'claimKeys', deptField: 'claimDeptIds', userField: 'claimUserIds' }
]
const permOpenSections = ref([]) // 默认全部折叠, 管理员按需展开
const permInitKeys = ref({ edit: [], delete: [], import: [], claim: [] }) // 编辑时各权限初始值(提交对比防误清空)

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

// ===== 池级操作权限(2026-08-10) =====
const secTreeMap = {} // key → 树 ref(函数 ref 绑定)
// 各权限已选标签
const secSelectedNodes = (secKey) => {
  const all = flattenOrgTree(orgTree.value)
  return (poolForm[secKey + 'Keys'] || []).map(k => {
    const n = all.find(x => String(x.id) === k)
    if (n) return { key: k, label: n.label, type: String(k).startsWith('d_') ? 'warning' : 'primary' }
    return { key: k, label: k, type: 'info' }
  })
}
// 树勾选变化 → 同步对应 keys
const onSecTreeCheck = (secKey, data, checkedInfo) => {
  poolForm[secKey + 'Keys'] = (checkedInfo.checkedKeys || []).map(String)
}
// 移除标签
const removeSecNode = (secKey, key) => {
  poolForm[secKey + 'Keys'] = (poolForm[secKey + 'Keys'] || []).filter(k => k !== key)
  const tree = secTreeMap[secKey]
  if (tree) tree.setCheckedKeys(poolForm[secKey + 'Keys'])
}
const clearSecSelection = (secKey) => {
  poolForm[secKey + 'Keys'] = []
  const tree = secTreeMap[secKey]
  if (tree) tree.setCheckedKeys([])
}
// 回填某权限 keys 到树(编辑时)
const setSecTreeChecked = (secKey, keys) => {
  nextTick(() => {
    const tree = secTreeMap[secKey]
    if (tree) tree.setCheckedKeys(keys || [])
  })
}
// 绑定 el-tree ref(函数 ref, 按 secKey 存 map)
const bindSecTree = (secKey, el) => {
  secTreeMap[secKey] = el || null
}

const handleAddPool = () => {
  Object.assign(poolForm, { id: null, poolName: '', visibleKeys: [], editKeys: [], deleteKeys: [], importKeys: [], claimKeys: [], sortOrder: 0, status: 1 })
  permOpenSections.value = permSections.map(s => s.key) // 新增也展开权限区块
  permInitKeys.value = { edit: [], delete: [], import: [], claim: [] } // 新增默认全空
  poolDialogVisible.value = true
}

const handleEditPool = (row) => {
  const newEdit = buildVisibleKeys(row.editDeptIds, row.editUserIds)
  const newDelete = buildVisibleKeys(row.deleteDeptIds, row.deleteUserIds)
  const newImport = buildVisibleKeys(row.importDeptIds, row.importUserIds)
  const newClaim = buildVisibleKeys(row.claimDeptIds, row.claimUserIds)
  Object.assign(poolForm, {
    id: row.id,
    poolName: row.poolName,
    visibleKeys: buildVisibleKeys(row.visibleDeptIds, row.visibleUserIds),
    editKeys: newEdit,
    deleteKeys: newDelete,
    importKeys: newImport,
    claimKeys: newClaim,
    sortOrder: row.sortOrder || 0,
    status: row.status === 0 ? 0 : 1
  })
  // 记录权限初始值: 提交时仅提交被修改的权限区块(未改动保留旧值, 防误清空)
  permInitKeys.value = { edit: [...newEdit], delete: [...newDelete], import: [...newImport], claim: [...newClaim] }
  poolDialogVisible.value = true
  // 【2026-08-10 修复】编辑时展开所有权限区块——确保树渲染挂载后回填可靠, 避免折叠时回填失效导致误清空池权限
  permOpenSections.value = permSections.map(s => s.key)
  nextTick(() => {
    if (seaTreeRef.value) seaTreeRef.value.setCheckedKeys(poolForm.visibleKeys)
    permSections.forEach(sec => {
      const tree = secTreeMap[sec.key]
      if (tree) tree.setCheckedKeys(poolForm[sec.keys])
    })
    // 双保险: 展开动画后再次回填(树在 collapse-item 展开后完整渲染)
    setTimeout(() => {
      permSections.forEach(sec => {
        const tree = secTreeMap[sec.key]
        if (tree) tree.setCheckedKeys(poolForm[sec.keys])
      })
    }, 100)
  })
}

const handleDeletePool = async (row) => {
  try {
    // 【UI 整改 2026-10-08】删除类确认走 confirmAction：红色确认钮 + 取消左置
    await confirmAction(`确定删除公海池「${row.poolName}」？删除后不可恢复。`, '删除确认', { confirmText: '删除' })
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
    // 拆分树节点 key 为部门ID/人员ID
    const splitKeys = (keys) => ({
      dept: keys.filter(k => k.startsWith('d_')).map(k => k.slice(2)).join(','),
      user: keys.filter(k => k.startsWith('u_')).map(k => k.slice(2)).join(',')
    })
    const visible = splitKeys(poolForm.visibleKeys)
    const edit = splitKeys(poolForm.editKeys)
    const del = splitKeys(poolForm.deleteKeys)
    const imp = splitKeys(poolForm.importKeys)
    const claim = splitKeys(poolForm.claimKeys)
    // 仅提交被修改的权限区块(未改动不传字段→后端保留旧值, 防误清空); 显式清空(clearSecSelection)会提交空串实现清空
    const permVal = (secKey, split) => {
      const changed = JSON.stringify(poolForm[secKey + 'Keys'] || []) !== JSON.stringify(permInitKeys.value[secKey] || [])
      return changed ? split : undefined
    }
    const payload = {
      poolName: poolForm.poolName,
      visibleDeptIds: visible.dept,
      visibleUserIds: visible.user,
      ...(permVal('edit', edit) !== undefined ? { editDeptIds: edit.dept || '', editUserIds: edit.user || '' } : {}),
      ...(permVal('delete', del) !== undefined ? { deleteDeptIds: del.dept || '', deleteUserIds: del.user || '' } : {}),
      ...(permVal('import', imp) !== undefined ? { importDeptIds: imp.dept || '', importUserIds: imp.user || '' } : {}),
      ...(permVal('claim', claim) !== undefined ? { claimDeptIds: claim.dept || '', claimUserIds: claim.user || '' } : {}),
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

onMounted(() => { loadSeaPools(); loadOrgTree() })
</script>

<style scoped>
.tree-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 10px 12px; background: #fafbfc }
.tree-card-header { display: flex; align-items: center; gap: 5px; font-size: 13px; font-weight: 600; color: #4e5969; margin-bottom: 6px }
.tree-card-tip { font-size: 12px; color: var(--el-color-info); margin-top: 6px; line-height: 1.5 }
</style>
