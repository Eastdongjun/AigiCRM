#!/usr/bin/env bash
# verify-post-migrate.sh（迁移后断言）的行为测试(GL-07, 2026-11-24)
#
# 验证方式: 用一个假的 SQL 执行器返回预设结果。每条 SQL 带注释标签(-- vpm:<tag>), 假执行器按标签应答。
#   · 全部正确 → 退出码 0
#   · 每个场景改坏一处 → 退出码 1, 且输出点名对应的断言(负例自证: 改坏会红)
#   · 没给迁移前计数 → 退出码 2(BLOCKED, 不是 PASS)
#   · 表清单里混入非法表名 → 拒绝拼进 SQL
#   · 假执行器是严格的: 每条 SQL 必须带着该断言的关键判定条件, 且只许是单条 SELECT(断言脚本不写库)
#
# 全程不联网: 被测脚本通过 AIGICRM_SQL_RUNNER 把 SQL 交给本测试生成的假执行器。
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_SELF_DIR/.." && pwd)"
VPM="$ROOT/scripts/verify-post-migrate.sh"
REAL_MIG="$ROOT/aigicrm-backend/src/main/resources/db/migration"

fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$VPM" ] || fail "缺少被测脚本 $VPM —— 迁移后断言脚本尚未实现"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-vpm-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

# ---- 隔离配置: 假的部署 env、空的 known_hosts、只有 3 支迁移的假迁移目录 ----
printf 'APP_SLUG=ycmn\nDB_NAME=ycmn\n' > "$TMP/deploy.env"
: > "$TMP/known_hosts"
MIG="$TMP/mig"; mkdir -p "$MIG"
# 假迁移目录里的"本批"(晚于生产现值 2026.11.22.1 的全部文件)共 4 支:
#   2026.11.22.9、2026.11.23.1、2026.11.23.6(从真实目录拷来的行业词表迁移)、2026.11.23.9
# 另有 1 支已在生产的 2026.11.22.1, 不属于本批。
: > "$MIG/V2026.11.22.1__already_in_prod.sql"
: > "$MIG/V2026.11.22.9__last_before_batch.sql"
: > "$MIG/V2026.11.23.1__first_of_batch.sql"
: > "$MIG/V2026.11.23.9__last_of_batch.sql"
cp "$REAL_MIG/V2026.11.23.6__industry_dict_seed.sql" "$MIG/"   # 行业词表标签从真实迁移文件解析

# ---- 假的 SQL 执行器: 按标签应答, 场景由 SCENARIO 指定 ----
cat > "$TMP/fake-sql" <<'FAKE'
#!/usr/bin/env bash
SQL="$(cat)"
tag="$(printf '%s\n' "$SQL" | sed -n 's/^-- vpm:\([a-z_]*\).*/\1/p' | head -1)"
S="${SCENARIO:-good}"
tab="$(printf '\t')"
answer() { # $1=该标签的默认答案 $2=场景名 $3=该场景下的答案
  [ "$S" = "$2" ] && { printf '%b' "$3"; return; }
  printf '%b' "$1"
}
# 只读守卫: 去掉标签行后, 每条语句都必须以 SELECT 开头且不含分号。这个脚本在生产迁移后立刻执行, "只读"要由测试证明。
body="$(printf '%s\n' "$SQL" | grep -v '^-- vpm:')"
case "$body" in
  *';'*) echo "假执行器: 语句里出现分号, 断言脚本不许一次发多条或夹带写语句" >&2; exit 11 ;;
esac
while IFS= read -r stmt; do
  case "$stmt" in
    ''|'SELECT '*) ;;
    *) echo "假执行器: 收到 SELECT 以外的语句, 断言脚本不许写库: [${stmt:0:60}]" >&2; exit 11 ;;
  esac
