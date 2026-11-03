<template>
  <div class="psm">
    <!-- 【2026-11-03 权限中心重设计】骨架 = 给谁(主体chips) → 权限模块(左栏) → 权限明细(右栏行卡)。
         旧版 12模块×N角色 密格矩阵每格塞三控件、说明糊成一团, 已整体退役;
         数据链不变: 功能勾选 togglePerm→PUT /system/roles/{id}; 范围/锚点 perm-scopes 四函数。 -->
    <div class="psm-bar">
      <el-radio-group v-model="assigneeMode" size="small" @change="onModeChange">
        <el-radio-button value="ROLE">按角色</el-radio-button>
        <el-radio-button value="POSITION">按岗位</el-radio-button>
        <el-radio-button value="USER">按用户</el-radio-button>
      </el-radio-group>
      <el-select v-if="assigneeMode !== 'ROLE'" v-model="selectedKey" size="small" placeholder="选择岗位 / 用户"
                 style="width: 240px" filterable
                 :filter-method="assigneeMode === 'USER' ? searchUserOptions : undefined"
                 @change="loadScopesForSelected" @focus="assigneeMode === 'USER' && searchUserOptions('')">
        <el-option v-for="o in assigneeOptions" :key="o.key" :label="o.label" :value="o.key" />
      </el-select>
      <el-button size="small" @click="loadAll">刷新</el-button>
      <span v-if="!canEditScope" class="psm-readonly">当前账号只读：需系统管理员 + system:config:edit 才能修改</span>
    </div>

    <!-- ① 给谁: 角色chips(仅ROLE模式) -->
    <div v-if="assigneeMode === 'ROLE'" class="psm-who">
      <button v-for="r in allRoles" :key="r.id" class="psm-who-chip" :class="{ active: currentRoleId === r.id }"
              @click="switchRole(r)">
        <span class="psm-who-name">{{ r.roleName }}</span>
        <span class="psm-who-count">{{ enabledTotalOf(r) }} 项</span>
      </button>
      <span v-if="!allRoles.length && !permLoadError" class="psm-who-empty">加载中…</span>
    </div>

    <el-empty v-if="permLoadError" description="无权限查看权限矩阵（缺少 system:menu:view / system:role:view）或加载失败" />
    <div v-else-if="currentAssignee" class="psm-main">
      <!-- ② 权限模块(左栏) -->
      <aside class="psm-mods">
        <div class="psm-mods-title">权限模块</div>
        <button v-for="m in modules" :key="m.name" class="psm-mod" :class="{ active: current === m.name }"
                @click="current = m.name">
          <span class="psm-mod-name">{{ m.name }}</span>
          <span class="psm-mod-count" :class="{ on: enabledCountOf(m) > 0 }">
            <b>{{ enabledCountOf(m) }}</b>/{{ m.items.length }}
          </span>
        </button>
      </aside>

      <!-- ③ 权限明细(右栏) -->
      <section class="psm-detail">
        <header class="psm-head">
          <div class="psm-head-text">
            <div class="psm-head-title">{{ curModule?.name }}<span class="psm-head-sub"> · {{ currentAssigneeLabel }} · 已启用 {{ curEnabledCount }}/{{ curModule?.items.length }}</span></div>
            <div class="psm-head-hint">勾选 = 能做什么 · 范围 = 对谁的数据 · 锚点未手选时按推荐自动保存（查看类=按组织看盘子，编辑类=按人跟负责人）</div>
          </div>
          <div v-if="canEditScope && isRoleMode" class="psm-head-actions">
            <el-button size="small" @click="toggleModuleAll(false)">全不勾</el-button>
            <el-button size="small" type="primary" plain @click="toggleModuleAll(true)">全勾</el-button>
            <el-button size="small" type="danger" plain @click="resetCurrent">恢复默认范围</el-button>
          </div>
        </header>

        <div class="psm-rows">
          <div v-for="item in curModule?.items" :key="item.id" class="psm-row" :class="{ off: !isEnabled(item) }">
            <el-checkbox class="psm-row-check" :model-value="isEnabled(item)"
                         :disabled="!isRoleMode || !canEditScope" @change="() => onToggle(item)" />
            <div class="psm-row-info">
              <span class="psm-row-name">{{ item.label }}</span>
              <code class="psm-row-code">{{ item.permission }}</code>
            </div>
            <div class="psm-row-ctrl">
              <template v-if="isScopeDriven(item)">
                <el-select :model-value="scopeOf(item)" size="small" class="psm-scope"
                           :disabled="!canEditScope" @change="(s) => saveScope(item, s)">
                  <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <el-select :model-value="anchorOf(item)" size="small" class="psm-anchor"
                           :disabled="!canEditScope" @change="(a) => saveScope(item, scopeOf(item), a)">
                  <el-option value="OWNER" label="按人" />
                  <el-option value="ORG" label="按组织" />
                </el-select>
                <span v-if="anchorOf(item) === recommendedAnchorOf(item)" class="psm-rec" title="推荐锚点（未手选时自动按此保存）">荐</span>
              </template>
              <span v-else class="psm-na" :title="'该权限的数据范围' + scopeOwnerOf(item) + '，这里只配置功能开关'">
                {{ scopeOwnerOf(item) }}
              </span>
            </div>
          </div>
        </div>

        <!-- 人话摘要: 配完一眼回读 -->
        <footer class="psm-summary">
          <b>{{ currentAssigneeLabel }}</b> 在「{{ curModule?.name }}」：
          <template v-if="summaryLines.length">
            <span v-for="(line, i) in summaryLines" :key="i" class="psm-sum-chip">{{ line }}</span>
          </template>
          <span v-else class="psm-sum-none">未启用任何权限项</span>
        </footer>
      </section>
    </div>
    <el-empty v-else :description="assigneeMode === 'ROLE' ? '暂无角色可配置' : '请先选择岗位 / 用户'" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePermissionData } from '@/composables/usePermissionData'
