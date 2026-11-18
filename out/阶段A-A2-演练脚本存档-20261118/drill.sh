#!/bin/bash
# A2 副本演练驱动（只动测试服 shared-mysql 的临时副本库；生产零接触；口令远端派生 env 注入）
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
# shellcheck disable=SC1090
source "$REPO/scripts/deploy-lib.sh" || { echo "FATAL: source deploy-lib"; exit 1; }

TS=$(date +%Y%m%d-%H%M%S)
LOCAL_DIR="/tmp/a2-drill-local"
RDIR="/tmp/aigicrm-a2-drill-$TS"
SRC_DB="${TEST_DB_NAME:-aigicrm_prod_20261016}"
COPY_DB="aigicrm_a2_drill_20261118"
CT="${TEST_DB_CONTAINER:-shared-mysql}"
LOG="$LOCAL_DIR/drill-log-$TS.txt"
exec > >(tee -a "$LOG") 2>&1

emit_var() { printf '%s=%q\n' "$1" "$2"; }

prologue() {
  cat <<'EOF'
set -Eeuo pipefail
fail() { echo "!!DRILL_STEP_FAIL: $*" >&2; exit 1; }
EOF
  emit_var CT "$CT"
  emit_var SRC_DB "$SRC_DB"
  emit_var COPY_DB "$COPY_DB"
  emit_var RDIR "$RDIR"
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
run_remote() { # $1=步骤名；脚本已在 stdin
  if ! ssh_test_once "bash -s"; then echo "!!REMOTE_FAIL(step=$1)"; return 1; fi
}

cleanup() {
  step "CLEANUP 副本清理（无论如何都执行）"
  { prologue
    cat <<'EOF'
if MY -e "DROP DATABASE IF EXISTS $COPY_DB"; then echo "[cleanup] DROP DATABASE $COPY_DB ok"; else echo "[cleanup] DROP 失败（需人工跟进）"; fi
LEFT=$(MY -N -B -e "SHOW DATABASES LIKE 'aigicrm_a2_drill%'" | wc -l | tr -d ' ')
echo "[cleanup] 剩余 drill 库: $LEFT（期望 0）"
rm -rf "$RDIR" && echo "[cleanup] 远端暂存 $RDIR 已删除" || echo "[cleanup] rm $RDIR 失败（需人工跟进）"
echo "[cleanup] 源库复核: old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_folder_type_default") file_grant=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_file_grant" 2>/dev/null || echo 'NOT-EXISTS(snapshot 无此表,符合快照现状)')"
EOF
  } | ssh_test_once "bash -s" || echo "!!CLEANUP_REMOTE_FAIL（必须人工核实）"
}
trap cleanup EXIT

# ---------- 0. 探测 ----------
step "STEP0 连通性与容器探测"
{ prologue
  cat <<'EOF'
docker ps --format '{{.Names}}' | grep -x "$CT" || fail "container $CT not running"
MY -N -B -e "SELECT VERSION()" | sed 's/^/[mysql] version=/'
MY -N -B -e "SHOW DATABASES LIKE 'aigicrm_a2_drill%'" | grep . && fail "drill 库已存在，先人工清理" || echo "[ok] 无残留 drill 库"
echo "[ok] src db exists: $(MY -N -B -e "SHOW DATABASES LIKE '$SRC_DB'" | wc -l | tr -d ' ')"
EOF
} | run_remote STEP0 || exit 1

# ---------- 1. 上传 ----------
step "STEP1 上传演练 SQL 到测试服"
ssh_test "mkdir -p '$RDIR'" || exit 1
scp_test "$LOCAL_DIR/ddl.sql" "$LOCAL_DIR/migrate.sql" "$LOCAL_DIR/migrate-r2.sql" \
         "$LOCAL_DIR/reconcile.sql" \
         "$LOCAL_DIR/grant-create.sql" \
         "$LOCAL_DIR/sql-phase1-baseline.sql" "$LOCAL_DIR/sql-conflict.sql" \
         "$LOCAL_DIR/sql-post-migrate-checks.sql" \
         "${TEST_USER}@${TEST_HOST}:$RDIR/" || exit 1
ssh_test "ls -la '$RDIR/'" || exit 1

# ---------- 2. 源库只读基线 ----------
step "STEP2 源库只读基线（${SRC_DB}，不修改）"
{ prologue
  cat <<'EOF'
MYT "$SRC_DB" < "$RDIR/sql-phase1-baseline.sql" || fail "baseline"
echo "---- §2.10 冲突组（重复 scope 组） ----"
MYT "$SRC_DB" < "$RDIR/sql-conflict.sql" || fail "conflict"
echo "---- 冲突组数 ----"
MY -N -B "$SRC_DB" -e "SELECT COUNT(*) FROM (
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
  HAVING COUNT(*) > 1) t" | sed 's/^/[conflict_groups] /'
EOF
} | run_remote STEP2 || exit 1