done <<< "$body"
# 关键条件: 本标签的 SQL 必须含有的判定条件, 缺一条就拒绝应答。
# 否则"把断言的 SQL 改空"(例如条件换成 1 = 1、查一个不存在的值)时测试仍然是绿的。
need() {
  local f
  for f in "$@"; do
    printf '%s\n' "$SQL" | grep -qF -- "$f" || { echo "假执行器: 标签 ${tag} 的 SQL 缺少关键条件 [${f}]" >&2; exit 10; }
  done
}
case "$tag" in
  flyway_failed)            need 'flyway_schema_history' 'success = 0' ;;
  flyway_applied)           need 'flyway_schema_history' 'success = 1' 'ORDER BY installed_rank' ;;
  scope_dist)               need 'biz_fabushi_post' 'GROUP BY scope_type' ;;
  recitation_tenant_column) need 'biz_fabushi_recitation' 'tenant_id' ;;
  tenant_tables)            need "COLUMN_NAME = 'tenant_id'" 'ORDER BY TABLE_NAME' ;;
  tenant_union)             need 'tenant_id' 'UNION ALL' 'biz_a' 'biz_b' 'IS NULL' '<> 1' ;;
  menu_stats_group)         need 'sys_menu' 'menu_type = 1' "menu_name = '统计分析'" 'COALESCE(parent_id, 0) = 0' ;;
  menu_stats_parent)        need 'sys_menu' "'/fabushi/stats'" '统计分析' 'menu_type = 2' ;;
  menu_names)               need 'sys_menu' "'/fabushi/shelf'" "'/fabushi/feed'" "'/fabushi/content'" 'menu_type = 2' ;;
  menu_fabushi_order)       need 'sys_menu' "'法布施'" 'sort_order' "'/fabushi/shelf'" "'/fabushi/library'" "'/fabushi/book'" 'menu_type = 1' 'menu_type = 2' ;;
  perm_meta_name)           need 'sys_permission_meta' "'fabushi:content:view'" "'内容管理'" ;;
  action_name)              need 'sys_action' "'fabushi:content:view'" "'法布施内容管理页'" ;;
  industry_seed)            need "'industry'" 'sys_dict_data' '冶金' '维修厂' '新行业' ;;
  onboarding_users)         need 'sys_user' ;;
  onboarding_flag)          need 'sys_config' "'onboarding.completed'" ;;
  remind_freq_dict)         need "'fabushi_remind_freq'" "'workday'" 'sys_dict_data' ;;
  remind_freq_settings)     need 'biz_fabushi_user_setting' 'freq_code' "'workday'" ;;
  snap_version)             need 'flyway_schema_history' 'success = 1' 'ORDER BY installed_rank DESC LIMIT 1' ;;
  snap_counts)              need 'biz_fabushi_post' "'recitation'" "'heart'" "'znl'" 'COUNT(*)' ;;
esac
# 迁移前快照的两个场景: history_unreadable(连 Flyway 历史都读不到)、fresh_db(全新库: 没有 Flyway 历史, 表还不存在)
[ "$S" = history_unreadable ] && [ "$tag" = snap_version ] && exit 7
[ "$S" = fresh_db ] && [ "$tag" = snap_counts ] && exit 7
case "$tag" in
  snap_version)             answer "2026.11.22.1\n" fresh_db "" ;;
  snap_counts)              answer "6${tab}5${tab}2${tab}13\n" fresh_db "" ;;
  flyway_failed)            answer "0\n" failed_flyway_row "1\n" ;;
  flyway_applied)           if [ "$S" = missing_migration ]; then printf '2026.11.22.9\n2026.11.23.1\n2026.11.23.9\n'
                            else printf '2026.11.22.9\n2026.11.23.1\n2026.11.23.6\n2026.11.23.9\n'; fi ;;
  scope_dist)               case "$S" in
                              old_scope_type) printf 'songdu%s6\nsuishi%s7\nrecitation%s1\n' "$tab" "$tab" "$tab" ;;
                              scope_mismatch)  printf 'songdu%s5\nsuishi%s8\n' "$tab" "$tab" ;;
                              *)               printf 'songdu%s6\nsuishi%s7\n' "$tab" "$tab" ;;
                            esac ;;
  recitation_tenant_column) answer "1\n" recitation_tenant_missing "0\n" ;;
  tenant_tables)            answer "biz_a\nbiz_b\n" tenant_bad_name "biz_a\nbiz_x; DROP TABLE biz_user\n" ;;
  tenant_union)             answer "" tenant_stray "biz_b${tab}3\n" ;;
  menu_stats_group)         answer "1\n" stats_group_missing "0\n" ;;
  menu_stats_parent)        answer "1\n" stats_not_moved "0\n" ;;
  menu_names)               answer "/fabushi/content${tab}内容管理\n/fabushi/feed${tab}认知广场\n/fabushi/shelf${tab}我的觉醒\n" \
                                  content_not_renamed "/fabushi/content${tab}内容治理\n/fabushi/feed${tab}认知广场\n/fabushi/shelf${tab}我的觉醒\n" ;;
  menu_fabushi_order)       answer "/fabushi/book${tab}4\n/fabushi/content${tab}5\n/fabushi/feed${tab}2\n/fabushi/library${tab}3\n/fabushi/shelf${tab}1\n" \
                                  order_wrong "/fabushi/book${tab}4\n/fabushi/content${tab}5\n/fabushi/feed${tab}2\n/fabushi/library${tab}3\n/fabushi/shelf${tab}9\n" ;;
  perm_meta_name)           answer "1\n" perm_meta_old_name "0\n" ;;
  action_name)              answer "1\n" action_old_name "0\n" ;;
  industry_seed)            answer "10\n" industry_missing "9\n" ;;
  onboarding_users)         answer "293\n" no_users "0\n" ;;
  onboarding_flag)          answer "true\n" onboarding_missing "\n" ;;
  remind_freq_dict)         answer "0\n" workday_remains "1\n" ;;
  remind_freq_settings)     answer "0\n" workday_remains "1\n" ;;
  *) echo "假执行器不认识的标签: [$tag]" >&2; exit 9 ;;
