#!/bin/bash
# Batch-3 工单3演练 v2：V2026.11.18.3（收紧口径：仅完整 scope 同+规范化 perms 等值才合并）
# 夹具(插在 M2 前=模拟生产真重复)：201/202=perms 等值组(应合并)；203=perms 不同组(不合并+清单)；204=异 scope(红线不合并)
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
source "$REPO/scripts/deploy-lib.sh" || exit 1
TS=$(date +%Y%m%d-%H%M%S)
LOCAL_DIR="/tmp/a2-drill-local"
RDIR="/tmp/aigicrm-a2-drill3-$TS"
SRC_DB="${TEST_DB_NAME:-aigicrm_prod_20261016}"
COPY_DB="aigicrm_a2_drill_20261118"
CT="${TEST_DB_CONTAINER:-shared-mysql}"
LOG="$LOCAL_DIR/drill3-log-$TS.txt"
exec > >(tee -a "$LOG") 2>&1
M1="$REPO/aigicrm-backend/src/main/resources/db/migration/V2026.11.18.1__file_grant_scope_columns.sql"
M2="$REPO/aigicrm-backend/src/main/resources/db/migration/V2026.11.18.2__migrate_folder_type_default_to_file_grant.sql"
M3="$REPO/aigicrm-backend/src/main/resources/db/migration/V2026.11.18.3__merge_legacy_duplicate_grants.sql"
REC="$REPO/docs/design/phase-a/reconcile-file-grants.sql"

emit_var() { printf '%s=%q\n' "$1" "$2"; }
prologue() {
  cat <<'EOF'
set -Eeuo pipefail
fail() { echo "!!DRILL_STEP_FAIL: $*" >&2; exit 1; }
EOF
  emit_var CT "$CT"; emit_var SRC_DB "$SRC_DB"; emit_var COPY_DB "$COPY_DB"; emit_var RDIR "$RDIR"
  cat <<'EOF'
PW="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
[ -n "$PW" ] || fail "derive pw"
MY()  { docker exec -e MYSQL_PWD="$PW" "$CT" mysql -uroot --default-character-set=utf8mb4 "$@"; }
MYI() { docker exec -i -e MYSQL_PWD="$PW" "$CT" mysql -uroot --default-character-set=utf8mb4 "$@"; }
MYT() { docker exec -i -e MYSQL_PWD="$PW" "$CT" mysql -uroot --default-character-set=utf8mb4 --table "$@"; }
EOF
}
step() { echo; echo "########## $* ##########"; }
run_remote() { ssh_test_once "bash -s" || { echo "!!REMOTE_FAIL(step=$1)"; return 1; }; }
cleanup() {
  step "CLEANUP drill3"
  { prologue
    cat <<'EOF'
MY -e "DROP DATABASE IF EXISTS $COPY_DB" >/dev/null && echo "[cleanup] dropped" || echo "[cleanup] drop 失败"
echo "[cleanup] 剩余 drill 库: $(MY -N -B -e "SHOW DATABASES LIKE 'aigicrm_a2_drill%'" | wc -l | tr -d ' ')"
rm -rf "$RDIR" && echo "[cleanup] 暂存已删"
echo "[cleanup] 源库 old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_folder_type_default")（期望 62）"
EOF
  } | ssh_test_once "bash -s" || echo "!!CLEANUP_FAIL"
}
trap cleanup EXIT

step "STEP0/1 探测+上传"
{ prologue; cat <<'EOF'
MY -N -B -e "SHOW DATABASES LIKE 'aigicrm_a2_drill%'" | grep . && fail "残留 drill 库" || echo "[ok] 无残留"
EOF
} | run_remote S0 || exit 1
ssh_test "mkdir -p '$RDIR'" || exit 1
scp_test "$M1" "${TEST_USER}@${TEST_HOST}:$RDIR/m1.sql" >/dev/null || exit 1
scp_test "$M2" "${TEST_USER}@${TEST_HOST}:$RDIR/m2.sql" >/dev/null || exit 1
scp_test "$M3" "${TEST_USER}@${TEST_HOST}:$RDIR/m3.sql" >/dev/null || exit 1
scp_test "$REC" "${TEST_USER}@${TEST_HOST}:$RDIR/reconcile.sql" >/dev/null || exit 1
scp_test "$LOCAL_DIR/grant-create.sql" "${TEST_USER}@${TEST_HOST}:$RDIR/gc.sql" >/dev/null || exit 1
scp_test "$LOCAL_DIR/behave-before.sql" "$LOCAL_DIR/behave-after.sql" "$LOCAL_DIR/conflict-count.sql" "$LOCAL_DIR/behave-diff.sql" "$LOCAL_DIR/uncommitted-list.sql" "${TEST_USER}@${TEST_HOST}:$RDIR/" >/dev/null || exit 1

