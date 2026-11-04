#!/usr/bin/env bash
# 校验「双端权限绑定一致性」契约（2026-11-04 用户裁决: PC 权限中心=唯一真相源, 双端绑定注册码, 不硬编码权限）。
#
# 核心约束:
#   A. 注册表对账: PC 与 MP 的全部权限码绑定必须 ∈ 权限中心注册表（后端 flyway sys_menu seed 为权威源）, 零死码
#   B. 双端同源: 同名功能双端绑定同一注册码——学分排行 PC 菜单=score:view, MP 排行页并集含 score:view
#   C. MP 排行页注册码并集: 页内三榜各绑注册码(学分=score:view/客户=statistics:view/激励=incentive:view),
#      入口 any-of、页签过滤、无权页签不发请求; 应用中心支持 permissions 数组(any-of)通用门禁
#   D. 接口守卫=注册码: /score/ranking=score:view, /statistics/customer-rankings=statistics:view
#
# 防复发背景: MP 排行入口曾于 2026-10-17 被 statistics:view 单码补丁接管(mp-3), 造成 score:view 持有者
# (行政)手机端丢失入口、与 PC 漂移; 2026-11-04 归位为注册码并集模型, 本契约防止再次漂移。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI="$ROOT/aigicrm-ui/src"
BE="$ROOT/aigicrm-backend/src/main/java"
MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"
MP="$ROOT/aigicrm-miniprogram"
SCORE_CTRL="$BE/com/aigicrm/module/score/controller/ScoreController.java"
STAT_CTRL="$BE/com/aigicrm/module/statistics/controller/StatisticsController.java"

fail() { echo "FAIL: $*" >&2; exit 1; }

