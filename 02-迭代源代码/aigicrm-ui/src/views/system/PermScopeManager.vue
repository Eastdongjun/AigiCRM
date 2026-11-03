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
                 style="width: 220px; margin-left: 10px" filterable
                 :filter-method="assigneeMode === 'USER' ? searchUserOptions : undefined"
                 @change="loadScopesForSelected"
                 @focus="assigneeMode === 'USER' && searchUserOptions('')">
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
          · 范围未配置默认: 查看=角色数据范围, 编辑/删除/财务=仅本人; 锚点未配置按推荐保存: <b>查看类=按组织(看部门盘子), 编辑类=按人(跟负责人走)</b>
          · 仅后端真实消费的权限项显示范围列；其余权限码隐藏控件并标注实际控制面（如文件夹权限/激励授权面板）
          <!-- 【2026-09-13】只读角色（无 system:config:edit）不再显示可操作控件：此前能改下拉，
               保存时才被后端 403，属"界面已改、实际没变"的误导 -->
          <span v-if="!canEditScope" class="ps-readonly">（当前账号只读：需 system:config:edit 权限才能修改）</span>
          <el-empty v-if="permLoadError" description="无权限查看权限矩阵或加载失败" />
          <div v-else-if="curModule" class="ps-table-wrap">
            <table class="perm-table">
              <thead>
                <tr>
                  <th class="perm-item">权限项</th>
                  <th v-for="col in matrixCols" :key="col.key" class="ps-role-col">
                    {{ col.label }}
                    <el-link v-if="canEditScope && assigneeMode !== 'ROLE'" type="danger" :underline="false" style="font-size:12px" @click="resetAssignee(col)">恢复默认</el-link>
                    <el-link v-else-if="canEditScope" type="primary" :underline="false" style="font-size:12px" @click="resetRole(col)">恢复默认</el-link>
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
                    <el-checkbox :model-value="col.isRole ? hasPerm(col.role, item) : false"
                                 :disabled="!col.isRole || !canEditScope"
                                 @change="() => col.isRole && togglePerm(col.role, item)" />
                    <el-select v-if="isScopeDriven(item)" :model-value="scopeOf(col, item)" size="small" style="width:92px"
                               :disabled="!canEditScope"
                               @change="(s) => saveScope(col, item, s)">
                      <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
                    </el-select>
                    <el-select v-if="isScopeDriven(item)" :model-value="anchorOf(col, item)" size="small" style="width:70px"
                               :disabled="!canEditScope"
                               @change="(a) => saveScope(col, item, scopeOf(col, item), a)">
                      <el-option value="OWNER" label="按人" />
                      <el-option value="ORG" label="按组织" />
                    </el-select>
                    <span v-else class="ps-scope-na" :title="'该模块数据范围由' + scopeOwnerOf(item) + '控制，矩阵此处仅配置功能勾选'">
                      {{ scopeOwnerOf(item) }}
                    </span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePermissionData } from '@/composables/usePermissionData'
import { useUserStore } from '@/store/user'
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

// 【2026-11-03 权限中心】七档标签层级化(对齐 总部→大区→办事处→部门→团队→个人 的组织阶梯)
const scopeOptions = [
  { value: 'all', label: '全部(总部级)' },
  { value: 'region', label: '本大区及以下' },
  { value: 'office', label: '本办事处及以下' },
  { value: 'dept', label: '本部门及以下' },
  { value: 'team', label: '本人及下级' },
  { value: 'self', label: '仅本人' },
  { value: 'custom', label: '自定义组织' },
]

// 主体三级: ROLE(角色矩阵) / POSITION / USER
const assigneeMode = ref('ROLE')
const selectedKey = ref('')
const assigneeOptions = ref([])
const positions = ref([])

// scopeMap[key][permission] = { scope, anchor }
const scopeMap = ref({})

const matrixCols = computed(() => {
  // 【修复 2026-08-11 P1】ROLE 模式携带完整角色对象(含 .id)：hasPerm/togglePerm 依赖 rolePermMap[role.id]，
  // 此前 col 仅 {key,label} 无 .id，功能勾选列恒 false、点击无反应。POSITION/USER 无 RBAC 功能权限，
  // 功能列禁用(仅范围/锚点可配)。
  if (assigneeMode.value === 'ROLE') return allRoles.value.map(r => ({ key: `ROLE:${r.roleCode}`, label: r.roleName, role: r, isRole: true }))
  if (!selectedKey.value) return []
  const opt = assigneeOptions.value.find(o => o.key === selectedKey.value)
  return [{ key: `${assigneeMode.value}:${selectedKey.value}`, label: opt?.label || selectedKey.value, isRole: false }]
})

