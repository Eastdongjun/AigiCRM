#!/bin/bash
# 权限码字典生成: 从生产 sys_menu + sys_role_menu 生成 docs/permission/permission-dict.md
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV="${1:-prod}"
if [ "$ENV" != "local" ]; then
  source "$SCRIPT_DIR/deploy-lib.sh"
fi
if [ "$ENV" = "local" ]; then
  LABEL="本地"
  DB_HOST="${LOCAL_DB_HOST:-127.0.0.1}"
  DB_PORT="${LOCAL_DB_PORT:-3306}"
  DB_NAME="${LOCAL_DB_NAME:-aigicrm}"
  DB_USER="${LOCAL_DB_USER:-root}"
  DB_PASS="${LOCAL_DB_PASSWORD:-}"
  DB_C="${LOCAL_DB_CONTAINER:-${APP_SLUG:-aigicrm}-mysql}"
  if [ -z "$DB_PASS" ] && [ -n "${LOCAL_DB_PASSWORD_FILE:-}" ] && [ -r "$LOCAL_DB_PASSWORD_FILE" ]; then
    DB_PASS="$(< "$LOCAL_DB_PASSWORD_FILE")"
  fi
  if [ -z "$DB_PASS" ]; then
    echo "错误: local 权限字典生成需要 LOCAL_DB_PASSWORD（仅本机使用，不写入仓库）" >&2
    exit 1
  fi
  query() { docker exec -e "MYSQL_PWD=$DB_PASS" "$DB_C" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N --default-character-set=utf8mb4 -e "$1"; }
elif [ "$ENV" = "prod" ]; then
  SSH_FN=ssh_prod; MYSQL_C=${APP_SLUG:-aigicrm}-mysql; LABEL="生产"; DB_USER=root; DB_PASS="$DB_PASSWORD"
else
  SSH_FN=ssh_test; MYSQL_C=shared-mysql; LABEL="测试"; DB_USER="$TEST_DB_ADMIN_USER"; DB_PASS="$TEST_DB_ADMIN_PASSWORD"
fi
OUT="${OUT:-$SCRIPT_DIR/../docs/permission/permission-dict.md}"  # 默认写入当前源码仓库
mkdir -p "$(dirname "$OUT")"
if [ "$ENV" = "local" ]; then
  query "SELECT m.id, m.menu_name, m.permission, m.menu_type, m.path, m.status FROM \`$DB_NAME\`.sys_menu m WHERE m.permission IS NOT NULL AND m.permission != '' ORDER BY m.permission;" > /tmp/perm-menus.txt
  query "SELECT r.role_name, m.permission FROM \`$DB_NAME\`.sys_role r JOIN \`$DB_NAME\`.sys_role_menu rm ON r.id=rm.role_id JOIN \`$DB_NAME\`.sys_menu m ON rm.menu_id=m.id WHERE m.permission != '' ORDER BY r.id, m.permission;" > /tmp/perm-roles.txt
else
  # 库名不得硬编码（原为 aigicrm）：生产实际库名为 ycmn（见 PROD-RENAME-PLAN §6 同类风险），
  # 测试为 aigicrm。统一从 DB_NAME 取，与 deploy-*.sh 的口径一致。
  RM_DB="${DB_NAME:-aigicrm}"
  "$SSH_FN" "docker exec $MYSQL_C mysql -u"$DB_USER" -p"$DB_PASS" -N --default-character-set=utf8mb4 -e \"SELECT m.id, m.menu_name, m.permission, m.menu_type, m.path, m.status FROM $RM_DB.sys_menu m WHERE m.permission IS NOT NULL AND m.permission != '' ORDER BY m.permission;\" 2>/dev/null" > /tmp/perm-menus.txt
  "$SSH_FN" "docker exec $MYSQL_C mysql -u"$DB_USER" -p"$DB_PASS" -N --default-character-set=utf8mb4 -e \"SELECT r.role_name, m.permission FROM $RM_DB.sys_role r JOIN $RM_DB.sys_role_menu rm ON r.id=rm.role_id JOIN $RM_DB.sys_menu m ON rm.menu_id=m.id WHERE m.permission != '' ORDER BY r.id, m.permission;\" 2>/dev/null" > /tmp/perm-roles.txt
fi
if [ ! -s /tmp/perm-menus.txt ] || [ ! -s /tmp/perm-roles.txt ]; then
  echo "错误: 数据库查询未返回完整的权限字典结果，文档生成未完成" >&2
  exit 1
fi
python3 "$SCRIPT_DIR/gen-perm.py" "$OUT" "$LABEL"
