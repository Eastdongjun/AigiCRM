#!/usr/bin/env bash
# deploy-db.sh / deploy-db-test.sh 接入迁移后断言的行为测试(GL-07, 2026-11-24)
#
# 验证的是"接入"本身; 断言脚本 verify-post-migrate.sh 有自己的行为测试(test-verify-post-migrate.sh)。
#   ① 顺序: 备份 → 迁移前快照 → 迁移 → 迁移后断言 → 健康检查。快照必须在迁移之前, 断言必须在迁移之后
#   ② 断言不通过 → 迁移脚本以非零退出, 不做健康检查, 不打印"迁移完成"
#   ③ 快照读不到 → 在迁移之前就中止(宁可不迁, 也不能迁完才发现无从核对)
#   ④ 快照里的版本与计数原样传给断言(--since / --pre), 断言的输出能在迁移脚本的输出里看到
#
# 做法: 把两份迁移脚本和断言脚本复制到临时目录, 换上桩 deploy-lib.sh(不读部署配置、不连服务器,
#       只按发生顺序记录每个远端调用)和桩迁移契约检查; SQL 交给按标签应答的假执行器。全程不联网。
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_SELF_DIR/.." && pwd)"
fail() { echo "FAIL: $*" >&2; exit 1; }
for f in deploy-db.sh deploy-db-test.sh verify-post-migrate.sh; do
  [ -f "$TEST_SELF_DIR/$f" ] || fail "缺少 $TEST_SELF_DIR/$f —— 迁移脚本尚未接入迁移后断言"
done

TMP="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-dbverify-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
LOG="$TMP/phase.log"        # 桩与假执行器按发生顺序写入阶段名
OUT="$TMP/out"
SD="$TMP/scripts"; MIG="$TMP/aigicrm-backend/src/main/resources/db/migration"
mkdir -p "$SD" "$MIG"
cp "$TEST_SELF_DIR/deploy-db.sh" "$TEST_SELF_DIR/deploy-db-test.sh" "$TEST_SELF_DIR/verify-post-migrate.sh" "$SD/"
# 假迁移目录: 1 支不晚于迁移前版本(不属于本批) + 4 支本批, 其中行业词表那支用真实文件(标签从它解析)。
# 迁移前版本故意取一个不等于断言脚本默认值(2026.11.22.1)的版本: 迁移脚本若没把快照版本传给断言,
# 本批清单就会少一支(默认值下只有 3 支), 测试能发现。
: > "$MIG/V2026.11.16.9__before_this_release.sql"
: > "$MIG/V2026.11.22.1__pending_a.sql"
: > "$MIG/V2026.11.23.1__pending_b.sql"
cp "$ROOT/aigicrm-backend/src/main/resources/db/migration/V2026.11.23.6__industry_dict_seed.sql" "$MIG/"
: > "$MIG/V2026.11.23.9__pending_d.sql"

# ---- 桩 deploy-lib.sh: 每个"远端调用"只在日志里记一笔阶段名, 再回一句能走通的话 ----
cat > "$SD/deploy-lib.sh" <<'STUB'
APP_SLUG=ycmn; DB_NAME=ycmn; DEPLOY_DIR=/tmp/aigicrm-stub-deploy
TEST_HOST=stub; TEST_USER=stub; TEST_PASS="${TEST_USER}_stub"     # 桩值, 只为让测试迁移脚本的"凭据非空"检查通过
assert_prod_env_pinned() { echo pin >> "$PHASE_LOG"; }
check_deploy_arch()       { echo arch >> "$PHASE_LOG"; }
check_prod_tls()          { echo tls >> "$PHASE_LOG"; }
check_health()            { echo health >> "$PHASE_LOG"; }
ssh_prod() {
  case "$*" in
    *mysqldump*)        echo backup >> "$PHASE_LOG"; echo '备份完成: stub' ;;
    *'wc -l'*)          echo upload_count >> "$PHASE_LOG"; echo 3 ;;
    *'flyway/flyway:'*) echo migrate >> "$PHASE_LOG"; echo 'Successfully applied 3 migrations' ;;
    *)                  cat > /dev/null; echo stub-ok ;;      # 上传迁移目录那一步会往 stdin 灌 tar, 读掉
  esac
}
ssh_test() {
  case "$*" in
    *'wc -l'*) echo 3 ;;
    *)         cat > /dev/null; echo stub-ok ;;
  esac
}
scp_test() { echo scp-stub; }
ssh_test_once() {      # 远端脚本经 stdin 进来, 按内容区分是哪一步
  local body; body="$(cat)"
  case "$body" in
    *mysqldump*)  echo backup_test >> "$PHASE_LOG"; echo '备份完成: stub' ;;
    *' migrate'*) echo migrate_test >> "$PHASE_LOG"; echo 'Successfully applied 3 migrations' ;;
    *)            echo remote_test >> "$PHASE_LOG"; echo stub-ok ;;
  esac
}
STUB
printf '#!/usr/bin/env bash\nexit 0\n' > "$SD/check-migration-contract.sh"
chmod 700 "$SD/check-migration-contract.sh"