// 【2026-10-17 矩阵诚实化】权限码 → 后端运行时真实消费的 (module, action) 显式映射。
// 旧实现用 permission.split(':')[1]：system:log:view 被解析成 (system, log) 死配置，
// system:user:add 被解析成 (system, user)，后端永不读取，属于"配了不生效"的误导。
// 只对下列明确消费的权限项显示范围/锚点控件；其余权限码隐藏控件并标注实际控制面。
const PERMISSION_SCOPE_TARGETS = Object.freeze({
  'customer:view': { module: 'customer', action: 'view' },
  'customer:edit': { module: 'customer', action: 'edit' },
  'customer:delete': { module: 'customer', action: 'delete' },
  // 【2026-11-03 拆码】开票回款编辑带独立数据范围(行政管大区财务不碰客户资料的正解)
  'customer:finance:edit': { module: 'customer', action: 'finance_edit' },
  'incentive:view': { module: 'incentive', action: 'view' },
  'file:view': { module: 'file', action: 'view' },
  'reminder:view': { module: 'reminder', action: 'view' },
  'approval:view': { module: 'approval', action: 'view' },
  'task:view': { module: 'task', action: 'view' },
  'sea:view': { module: 'sea', action: 'view' },
  'system:log:view': { module: 'system', action: 'view' },
})

const scopeTargetOf = (permission) => PERMISSION_SCOPE_TARGETS[permission] || null
const actionOf = (permission) => {
  const target = scopeTargetOf(permission)
  if (target) return target.action
  const parts = (permission || '').split(':')
  return parts.length >= 2 ? parts[parts.length - 1] : ''
}
const moduleOf = (permission) => scopeTargetOf(permission)?.module || (permission || '').split(':')[0] || ''

// 未接入范围矩阵的模块/权限码：隐去下拉，标注真实控制面，避免管理员误以为配置生效
const SCOPE_OWNER_HINTS = {
  incentive: '激励授权面板',
  file: '文件夹权限',
  task: '按经办人',
  dashboard: '跟随各模块',
  indicator: '统计分析口径',
  statistics: '统计分析口径',
  score: '学分规则',
}
const isScopeDriven = (item) => scopeTargetOf(item.permission) !== null
const scopeOwnerOf = (item) => SCOPE_OWNER_HINTS[moduleOf(item.permission)] || '模块自身规则'

// 后端配置以 (module, action) 为键返回；回显/保存都必须按映射后的键读写，
// 否则 system:log:view 永远读不到已保存的 system:view 配置。
const scopeKeyOf = (permission) => {
  const target = scopeTargetOf(permission)
  return target ? `${target.module}:${target.action}` : permission
}
const scopeOf = (col, item) => scopeMap.value[col.key]?.[scopeKeyOf(item.permission)]?.scope || 'self'
// 【2026-11-03 权限中心·锚点推荐】未显式配置的格子在界面与保存时都按推荐锚点走:
// 查看/统计类=按组织(看部门盘子), 编辑/删除/财务类=按人(跟负责人走)——存量已配置项不受影响。
const recommendedAnchorOf = (item) => {
  const action = scopeTargetOf(item.permission)?.action || ''
  return action === 'view' ? 'ORG' : 'OWNER'
}
const anchorOf = (col, item) => scopeMap.value[col.key]?.[scopeKeyOf(item.permission)]?.anchor || recommendedAnchorOf(item)

// 【2026-10-17】编辑门禁与后端对齐：PUT 需 system:config:edit（@PreAuthorize）且 requireAdmin()
// 仅系统管理员可写；非 admin 即使持 config:edit 也只读，避免"界面已改、实际 403"的假成功。
const userStore = useUserStore()
const canEditScope = computed(() => userStore.isAdmin() && userStore.hasPermission('system:config:edit'))

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

