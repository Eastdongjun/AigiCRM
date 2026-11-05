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
    sed -n '/const MAIN_TAB_PERMISSIONS = {/,/^}/p' "$MP/utils/ranking-tabs.js" 2>/dev/null || true
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

# ---- C. MP 排行页注册码并集（映射=utils/ranking-tabs.js 纯函数, 页面经 util 派生; 单测钉该 util）----
UTIL="$MP/utils/ranking-tabs.js"
grep -Fq "score: 'score:view'" "$UTIL" || fail 'ranking util must bind registry code score:view'
grep -Fq "customer: 'statistics:view'" "$UTIL" || fail 'ranking util must bind registry code statistics:view'
grep -Fq "incentive: 'incentive:view'" "$UTIL" || fail 'ranking util must bind registry code incentive:view'
grep -Fq "const canTabs = rankingTabs.computeCanTabs(hasPermission, demo)" "$MP/pages/ranking/index.js" || fail 'ranking onLoad must derive canTabs via the pure util'
grep -Fq "if (canTabs[rankTab] === false) return" "$MP/pages/ranking/index.js" || fail 'ranking must block switching to tabs without the registry code'
grep -Fq "rankingTabs.visibleMainTabs(canTabs)" "$MP/pages/ranking/index.js" || fail 'ranking main tabs must render via visibleMainTabs (per-tab registry codes)'
# 反钉: 页面不得残留不带 rankingTabs. 前缀的裸引用(2026-11-04 曾因漏改 loadRanking 引发 ReferenceError, loading 恒挂)
C_TOTAL=$(grep -c "MAIN_TAB_PERMISSIONS" "$MP/pages/ranking/index.js" || true)
C_QUALIFIED=$(grep -c "rankingTabs.MAIN_TAB_PERMISSIONS" "$MP/pages/ranking/index.js" || true)
[ "$C_TOTAL" = "$C_QUALIFIED" ] || fail "ranking page has $((C_TOTAL - C_QUALIFIED)) bare MAIN_TAB_PERMISSIONS reference(s) (must go through rankingTabs util)"
grep -Fq "canCustomer" "$MP/pages/ranking/index.js" || fail 'ranking must skip the customer-rankings request without statistics:view'
! grep -q "hasPermission('statistics:view')" "$MP/pages/ranking/index.js" || fail 'ranking must not single-gate the whole page on statistics:view (registry union instead)'

# ---- C2. 应用中心 any-of 通用门禁 ----
grep -Fq "permissions: ['score:view', 'statistics:view', 'incentive:view']" "$MP/pages/app/index.js" || fail 'app-center ranking entry must use the registry-code union (score:view/statistics:view/incentive:view)'
grep -Fq "Array.isArray(item.permissions)" "$MP/pages/app/index.js" || fail 'app-center must support permissions array (any-of) gating'

# ---- B. 双端同源: 学分排行/客户排行 ----
grep -Fq "path: 'score/ranking'" "$UI/router/index.js" || fail 'PC router must register score/ranking (score ranking menu)'
grep -Fq "path: 'ranking/customer'" "$UI/router/index.js" || fail 'PC router must register ranking/customer (customer ranking menu)'
grep -q "activeMenu: '/score/ranking'" "$UI/router/index.js" || fail 'PC score ranking route must anchor activeMenu /score/ranking'
grep -q "title: '学分排行', parentTitle: '学分管理', activeMenu: '/score/ranking', icon: 'Medal', permission: 'score:view'" "$UI/router/index.js" || fail 'PC score ranking menu must bind registry code score:view (parentTitle=学分管理, 2026-11-05 模块对齐)'
grep -q "title: '客户排行', parentTitle: '客户管理', icon: 'DataLine', permission: 'statistics:view'" "$UI/router/index.js" || fail 'PC customer ranking menu must bind registry code statistics:view (parentTitle=客户管理, 2026-11-05 用户拍板)'