# ---- 假的 SQL 执行器: 记阶段名, 按标签应答; 场景由 SCENARIO 指定 ----
cat > "$TMP/fake-sql" <<'FAKE'
#!/usr/bin/env bash
SQL="$(cat)"
tag="$(printf '%s\n' "$SQL" | sed -n 's/^-- vpm:\([a-z_]*\).*/\1/p' | head -1)"
S="${SCENARIO:-good}"
tab="$(printf '\t')"
case "$tag" in
  snap_*) echo snapshot >> "$PHASE_LOG" ;;
  *)      echo assert >> "$PHASE_LOG" ;;
esac
[ "$S" = history_unreadable ] && [ "$tag" = snap_version ] && exit 7
case "$tag" in
  snap_version)             printf '2026.11.16.9\n' ;;
  snap_counts)              printf '6%s5%s2%s13\n' "$tab" "$tab" "$tab" ;;
  flyway_failed)            printf '0\n' ;;
  flyway_applied)           if [ "$S" = missing_migration ]; then printf '2026.11.16.9\n2026.11.22.1\n2026.11.23.1\n2026.11.23.9\n'
                            else printf '2026.11.16.9\n2026.11.22.1\n2026.11.23.1\n2026.11.23.6\n2026.11.23.9\n'; fi ;;
  scope_dist)               printf 'songdu%s6\nsuishi%s7\n' "$tab" "$tab" ;;
  recitation_tenant_column) printf '1\n' ;;
  tenant_tables)            printf 'biz_a\n' ;;
  tenant_union)             : ;;
  menu_stats_group|menu_stats_parent|perm_meta_name|action_name) printf '1\n' ;;
  menu_names)               if [ "$S" = content_not_renamed ]; then printf '/fabushi/content%s内容治理\n' "$tab"
                            else printf '/fabushi/content%s内容管理\n/fabushi/feed%s认知广场\n/fabushi/shelf%s我的觉醒\n' "$tab" "$tab" "$tab"; fi ;;
  menu_fabushi_order)       printf '/fabushi/book%s4\n/fabushi/content%s5\n/fabushi/feed%s2\n/fabushi/library%s3\n/fabushi/shelf%s1\n' "$tab" "$tab" "$tab" "$tab" "$tab" ;;
  industry_seed)            printf '10\n' ;;
  onboarding_users)         printf '1\n' ;;
  onboarding_flag)          printf 'true\n' ;;
  remind_freq_dict|remind_freq_settings) printf '0\n' ;;
  *) echo "假执行器不认识的标签: [$tag]" >&2; exit 9 ;;
esac
FAKE
chmod 700 "$TMP/fake-sql"

run_prod() { # $1=场景; 输出进 $OUT, 返回被测脚本的退出码
  : > "$LOG"
  PHASE_LOG="$LOG" AIGICRM_SQL_RUNNER="$TMP/fake-sql" SCENARIO="$1" bash "$SD/deploy-db.sh" "$MIG" > "$OUT" 2>&1 < /dev/null
}
run_test() {
  : > "$LOG"
  PHASE_LOG="$LOG" AIGICRM_SQL_RUNNER="$TMP/fake-sql" SCENARIO="$1" bash "$SD/deploy-db-test.sh" > "$OUT" 2>&1 < /dev/null
}
log_text() { tr '\n' ' ' < "$LOG"; }
phase_line() { /usr/bin/grep -n -x -- "$1" "$LOG" | sed -n "${2:-\$}p" | cut -d: -f1; }   # 该阶段第一次(1)或最后一次(默认)出现的行号
assert_order() { # 参数=阶段名; 要求各自"最后一次出现"的行号严格递增
  local last=0 n pat
  for pat in "$@"; do
    n="$(phase_line "$pat")"
    [ -n "$n" ] || fail "日志里没有阶段 [${pat}]; 日志: $(log_text)"
    [ "$n" -gt "$last" ] || fail "阶段 [${pat}] 没有排在前面各阶段之后; 日志: $(log_text)"
    last="$n"
  done
}
assert_absent() { /usr/bin/grep -qx -- "$1" "$LOG" && fail "不应出现阶段 [${1}]; 日志: $(log_text)"; return 0; }
assert_around_migrate() { # $1=迁移阶段名: 断言查询不得早于迁移, 快照不得晚于迁移
  local mig first_assert last_snap
  mig="$(phase_line "$1")"; first_assert="$(phase_line assert 1)"; last_snap="$(phase_line snapshot)"
  [ -n "$mig" ] || fail "日志里没有迁移阶段 [${1}]; 日志: $(log_text)"
  [ -n "$last_snap" ] && [ "$last_snap" -lt "$mig" ] || fail "迁移之前没有取到完整快照(快照晚于迁移或缺失); 日志: $(log_text)"
  [ -n "$first_assert" ] && [ "$first_assert" -gt "$mig" ] || fail "断言查询没有排在迁移之后; 日志: $(log_text)"
}
out() { cat "$OUT"; }