# ---------- 3. 建副本并导入 ----------
step "STEP3 建副本库并导入（源库仅旧表；biz_file_grant 按迁移 DDL 建空表）"
{ prologue
  cat <<'EOF'
MY -e "CREATE DATABASE $COPY_DB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci" || fail "create db"
docker exec -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction "$SRC_DB" biz_folder_type_default > "$RDIR/dump.sql" || fail "mysqldump"
[ -s "$RDIR/dump.sql" ] || fail "dump empty"
grep -c '^CREATE TABLE' "$RDIR/dump.sql" | sed 's/^/[dump] create_table=/'
MYI "$COPY_DB" < "$RDIR/dump.sql" || fail "import"
MYI "$COPY_DB" < "$RDIR/grant-create.sql" || fail "grant-create"
echo "[copy] old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_folder_type_default") src_old_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_folder_type_default")"
echo "[copy] file_grant_rows=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant")"
EOF
} | run_remote STEP3 || exit 1

# ---------- 4. 执行 .1 DDL 草案 ----------
step "STEP4 副本执行 DDL 草案（.1）"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/ddl.sql" 2>&1 || fail "ddl"
echo "---- 结构确认 ----"
MY --table "$COPY_DB" -e "SHOW COLUMNS FROM biz_file_grant"
MY -N -B "$COPY_DB" -e "SHOW INDEX FROM biz_file_grant" | awk 'NR==1{next}{print "[index] "$3" col="$5" seq="$4}'
echo "[backfill-space] rows updated by space backfill (expect 0, file_grant was empty): 演练不重复执行，以 DDL 输出为准"
EOF
} | run_remote STEP4 || exit 1

# ---------- 5. 执行 .2 迁移草案 ----------
step "STEP5 副本执行迁移草案（.2）并检查"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/migrate-r2.sql" 2>&1 || fail "migrate"
MYT "$COPY_DB" < "$RDIR/sql-post-migrate-checks.sql" || fail "checks"
EOF
} | run_remote STEP5 || exit 1

# ---------- 6. 对账 #1 ----------
step "STEP6 对账 #1（预期 0 差异）"
{ prologue
  cat <<'EOF'
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile1"
EOF
} | run_remote STEP6 || exit 1

# ---------- 7. 幂等重跑 ----------
step "STEP7 幂等：重跑 .2（预期 0 行受影响）+ 对账 #2"
{ prologue
  cat <<'EOF'
MYI -vv "$COPY_DB" < "$RDIR/migrate-r2.sql" 2>&1 || fail "migrate-rerun"
MYT "$COPY_DB" < "$RDIR/reconcile.sql" || fail "reconcile2"
echo "[rows] total=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant") legacy=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant WHERE source='legacy'")"
EOF
} | run_remote STEP7 || exit 1

# ---------- 8. 负例：漂移可检出 ----------
step "STEP8 负例（删行→检出；改过期→检出；重跑迁移→只补行不愈字段）"
{ prologue
  cat <<'EOF'
VICTIM_REF=$(MY -N -B "$COPY_DB" -e "SELECT legacy_ref FROM biz_file_grant WHERE source='legacy' ORDER BY id LIMIT 1")
[ -n "$VICTIM_REF" ] || fail "no victim"
ORIG_EXP=$(MY -N -B "$COPY_DB" -e "SELECT COALESCE(expire_at,'NULL') FROM biz_file_grant WHERE legacy_ref='$VICTIM_REF'")
echo "[victim] ref=$VICTIM_REF orig_expire=$ORIG_EXP"
echo "---- 8a 删除该行 ----"
MY "$COPY_DB" -e "DELETE FROM biz_file_grant WHERE legacy_ref='$VICTIM_REF'" || fail "delete"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8b 重跑迁移（应补回 1 行） ----"
MYI -vv "$COPY_DB" < "$RDIR/migrate-r2.sql" 2>&1 || fail "refill"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8c 篡改过期时间 ----"
MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at=DATE_ADD(NOW(), INTERVAL 99 DAY) WHERE legacy_ref='$VICTIM_REF' AND expire_at IS NOT NULL" || fail "tamper1"
MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at=DATE_ADD(NOW(), INTERVAL 99 DAY) WHERE legacy_ref='$VICTIM_REF' AND expire_at IS NULL" || fail "tamper2"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8d 再重跑迁移（不愈字段漂移，仍应 expire_diff=1） ----"
MYI -vv "$COPY_DB" < "$RDIR/migrate-r2.sql" 2>&1 || fail "rerun3"
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "---- 8e 还原过期时间（NULL 则置回 NULL） ----"
if [ "$ORIG_EXP" = "NULL" ]; then
  MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at=NULL WHERE legacy_ref='$VICTIM_REF'" || fail "restore"
else
  MY "$COPY_DB" -e "UPDATE biz_file_grant SET expire_at='$ORIG_EXP' WHERE legacy_ref='$VICTIM_REF'" || fail "restore"
fi
MYT "$COPY_DB" < "$RDIR/reconcile.sql"
echo "[final-rows] legacy=$(MY -N -B -e "SELECT COUNT(*) FROM $COPY_DB.biz_file_grant WHERE source='legacy'")"
EOF
} | run_remote STEP8 || exit 1

step "DRILL DONE（cleanup 由 trap 执行）"
