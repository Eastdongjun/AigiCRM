<template>
  <div class="perm-scope-manager">
    <!-- 【批③ 2026-08-10】主体三级切换: 角色(矩阵) / 岗位 / 用户 -->
    <div class="ps-tabs">
      <el-radio-group v-model="assigneeMode" size="small" @change="onModeChange">
        <el-radio-button value="ROLE">按角色</el-radio-button>
        <el-radio-button value="POSITION">按岗位</el-radio-button>
        <el-radio-button value="USER">按用户</el-radio-button>
      </el-radio-group>
      <el-select v-if="assigneeMode !== 'ROLE'" v-model="selectedKey" size="small" placeholder="选择岗位/用户"
                 style="width: 220px; margin-left: 10px" filterable @change="loadScopesForSelected">
        <el-option v-for="o in assigneeOptions" :key="o.key" :label="o.label" :value="o.key" />
      </el-select>
      <el-button size="small" style="margin-left: 8px" @click="loadAll">刷新</el-button>
    </div>
    <div class="ps-layout">
      <!-- 左：模块导航 -->
      <div class="ps-sidebar">
        <div class="ps-sidebar-title">权限模块</div>
        <div v-for="m in modules" :key="m.name" class="ps-nav" :class="{ active: current === m.name }"
             @click="current = m.name">{{ m.name }}</div>
      </div>

      <!-- 右：该模块 权限项×角色 矩阵 -->
      <div class="ps-body">
        <div class="ps-tip">
          每格 = <b>功能</b>(勾选=能做什么) + <b>范围</b>(下拉=对谁的数据) + <b>锚点</b>(按人/按组织)
          · 未配置默认: 查看=角色数据范围, 编辑/删除=仅本人, 锚点=按人
          <el-empty v-if="permLoadError" description="无权限查看权限矩阵或加载失败" />
          <div v-else-if="curModule" class="ps-table-wrap">
            <table class="perm-table">
              <thead>
                <tr>
                  <th class="perm-item">权限项</th>
                  <th v-for="col in matrixCols" :key="col.key" class="ps-role-col">
                    {{ col.label }}
                    <el-link v-if="assigneeMode !== 'ROLE'" type="danger" :underline="false" style="font-size:11px" @click="resetAssignee(col)">恢复默认</el-link>
                    <el-link v-else type="primary" :underline="false" style="font-size:11px" @click="resetRole(col)">恢复默认</el-link>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in curModule.items" :key="item.id">
                  <td class="perm-item">
                    {{ item.label }}
                    <span class="ps-action">{{ actionOf(item.permission) }}</span>
                  </td>
                  <td v-for="col in matrixCols" :key="col.key" class="ps-cell">
                    <el-checkbox :model-value="hasPerm(col, item)" @change="() => togglePerm(col, item)" />
                    <el-select :model-value="scopeOf(col, item)" size="small" style="width:92px"
                               @change="(s) => saveScope(col, item, s)">
                      <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                    <el-select :model-value="anchorOf(col, item)" size="small" style="width:70px"
                               @change="(a) => saveScope(col, item, scopeOf(col, item), a)">
                      <el-option value="OWNER" label="按人" />
                      <el-option value="ORG" label="按组织" />
                    </el-select>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { usePermissionData } from '@/composables/usePermissionData'
import {
  getRolePermScopes, updateRolePermScopes,
  getAssigneePermScopes, updateAssigneePermScopes,
  getPositionList, getUserList,
} from '@/api/system'
import './perm-common.css'

const {
  allRoles, permModules, hasPerm, togglePerm, loadPermData, permLoadError,
} = usePermissionData()

const current = ref('')
const modules = computed(() => permModules.value)
const curModule = computed(() => modules.value.find(m => m.name === current.value))

const scopeOptions = [
  { value: 'all', label: '全部' },
  { value: 'region', label: '本大区及以下' },
  { value: 'office', label: '本办事处及以下' },
  { value: 'dept', label: '本部门及以下' },
  { value: 'team', label: '本人及下级' },
  { value: 'self', label: '仅本人' },
]

// 主体三级: ROLE(角色矩阵) / POSITION / USER
const assigneeMode = ref('ROLE')
const selectedKey = ref('')
const assigneeOptions = ref([])
const positions = ref([])
const users = ref([])

// scopeMap[key][permission] = { scope, anchor }
const scopeMap = ref({})

const matrixCols = computed(() => {
  if (assigneeMode.value === 'ROLE') return allRoles.value.map(r => ({ key: `ROLE:${r.roleCode}`, label: r.roleName }))
  if (!selectedKey.value) return []
  const opt = assigneeOptions.value.find(o => o.key === selectedKey.value)
  return [{ key: `${assigneeMode.value}:${selectedKey.value}`, label: opt?.label || selectedKey.value }]
})

const actionOf = (permission) => (permission || '').split(':')[1] || ''
const moduleOf = (permission) => (permission || '').split(':')[0] || ''