import { useUserStore } from '@/store/user'
import {
  getRolePermScopes, updateRolePermScopes,
  getAssigneePermScopes, updateAssigneePermScopes,
  getPositionList, getUserList, updateRole,
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
const scopeLabelOf = (v) => scopeOptions.find(o => o.value === v)?.label || v

// ---- 主体三级: ROLE(默认, 角色chips) / POSITION / USER ----
const assigneeMode = ref('ROLE')
const selectedKey = ref('')
const assigneeOptions = ref([])
const positions = ref([])
const currentRoleId = ref(null)

// scopeMap['{TYPE}:{key}'][permission] = { scope, anchor }
const scopeMap = ref({})

const isRoleMode = computed(() => assigneeMode.value === 'ROLE')
const currentRole = computed(() => allRoles.value.find(r => r.id === currentRoleId.value))
const currentAssignee = computed(() => {
  if (isRoleMode.value) return currentRole.value || null
  return selectedKey.value ? { key: selectedKey.value } : null
})
const currentAssigneeLabel = computed(() => {
  if (isRoleMode.value) return currentRole.value?.roleName || ''
  const opt = assigneeOptions.value.find(o => o.key === selectedKey.value)
  return opt?.label || selectedKey.value
})
const assigneeScopeKey = computed(() => {
  if (isRoleMode.value) return currentRole.value ? `ROLE:${currentRole.value.roleCode}` : ''
  return selectedKey.value ? `${assigneeMode.value}:${selectedKey.value}` : ''
})

// 【2026-10-17 矩阵诚实化】权限码 → 后端运行时真实消费的 (module, action) 显式映射。
// 只对下列明确消费的权限项显示范围/锚点控件；其余权限码隐藏控件并标注实际控制面。
const PERMISSION_SCOPE_TARGETS = Object.freeze({
  'customer:view': { module: 'customer', action: 'view' },
  'customer:edit': { module: 'customer', action: 'edit' },
  'customer:delete': { module: 'customer', action: 'delete' },
  // 【2026-11-03 拆码】开票回款编辑带独立数据范围(行政管大区财务不碰客户资料的正解)
  'customer:finance:edit': { module: 'customer', action: 'finance_edit' },
  'reminder:view': { module: 'reminder', action: 'view' },
  'approval:view': { module: 'approval', action: 'view' },
  'task:view': { module: 'task', action: 'view' },
  'sea:view': { module: 'sea', action: 'view' },
  'system:log:view': { module: 'system', action: 'view' },
  // 【2026-11-03 范围对账修复】摘 incentive:view/file:view(后端零消费的死配置:
  // 激励可见性=分享制+授权面板, 文件=文件夹权限, 统计文件计数走 statistics×view);
  // 补 statistics/score(后端真实消费却没露出入口的漏配)
  'statistics:view': { module: 'statistics', action: 'view' },
  'score:view': { module: 'score', action: 'view' },
})
const scopeTargetOf = (permission) => PERMISSION_SCOPE_TARGETS[permission] || null

// 未接入范围矩阵的模块: 标注真实控制面, 避免管理员误以为配置生效
const SCOPE_OWNER_HINTS = {
  // 【2026-11-03 明确化】开关码的灰字直接写跟随谁, 免得"模块自身规则"让人再问一遍
  finance: '跟随客户范围(查看类=客户·查看, manage=全集团代录)',
  contact: '跟随客户范围(查看=客户·查看, 管理=客户·编辑)',
  incentive: '激励授权面板(分享制+授权表, 不走组织范围)',
  file: '文件夹权限',
  task: '按经办人',
  dashboard: '跟随各模块',
  indicator: '统计分析口径',
  statistics: '统计分析口径',
  score: '学分规则',
}
const isScopeDriven = (item) => scopeTargetOf(item.permission) !== null
const scopeOwnerOf = (item) => SCOPE_OWNER_HINTS[(item.permission || '').split(':')[0]] || '由模块自身规则控制'

// 后端配置以 (module, action) 为键; system:log:view 读写的都是 (system, view)
const scopeKeyOf = (permission) => {
  const target = scopeTargetOf(permission)
  return target ? `${target.module}:${target.action}` : permission
}
const scopeOf = (item) => scopeMap.value[assigneeScopeKey.value]?.[scopeKeyOf(item.permission)]?.scope || 'self'
// 【锚点推荐】未显式配置的项在界面与保存时都按推荐走: 查看/统计类=按组织, 编辑/删除/财务类=按人
const recommendedAnchorOf = (item) => (scopeTargetOf(item.permission)?.action === 'view' ? 'ORG' : 'OWNER')
const anchorOf = (item) => scopeMap.value[assigneeScopeKey.value]?.[scopeKeyOf(item.permission)]?.anchor || recommendedAnchorOf(item)

const canEditScope = computed(() => userStore.isAdmin() && userStore.hasPermission('system:config:edit'))
const userStore = useUserStore()

// ---- 功能勾选(ROLE) ----
const isEnabled = (item) => (isRoleMode.value && currentRole.value ? hasPerm(currentRole.value, item) : false)
const enabledCountOf = (m) => m.items.filter(i => isEnabled(i)).length
const enabledTotalOf = (r) => modules.value.reduce((sum, m) => sum + m.items.filter(i => hasPerm(r, i)).length, 0)
const curEnabledCount = computed(() => (curModule.value ? enabledCountOf(curModule.value) : 0))

const onToggle = (item) => {
  if (!isRoleMode.value || !currentRole.value) return
  togglePerm(currentRole.value, item)
}

// 模块级批量勾选: 单次 PUT 提交整个角色的 menuIds(旧逐项 toggle 会发 N 个请求)
const toggleModuleAll = async (enable) => {
  if (!isRoleMode.value || !currentRole.value || !curModule.value) return
  const set = new Set(usePermissionDataRoleIds(currentRole.value) || [])
  for (const item of curModule.value.items) {
    if (enable) set.add(item.id)
    else set.delete(item.id)
  }
  try {
    await updateRole(currentRole.value.id, { menuIds: [...set] })
    const inner = rolePermMapOf(currentRole.value)
    inner.clear()
    for (const id of set) inner.add(id)
    ElMessage.success(`${currentRole.value.roleName} · ${curModule.value.name} 已${enable ? '全勾' : '全不勾'}`)
  } catch (e) {
    ElMessage.error('批量保存失败，请重试')
  }
}
// rolePermMap 属共享单例, 这里取引用供批量回写(与 togglePerm 同一数据面)
const { rolePermMap } = usePermissionData()
const usePermissionDataRoleIds = (role) => [...(rolePermMap.value[role.id] || [])]
const rolePermMapOf = (role) => {
  if (!rolePermMap.value[role.id]) rolePermMap.value[role.id] = new Set()
  return rolePermMap.value[role.id]
}

// ---- 范围/锚点读写(ROLE/POSITION/USER 同链) ----
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
  } catch { /* 读失败保持空(按默认语义展示) */ }
}

