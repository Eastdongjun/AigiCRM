#!/bin/bash
# 【WP9 新增契约 2026-11-13】权限内核契约(施工规格书 WP9: epoch 消费/开关只读/双轨守卫/双写四写点)
# 静态扫描 + 负例自证(AIGI_NEGATIVE=1 反向验证: 去掉守卫必须能测出, 防假绿)。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BE="$ROOT/aigicrm-backend/src/main/java/com/aigicrm"
UI="$ROOT/aigicrm-ui/src"
MP="$ROOT/aigicrm-miniprogram"
fail() { echo "FAIL: $1" >&2; exit 1; }
NEG="${AIGI_NEGATIVE:-0}"
pass() { echo "PASS: $1"; }

# ---- ① epoch 消费契约(规格 WP5): 后端回传+双端比对+写点汇聚 ----
grep -q "private Long permEpoch" "$BE/module/auth/dto/UserInfoResponse.java" || fail "① UserInfoResponse 缺 permEpoch"
grep -q "setPermEpoch(permEpochService.current())" "$BE/module/auth/service/AuthService.java" || fail "① AuthService 未回传 epoch"
[ -f "$BE/security/PermEpochHeaderFilter.java" ] || fail "① 缺 X-Perm-Epoch 过滤器"
grep -q "x-perm-epoch" "$UI/utils/request.js" && grep -q "x-perm-epoch" "$MP/utils/request.js" \
  || fail "① PC/MP 未消费 X-Perm-Epoch"
grep -q "markChanged" "$BE/security/PermissionCacheService.java" || fail "① evictAll 未汇聚 markChanged"
pass "① epoch 消费链(回传/响应头/双端比对/写点汇聚)"

# ---- ② 开关只读契约(锁定决策#8): admin-only 硬校验 + fail-closed 默认 + 前端归一 false ----
grep -q "ADMIN_ONLY_KEYS" "$BE/module/system/service/SysConfigService.java" || fail "② 缺 admin-only 键集"
grep -q "该开关仅系统管理员可修改" "$BE/module/system/service/SysConfigService.java" || fail "② 缺 admin-only 拒绝"
grep -q "getBooleanValue(KEY_SUPERIOR_SEE_SUBORDINATE, false)" "$BE/common/scope/OrgScopeResolver.java" \
  || fail "② 开关缺 fail-closed 默认 false"
grep -q "normalizeSwitchValue(configMap\['perm.superior.see.subordinate'\], 'false')" "$UI/views/system/BasicSettings.vue" \
  || fail "② 前端缺行归一未钉 false"
pass "② 开关只读(admin-only + fail-closed + 前端归一)"

# ---- ③ 双轨守卫契约(WP7): resolveModuleUsers 入口守卫 + 锚点保真 + ObjectProvider 断环 ----
grep -q "auth.isNewTrack(module)" "$BE/common/scope/OrgScopeResolver.java" || fail "③ resolveModuleUsers 缺双轨守卫"
grep -q "auth.isNewTrack(\"customer\")" "$BE/module/customer/service/CustomerVisibilityService.java" || fail "③ customer 可见性缺双轨守卫"
grep -q "legacyAnchor(module, action)" "$BE/common/auth/AuthorizationService.java" || fail "③ 旧轨缺锚点保真"
grep -q "ObjectProvider<com.aigicrm.common.auth.AuthorizationService>" "$BE/common/scope/OrgScopeResolver.java" \
  || fail "③ 缺 ObjectProvider 断环"
pass "③ 双轨守卫(入口守卫/锚点保真/断环)"

# ---- ④ 双写四写点契约(WP8): 保存/清空/删角色/改角色码 ----
grep -q "dualUpsertGrants(assigneeType, assigneeKey, module.toString()" "$BE/module/system/service/RolePermScopeService.java" \
  || fail "④ 保存缺授予表双写"
grep -c "dualDeleteGrants(" "$BE/module/system/service/RolePermScopeService.java" | grep -qE "^[2-9]" || fail "④ 双删点不足(清空/删角色)"
grep -q "SET subject_id=? WHERE tenant_id=1 AND subject_type='ROLE'" "$BE/module/system/service/RolePermScopeService.java" \
  || fail "④ 角色改名缺授予表迁移"