const scopeOf = (col, item) => scopeMap.value[col.key]?.[item.permission]?.scope || 'self'
const anchorOf = (col, item) => scopeMap.value[col.key]?.[item.permission]?.anchor || 'OWNER'

const loadScopesFor = async (assigneeType, assigneeKey) => {
  try {
    const res = assigneeType === 'ROLE'
      ? await getRolePermScopes(assigneeKey)
      : await getAssigneePermScopes(assigneeType, assigneeKey)
    const map = {}
    for (const cfg of (res.data || [])) {
      map[`${cfg.module}:${cfg.action}`] = { scope: cfg.scope_level, anchor: cfg.anchor || 'OWNER' }
    }
    scopeMap.value[`${assigneeType}:${assigneeKey}`] = map
  } catch {}
}

const saveScope = async (col, item, scopeLevel, anchor = anchorOf(col, item)) => {
  const key = col.key
  const [type, assigneeKey] = key.split(':')
  const prev = scopeMap.value[key]?.[item.permission] || { scope: 'self', anchor: 'OWNER' }
  scopeMap.value[key] = { ...(scopeMap.value[key] || {}), [item.permission]: { scope: scopeLevel, anchor } }
  try {
    const cfg = [{ module: moduleOf(item.permission), action: actionOf(item.permission), scopeLevel, anchor }]
    if (type === 'ROLE') await updateRolePermScopes(assigneeKey, cfg)
    else await updateAssigneePermScopes(type, assigneeKey, cfg)
    ElMessage.success(`${col.label} · ${item.label} 范围 → ${scopeOptions.find(o => o.value === scopeLevel)?.label}(${anchor === 'ORG' ? '按组织' : '按人'})`)
  } catch (e) {
    scopeMap.value[key] = { ...(scopeMap.value[key] || {}), [item.permission]: prev }
    ElMessage.error('范围保存失败')
  }
}

// 恢复默认: 空列表=删除该主体全部配置
const resetRole = async (col) => {
  const [, assigneeKey] = col.key.split(':')
  try {
    await updateRolePermScopes(assigneeKey, [])
    scopeMap.value[col.key] = {}
    ElMessage.success(`${col.label} 范围已恢复默认`)
  } catch (e) { ElMessage.error('恢复默认失败') }
}

const resetAssignee = async (col) => {
  const [type, assigneeKey] = col.key.split(':')
  try {
    await updateAssigneePermScopes(type, assigneeKey, [])
    scopeMap.value[col.key] = {}
    ElMessage.success(`${col.label} 范围已恢复默认`)
  } catch (e) { ElMessage.error('恢复默认失败') }
}

const loadScopesForSelected = async () => {
  if (!selectedKey.value) return
  await loadScopesFor(assigneeMode.value, selectedKey.value)
}

const onModeChange = async () => {
  selectedKey.value = ''
  if (assigneeMode.value === 'ROLE') {
    await loadAll()
  } else {
    // 岗位/用户列表(已按集团过滤的后端接口)
    try {
      if (assigneeMode.value === 'POSITION') {
        if (!positions.value.length) positions.value = (await getPositionList()).data || []
        assigneeOptions.value = positions.value.map(p => ({ key: String(p.id), label: `${p.name}（岗位）` }))
      } else {
        if (!users.value.length) users.value = (await getUserList({ page: 1, size: 200 })).data?.records || []
        assigneeOptions.value = users.value.map(u => ({ key: String(u.id), label: `${u.realName || u.username}（用户）` }))
      }
    } catch { assigneeOptions.value = [] }
  }
}

const loadAll = async () => {
  await loadPermData()
  // 角色级: 每个角色加载范围配置
  for (const role of allRoles.value) {
    await loadScopesFor('ROLE', role.roleCode)
  }
  if (selectedKey.value) await loadScopesForSelected()
  if (!current.value && modules.value.length) current.value = modules.value[0].name
}

onMounted(loadAll)
</script>

<style scoped>
.perm-scope-manager { height: 100%; display: flex; flex-direction: column; }
.ps-tabs { margin-bottom: 10px; display: flex; align-items: center; }
.ps-layout { display: flex; flex: 1; min-height: 0; gap: 16px; }
.ps-sidebar { width: 170px; flex-shrink: 0; border-right: 1px solid #ebe5f5; padding-right: 12px; overflow-y: auto; }
.ps-sidebar-title { font-size: 12px; color: #909399; padding: 4px 8px 8px; }
.ps-nav { padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; color: #606266; margin-bottom: 2px; }
.ps-nav:hover { background: #f5f7fa; }
.ps-nav.active { background: #ecf5ff; color: #409eff; font-weight: 600; }
.ps-body { flex: 1; overflow-y: auto; min-width: 0; }
.ps-tip { font-size: 12px; color: #606266; margin-bottom: 10px; }
.ps-table-wrap { overflow-x: auto; }
.ps-action { font-size: 11px; color: #909399; margin-left: 6px; }
.ps-cell { text-align: center; min-width: 180px; white-space: nowrap; }
.ps-role-col { min-width: 130px; }
</style>