const saveScope = async (item, scopeLevel, anchor) => {
  if (!canEditScope.value) { ElMessage.warning('无权限修改权限范围（需系统管理员 + system:config:edit）'); return }
  const target = scopeTargetOf(item.permission)
  if (!target) { ElMessage.warning('该权限项未接入数据范围配置'); return }
  const finalAnchor = anchor || scopeMap.value[assigneeScopeKey.value]?.[scopeKeyOf(item.permission)]?.anchor || recommendedAnchorOf(item)
  const key = assigneeScopeKey.value
  if (!key) { ElMessage.warning('请先选择要配置的角色 / 岗位 / 用户'); return }
  const entryKey = scopeKeyOf(item.permission)
  const [type, assigneeKey] = key.split(':')
  const prev = scopeMap.value[key]?.[entryKey] || { scope: 'self', anchor: 'OWNER' }
  // 危险组合二次确认: 删除/财务类 × 全部数据
  if (scopeLevel === 'all' && /delete|finance/.test(target.action)) {
    try {
      await ElMessageBox.confirm(
        `正在授予「${currentAssigneeLabel.value} · ${item.label} = 全部(总部级)」——全公司范围的高危组合，确认继续？`,
        '高危授权确认', { type: 'warning', confirmButtonText: '确认授予', cancelButtonText: '取消' }
      )
    } catch { return }
  }
  scopeMap.value[key] = { ...(scopeMap.value[key] || {}), [entryKey]: { scope: scopeLevel, anchor: finalAnchor } }
  try {
    const cfg = [{ module: target.module, action: target.action, scopeLevel, anchor: finalAnchor }]
    if (type === 'ROLE') await updateRolePermScopes(assigneeKey, cfg)
    else await updateAssigneePermScopes(type, assigneeKey, cfg)
    ElMessage.success(`${item.label} → ${scopeLabelOf(scopeLevel)}（${finalAnchor === 'ORG' ? '按组织' : '按人'}）`)
  } catch (e) {
    scopeMap.value[key] = { ...(scopeMap.value[key] || {}), [entryKey]: prev }
    ElMessage.error('范围保存失败')
  }
}

