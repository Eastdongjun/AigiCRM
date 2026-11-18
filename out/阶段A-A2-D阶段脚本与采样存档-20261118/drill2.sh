#!/bin/bash
# A2 演练 v2 —— 用 db/migration 最终形态 M1/M2 转正稿重跑副本演练 + QC 第2轮清单 C1-C6 专项。
# 只动测试服 shared-mysql 临时副本库 aigicrm_a2_drill_20261118；生产零接触；口令远端派生 env 注入。
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
# shellcheck disable=SC1090
source "$REPO/scripts/deploy-lib.sh" || { echo "FATAL: source deploy-lib"; exit 1; }

TS=$(date +%Y%m%d-%H%M%S)
LOCAL_DIR="/tmp/a2-drill-local"
RDIR="/tmp/aigicrm-a2-drill2-$TS"
SRC_DB="${TEST_DB_NAME:-aigicrm_prod_20261016}"
COPY_DB="aigicrm_a2_drill_20261118"
CT="${TEST_DB_CONTAINER:-shared-mysql}"
LOG="$LOCAL_DIR/drill2-log-$TS.txt"
RECONCILE_SH="$REPO/scripts/reconcile-file-grants.sh"
exec > >(tee -a "$LOG") 2>&1

emit_var() { printf '%s=%q\n' "$1" "$2"; }
prologue() {
  cat <<'EOF'
set -Eeuo pipefail
fail() { echo "!!DRILL_STEP_FAIL: $*" >&2; exit 1; }
EOF
  emit_var CT "$CT"; emit_var SRC_DB "$SRC_DB"; emit_var COPY_DB "$COPY_DB"; emit_var RDIR "$RDIR"
  cat <<'EOF'
DB_ADMIN_PASS="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
[ -n "$DB_ADMIN_PASS" ] || fail "derive MYSQL_ROOT_PASSWORD"
MY()  { docker exec -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysql -uroot --default-character-set=utf8mb4 "$@"; }
MYI() { docker exec -i -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysql -uroot --default-character-set=utf8mb4 "$@"; }
MYT() { docker exec -i -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysql -uroot --default-character-set=utf8mb4 --table "$@"; }
echo "[remote env] container=$CT src=$SRC_DB copy=$COPY_DB scratch=$RDIR"
EOF
}
step() { echo; echo "########## $* ##########"; }
run_remote() { if ! ssh_test_once "bash -s"; then echo "!!REMOTE_FAIL(step=$1)"; return 1; fi; }

# C5：本地包装脚本退出码（0=0差异 / 1=有差异）
c5() { local tag="$1"; bash "$RECONCILE_SH" "$COPY_DB" >/tmp/a2-drill-local/c5-$tag.out 2>&1; local rc=$?; echo "[C5:$tag] exit=$rc  $(tail -1 /tmp/a2-drill-local/c5-$tag.out)"; return 0; }

cleanup() {
  step "CLEANUP v2 副本清理（无论如何都执行）"
  { prologue
    cat <<'EOF'
if MY -e "DROP DATABASE IF EXISTS $COPY_DB"; then echo "[cleanup] DROP DATABASE $COPY_DB ok"; else echo "[cleanup] DROP 失败（需人工跟进）"; fi
LEFT=$(MY -N -B -e "SHOW DATABASES LIKE 'aigicrm_a2_drill%'" | wc -l | tr -d ' ')
echo "[cleanup] 剩余 drill 库: $LEFT（期望 0）"
rm -rf "$RDIR" && echo "[cleanup] 远端暂存 $RDIR 已删除" || echo "[cleanup] rm $RDIR 失败（需人工跟进）"
echo "[cleanup] 源库复核: old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_folder_type_default") file_grant=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_file_grant" 2>/dev/null || echo 'NOT-EXISTS(快照原状)')"
EOF
  } | ssh_test_once "bash -s" || echo "!!CLEANUP_REMOTE_FAIL（必须人工核实）"
}
trap cleanup EXIT

