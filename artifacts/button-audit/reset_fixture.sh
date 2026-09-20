#!/usr/bin/env bash
# 重置一次性审计库 aigicrm_btn_audit = 源库副本 + 四个测试账号的临时口令。
# 只用本地容器 aigicrm-local-mysql; 不触碰 aigicrm 源库内容。
set -euo pipefail

SECRET_DIR=/tmp/aigicrm-btn-audit
PW_FILE="$SECRET_DIR/pw.txt"
[ -f "$PW_FILE" ] || { echo "ERROR: $PW_FILE missing" >&2; exit 2; }
PW="$(cat "$PW_FILE")"

HASH="$(htpasswd -bnBC 10 '' "$PW" | sed 's/^://;s/^\$2y\$/\$2a\$/')"

docker exec aigicrm-local-mysql sh -c '
  set -e
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
    DROP DATABASE IF EXISTS aigicrm_btn_audit;
    CREATE DATABASE aigicrm_btn_audit CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" 2>/dev/null
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers aigicrm 2>/dev/null \
    | mysql -uroot -p"$MYSQL_ROOT_PASSWORD" aigicrm_btn_audit 2>/dev/null
'

docker exec -i aigicrm-local-mysql sh -c 'cat > /tmp/h.txt && chmod 600 /tmp/h.txt' <<< "$HASH"
docker exec aigicrm-local-mysql sh -c '
  H=$(cat /tmp/h.txt)
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" aigicrm_btn_audit -e "
    update sys_user set password=\"$H\", pwd_reset_required=0, status=1, deleted=0
    where id in (1,11,38,43);" 2>/dev/null
  rm -f /tmp/h.txt
'

docker exec aigicrm-local-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -N -B -e "
  select concat(\"tables=\", (select count(*) from information_schema.tables where table_schema=\"aigicrm_btn_audit\"));
  select concat(\"customers=\", (select count(*) from aigicrm_btn_audit.biz_customer));
  select concat(\"audit_accounts=\", (select count(*) from aigicrm_btn_audit.sys_user where id in (1,11,38,43) and pwd_reset_required=0));" 2>/dev/null'
echo "fixture reset OK"
