#!/bin/bash
# 生产行对账（只读）：reconcile SQL 四数硬门（P3）
# 通道=ssh_prod + ycmn-mysql 容器内口令派生（env 注入不打印）；任一不满足 exit 1
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
source "$REPO/scripts/deploy-lib.sh" || exit 1
RECONCILE_SQL="$REPO/docs/design/phase-a/reconcile-file-grants.sql"

RESULT=$(ssh_prod_once "bash -s" <<REMOTE
set -Eeuo pipefail
PW="\$(docker exec ycmn-mysql sh -c 'printf %s "\$MYSQL_ROOT_PASSWORD"')"
[ -n "\$PW" ] || { echo "PASS_FAIL"; exit 2; }
docker exec -i -e MYSQL_PWD="\$PW" ycmn-mysql mysql -uroot --default-character-set=utf8mb4 -N -B ycmn <<'SQL'
$(cat "$RECONCILE_SQL")
SQL
REMOTE
) || { echo "❌ 通道/执行失败" >&2; exit 2; }

MISSING=$(printf '%s\n' "$RESULT" | awk '$1=="missing_in_new"{print $2}')
EXTRA=$(printf '%s\n' "$RESULT"   | awk '$1=="extra_in_new"{print $2}')
EXPIRE=$(printf '%s\n' "$RESULT" | awk '$1=="expire_diff"{print $2}')
OLD_R=$(printf '%s\n' "$RESULT"  | awk '$1=="old_expanded_rows"{print $2}')
NEW_R=$(printf '%s\n' "$RESULT"  | awk '$1=="new_legacy_rows"{print $2}')
echo "PROD reconcile: missing=$MISSING extra=$EXTRA expire_diff=$EXPIRE old=$OLD_R new=$NEW_R"
for v in "$MISSING" "$EXTRA" "$EXPIRE" "$OLD_R" "$NEW_R"; do
  [ -n "$v" ] || { echo "❌ 输出不完整"; exit 2; }
done
if [ "$MISSING" = "0" ] && [ "$EXTRA" = "0" ] && [ "$EXPIRE" = "0" ] && [ "$OLD_R" = "$NEW_R" ]; then
  echo "✅ 生产行对账 0 差异（$OLD_R=$NEW_R）——硬门通过"
  exit 0
fi
echo "❌ 生产对账有差异——停线回滚（恢复 /data/a2-window-premigrate-*.sql）" >&2
exit 1