# ---------- 0/1 探测+上传 ----------
step "STEP0 连通性探测"
{ prologue
  cat <<'EOF'
docker ps --format '{{.Names}}' | grep -x "$CT" || fail "container $CT not running"
MY -N -B -e "SHOW DATABASES LIKE 'aigicrm_a2_drill%'" | grep . && fail "drill 库已存在" || echo "[ok] 无残留 drill 库"
EOF
} | run_remote STEP0 || exit 1

step "STEP1 上传最终形态 M1/M2 与演练 SQL"
ssh_test "mkdir -p '$RDIR'" || exit 1
scp_test "$LOCAL_DIR/m1-final.sql" "$LOCAL_DIR/m2-final.sql" "$LOCAL_DIR/reconcile.sql" \
         "$LOCAL_DIR/grant-create.sql" "$LOCAL_DIR/sql-phase1-baseline.sql" "$LOCAL_DIR/sql-conflict.sql" \
         "$LOCAL_DIR/sql-v2-checks.sql" "$LOCAL_DIR/sql-v2-fixtures.sql" "$LOCAL_DIR/sql-v2-fixture-asserts.sql" \
         "${TEST_USER}@${TEST_HOST}:$RDIR/" || exit 1
ssh_test "ls '$RDIR/' | tr '\n' ' '; echo" || exit 1

# ---------- 2 源库只读基线 ----------
step "STEP2 源库只读基线"
{ prologue
  cat <<'EOF'
echo "[src] old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_folder_type_default") conflict_groups=$(MY -N -B "$SRC_DB" -e "SELECT COUNT(*) FROM (
  SELECT 1 FROM (
    SELECT d.id, d.subject_type, d.subject_id, d.folder_id, d.scope_level, d.scope_org_id,
           CASE WHEN a.action IN ('rename','replace') THEN 'edit' ELSE a.action END AS action
      FROM biz_folder_type_default d
      JOIN (SELECT 'view' AS action UNION ALL SELECT 'download' UNION ALL SELECT 'upload' UNION ALL SELECT 'edit'
            UNION ALL SELECT 'rename' UNION ALL SELECT 'replace' UNION ALL SELECT 'delete'
            UNION ALL SELECT 'share' UNION ALL SELECT 'manage' UNION ALL SELECT 'create') a
        ON FIND_IN_SET(a.action, REPLACE(COALESCE(d.perms,''),' ','')) > 0
     WHERE d.subject_type IN ('ROLE','USER') AND d.subject_id IS NOT NULL
  ) x
  GROUP BY subject_type, subject_id, CASE WHEN folder_id IS NULL THEN 'SPACE' ELSE 'FOLDER' END, folder_id, action
  HAVING COUNT(*) > 1) t")"
EOF
} | run_remote STEP2 || exit 1

# ---------- 3 建副本 ----------
step "STEP3 建副本并导入（旧表快照 + V2026.11.17.1 同构空表）"
{ prologue
  cat <<'EOF'
MY -e "CREATE DATABASE $COPY_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci" || fail "create db"
docker exec -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction "$SRC_DB" biz_folder_type_default > "$RDIR/dump.sql" || fail "mysqldump"
[ -s "$RDIR/dump.sql" ] || fail "dump empty"
MYI "$COPY_DB" < "$RDIR/dump.sql" || fail "import"
MYI "$COPY_DB" < "$RDIR/grant-create.sql" || fail "grant-create"
echo "[copy] old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_folder_type_default") file_grant=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant")"
EOF
} | run_remote STEP3 || exit 1

# ---------- 4 M1 最终稿 ----------
step "STEP4 副本执行 M1 转正稿（V2026.11.18.1__file_grant_scope_columns）"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/m1-final.sql" 2>&1 | grep -E "Query OK|Rows matched|ERROR" || fail "m1"
MY -N -B "$COPY_DB" -e "SHOW INDEX FROM biz_file_grant" | awk '{print "[index] "$3" col="$5}'
EOF
} | run_remote STEP4 || exit 1

# ---------- 5 M2 最终稿 + C1/C3 ----------
step "STEP5 副本执行 M2 转正稿（并入 B-1/B-2/B-3）+ C1/C3 检查"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/m2-final.sql" 2>&1 | grep -E "Query OK|Rows matched|ERROR" || fail "m2"
MYT "$COPY_DB" < "$RDIR/sql-v2-checks.sql" || fail "v2-checks"
EOF
} | run_remote STEP5 || exit 1