# ---- D. 接口守卫=注册码 ----
grep -Fq "hasAuthority('score:view')" "$SCORE_CTRL" || fail 'backend /score/ranking guard must stay score:view'
grep -Fq "customer-rankings" "$STAT_CTRL" || fail 'backend customer-rankings route is missing'
sed -n '/customer-rankings/,+2p' "$STAT_CTRL" | grep -Fq "hasAuthority('statistics:view')" || fail 'backend /statistics/customer-rankings guard must stay statistics:view'

# ---- E. 命名与数据来源同源（2026-11-04 用户令"统一命名 统一数据来源"）----
# E1. MP 应用中心模块分组=PC 菜单分组(2026-11-05 用户令模块归属对齐, V2026.11.05.4):
#     考核与分析组撤销 → 学分管理(学分排行) / 统计分析(独立) / 核心指标+客户排行 归 客户经营
grep -Fq "{ key: 'assessment', title: '学分管理', subtitle: '学分排行', apps: ['ranking'] }," "$MP/pages/app/index.js" || fail 'assessment module must be renamed 学分管理 hosting ranking only'
grep -Fq "{ key: 'statistics', title: '统计分析', subtitle: '客户 · 审批 · 公海摘要', apps: ['statistics'] }," "$MP/pages/app/index.js" || fail 'app-center must keep a standalone 统计分析 module'
grep -Fq "apps: ['customer', 'customerCreate', 'indicator']" "$MP/pages/app/index.js" || fail 'customer module must host indicator app (核心客户六项指标归客户模块, 2026-11-05)'
if grep -q "'analytics'" "$MP/pages/app/index.js"; then
  fail 'app-center must not keep the analytics module (statistics standalone since 2026-11-05)'
fi
# E2. 首页宫格入口名=PC 菜单权威名（反钉旧名回潮）
grep -Fq '新增客户' "$MP/pages/home/index.wxml" || fail 'home grid must use PC menu name 新增客户'
grep -Fq '激励上传' "$MP/pages/home/index.wxml" || fail 'home grid must use PC menu name 激励上传'
grep -Fq '提醒管理' "$MP/pages/home/index.wxml" || fail 'home grid must use PC menu name 提醒管理'
grep -Fq '统计分析' "$MP/pages/home/index.wxml" || fail 'home grid must use PC menu name 统计分析'
grep -Eq '新客户|客户统计|四项服务上传' "$MP/pages/home/index.wxml" && fail 'home grid must not resurrect old entry names (新客户/客户统计/四项服务上传)'
# E3. 统计家族双端同接口（同页同数据源）:
#   统计分析: MP pages/statistics 与 PC views/statistics 同用 getAnalysis(+score ranking+scope depts)
#   核心客户六项指标: 双端同用 getIndicatorStats
grep -Fq "getAnalysis" "$MP/pages/statistics/index.js" || fail 'MP statistics page data source must stay getAnalysis (same as PC)'
grep -Fq "getScoreRanking" "$MP/pages/statistics/index.js" || fail 'MP statistics page data source must stay getScoreRanking (same as PC)'
grep -Fq "getScopeDepts" "$MP/pages/statistics/index.js" || fail 'MP statistics page data source must stay getScopeDepts (same as PC)'
grep -Fq "getAnalysis" "$UI/views/statistics/index.vue" || fail 'PC statistics page data source must stay getAnalysis (same as MP)'
grep -Fq "getScoreRanking" "$UI/views/statistics/index.vue" || fail 'PC statistics page data source must stay getScoreRanking (same as MP)'
grep -Fq "getIndicatorStats" "$MP/pages/indicator/index.js" || fail 'MP indicator page data source must stay getIndicatorStats (same as PC)'
grep -Fq "getIndicatorStats" "$UI/views/indicator/index.vue" || fail 'PC indicator page data source must stay getIndicatorStats (same as MP)'
# E4. 统计分析页总览区块命名统一为「业务总览」（与 PC 业务总览口径一致, 反钉旧名经营分析）
grep -Fq '业务总览' "$MP/pages/statistics/index.wxml" || fail 'statistics overview section must use unified name 业务总览'
if grep -q '经营分析' "$MP/pages/statistics/index.wxml"; then
  fail 'statistics overview section must not resurrect old name 经营分析 (unified to 业务总览)'