grep -q "codesOf(module, action)" "$BE/module/system/service/RolePermScopeService.java" || fail "④ 双写未覆盖一槽多码"
pass "④ 双写四写点(保存/清空/删角色/改码)+一槽多码"

# ---- ⑤ ScopeQuery fail-closed 三分支(规格 §3.3): 未注册码/空集→1=0, all→不加条件 ----
grep -q 'wrapper.apply("1=0")' "$BE/common/auth/ScopeQuery.java" || fail "⑤ ScopeQuery 缺恒空防线"
grep -q "isScopeRequired()" "$BE/common/auth/ScopeQuery.java" || fail "⑤ ScopeQuery 缺入口码 no-op"
pass "⑤ ScopeQuery fail-closed(未注册 1=0 / 入口码 no-op)"

# ---- ⑥ 文件 manage 槽修复契约(文件专项检查 P0): 五码 scope_required=1 + 槽覆盖五码 ----
MIG4="$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.13.4__wp2_file_manage_slot_fix.sql"
[ -f "$MIG4" ] || fail "⑥ 缺 V2026.11.13.4 修复迁移"
grep -q "'file:acl:manage', 'file:folder:manage', 'file:upload', 'file:edit', 'file:share'" "$MIG4" \
  || fail "⑥ 迁移缺五码 scope_required=1"
grep -q '"file|manage", List.of("file:acl:manage", "file:folder:manage",' "$BE/common/auth/PermActionRegistry.java" \
  || fail "⑥ Registry 缺 (file,manage) 槽五码覆盖"
grep -q 'case "file:upload":' "$BE/common/auth/AuthorizationService.java" \
  || fail "⑥ legacySlotAction 缺五码 manage 映射"
grep -q "grantsForSlot" "$BE/common/auth/AuthorizationService.java" || fail "⑥ 缺槽级授权聚合"
pass "⑥ 文件 manage 槽修复(五码 scope_required=1/槽覆盖/槽锚/槽聚合)"

# ---- ⑦ 锚点透传契约(文件专项复检 FILE-07 P0): 新轨不得丢调用方请求锚 ----
grep -q "scopeForUser(SecurityUser user, String actionCode," "$BE/common/auth/AuthorizationService.java" \
  || fail "⑦ 缺带锚点覆盖的 scopeForUser 重载"
grep -q "anchorOverride != null" "$BE/common/auth/AuthorizationService.java" \
  || fail "⑦ newTrackScope 缺请求锚优先逻辑"
grep -q "auth.scopeForUser(req.getUser(), code, req.getAnchor())" "$BE/common/scope/OrgScopeResolver.java" \
  || fail "⑦ resolveScope 未透传请求锚点"
grep -q "auth.scopeForUser(user, code, ScopeRequest.AnchorType.OWNER)" "$BE/common/scope/OrgScopeResolver.java" \
  || fail "⑦ resolveModuleUsers 新轨未固定 OWNER 锚(旧轨同口径)"
pass "⑦ 锚点透传(resolveScope 请求锚/resolveModuleUsers 恒 OWNER/锚覆盖重载)"

# ---- 负例自证: 构造违规内容必须被本契约抓出(防扫描器假绿) ----
if [ "$NEG" = "1" ]; then
  TMP="$(mktemp -d)"
  cat > "$TMP/probe.java" <<'PROBE'
class Probe { String x = "x-perm-epoch"; }
PROBE
  # 反向验证 ①: 临时移走过滤器文件应使 ① 失败
  mv "$BE/security/PermEpochHeaderFilter.java" "$TMP/"
  if bash "$0" >/dev/null 2>&1; then
    mv "$TMP/PermEpochHeaderFilter.java" "$BE/security/"
    rm -rf "$TMP"
    fail "负例自证失败: 移除 epoch 过滤器未被契约抓出(扫描器假绿)"
  fi
  mv "$TMP/PermEpochHeaderFilter.java" "$BE/security/"
  rm -rf "$TMP"
  pass "负例自证(移除守卫可测出=扫描器有效)"
fi

pass "权限内核契约全绿"