# ---------- 6 对账 #1 ----------
step "STEP6 对账 #1（C1 四数，预期全 0）"
{ prologue; cat <<'EOF'
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile1"
EOF
} | run_remote STEP6 || exit 1

# ---------- 7 幂等 ----------
step "STEP7 幂等重跑 M2（预期 0 行受影响）+ 对账 #2"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/m2-final.sql" 2>&1 | grep -E "Query OK|Rows matched|ERROR" || fail "m2-rerun"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile2"
echo "[rows] legacy=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant WHERE source='legacy'")"
EOF
} | run_remote STEP7 || exit 1

# ---------- 7b C2/C4 夹具 ----------
step "STEP7b C2/C4 合成夹具：blank 归一 + expires 混合组 → 重跑 M2 + 断言 + 对账 #3"
{ prologue
  cat <<'EOF'
MYT "$COPY_DB" < "$RDIR/sql-v2-fixtures.sql" || fail "fixtures"
MYI -vv "$COPY_DB" < "$RDIR/m2-final.sql" 2>&1 | grep -E "Query OK|Rows matched|ERROR" || fail "m2-fixtures"
MYT "$COPY_DB" < "$RDIR/sql-v2-fixture-asserts.sql" || fail "fixture-asserts"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile3"
EOF
} | run_remote STEP7b || exit 1

# ---------- 8 负例 + C5 退出码 ----------
step "STEP8 负例（删行/篡改→检出→重跑不愈→还原）× C5 包装脚本退出码三态"
{ prologue
  cat <<'EOF'
VICTIM_REF=$(MY -N -B "$COPY_DB" -e "SELECT legacy_ref FROM biz_file_grant WHERE source='legacy' ORDER BY id LIMIT 1")
[ -n "$VICTIM_REF" ] || fail "no victim"
ORIG_EXP=$(MY -N -B "$COPY_DB" -e "SELECT COALESCE(expire_at,'NULL') FROM biz_file_grant WHERE legacy_ref='$VICTIM_REF'")
echo "[victim] ref=$VICTIM_REF orig_expire=$ORIG_EXP"
echo "---- 8a 删除该行 ----"
MY "$COPY_DB" -e "DELETE FROM biz_file_grant WHERE legacy_ref='$VICTIM_REF'" || fail "delete"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8b 重跑 M2 补回 ----"
MYI -vv "$COPY_DB" < "$RDIR/m2-final.sql" 2>&1 | grep -E "Query OK|ERROR" || fail "refill"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8c 篡改过期 ----"
MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at=DATE_ADD(NOW(), INTERVAL 99 DAY) WHERE legacy_ref='$VICTIM_REF' AND expire_at IS NOT NULL" || fail "tamper1"
MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at=DATE_ADD(NOW(), INTERVAL 99 DAY) WHERE legacy_ref='$VICTIM_REF' AND expire_at IS NULL" || fail "tamper2"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8d 重跑 M2 不愈字段漂移 ----"
MYI -vv "$COPY_DB" < "$RDIR/m2-final.sql" 2>&1 | grep -E "Query OK|ERROR" || fail "rerun3"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8e 还原 ----"
if [ "$ORIG_EXP" = "NULL" ]; then
  MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at=NULL WHERE legacy_ref='$VICTIM_REF'" || fail "restore"
else
  MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at='$ORIG_EXP' WHERE legacy_ref='$VICTIM_REF'" || fail "restore"
fi
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
EOF
} | run_remote STEP8 || exit 1
# C5-2：0 差异态退出码（8e 还原后）
c5 "zero-state"