esac
FAKE
chmod 700 "$TMP/fake-sql"

export AIGICRM_DEPLOY_ENV="$TMP/deploy.env" AIGICRM_KNOWN_HOSTS="$TMP/known_hosts" AIGICRM_TEST_ONLY=1
export AIGICRM_MIGRATION_DIR="$MIG"
export AIGICRM_SQL_RUNNER="$TMP/fake-sql"

run_vpm() { # 其余参数透传; 返回被测脚本退出码, 输出进 $TMP/out
  SCENARIO="$1" bash "$VPM" prod --pre 6,5,2,13 "${@:2}" > "$TMP/out" 2>&1
}

# ---- ① 全部正确 → 0, 且没有 BLOCKED ----
rc=0; run_vpm good || rc=$?
[ "$rc" -eq 0 ] || fail "全部正确时应退出 0, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
# 只认阻塞条目的行首标记: 汇总行里的计数器 "BLOCKED=0" 不算
grep -q '^  BLOCKED' "$TMP/out" && fail "全部正确时不应出现 BLOCKED: $(cat "$TMP/out")"
n_pass="$(grep -c '^  PASS' "$TMP/out")"
[ "$n_pass" -ge 14 ] || fail "全部正确时应有至少 14 条 PASS, 实际 $n_pass: $(cat "$TMP/out")"
# 本批只核对晚于生产现值的 4 支(已在生产的 2026.11.22.1 不重复计); 输出应如实说明核对了几支
grep -q '4 支' "$TMP/out" || fail "输出应说明本批核对了 4 支迁移: $(cat "$TMP/out")"
grep -q '2026.11.22.1' "$TMP/out" && fail "已在生产的 2026.11.22.1 不应出现在本批清单里: $(cat "$TMP/out")"

# ---- ② 负例: 每个场景改坏一处 → 1, 且点名对应断言 ----
expect_fail() { # $1=场景 $2=应点名的断言关键字
  rc=0; run_vpm "$1" || rc=$?
  [ "$rc" -eq 1 ] || fail "场景 $1 应退出 1, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
  grep -q -- "FAIL.*$2" "$TMP/out" || fail "场景 $1 应点名 [$2]; 输出: $(cat "$TMP/out")"
}
expect_fail failed_flyway_row        flyway_failed
expect_fail missing_migration       flyway_applied
expect_fail old_scope_type          scope_old_values
expect_fail scope_mismatch          scope_reconcile
expect_fail recitation_tenant_missing recitation_tenant_column
expect_fail tenant_bad_name         tenant_tables
expect_fail tenant_stray            tenant_single
expect_fail stats_group_missing     menu_stats_group
expect_fail stats_not_moved         menu_stats_parent
expect_fail content_not_renamed     menu_names
expect_fail order_wrong             menu_fabushi_order
expect_fail perm_meta_old_name      perm_meta_name
expect_fail action_old_name         action_name
expect_fail industry_missing        industry_seed
expect_fail onboarding_missing      onboarding_flag
expect_fail workday_remains         remind_freq

