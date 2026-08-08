#!/bin/bash
# 权限码字典生成: 从生产 sys_menu + sys_role_menu 生成 docs/permission/permission-dict.md
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"
ENV="${1:-prod}"
if [ "$ENV" = "prod" ]; then SSH_FN=ssh_prod; MYSQL_C=ycmn-mysql; LABEL="生产"; else SSH_FN=ssh_test; MYSQL_C=shared-mysql; LABEL="测试"; fi
OUT="/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/docs/permission/permission-dict.md"
mkdir -p "$(dirname "$OUT")"
"$SSH_FN" "docker exec $MYSQL_C mysql -uroot -p"$DB_PASSWORD" -N --default-character-set=utf8mb4 -e \"SELECT m.id, m.menu_name, m.permission, m.menu_type, m.path FROM ycmn.sys_menu m WHERE m.permission IS NOT NULL AND m.permission != '' ORDER BY m.permission;\" 2>/dev/null" > /tmp/perm-menus.txt
"$SSH_FN" "docker exec $MYSQL_C mysql -uroot -p"$DB_PASSWORD" -N --default-character-set=utf8mb4 -e \"SELECT r.role_name, m.permission FROM ycmn.sys_role r JOIN ycmn.sys_role_menu rm ON r.id=rm.role_id JOIN ycmn.sys_menu m ON rm.menu_id=m.id WHERE m.permission != '' ORDER BY r.id, m.permission;\" 2>/dev/null" > /tmp/perm-roles.txt
python3 "$SCRIPT_DIR/gen-perm.py" "$OUT" "$LABEL"
