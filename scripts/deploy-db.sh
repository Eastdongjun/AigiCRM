#!/bin/bash
# 数据库迁移: 生产备份 → 执行迁移脚本 → 结构验证
# 用法: ./deploy-db.sh <迁移SQL路径(本地)>
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

MIGRATION_SQL="${1:?用法: deploy-db.sh <迁移SQL路径>}"
[ -f "$MIGRATION_SQL" ] || { echo "❌ 迁移脚本不存在: $MIGRATION_SQL"; exit 1; }

echo "=== [1/4] 生产数据库备份 ==="
ssh_prod "
  docker exec ycmn-mysql mysqldump -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 --single-transaction ycmn > /root/ycmn/backup-pre-migrate-$(date +%Y%m%d-%H%M%S).sql
  echo '备份完成:'; ls -t /root/ycmn/backup-pre-migrate-*.sql | head -1
"

echo "=== [2/4] 上传迁移脚本 ==="
scp_prod "$MIGRATION_SQL" "${PROD_USER}@${PROD_HOST}:/root/ycmn/migration-current.sql"

echo "=== [3/4] 执行迁移 ==="
ssh_prod "
  echo '迁移前: 表数 \$(docker exec ycmn-mysql mysql -uroot -p"$DB_PASSWORD" -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ycmn'\" 2>/dev/null)'
  # 必须带库名 ycmn（否则 UPDATE/ALTER 报 1046 No database selected）
  OUT=\$(docker exec -i ycmn-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 ycmn < /root/ycmn/migration-current.sql 2>&1)
  if echo \"\$OUT\" | grep -qi 'error'; then
    echo '❌ 迁移执行失败:'; echo \"\$OUT\" | grep -i error | head -5; exit 1
  fi
  [ -n \"\$OUT\" ] && echo \"迁移输出: \$(echo \"\$OUT\" | head -3)\"
  echo '迁移执行完成'
"

echo "=== [4/4] 结构验证 ==="
ssh_prod "
  docker exec ycmn-mysql mysql -uroot -p"$DB_PASSWORD" -N --default-character-set=utf8mb4 -e \"
    SELECT '表数', COUNT(*) FROM information_schema.tables WHERE table_schema='ycmn';
    SELECT '菜单', COUNT(*) FROM ycmn.sys_menu;
    SELECT '客户', COUNT(*) FROM ycmn.biz_customer;
  \" 2>/dev/null
"
check_health
echo "✅ 数据库迁移完成"