# 造差异 → C5-3 exit 1 → 还原 → C5-4 exit 0
step "STEP8b C5 退出码：造差异(≠0)与复原(=0)"
{ prologue
  cat <<'EOF'
DRIFT_REF=$(MY -N -B "$COPY_DB" -e "SELECT legacy_ref FROM biz_file_grant WHERE source='legacy' ORDER BY id DESC LIMIT 1")
[ -n "$DRIFT_REF" ] || fail "no drift victim"
MY "$COPY_DB" -e "DELETE FROM biz_file_grant WHERE legacy_ref='$DRIFT_REF'" || fail "drift-delete"
echo "[c5-drift] deleted $DRIFT_REF"
EOF
} | run_remote STEP8b-drift || exit 1
c5 "drift-state"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/m2-final.sql" 2>&1 | grep -E "Query OK|ERROR" || fail "heal"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile-heal"
EOF
} | run_remote STEP8b-heal || exit 1
c5 "healed-state"

# ---------- 9 C6 双写闭环模拟 ----------
step "STEP9 C6 双写闭环：旧表续期 + 新表全动作同值 upsert（模拟 syncUpsert）→ legacy_ref 不换行、对账仍 0"
{ prologue
  cat <<'EOF'
# 选单行组目标（组内唯一含 view 的旧行）：避免组 expire 合并语义干扰，验证行级 upsert 闭环
X=$(MY -N -B "$COPY_DB" -e "SELECT d.id FROM biz_folder_type_default d
  WHERE FIND_IN_SET('view', REPLACE(COALESCE(d.perms,' '),' ','')) > 0
    AND EXISTS (SELECT 1 FROM biz_file_grant g WHERE g.legacy_ref = CONCAT('ftd:', d.id, ':view') AND g.source='legacy')
    AND (SELECT COUNT(*) FROM biz_folder_type_default d2
         WHERE d2.subject_type=d.subject_type AND d2.subject_id=d.subject_id
           AND d2.object_type=d.object_type AND (d2.folder_id <=> d.folder_id)
           AND FIND_IN_SET('view', REPLACE(COALESCE(d2.perms,' '),' ','')) > 0) = 1
  ORDER BY d.id LIMIT 1")
[ -n "$X" ] || fail "no dual-write target"
ACTIONS=$(MY -N -B "$COPY_DB" -e "SELECT COUNT(*) FROM biz_file_grant WHERE legacy_ref LIKE CONCAT('ftd:', $X, ':%') AND source='legacy'")
echo "[c6] old_id=$X（单行组）动作行数=$ACTIONS"
BEFORE=$(MY -N -B "$COPY_DB" -e "SELECT GROUP_CONCAT(CONCAT(legacy_ref,'=',id) ORDER BY legacy_ref) FROM biz_file_grant WHERE legacy_ref LIKE CONCAT('ftd:', $X, ':%') AND source='legacy'")
echo "[c6] before: $BEFORE"
# 模拟 renewGrant 双写（= LegacyFileGrantSyncService.syncUpsert）：旧表改 expire + 新表该 ftd 前缀全部动作行同值
MY "$COPY_DB" -e "UPDATE biz_folder_type_default SET expires_at='2027-01-15 00:00:00' WHERE id=$X" || fail "old-renew"
MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at='2027-01-15 00:00:00' WHERE legacy_ref LIKE CONCAT('ftd:', $X, ':%') AND source IN ('legacy','dual_write')" || fail "new-upsert"
AFTER=$(MY -N -B "$COPY_DB" -e "SELECT GROUP_CONCAT(CONCAT(legacy_ref,'=',id) ORDER BY legacy_ref) FROM biz_file_grant WHERE legacy_ref LIKE CONCAT('ftd:', $X, ':%') AND source='legacy'")
echo "[c6] after:  $AFTER"
[ "$BEFORE" = "$AFTER" ] || fail "legacy_ref 换行（ref=id 对应关系变化）"
SYNCED=$(MY -N -B "$COPY_DB" -e "SELECT CONCAT((SELECT COUNT(*) FROM biz_folder_type_default WHERE id=$X AND expires_at='2027-01-15 00:00:00'), '/', (SELECT COUNT(*) FROM biz_file_grant WHERE legacy_ref LIKE CONCAT('ftd:', $X, ':%') AND expire_at='2027-01-15 00:00:00'))")
echo "[c6] expire 同步(旧行/新行, 应 1/$ACTIONS): $SYNCED"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile-c6"
EOF
} | run_remote STEP9 || exit 1
c5 "c6-final"

step "DRILL v2 DONE（cleanup 由 trap 执行）"
