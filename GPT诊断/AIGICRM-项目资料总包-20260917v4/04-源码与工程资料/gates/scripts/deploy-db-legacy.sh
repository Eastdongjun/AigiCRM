#!/bin/bash
# 应急单文件迁移（2026-08-09 从 deploy-db.sh 拆出，Flyway 化后旧路径保留作应急通道）
# 用法: ./deploy-db-legacy.sh <迁移SQL路径(本地)>
# ⚠️ 该通道不经 Flyway 版本化，仅应急使用；常规迁移走 ./deploy-db.sh（Flyway 迁移目录）
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

MIGRATION_SQL="${1:?用法: deploy-db.sh <迁移SQL路径>}"
[ -f "$MIGRATION_SQL" ] || { echo "❌ 迁移脚本不存在: $MIGRATION_SQL"; exit 1; }

echo "=== [1/4] 生产数据库备份 ==="
ssh_prod "
  docker exec ${APP_SLUG}-mysql mysqldump -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 --single-transaction $DB_NAME > $DEPLOY_DIR/backup-pre-migrate-$(date +%Y%m%d-%H%M%S).sql
  echo '备份完成:'; ls -t $DEPLOY_DIR/backup-pre-migrate-*.sql | head -1
"

echo "=== [2/4] 上传迁移脚本 ==="
scp_prod "$MIGRATION_SQL" "${PROD_USER}@${PROD_HOST}:$DEPLOY_DIR/migration-current.sql"

echo "=== [3/4] 执行迁移 ==="
ssh_prod "
  echo '迁移前: 表数 \$(docker exec ${APP_SLUG}-mysql mysql -uroot -p<密码已掩码> -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'\" 2>/dev/null)'
  # 必须带库名 $DB_NAME（否则 UPDATE/ALTER 报 1046 No database selected）
  OUT=\$(docker exec -i ${APP_SLUG}-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 $DB_NAME < $DEPLOY_DIR/migration-current.sql 2>&1)
  if echo \"\$OUT\" | grep -qi 'error'; then
    echo '❌ 迁移执行失败:'; echo \"\$OUT\" | grep -i error | head -5; exit 1
  fi
  [ -n \"\$OUT\" ] && echo \"迁移输出: \$(echo \"\$OUT\" | head -3)\"
  echo '迁移执行完成'
"

echo "=== [4/4] 结构验证 ==="
ssh_prod "
  docker exec ${APP_SLUG}-mysql mysql -uroot -p"$DB_PASSWORD" -N --default-character-set=utf8mb4 -e \"
    SELECT '表数', COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';
    SELECT '菜单', COUNT(*) FROM $DB_NAME.sys_menu;
    SELECT '客户', COUNT(*) FROM $DB_NAME.biz_customer;
  \" 2>/dev/null
"
check_health
echo "✅ 数据库迁移完成"