# ================= 生产迁移脚本 deploy-db.sh =================
# ---- ① 正常: 退出 0, 顺序正确, 快照值传到了断言 ----
rc=0; run_prod good || rc=$?
[ "$rc" -eq 0 ] || fail "deploy-db.sh 正常时应退出 0, 实际 rc=${rc}; 输出: $(out)"
assert_order backup snapshot migrate assert health
assert_around_migrate migrate
grep -q '迁移前: 已应用版本=2026.11.16.9, 内容类型(诵读,心得,正能量,总量)=6,5,2,13' "$OUT" || fail "应打印迁移前快照: $(out)"
grep -q '本批迁移(晚于迁移前版本)共 4 支: 2026.11.22.1 2026.11.23.1 2026.11.23.6 2026.11.23.9' "$OUT" || fail "断言应以快照版本(2026.11.16.9)为基准, 本批应为 4 支: $(out)"
grep -q 'PASS     scope_reconcile: 诵读 6=6, 随施 7=5+2, 总量 13 不变' "$OUT" || fail "断言应以快照计数对账(6,5,2,13): $(out)"
grep -q '迁移后断言结果: PASS=' "$OUT" || fail "迁移脚本的输出里应能看到断言结果: $(out)"
grep -q '✅ 数据库迁移完成' "$OUT" || fail "正常结束应打印迁移完成: $(out)"

# ---- ② 断言不通过: 非零退出, 已迁移, 但不做健康检查、不算完成 ----
for sc in content_not_renamed missing_migration; do
  rc=0; run_prod "$sc" || rc=$?
  [ "$rc" -eq 1 ] || fail "场景 ${sc}: deploy-db.sh 应退出 1, 实际 rc=${rc}; 输出: $(out)"
  grep -q '^  FAIL     ' "$OUT" || fail "场景 ${sc}: 输出里应有 FAIL 条目: $(out)"
  grep -q '迁移后断言未通过' "$OUT" || fail "场景 ${sc}: 应明确说明是迁移后断言未通过: $(out)"
  grep -q '✅ 数据库迁移完成' "$OUT" && fail "场景 ${sc}: 断言未通过时不应打印迁移完成: $(out)"
  assert_absent health
  assert_order backup snapshot migrate assert
done

# ---- ③ 快照读不到: 在迁移之前就中止 ----
rc=0; run_prod history_unreadable || rc=$?
[ "$rc" -eq 1 ] || fail "快照读不到时 deploy-db.sh 应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '读不到迁移前快照' "$OUT" || fail "应说明是快照读不到: $(out)"
assert_absent migrate
assert_absent assert
assert_absent health
grep -q '✅ 数据库迁移完成' "$OUT" && fail "快照读不到时不应打印迁移完成: $(out)"

# ================= 测试库迁移脚本 deploy-db-test.sh =================
rc=0; run_test good || rc=$?
[ "$rc" -eq 0 ] || fail "deploy-db-test.sh 正常时应退出 0, 实际 rc=${rc}; 输出: $(out)"
assert_order backup_test snapshot migrate_test assert
assert_around_migrate migrate_test
grep -q '迁移前: 已应用版本=2026.11.16.9, 内容类型(诵读,心得,正能量,总量)=6,5,2,13' "$OUT" || fail "应打印迁移前快照: $(out)"
grep -q '本批迁移(晚于迁移前版本)共 4 支: 2026.11.22.1 2026.11.23.1 2026.11.23.6 2026.11.23.9' "$OUT" || fail "断言应以快照版本为基准, 本批应为 4 支: $(out)"
grep -q 'PASS     scope_reconcile: 诵读 6=6, 随施 7=5+2, 总量 13 不变' "$OUT" || fail "断言应以快照计数对账: $(out)"
grep -q '迁移后断言结果: PASS=' "$OUT" || fail "输出里应能看到断言结果: $(out)"
grep -q '✅ 测试库迁移完成' "$OUT" || fail "正常结束应打印迁移完成: $(out)"

rc=0; run_test content_not_renamed || rc=$?
[ "$rc" -eq 1 ] || fail "deploy-db-test.sh 断言不通过时应退出 1, 实际 rc=${rc}; 输出: $(out)"
grep -q '迁移后断言未通过' "$OUT" || fail "应明确说明是迁移后断言未通过: $(out)"
grep -q '✅ 测试库迁移完成' "$OUT" && fail "断言未通过时不应打印迁移完成: $(out)"

rc=0; run_test history_unreadable || rc=$?
[ "$rc" -eq 1 ] || fail "deploy-db-test.sh 快照读不到时应退出 1, 实际 rc=${rc}; 输出: $(out)"
assert_absent migrate_test
assert_absent assert

echo "PASS: deploy-db.sh / deploy-db-test.sh 接入迁移后断言(先快照后迁移再断言 / 断言不通过即中止 / 快照读不到在迁移前中止 / 快照值传到断言)。"