const resetCurrent = async () => {
  if (!canEditScope.value) { ElMessage.warning('无权限修改权限范围'); return }
  if (isRoleMode.value && currentRole.value) {
    try {
      await updateRolePermScopes(currentRole.value.roleCode, [])
      scopeMap.value[`ROLE:${currentRole.value.roleCode}`] = {}
      ElMessage.success(`${currentRole.value.roleName} 的数据范围已恢复默认`)
    } catch (e) { ElMessage.error('恢复默认失败') }
  } else if (selectedKey.value) {
    try {
      await updateAssigneePermScopes(assigneeMode.value, selectedKey.value, [])
      scopeMap.value[`${assigneeMode.value}:${selectedKey.value}`] = {}
      ElMessage.success('数据范围已恢复默认')
    } catch (e) { ElMessage.error('恢复默认失败') }
  }
}

// ---- 人话摘要 ----
const summaryLines = computed(() => {
  if (!curModule.value) return []
  const lines = []
  for (const item of curModule.value.items) {
    if (!isEnabled(item)) continue
    if (isScopeDriven(item)) {
      lines.push(`${item.label} = ${scopeLabelOf(scopeOf(item))}·${anchorOf(item) === 'ORG' ? '按组织' : '按人'}`)
    } else {
      lines.push(`${item.label} = 开`)
    }
  }
  return lines
})

// ---- 装载 ----
const switchRole = async (role) => {
  currentRoleId.value = role.id
  await loadScopesFor('ROLE', role.roleCode)
  if (!curModule.value && modules.value.length) current.value = modules.value[0].name
}

const loadScopesForSelected = async () => {
  if (!selectedKey.value) return
  await loadScopesFor(assigneeMode.value, selectedKey.value)
}

const onModeChange = async () => {
  selectedKey.value = ''
  if (assigneeMode.value === 'ROLE') {
    if (!allRoles.value.length) await loadPermData()
    if (allRoles.value.length && !currentRoleId.value) await switchRole(allRoles.value[0])
    else if (currentRoleId.value) await loadScopesFor('ROLE', currentRole.value?.roleCode)
  } else {
    try {
      if (assigneeMode.value === 'POSITION') {
        if (!positions.value.length) positions.value = (await getPositionList()).data || []
        assigneeOptions.value = positions.value.map(p => ({ key: String(p.id), label: `${p.name}（岗位）` }))
      } else {
        await searchUserOptions('')
      }
    } catch { assigneeOptions.value = [] }
  }
}

// 【M1 2026-10-04】按用户: keyword 远程检索(后端只认 current/size/keyword), 已选中项钉首防回显退化
let usersPrev = []
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
  if (allRoles.value.length) {
    if (!currentRoleId.value || !allRoles.value.some(r => r.id === currentRoleId.value)) {
      await switchRole(allRoles.value[0])
    } else {
      await loadScopesFor('ROLE', currentRole.value.roleCode)
    }
  }
  if (selectedKey.value) await loadScopesForSelected()
  if (!current.value && modules.value.length) current.value = modules.value[0].name
}

