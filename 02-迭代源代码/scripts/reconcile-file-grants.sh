#!/bin/bash
# ============================================================
# 【A2 2026-11-18，用户拍板 #3】biz_folder_type_default ↔ biz_file_grant 行对账包装脚本
#
# 作用：在测试服 MySQL（shared-mysql）上执行 docs/design/phase-a/reconcile-file-grants.sql
#       （只读），判四数——missing_in_new=0 ∧ extra_in_new=0 ∧ expire_diff=0 ∧
#       old_expanded_rows=new_legacy_rows——任一不满足退出非 0（手工/CI 均可复用）。
#
# 通道：与 deploy-db-test.sh 同款（ssh_test + AIGICRM_TEST_ONLY，不触生产口令）；
#       MySQL 口令从远端容器 Env 派生（MYSQL_ROOT_PASSWORD，远端解析不回传不落盘），
#       经 docker exec -e MYSQL_PWD env 形式注入（不进 argv）。
# 只读保证：本脚本只上传并执行 SELECT（reconcile SQL 全文为 SELECT/UNION），不写任何表。
#
# 用法:
#   ./reconcile-file-grants.sh                 # 对测试快照库 ${TEST_DB_NAME:-aigicrm_prod_20261016}
#   ./reconcile-file-grants.sh aigicrm_xxx     # 对指定测试服库（如演练副本库）
#   ./reconcile-file-grants.sh --prod          # 生产 ycmn 库（观察期例行对账; 只读, 需部署 env 生产通道）
# 退出码: 0=对账 0 差异; 1=有差异; 2=通道/环境错误
# ============================================================
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODE="test"
if [ "${1:-}" = "--prod" ]; then MODE="prod"; shift; fi
TARGET_DB="${1:-${TEST_DB_NAME:-aigicrm_prod_20261016}}"
[ "$MODE" = "prod" ] && TARGET_DB="ycmn"
RECONCILE_SQL="$SCRIPT_DIR/../docs/design/phase-a/reconcile-file-grants.sql"
CT="${TEST_DB_CONTAINER:-shared-mysql}"

[ -f "$RECONCILE_SQL" ] || { echo "❌ 缺少 $RECONCILE_SQL" >&2; exit 2; }
grep -qiE '^\s*(INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|TRUNCATE)' "$RECONCILE_SQL" \
  && { echo "❌ reconcile SQL 含写语句，拒绝执行（只读契约被破坏）" >&2; exit 2; }

if [ "$MODE" = "prod" ]; then
  # 生产通道：完整 deploy-lib（含 PROD 校验），只读 SELECT
  # shellcheck disable=SC1090
  source "$SCRIPT_DIR/deploy-lib.sh"
  CT="ycmn-mysql"
else
  export AIGICRM_TEST_ONLY=1
  # shellcheck disable=SC1090
  source "$SCRIPT_DIR/deploy-lib.sh"
fi

# 四数断言（远端跑 SQL→本行原样回传→本地判定；结果行顺序与 SQL UNION ALL 一致）
if [ "$MODE" = "prod" ]; then
  RESULT=$(ssh_prod_once "bash -s" <<REMOTE
set -Eeuo pipefail
TARGET_DB="$TARGET_DB"
DB_ADMIN_PASS="\$(docker exec ycmn-mysql sh -c 'printf %s "\$MYSQL_ROOT_PASSWORD"')"
[ -n "\$DB_ADMIN_PASS" ] || { echo "PASS_DERIVE_FAIL"; exit 2; }
docker exec -i -e MYSQL_PWD="\$DB_ADMIN_PASS" ycmn-mysql mysql -uroot --default-character-set=utf8mb4 -N -B "\$TARGET_DB" <<'SQL'
$(cat "$RECONCILE_SQL")
SQL
REMOTE
  ) || { echo "❌ 通道/执行失败" >&2; exit 2; }
else
  RESULT=$(ssh_test_once "bash -s" <<REMOTE
set -Eeuo pipefail
CT="$CT"
TARGET_DB="$TARGET_DB"
DB_ADMIN_PASS="\$(docker inspect "\$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-')"
[ -n "\$DB_ADMIN_PASS" ] || { echo "PASS_DERIVE_FAIL"; exit 2; }
docker exec -i -e MYSQL_PWD="\$DB_ADMIN_PASS" "\$CT" mysql -uroot --default-character-set=utf8mb4 -N -B "\$TARGET_DB" <<'SQL'
$(cat "$RECONCILE_SQL")
SQL
REMOTE
  ) || { echo "❌ 通道/执行失败" >&2; exit 2; }
fi

MISSING=$(printf '%s\n' "$RESULT" | awk '$1=="missing_in_new"{print $2}')
EXTRA=$(printf '%s\n' "$RESULT"   | awk '$1=="extra_in_new"{print $2}')
EXPIRE=$(printf '%s\n' "$RESULT" | awk '$1=="expire_diff"{print $2}')
OLD_ROWS=$(printf '%s\n' "$RESULT" | awk '$1=="old_expanded_rows"{print $2}')
NEW_ROWS=$(printf '%s\n' "$RESULT" | awk '$1=="new_legacy_rows"{print $2}')

echo "库=$TARGET_DB  missing_in_new=${MISSING:-?}  extra_in_new=${EXTRA:-?}  expire_diff=${EXPIRE:-?}  old_expanded=${OLD_ROWS:-?}  new_legacy=${NEW_ROWS:-?}"
for v in "$MISSING" "$EXTRA" "$EXPIRE" "$OLD_ROWS" "$NEW_ROWS"; do
  [ -n "$v" ] || { echo "❌ 对账输出不完整（期望 5 行指标）" >&2; exit 2; }
done

if [ "$MISSING" = "0" ] && [ "$EXTRA" = "0" ] && [ "$EXPIRE" = "0" ] && [ "$OLD_ROWS" = "$NEW_ROWS" ]; then
  echo "✅ 行对账 0 差异（${OLD_ROWS}=${NEW_ROWS}）"
  exit 0
fi
echo "❌ 行对账有差异（见上）——禁止切换 dual→new" >&2
exit 1