step "STEP2 建副本→M1→夹具→M2→对账#1"
{ prologue; cat <<'EOF'
MY -e "CREATE DATABASE $COPY_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci" || fail "create"
docker exec -e MYSQL_PWD="$PW" "$CT" mysqldump -uroot --single-transaction "$SRC_DB" biz_folder_type_default > "$RDIR/dump.sql" || fail "dump"
MYI "$COPY_DB" < "$RDIR/dump.sql" || fail "import"
MYI "$COPY_DB" < "$RDIR/gc.sql" || fail "gc"
MYI "$COPY_DB" < "$RDIR/m1.sql" || fail "m1"
MYI "$COPY_DB" <<'FX'
INSERT INTO biz_folder_type_default (folder_type, object_type, folder_id, scope_org_id, scope_level, subject_type, subject_id, perms, expires_at, created_by, created_at, tenant_id) VALUES
  ('public','FILE',NULL,NULL,NULL,'ROLE',201,'view,download',NULL,1,NOW(),1),
  ('public','FILE',NULL,NULL,NULL,'ROLE',201,'download,view','2026-12-31 00:00:00',1,NOW(),1),
  ('private','FILE',NULL,NULL,NULL,'ROLE',202,'view,upload','2026-12-31 00:00:00',1,NOW(),1),
  ('private','FILE',NULL,NULL,NULL,'ROLE',202,'upload,view','2027-06-30 00:00:00',1,NOW(),1),
  ('incentive','FILE',NULL,NULL,NULL,'ROLE',203,'view',NULL,1,NOW(),1),
  ('incentive','FILE',NULL,NULL,NULL,'ROLE',203,'view,download',NULL,1,NOW(),1),
  ('public','FILE',NULL,NULL,NULL,'ROLE',204,'view',NULL,1,NOW(),1),
  ('public','FILE',NULL,NULL,'region','ROLE',204,'view',NULL,1,NOW(),1);
FX
MYI "$COPY_DB" < "$RDIR/m2.sql" || fail "m2"
echo "[rows] old=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_folder_type_default") legacy=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant WHERE source='legacy'")"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "recon1"
EOF
} | run_remote S2 || exit 1

step "STEP3 合并前快照：冲突组总数 + 行为展开集合"
{ prologue; cat <<'XEOF'
CONFLICT_BEFORE=$(MYI -N -B "$COPY_DB" < "$RDIR/conflict-count.sql") || fail "conflict-count"
echo "[before] conflict_total=$CONFLICT_BEFORE"
MYI "$COPY_DB" < "$RDIR/behave-before.sql" || fail "snapshot-before"
echo "[before] behave_rows=$(MY -N -B "$COPY_DB" -e 'SELECT COUNT(*) FROM _a2_before')"
XEOF
} | run_remote S3 || exit 1

step "STEP4 执行 M3 → 断言①真重复(可合并)归零 ②对账四数 0 ③夹具语义 ④行为快照不变 ⑤清单=perms不同组"
{ prologue; cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/m3.sql" 2>&1 | grep -E "Query OK|Rows matched|ERROR" || fail "m3"
echo "[after] old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_folder_type_default")"
echo "---- ① 对账四数（期望全 0）----"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "recon2"
echo "---- ② 夹具断言 ----"
F1=$(MY -N -B "$COPY_DB" -e "SELECT CONCAT(COUNT(*),'|',MIN(perms),'|',IF(MIN(expires_at IS NULL)=1,'NULL','HAS')) FROM biz_folder_type_default WHERE subject_id=201")
[ "$F1" = "1|view,download|NULL" ] || fail "组201: '$F1'（期望 1|view,download|NULL: 合并+词表序+任一永久）"
F2=$(MY -N -B "$COPY_DB" -e "SELECT CONCAT(COUNT(*),'|',MIN(perms),'|',COALESCE(MAX(expires_at),'x')) FROM biz_folder_type_default WHERE subject_id=202")
echo "$F2" | grep -q "^1|view,upload|2027-06-30" || fail "组202: '$F2'（期望 1|view,upload|2027-06-30: 合并+MAX）"
F3=$(MY -N -B "$COPY_DB" -e "SELECT COUNT(*) FROM biz_folder_type_default WHERE subject_id=203")
[ "$F3" = "2" ] || fail "组203: $F3 行（期望 2: perms 不同不合并）"
F4=$(MY -N -B "$COPY_DB" -e "SELECT COUNT(*) FROM biz_folder_type_default WHERE subject_id=204")
[ "$F4" = "2" ] || fail "组204: $F4 行（期望 2: 异 scope 不合并红线）"
echo "[fixtures] 201/202 合并正确、203(perms不同)与204(异scope)保持 2 行 ✅"
echo "---- ③ 未合并真重复组清单（期望=203 一组）----"
MYT "$COPY_DB" < "$RDIR/uncommitted-list.sql" || fail "list"
EOF
} | run_remote S4 || exit 1

step "STEP4b 行为快照对比 + 冲突组后值（隔离块）"
{ prologue; cat <<'XEOF'
MYI "$COPY_DB" < "$RDIR/behave-after.sql" || fail "snapshot-after"
MYT "$COPY_DB" < "$RDIR/behave-diff.sql" || fail "behave-diff"
CONFLICT_AFTER=$(MYI -N -B "$COPY_DB" < "$RDIR/conflict-count.sql") || fail "conflict-after"
echo "[after] conflict_total=$CONFLICT_AFTER（合并组退出冲突组）"
XEOF
} | run_remote S4b || exit 1

step "STEP5 幂等：重跑 M3 → 0 行受影响 + 对账仍 0"
{ prologue; cat <<'EOF'
OUT=$(MYI -vv "$COPY_DB" < "$RDIR/m3.sql" 2>&1 | grep -E "Rows matched: [1-9]|Deleted: [1-9]" || true)
[ -z "$OUT" ] && echo "[idempotent] 重跑 0 行受影响 ✅" || { echo "[idempotent] 非零受影响: $OUT"; fail "幂等破坏"; }
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "recon3"
EOF
} | run_remote S5 || exit 1

step "DRILL3 DONE"
