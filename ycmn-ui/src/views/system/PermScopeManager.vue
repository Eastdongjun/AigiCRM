<template>
  <div class="perm-scope-manager">
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
          每格 = <b>功能</b>（勾选 = 该角色能做什么） + <b>范围</b>（下拉 = 能对谁的数据做）· 未配置范围默认 仅本人
          <el-button size="small" style="margin-left:8px" @click="loadAll">刷新</el-button>
        </div>
        <el-empty v-if="permLoadError" description="无权限查看权限矩阵或加载失败" />
        <div v-else-if="curModule" class="ps-table-wrap">
          <table class="perm-table">
            <thead>
              <tr>
                <th class="perm-item">权限项</th>
                <th v-for="r in allRoles" :key="r.id" class="ps-role-col">
                  {{ r.roleName }}
                  <!-- 【修复 2026-08-10 P2-07】恢复默认: 删除该角色全部范围配置(查看回角色 data_scope, 编辑/删除回仅本人) -->
                  <el-link type="primary" :underline="false" style="font-size:11px" @click="resetRole(r)">恢复默认</el-link>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in curModule.items" :key="item.id">
                <td class="perm-item">
                  {{ item.label }}
                  <span class="ps-action">{{ actionOf(item.permission) }}</span>
                </td>
                <td v-for="r in allRoles" :key="r.id" class="ps-cell">
                  <el-checkbox :model-value="hasPerm(r, item)" @change="() => togglePerm(r, item)" />
                  <el-select :model-value="scopeOf(r, item)" size="small" style="width:112px"
                             @change="(s) => saveScope(r, item, s)">
                    <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { usePermissionData } from '@/composables/usePermissionData'
import { getRolePermScopes, updateRolePermScopes } from '@/api/system'
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

// scopeMap[roleCode][permission] = scopeLevel
const scopeMap = ref({})

const actionOf = (permission) => (permission || '').split(':')[1] || ''
const moduleOf = (permission) => (permission || '').split(':')[0] || ''

const scopeOf = (role, item) => {
  return scopeMap.value[role.roleCode]?.[item.permission] || 'self'
}

// 【修复 2026-08-10 P2-07】恢复默认: 空列表=后端删除该角色全部范围配置
const resetRole = async (role) => {
  try {
    await updateRolePermScopes(role.roleCode, [])
    scopeMap.value[role.roleCode] = {}
    ElMessage.success(`${role.roleName} 范围已恢复默认`)
  } catch (e) {
    ElMessage.error('恢复默认失败')
  }
}

const saveScope = async (role, item, scopeLevel) => {
  const prev = scopeMap.value[role.roleCode]?.[item.permission] || 'self'
  scopeMap.value[role.roleCode] = { ...(scopeMap.value[role.roleCode] || {}), [item.permission]: scopeLevel }
  try {
    await updateRolePermScopes(role.roleCode, [{ module: moduleOf(item.permission), action: actionOf(item.permission), scopeLevel }])
    ElMessage.success(`${role.roleName} · ${item.label} 范围 → ${scopeOptions.find(o => o.value === scopeLevel)?.label}`)
  } catch (e) {
    scopeMap.value[role.roleCode] = { ...(scopeMap.value[role.roleCode] || {}), [item.permission]: prev }
    ElMessage.error('范围保存失败')
  }
}

const loadAll = async () => {
  await loadPermData()
  // 加载每个角色的范围配置
  for (const role of allRoles.value) {
    try {
      const res = await getRolePermScopes(role.roleCode)
      const map = {}
      for (const cfg of (res.data || [])) {
        map[`${cfg.module}:${cfg.action}`] = cfg.scope_level
      }
      scopeMap.value[role.roleCode] = map
    } catch {}
  }
  if (!current.value && modules.value.length) current.value = modules.value[0].name
}

onMounted(loadAll)
</script>

<style scoped>
.perm-scope-manager { height: 100%; display: flex; flex-direction: column; }
.ps-layout { display: flex; flex: 1; min-height: 0; gap: 16px; }
.ps-sidebar { width: 170px; flex-shrink: 0; border-right: 1px solid #ebeef5; padding-right: 12px; overflow-y: auto; }
.ps-sidebar-title { font-size: 12px; color: #909399; padding: 4px 8px 8px; }
.ps-nav { padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13px; color: #606266; margin-bottom: 2px; }
.ps-nav:hover { background: #f5f7fa; }
.ps-nav.active { background: #ecf5ff; color: #409eff; font-weight: 600; }
.ps-body { flex: 1; overflow-y: auto; min-width: 0; }
.ps-tip { font-size: 12px; color: #606266; margin-bottom: 10px; }
.ps-table-wrap { overflow-x: auto; }
.ps-action { font-size: 11px; color: #909399; margin-left: 6px; }
.ps-cell { text-align: center; min-width: 130px; }
.ps-role-col { min-width: 90px; }
</style>