# ---- 注册表: flyway sys_menu seed 中的权限码（仓库内权威源, 与权限中心同源）----
REGISTRY=$(grep -rhoE "'[a-z]+:[a-z-]+(:[a-z-]+)?'" "$MIG"/*.sql 2>/dev/null | tr -d "'" | sort -u || true)
[ -n "$REGISTRY" ] || fail 'registry extraction from flyway seeds produced nothing'
REG_COUNT=$(printf '%s\n' "$REGISTRY" | wc -l | tr -d ' ')
[ "$REG_COUNT" -ge 60 ] || fail "registry extraction looks wrong (only $REG_COUNT codes from flyway seeds)"

# ---- A1. MP 绑定 ⊆ 注册表 ----
MP_CODES=$(
  {
    grep -rhoE "hasPermission\('[a-z]+:[a-z-]+(:[a-z-]+)?'\)" "$MP/pages" "$MP/utils" "$MP/components" 2>/dev/null || true
    grep -rhoE "permission: '[a-z]+:[a-z-]+(:[a-z-]+)?'" "$MP/pages" 2>/dev/null || true
    grep -rhoE "permissions: \[[^]]*\]" "$MP/pages" 2>/dev/null || true
    sed -n '/const MAIN_TAB_PERMISSIONS = {/,/^}/p' "$MP/pages/ranking/index.js" 2>/dev/null || true
  } | grep -oE "[a-z]+:[a-z-]+(:[a-z-]+)?" | sort -u
)
[ -n "$MP_CODES" ] || fail 'no MP permission bindings found'
MP_DEAD=$(comm -23 <(printf '%s\n' "$MP_CODES") <(printf '%s\n' "$REGISTRY"))
[ -z "$MP_DEAD" ] || fail "MP binds codes outside the permission registry: $(echo "$MP_DEAD" | tr '\n' ' ')"

# ---- A2. PC 绑定 ⊆ 注册表 ----
PC_CODES=$(
  {
    grep -rhoE "permission: '[a-z]+:[a-z-]+(:[a-z-]+)?'" "$UI/router" 2>/dev/null || true
    grep -rhoE "v-permission=\"'[a-z]+:[a-z-]+(:[a-z-]+)?'\"" "$UI" --include='*.vue' --exclude='*.spec.js' 2>/dev/null || true
    grep -rhoE "hasPermission\('[a-z]+:[a-z-]+(:[a-z-]+)?'\)" "$UI" --include='*.vue' --include='*.js' --exclude='*.spec.js' 2>/dev/null || true
  } | grep -oE "[a-z]+:[a-z-]+(:[a-z-]+)?" | sort -u
)
[ -n "$PC_CODES" ] || fail 'no PC permission bindings found'
PC_DEAD=$(comm -23 <(printf '%s\n' "$PC_CODES") <(printf '%s\n' "$REGISTRY"))
[ -z "$PC_DEAD" ] || fail "PC binds codes outside the permission registry: $(echo "$PC_DEAD" | tr '\n' ' ')"

# ---- C. MP 排行页注册码并集 ----
grep -Fq "score: 'score:view'" "$MP/pages/ranking/index.js" || fail 'ranking score tab must bind registry code score:view'
grep -Fq "customer: 'statistics:view'" "$MP/pages/ranking/index.js" || fail 'ranking customer tab must bind registry code statistics:view'
grep -Fq "incentive: 'incentive:view'" "$MP/pages/ranking/index.js" || fail 'ranking incentive tab must bind registry code incentive:view'
grep -Fq "const canTabs = {" "$MP/pages/ranking/index.js" || fail 'ranking onLoad must compute per-tab registry permissions (canTabs)'
grep -Fq "canTabs.score && !canTabs.customer && !canTabs.incentive" "$MP/pages/ranking/index.js" || fail 'ranking union-entry bounce (none-of) is missing'
grep -Fq "if (canTabs[rankTab] === false) return" "$MP/pages/ranking/index.js" || fail 'ranking must block switching to tabs without the registry code'
grep -Fq "canTabs[item.key] !== false" "$MP/pages/ranking/index.js" || fail 'ranking main tabs must be filtered by per-tab registry codes'
grep -Fq "canCustomer" "$MP/pages/ranking/index.js" || fail 'ranking must skip the customer-rankings request without statistics:view'
! grep -q "hasPermission('statistics:view')" "$MP/pages/ranking/index.js" || fail 'ranking must not single-gate the whole page on statistics:view (registry union instead)'

# ---- C2. 应用中心 any-of 通用门禁 ----
grep -Fq "permissions: ['score:view', 'statistics:view', 'incentive:view']" "$MP/pages/app/index.js" || fail 'app-center ranking entry must use the registry-code union (score:view/statistics:view/incentive:view)'
grep -Fq "Array.isArray(item.permissions)" "$MP/pages/app/index.js" || fail 'app-center must support permissions array (any-of) gating'

# ---- B. 双端同源: 学分排行/客户排行 ----
grep -Fq "path: 'score/ranking'" "$UI/router/index.js" || fail 'PC router must register score/ranking (score ranking menu)'
grep -Fq "path: 'ranking/customer'" "$UI/router/index.js" || fail 'PC router must register ranking/customer (customer ranking menu)'
grep -q "activeMenu: '/score/ranking'" "$UI/router/index.js" || fail 'PC score ranking route must anchor activeMenu /score/ranking'
grep -q "title: '学分排行', parentTitle: '考核与分析', activeMenu: '/score/ranking', icon: 'Medal', permission: 'score:view'" "$UI/router/index.js" || fail 'PC score ranking menu must bind registry code score:view'
grep -q "title: '客户排行', parentTitle: '考核与分析', icon: 'DataLine', permission: 'statistics:view'" "$UI/router/index.js" || fail 'PC customer ranking menu must bind registry code statistics:view'

# ---- D. 接口守卫=注册码 ----
grep -Fq "hasAuthority('score:view')" "$SCORE_CTRL" || fail 'backend /score/ranking guard must stay score:view'
grep -Fq "customer-rankings" "$STAT_CTRL" || fail 'backend customer-rankings route is missing'
sed -n '/customer-rankings/,+2p' "$STAT_CTRL" | grep -Fq "hasAuthority('statistics:view')" || fail 'backend /statistics/customer-rankings guard must stay statistics:view'

echo "PASS: dual-end permission bindings reconcile with the perm-center registry (MP=${MP_CODES}c PC=$(printf '%s' "$PC_CODES" | grep -c ':')c registry=${REG_COUNT}c); ranking union-entry and any-of gating pinned."