onMounted(loadAll)
</script>

<style scoped>
.psm { height: 100%; display: flex; flex-direction: column; gap: 12px; }
.psm-bar { display: flex; align-items: center; gap: 10px; }
.psm-readonly { color: var(--el-color-warning); font-size: 12px; }

/* ① 给谁: 角色chips */
.psm-who { display: flex; gap: 8px; flex-wrap: wrap; }
.psm-who-chip { display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px; border: 1px solid #dcdfe6;
  border-radius: 10px; background: #fff; cursor: pointer; transition: all .15s; }
.psm-who-chip:hover { border-color: var(--el-color-primary-light-5); }
.psm-who-chip.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset; }
.psm-who-name { font-size: 14px; font-weight: 600; color: #303133; }
.psm-who-chip.active .psm-who-name { color: var(--el-color-primary); }
.psm-who-count { font-size: 12px; color: var(--el-color-info); }
.psm-who-empty { font-size: 13px; color: var(--el-color-info); align-self: center; }

/* ② + ③ 主体两栏 */
.psm-main { display: flex; gap: 14px; flex: 1; min-height: 0; align-items: stretch; }
.psm-mods { width: 200px; flex-shrink: 0; border: 1px solid #ebeef5; border-radius: 10px; padding: 8px;
  overflow-y: auto; background: #fafbfc; display: flex; flex-direction: column; gap: 2px; }
.psm-mods-title { font-size: 12px; color: var(--el-color-info); padding: 4px 8px 8px; }
.psm-mod { display: flex; align-items: center; justify-content: space-between; padding: 9px 10px; border: none;
  background: transparent; border-radius: 8px; cursor: pointer; font-size: 13px; color: #606266; text-align: left; }
.psm-mod:hover { background: #f0f2f5; }
.psm-mod.active { background: var(--el-color-primary); color: #fff; }
.psm-mod.active .psm-mod-count { color: rgba(255,255,255,.85); }
.psm-mod-count { font-size: 12px; color: var(--el-color-info); }
.psm-mod-count b { color: var(--el-color-primary); font-weight: 600; }
.psm-mod.active .psm-mod-count b { color: #fff; }

/* ③ 明细 */
.psm-detail { flex: 1; min-width: 0; border: 1px solid #ebeef5; border-radius: 10px; background: #fff;
  display: flex; flex-direction: column; overflow: hidden; }
.psm-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px;
  padding: 12px 16px; border-bottom: 1px solid #f0f2f5; }
.psm-head-title { font-size: 15px; font-weight: 700; color: #1f2d3d; }
.psm-head-sub { font-size: 12px; font-weight: 400; color: var(--el-color-info); }
.psm-head-hint { font-size: 12px; color: var(--el-color-info); margin-top: 4px; }
.psm-head-actions { display: flex; gap: 8px; flex-shrink: 0; }
.psm-rows { flex: 1; overflow-y: auto; padding: 6px 10px; }
.psm-row { display: flex; align-items: center; gap: 12px; padding: 9px 10px; border-radius: 8px; }
.psm-row:hover { background: #f7f8fa; }
.psm-row.off .psm-row-name, .psm-row.off .psm-row-code, .psm-row.off .psm-row-ctrl { opacity: .45; }
.psm-row-check { flex-shrink: 0; }
.psm-row-info { width: 300px; flex-shrink: 0; display: flex; align-items: center; gap: 8px; min-width: 0; }
.psm-row-name { font-size: 13.5px; font-weight: 600; color: #303133; white-space: nowrap; }
.psm-row-code { font-size: 11.5px; color: #909399; background: #f4f4f5; padding: 1px 6px; border-radius: 4px; }
.psm-row-ctrl { display: flex; align-items: center; gap: 6px; }
.psm-scope { width: 150px; }
.psm-anchor { width: 100px; }
.psm-rec { font-size: 11px; color: var(--el-color-success); border: 1px solid var(--el-color-success-light-5);
  border-radius: 4px; padding: 0 4px; line-height: 16px; }
.psm-na { font-size: 12px; color: #a8abb2; }

/* 人话摘要 */
.psm-summary { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 10px 16px;
  border-top: 1px solid #f0f2f5; background: #fafbfc; font-size: 12.5px; color: #4e5969; }
.psm-sum-chip { background: #eef2ff; color: #3b5bdb; border-radius: 6px; padding: 2px 8px; font-size: 12px; }
.psm-sum-none { color: var(--el-color-info); }
</style>
