#!/usr/bin/env bash
# 校验「统一权限配置中心」(2026-11-03 用户裁决: 门面统一 + 收口单入口 + 拆财务编辑码 + 公海/激励按角色) 契约。
#
# 核心约束:
#   A. 唯一可写入口: 权限配置只在 /system/perm-center; 系统设置不再承载权限可写 Tab; 角色管理功能勾选弹窗只读
#   B. customer:finance:edit 拆码: 迁移 seed + 守卫三通道(manage/finance:edit/edit∧view) + 对象级 finance_edit 范围
#   C. 公海名单按角色引用: 四列 + isInScope 角色交集判定 + UI 角色多选
#   D. 激励授权按角色批量: grant 展开 roleIds 成员并集
#   E. 生效预览 + 审计: preview 端点复用引擎; 范围/激励授权变更记 sys_rule_version
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI="$ROOT/aigicrm-ui/src"
BE="$ROOT/aigicrm-backend/src/main/java"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"
MP="$ROOT/aigicrm-miniprogram"

fail() { echo "FAIL: $*" >&2; exit 1; }

# ---- A. 唯一可写入口 ----
grep -Fq "system/perm-center" "$UI/router/index.js" || fail 'router must register /system/perm-center (PermCenter)'
grep -Fq "PermCenter.vue" "$UI/router/index.js" || fail 'router must load PermCenter.vue'
grep -Fq "/system/perm-center" "$MIG/V2026.11.03.2__perm_center_menu.sql" || fail 'migration must seed perm-center menu'
grep -Fq "PermCenter.vue" "$UI/views/system/PermCenter.vue" 2>/dev/null || true
[ -f "$UI/views/system/PermCenter.vue" ] || fail 'PermCenter.vue must exist'
grep -Fq "PermScopeManager" "$UI/views/system/PermCenter.vue" || fail 'PermCenter tab-1 must embed PermScopeManager'
grep -Fq "SeaConfigManager" "$UI/views/system/PermCenter.vue" || fail 'PermCenter tab-2 must embed SeaConfigManager'
# 收口反钉: 系统设置页不得再承载权限/公海可写组件
if grep -Eq "PermScopeManager|PermMatrixManager|SeaConfigManager" "$UI/views/system/config.vue"; then
  fail 'config.vue must not host permission/sea config anymore (moved to PermCenter)'
fi
# 收口反钉: 角色管理页不得再有可写功能勾选保存链
if grep -Fq "handleSavePerm" "$UI/views/system/role.vue"; then
  fail 'role.vue perm dialog must be read-only (no handleSavePerm; writable entry = PermCenter)'
fi
grep -Fq "去权限中心配置" "$UI/views/system/role.vue" || fail 'role.vue must offer jump to PermCenter'
# 收口反钉: CategoryManager 不再承载激励授权区块
if grep -Fq "revokePerm" "$UI/views/system/CategoryManager.vue"; then
  fail 'CategoryManager must not host incentive grant table (moved to PermCenter)'
fi

# ---- B. customer:finance:edit 拆码 ----
grep -Fq "customer:finance:edit" "$MIG/V2026.11.03.3__customer_finance_edit_permission.sql" || fail 'migration must seed customer:finance:edit'
grep -Fq "customer:finance:edit" "$BE/com/aigicrm/common/engine/CustomerAccessService.java" || fail 'AccessService must consume customer:finance:edit'
grep -Fq '"finance_edit"' "$BE/com/aigicrm/common/engine/CustomerAccessService.java" || fail 'finance branch must resolve scope via action finance_edit'
cnt=$(grep -c "hasAuthority('customer:finance:edit')" "$BE/com/aigicrm/module/customer/controller/CustomerController.java" || true)
[ "$cnt" -ge 9 ] || fail "CustomerController must extend all 9 finance endpoints with customer:finance:edit, found $cnt"
grep -Fq "customer:finance:edit" "$MP/pages/customer/finance/form/index.js" || fail 'MP finance form gate must accept customer:finance:edit'
grep -Fq "customer:finance:edit" "$MP/pages/customer/finance/index.js" || fail 'MP finance page gate must accept customer:finance:edit'

# ---- C. 公海名单按角色引用 ----
grep -Fq "edit_role_ids" "$MIG/V2026.11.03.4__sea_pool_role_refs.sql" || fail 'migration must add sea role-ref columns'
grep -Fq "editRoleIds" "$BE/com/aigicrm/module/sea/entity/BizSeaConfig.java" || fail 'BizSeaConfig must map editRoleIds'
grep -Fq "selectRolesByUserId" "$BE/com/aigicrm/module/sea/service/SeaConfigService.java" || fail 'isInScope must match user roles (selectRolesByUserId)'
grep -Fq "getEditRoleIds" "$BE/com/aigicrm/module/sea/service/SeaConfigService.java" || fail 'hasPoolPerm must pass role ref columns'
grep -Fq "roleField" "$UI/views/system/SeaConfigManager.vue" || fail 'SeaConfigManager sections must carry roleField'
grep -Fq "按角色引用" "$UI/views/system/SeaConfigManager.vue" || fail 'SeaConfigManager must expose role multi-select'

# ---- D. 激励授权按角色批量 ----
grep -Fq 'body.get("roleIds")' "$BE/com/aigicrm/module/incentive/controller/IncentivePermissionController.java" || fail 'grant must expand roleIds to members'
grep -Fq "grantRoleIds" "$UI/composables/usePermissionData.js" || fail 'grant dialog state must carry grantRoleIds'

# ---- E. 生效预览 + 审计 ----
[ -f "$BE/com/aigicrm/module/system/controller/PermCenterController.java" ] || fail 'PermCenterController must exist'
grep -Fq "/preview" "$BE/com/aigicrm/module/system/controller/PermCenterController.java" || fail 'preview endpoint missing'
grep -Fq "loadUserByUsername" "$BE/com/aigicrm/module/system/service/PermCenterService.java" || fail 'preview must rebuild target user via UserDetailsServiceImpl (same chain as auth)'
grep -Fq "hasPoolPerm" "$BE/com/aigicrm/module/system/service/PermCenterService.java" || fail 'preview must include sea pool verdicts'
grep -Fq "countByScopeFor" "$BE/com/aigicrm/module/customer/service/CustomerService.java" || fail 'CustomerService must expose countByScopeFor(target)'
grep -Fq "recordScopeVersion" "$BE/com/aigicrm/module/system/service/RolePermScopeService.java" || fail 'scope changes must record sys_rule_version'
grep -Fq "recordGrantVersion" "$BE/com/aigicrm/module/incentive/controller/IncentivePermissionController.java" || fail 'grant/revoke must record sys_rule_version'

echo 'PASS: 统一权限中心契约一致（唯一可写入口/finance:edit 拆码三通道/公海与激励按角色/生效预览/审计入册）'