fi

# ---- F. 激励统计/排行 逐功能建码（2026-11-04 用户令）----
# F1. 迁移注册: 两新码入 flyway seed(注册表), 零死码由 A 段注册表对账自动覆盖
INCENT_CTRL="$BE/com/aigicrm/module/incentive/controller/IncentiveController.java"
MIG_NEW="$MIG/V2026.11.04.1__incentive_stats_rank_permission_codes.sql"
[ -f "$MIG_NEW" ] || fail 'incentive stats/rank permission migration is missing'
grep -Fq "'incentive:stats:view'" "$MIG_NEW" || fail 'migration must seed incentive:stats:view (激励统计查看)'
grep -Fq "'incentive:rank:view'" "$MIG_NEW" || fail 'migration must seed incentive:rank:view (激励排行查看)'
# F2. MP 激励统计页: 双 pane 各绑注册码(统计=stats:view/排行=rank:view), 无权 pane 不渲染不发请求
grep -q "incentive:stats:view" "$MP/pages/incentive/index.js" || fail 'MP incentive stats page must gate stats pane via incentive:stats:view'
grep -q "incentive:rank:view" "$MP/pages/incentive/index.js" || fail 'MP incentive stats page must gate ranking pane via incentive:rank:view'
# F3. 后端数据面守卫: 统计=stats:view; 排行数据面(scoreboard/ranking)=stats:view∨rank:view 双码
grep -Fq "hasAuthority('incentive:stats:view')" "$INCENT_CTRL" || fail 'backend category-stats guard must stay incentive:stats:view'
grep -Fq "hasAnyAuthority('incentive:stats:view','incentive:rank:view')" "$INCENT_CTRL" || fail 'backend scoreboard/ranking guard must accept stats:view or rank:view'
# F4. 应用中心入口=注册码并集
grep -Fq "permissions: ['incentive:stats:view', 'incentive:rank:view']" "$MP/pages/app/index.js" || fail 'app-center 激励统计 entry must use the registry-code union'
# F5. 应用中心四项激励单组（2026-11-05 用户令"应用中心里面的四项服务叫做四项激励, 激励上传/查询/统计
#     都归属于四项激励"——推翻 11-04 MP 拆分, 对齐 PC 11-05 合并口径 V2026.11.05.1）; 独立组防复活
grep -Fq "{ key: 'incentive', title: '四项激励', subtitle: '上传、查询与统计', apps: ['incentiveUpload', 'incentiveQuery', 'incentiveStats'] }" "$MP/pages/app/index.js" || fail 'app-center 四项激励 module must host upload/query/stats together (merged 2026-11-05, aligned with PC)'
grep -Fq "key: 'incentiveReward'" "$MP/pages/app/index.js" && fail 'app-center must not resurrect the 11-04 standalone incentiveReward group (merged 2026-11-05)'
# F6. PC 激励统计路由=注册码 stats:view + 四项激励分组（反钉 11-04 拆码前的旧口径 incentive:view/四项服务 回潮）
grep -Fq "meta: { title: '激励统计', parentTitle: '四项激励', icon: 'TrendCharts', permission: 'incentive:stats:view' }" "$UI/router/index.js" || fail 'PC incentive-stats route must bind incentive:stats:view under the 四项激励 group'
grep -Fq "activeMenu: '/incentive/stats', icon: 'Document', permission: 'incentive:stats:view'" "$UI/router/index.js" || fail 'PC incentive-stats detail route must follow stats:view (drill-down same code as page)'
# F6b. PC 激励统计页页签按码门禁(2026-11-05 G1 修复, 与 MP F2 逐 pane 门禁同构): 三页签此前零门禁
grep -Fq "v-if=\"canStats\" class=\"tab-item\"" "$UI/views/incentive/stats.vue" || fail 'PC stats tab 统计表 must gate by incentive:stats:view'
grep -Fq "v-if=\"canRank\" class=\"tab-item\"" "$UI/views/incentive/stats.vue" || fail 'PC rank/person tabs must gate by incentive:rank:view'
grep -Fq "const canStats = computed(() => userStore.hasPermission('incentive:stats:view'))" "$UI/views/incentive/stats.vue" || fail 'stats.vue must derive canStats from registry code'
grep -Fq "const canRank = computed(() => userStore.hasPermission('incentive:rank:view'))" "$UI/views/incentive/stats.vue" || fail 'stats.vue must derive canRank from registry code'
# F6c. 客户排行导出独立码(2026-11-05 用户令"客户排行导出加码", 镜像个人排行 incentive:person-rank:export 先例):
#      注册 incentive:rank:export(V2026.11.05.8, 默认仅 admin, 其他角色权限中心勾选授予) + 按钮 v-if 按码
grep -Fq "v-if=\"userStore.hasPermission('incentive:rank:export')\"" "$UI/views/incentive/RankTableView.vue" || fail 'customer-rank export button must gate by dedicated code incentive:rank:export'
grep -Fq "'incentive:rank:export'" "$MIG/V2026.11.05.8__customer_rank_export_permission.sql" || fail 'migration must register incentive:rank:export (customer rank export, 镜像先例)'
# F7. PC 侧栏结构(2026-11-05 用户令纠偏: "PC端还是叫四项激励，激励统计还是四项激励下面的"
#     + "PC端也需要增加一个四项服务，四项服务的定位是给客户展示案例库的"):
#     四项激励=上传/查询/统计一组(V2026.11.05.1 撤销 11-04.2 的 PC 拆组, 历史迁移文件按 flyway 不可变保留);
#     四项服务=新建案例库组+案例库照片墙页(码复用 incentive:view, 三方同源零新码)
MIG_FIX="$MIG/V2026.11.05.1__pc_menu_restructure_incentive_perm_center.sql"
[ -f "$MIG_FIX" ] || fail 'PC menu restructure migration (2026-11-05) is missing'
grep -Fq "SET menu_name = '四项激励'" "$MIG_FIX" || fail 'migration must rename /incentive-group back to 四项激励'
grep -Fq "DELETE FROM sys_menu WHERE path = '/incentive-reward-group' AND menu_type = 2" "$MIG_FIX" || fail 'migration must dissolve the 11-04 standalone 四项激励 group'
grep -Fq "'/service-case-group'" "$MIG_FIX" || fail 'migration must create the 四项服务 sidebar group (/service-case-group)'
grep -Fq "'/case-library', 'incentive/case-library', 'incentive:view'" "$MIG_FIX" || fail 'migration must seed the 案例库 page node bound to incentive:view'
grep -Fq "meta: { title: '案例库', parentTitle: '四项服务', icon: 'Picture', permission: 'incentive:view' }" "$UI/router/index.js" || fail 'PC case-library route must bind incentive:view under 四项服务'
# 反钉: 激励上传/查询不得再挂「四项服务」名下(四项服务=案例库专用, 2026-11-05)
if grep -Fq "parentTitle: '四项服务', icon: 'Trophy'" "$UI/router/index.js" || grep -Fq "parentTitle: '四项服务', icon: 'Search'" "$UI/router/index.js"; then
  fail 'incentive upload/query must sit under 四项激励, not 四项服务 (2026-11-05)'
fi

echo "PASS: dual-end permission bindings reconcile with the perm-center registry (MP=${MP_CODES}c PC=$(printf '%s' "$PC_CODES" | grep -c ':')c registry=${REG_COUNT}c); ranking union-entry, any-of gating, and incentive stats/rank codes pinned."