# 新库(没有用户)不写首启向导标记是正确的, 不应判失败
rc=0; run_vpm no_users || rc=$?
[ "$rc" -eq 0 ] || fail "没有用户的新库不应因缺首启向导标记而失败, rc=$rc; 输出: $(cat "$TMP/out")"

# ---- ③ 没给迁移前计数 → 2(BLOCKED), 不是 0 也不是 1 ----
rc=0; SCENARIO=good bash "$VPM" prod > "$TMP/out" 2>&1 || rc=$?
[ "$rc" -eq 2 ] || fail "缺迁移前计数时应 BLOCKED 退出 2, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
grep -q 'BLOCKED.*scope_reconcile' "$TMP/out" || fail "BLOCKED 输出应指明是哪一条无法核对: $(cat "$TMP/out")"
grep -q '^  FAIL' "$TMP/out" && fail "缺迁移前计数时其余断言应照常通过, 不应出现 FAIL: $(cat "$TMP/out")"

# ---- ④ 目标参数非法 → 2, 且不得触碰任何连接 ----
rc=0; bash "$VPM" staging > "$TMP/out" 2>&1 || rc=$?
[ "$rc" -eq 2 ] || fail "未知目标应退出 2, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
# 迁移前计数必须是数字
rc=0; SCENARIO=good bash "$VPM" prod --pre '6,5,2,abc' > "$TMP/out" 2>&1 || rc=$?
[ "$rc" -eq 2 ] || fail "迁移前计数不是数字应退出 2, 实际 rc=$rc; 输出: $(cat "$TMP/out")"

# ---- ⑤ 查询执行器本身出错 → 1(不是静默通过) ----
printf '#!/usr/bin/env bash\ncat >/dev/null\nexit 7\n' > "$TMP/broken-sql"; chmod 700 "$TMP/broken-sql"
rc=0; AIGICRM_SQL_RUNNER="$TMP/broken-sql" bash "$VPM" prod --pre 6,5,2,13 > "$TMP/out" 2>&1 || rc=$?
[ "$rc" -eq 1 ] || fail "查询执行器失败时应退出 1, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
grep -q 'query_failed' "$TMP/out" || fail "查询失败应点名 query_failed: $(cat "$TMP/out")"

# ---- ⑥ 迁移前快照: 给出当前已应用版本与旧类型计数, 迁移脚本原样传回 --since / --pre ----
rc=0; SCENARIO=good bash "$VPM" prod --snapshot > "$TMP/out" 2>&1 || rc=$?
[ "$rc" -eq 0 ] || fail "迁移前快照应退出 0, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
grep -qx 'SINCE=2026.11.22.1' "$TMP/out" || fail "快照应给出迁移前版本 SINCE=2026.11.22.1: $(cat "$TMP/out")"
grep -qx 'PRE=6,5,2,13' "$TMP/out" || fail "快照应给出 PRE=6,5,2,13: $(cat "$TMP/out")"
# 全新库: 没有 Flyway 历史 → 版本记 0(本批 = 全部迁移); 表还不存在 → 计数记 0
rc=0; SCENARIO=fresh_db bash "$VPM" prod --snapshot > "$TMP/out" 2>&1 || rc=$?
{ [ "$rc" -eq 0 ] && grep -qx 'SINCE=0' "$TMP/out" && grep -qx 'PRE=0,0,0,0' "$TMP/out"; } \
  || fail "全新库快照应为 SINCE=0 / PRE=0,0,0,0, rc=$rc: $(cat "$TMP/out")"
# 连 Flyway 历史都读不到 → 1, 且不输出半截快照(不能猜一个版本号)
rc=0; SCENARIO=history_unreadable bash "$VPM" prod --snapshot > "$TMP/out" 2>&1 || rc=$?
[ "$rc" -eq 1 ] || fail "读不到 Flyway 历史时快照应退出 1, 实际 rc=$rc; 输出: $(cat "$TMP/out")"
grep -q '^SINCE=' "$TMP/out" && fail "读不到版本时不应该输出 SINCE=: $(cat "$TMP/out")"

echo "PASS: verify-post-migrate.sh 行为测试(全部正确通过 / 17 种改坏逐一报红 / 缺前置计数为 BLOCKED / 非法表名拒绝 / 执行器失败不静默 / 每条 SQL 带关键条件且只读)。"