const saveScope = async (col, item, scopeLevel, anchor) => {
  if (!canEditScope.value) { ElMessage.warning('无权限修改权限范围（需系统管理员 + system:config:edit）'); return }
  // 未接入显式映射的权限码不写死配置（如 system:user:add / sea:config / customer:date）
  const target = scopeTargetOf(item.permission)
  if (!target) { ElMessage.warning('该权限项未接入数据范围配置'); return }
  // 【锚点推荐】未显式选择锚点(切换范围)时按推荐: 查看类=按组织, 编辑/删除/财务类=按人
  const finalAnchor = anchor || scopeMap.value[col.key]?.[scopeKeyOf(item.permission)]?.anchor || recommendedAnchorOf(item)
  const key = col.key
  const entryKey = scopeKeyOf(item.permission)
  const [type, assigneeKey] = key.split(':')
  const prev = scopeMap.value[key]?.[entryKey] || { scope: 'self', anchor: 'OWNER' }
  // 【危险组合警示】删除类×全部数据 = 全公司删除权, 保存前二次确认
  if (scopeLevel === 'all' && /delete|finance/.test(target.action)) {
    try {
      await ElMessageBox.confirm(
        `正在授予「${col.label} · ${item.label} = 全部(总部级)」——这是全公司范围的高危组合，确认继续？`,
        '高危授权确认', { type: 'warning', confirmButtonText: '确认授予', cancelButtonText: '取消' }
      )
    } catch { return }
  }
  scopeMap.value[key] = { ...(scopeMap.value[key] || {}), [entryKey]: { scope: scopeLevel, anchor: finalAnchor } }
  try {
    const cfg = [{ module: target.module, action: target.action, scopeLevel, anchor: finalAnchor }]
    if (type === 'ROLE') await updateRolePermScopes(assigneeKey, cfg)
    else await updateAssigneePermScopes(type, assigneeKey, cfg)
    ElMessage.success(`${col.label} · ${item.label} 范围 → ${scopeOptions.find(o => o.value === scopeLevel)?.label}(${finalAnchor === 'ORG' ? '按组织' : '按人'})`)
  } catch (e) {
    scopeMap.value[key] = { ...(scopeMap.value[key] || {}), [entryKey]: prev }
    ElMessage.error('范围保存失败')
  }
}

// 恢复默认: 空列表=删除该主体全部配置
const resetRole = async (col) => {
  if (!canEditScope.value) { ElMessage.warning('无权限修改权限范围（需 system:config:edit）'); return }
  const [, assigneeKey] = col.key.split(':')
  try {
    await updateRolePermScopes(assigneeKey, [])
    scopeMap.value[col.key] = {}
    ElMessage.success(`${col.label} 范围已恢复默认`)
  } catch (e) { ElMessage.error('恢复默认失败') }
}

const resetAssignee = async (col) => {
  if (!canEditScope.value) { ElMessage.warning('无权限修改权限范围（需 system:config:edit）'); return }
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
        // 【M1 2026-10-04】参数名对齐后端契约：GET /system/users 只认 current/size/keyword，
        // 此前传 page 后端收不到（恒回第 1 页）；且 200 条封顶选不全，
        // 改为聚焦/输入时走 searchUserOptions 按 keyword 远程检索（不整装截断列表）。
        await searchUserOptions('')
      }
    } catch { assigneeOptions.value = [] }
  }
}

// 【M1 2026-10-04】按用户：keyword 远程过滤（后端 SysUserController.page 支持 keyword），
// 每次最多取 50 条匹配项；已选中项不在结果中时钉在首位，避免回显 label 退化成裸 key。
let usersPrev = [] // 上一轮检索结果（含已选中项的 label 缓存）
const searchUserOptions = async (query) => {
  try {
    const res = await getUserList({ current: 1, size: 50, keyword: query || undefined })
    const records = res.data?.records || []
    const opts = records.map(u => ({ key: String(u.id), label: `${u.realName || u.username}（用户）` }))
    if (selectedKey.value && !opts.some(o => o.key === selectedKey.value)) {
      const sel = usersPrev.find(o => o.key === selectedKey.value)
      if (sel) opts.unshift(sel)
    }
    usersPrev = opts
    assigneeOptions.value = opts
  } catch { /* 检索失败保留现有选项 */ }
}

const loadAll = async () => {
  await loadPermData()
  // 【性能 2026-10-04】各角色的范围配置互不依赖，串行 for 改为 Promise.all 并发（参照 reminder 页 2026-08-08 改法）
  await Promise.all(allRoles.value.map(role => loadScopesFor('ROLE', role.roleCode)))
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
.ps-sidebar-title { font-size: 12px; color: var(--el-color-info); padding: 4px 8px 8px; }
.ps-nav { padding: 8px 10px; border-radius: var(--radius-sm); cursor: pointer; font-size: 13px; color: #606266; margin-bottom: 2px; }
.ps-nav:hover { background: #f5f7fa; }
.ps-nav.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.ps-body { flex: 1; overflow-y: auto; min-width: 0; }
.ps-tip { font-size: 12px; color: #606266; margin-bottom: 10px; }
.ps-readonly { color: var(--el-color-warning); }
.ps-table-wrap { overflow-x: auto; }
.ps-action { font-size: 12px; color: #6b7280; margin-left: 6px; }
.ps-cell { text-align: center; min-width: 180px; white-space: nowrap; }
.ps-scope-na { font-size: 12px; color: #6b7280; margin-left: 6px; cursor: help; }
.ps-role-col { min-width: 130px; }
</style>
