#!/bin/bash
# 数据库迁移（2026-08-09 Flyway 化）: 生产备份 → Flyway migrate → 结构验证
#
# 用法:
#   ./deploy-db.sh              # 迁移 src/main/resources/db/migration 目录（Flyway 版本化）
#   ./deploy-db.sh <sql文件>    # 兼容旧用法: 直接执行单个 SQL（非 Flyway，仅应急）
#
# Flyway 说明:
#   - 迁移在服务器端用 docker flyway/flyway 镜像执行（Mac 无法直连 DB 端口，数据库访问必须经 SSH）
#   - 连接生产 ycmn-mysql 容器（root，经 ~/.ycmn-deploy.env 注入 DB_PASSWORD）
#   - 迁移目录仅 V1__baseline.sql(2026-08-11 重建的生产 schema 快照,mysqldump 自带 FOREIGN_KEY_CHECKS=0,
#     空库可直接加载; 旧 V1-V9 已归档 sql/migration/archive/)。V1 为唯一 schema 真源。
#   - 生产应用账号 ycmn_app 无 DDL 权限，迁移仅限本脚本以 root 执行
#   - Flyway 版本锁定 9.22.3(与 pom flyway-core 一致), 不用 latest 保证可复现
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

MIGRATION_DIR="${1:-$SCRIPT_DIR/../ycmn-backend/src/main/resources/db/migration}"

# 兼容旧用法: 传了 .sql 文件则用旧方式直接执行（应急通道，不走 Flyway）
if [ -f "$MIGRATION_DIR" ] && [[ "$MIGRATION_DIR" == *.sql ]]; then
  echo "⚠️ 检测到单 SQL 文件参数 —— 使用旧版直接执行方式（应急，不经 Flyway 版本化）"
  bash "$SCRIPT_DIR/deploy-db-legacy.sh" "$MIGRATION_DIR"
  exit $?
fi

[ -d "$MIGRATION_DIR" ] || { echo "❌ 迁移目录不存在: $MIGRATION_DIR"; exit 1; }

echo "=== [1/4] 生产数据库备份 ==="
ssh_prod "
  docker exec -e MYSQL_PWD=\"$DB_PASSWORD\" ycmn-mysql mysqldump -uroot --default-character-set=utf8mb4 --single-transaction ycmn > /root/ycmn/backup-pre-migrate-$(date +%Y%m%d-%H%M%S).sql
  echo '备份完成:'; ls -t /root/ycmn/backup-pre-migrate-*.sql | head -1
"

echo "=== [2/4] 上传迁移目录到生产 ==="
ssh_prod "rm -rf /root/ycmn/flyway-sql && mkdir -p /root/ycmn/flyway-sql"
# 逐个上传迁移文件（tar 管道避免 scp 目录传输的权限问题）
# --exclude='._*' 排除 macOS AppleDouble 元数据文件(会被 Flyway 识别为非约定迁移并告警)
tar czf - --exclude='._*' --exclude='.DS_Store' -C "$MIGRATION_DIR" . | ssh_prod "tar xzf - -C /root/ycmn/flyway-sql"
echo "迁移文件: $(ssh_prod "ls /root/ycmn/flyway-sql/ | wc -l") 个"

echo "=== [3/4] Flyway migrate ==="
ssh_prod "
  TABLE_CNT=\$(docker exec -e MYSQL_PWD=\"$DB_PASSWORD\" ycmn-mysql mysql -uroot -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ycmn'\" 2>/dev/null)
  echo \"迁移前: 表数 \$TABLE_CNT\"
  OUT=\$(docker run --rm --network ycmn_default -v /root/ycmn/flyway-sql:/flyway/sql \
    flyway/flyway:9.22.3-alpine \
    -url='jdbc:mysql://ycmn-mysql:3306/ycmn?useSSL=false&allowPublicKeyRetrieval=true' \
    -user=root -password=\"$DB_PASSWORD\" \
    -baselineOnMigrate=true \
    migrate 2>&1) || true
  if echo \"\$OUT\" | grep -qi 'error\|failed'; then
    echo '❌ Flyway 迁移失败:'; echo \"\$OUT\" | grep -i 'error\|failed' | head -5; exit 1
  fi
  echo \"\$OUT\" | grep -E 'Migrating|Successfully|Version|Current version' | head -10
  echo '迁移执行完成'
"

echo "=== [4/4] 结构验证 ==="
ssh_prod "
  docker exec -e MYSQL_PWD="$DB_PASSWORD" ycmn-mysql mysql -uroot -N --default-character-set=utf8mb4 -e \"
    SELECT '表数', COUNT(*) FROM information_schema.tables WHERE table_schema='ycmn';
    SELECT '菜单', COUNT(*) FROM ycmn.sys_menu;
    SELECT '客户', COUNT(*) FROM ycmn.biz_customer;
    SELECT 'flyway记录', COUNT(*) FROM ycmn.flyway_schema_history;
  \" 2>/dev/null
"
check_health
echo "✅ 数据库迁移完成"
