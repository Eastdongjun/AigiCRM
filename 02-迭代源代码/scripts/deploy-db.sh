#!/bin/bash
# 数据库迁移（2026-08-09 Flyway 化）: 生产备份 → Flyway migrate → 结构验证
#
# 用法:
#   ./deploy-db.sh              # 迁移 src/main/resources/db/migration 目录（Flyway 版本化）
#   ./deploy-db.sh <sql文件>    # 兼容旧用法: 直接执行单个 SQL（非 Flyway，仅应急）
#
# Flyway 说明:
#   - 迁移在服务器端用 docker flyway/flyway 镜像执行（Mac 无法直连 DB 端口，数据库访问必须经 SSH）
#   - 连接生产 ${APP_SLUG}-mysql 容器（root，经部署 env 注入 DB_PASSWORD）
#   - 迁移目录仅 V1__baseline.sql(2026-08-11 重建的生产 schema 快照,mysqldump 自带 FOREIGN_KEY_CHECKS=0,
#     空库可直接加载; 旧 V1-V9 已归档 sql/migration/archive/)。V1 为唯一 schema 真源。
#   - 生产应用账号无 DDL 权限，迁移仅限本脚本以 root 执行
#   - Flyway 版本锁定 9.22.3(与 pom flyway-core 一致), 不用 latest 保证可复现
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

# 【2026-09-12】生产环境前置硬门禁（见 deploy-lib.sh: assert_prod_env_pinned）
assert_prod_env_pinned
check_deploy_arch

MIGRATION_DIR="${1:-$SCRIPT_DIR/../aigicrm-backend/src/main/resources/db/migration}"

# 兼容旧用法: 传了 .sql 文件则用旧方式直接执行（应急通道，不走 Flyway）
if [ -f "$MIGRATION_DIR" ] && [[ "$MIGRATION_DIR" == *.sql ]]; then
  echo "⚠️ 检测到单 SQL 文件参数 —— 使用旧版直接执行方式（应急，不经 Flyway 版本化）"
  bash "$SCRIPT_DIR/deploy-db-legacy.sh" "$MIGRATION_DIR"
  exit $?
fi

[ -d "$MIGRATION_DIR" ] || { echo "❌ 迁移目录不存在: $MIGRATION_DIR"; exit 1; }

echo "=== [1/4] 生产数据库备份 ==="
# 【DeepSeek四审 P0-2 修复 2026-09-22】旧写法 mysqldump 失败后分号仍执行 ls, ssh 返回 ls 的 0 = 假绿,
# 旧备份可掩盖本次失败而迁移照跑。改为: 记录本次目标文件 → mysqldump 显式判错 → 校验本次文件存在且非空
ssh_prod "
  BK=$DEPLOY_DIR/backup-pre-migrate-\$(date +%Y%m%d-%H%M%S).sql
  if ! docker exec -e MYSQL_PWD=\"$DB_PASSWORD\" ${APP_SLUG}-mysql mysqldump -uroot --default-character-set=utf8mb4 --single-transaction $DB_NAME > \"\$BK\"; then
    echo '❌ mysqldump 失败, 中止(不得依赖旧备份继续迁移)'; exit 1
  fi
  if [ ! -s \"\$BK\" ]; then echo '❌ 备份文件为空, 中止'; exit 1; fi
  echo '备份完成:'; ls -la \"\$BK\"
"

echo "=== [2/4] 上传迁移目录到生产 ==="
ssh_prod "rm -rf $DEPLOY_DIR/flyway-sql && mkdir -p $DEPLOY_DIR/flyway-sql"
# 逐个上传迁移文件（tar 管道避免 scp 目录传输的权限问题）
# macOS AppleDouble 元数据: BSD tar 的 --exclude 按 './' 前缀全路径匹配, 需 '*/._*' 才生效;
# 上传后再远端兜底删一遍(2026-08-15 实测 '._*' 写法失效导致 ._V*.sql 混入 flyway-sql)
tar czf - --exclude='*/._*' --exclude='.DS_Store' -C "$MIGRATION_DIR" . | ssh_prod "tar xzf - -C $DEPLOY_DIR/flyway-sql"
ssh_prod "find $DEPLOY_DIR/flyway-sql -name '._*' -delete 2>/dev/null; true"
MIG_CNT=$(ssh_prod "ls $DEPLOY_DIR/flyway-sql/*.sql 2>/dev/null | wc -l" | tr -d '[:space:]')
echo "迁移文件: $MIG_CNT 个"
[ "${MIG_CNT:-0}" -gt 0 ] || { echo '❌ 迁移文件上传失败(0 个 .sql) —— 中止, 避免 flyway 空跑后误报成功'; exit 1; }

echo "=== [3/4] Flyway repair + migrate ==="
# 2026-08-15 审计修复(生产 flyway 历史实测状态):
#   ① 历史含 V2-V11 但仓库已归档/缺失对应文件 → validate 报 applied not resolved, 必须 -ignoreMigrationPatterns='*:missing'
#   ② V12-V14 为手工登记的 checksum=0 与本地文件不符 → repair 按本地文件重算回写
#   ③ repair 前先做同样 ignore 处理, 否则 repair 也会死在 validate 阶段
# 2026-09-11 加固(防"静默空跑"):
#   ④ -validateMigrationNaming=true: 默认 false 时非法文件名的迁移只打一行 warning 然后**不执行**,
#      migrate 仍退出 0 —— 实测 V2026-09-11-v1__x.sql 这种连字符版本号就是这样被静默跳过的
#   ⑤ 上传后断言 .sql 数量 > 0, 否则 flyway 报 "No migrations found" 也算成功
#   ⑥ 失败判定补上 "did not follow the filename convention" / "No migrations found" 两种伪成功输出
ssh_prod "
  TABLE_CNT=\$(docker exec -e MYSQL_PWD=\"$DB_PASSWORD\" ${APP_SLUG}-mysql mysql -uroot -N -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'\" 2>/dev/null)
  echo \"迁移前: 表数 \$TABLE_CNT\"
  REPAIR=\$(docker run --rm --network ${APP_SLUG}_default -v $DEPLOY_DIR/flyway-sql:/flyway/sql \
    flyway/flyway:9.22.3-alpine \
    -url='jdbc:mysql://${APP_SLUG}-mysql:3306/$DB_NAME?characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true' \
    -user=root -password=\"$DB_PASSWORD\" \
    -validateMigrationNaming=true \
    -ignoreMigrationPatterns='*:missing' \
    repair 2>&1) || true
  echo \"\$REPAIR\" | grep -E 'Repairing|Successfully repaired|not necessary' | head -6
  OUT=\$(docker run --rm --network ${APP_SLUG}_default -v $DEPLOY_DIR/flyway-sql:/flyway/sql \
    flyway/flyway:9.22.3-alpine \
    -url='jdbc:mysql://${APP_SLUG}-mysql:3306/$DB_NAME?characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true' \
    -user=root -password=\"$DB_PASSWORD\" \
    -validateMigrationNaming=true \
    -baselineOnMigrate=true \
    -ignoreMigrationPatterns='*:missing' \
    migrate 2>&1) || true
  if echo \"\$OUT\" | grep -qiE 'error|failed|did not follow the filename convention|No migrations found'; then
    echo '❌ Flyway 迁移失败:'; echo \"\$OUT\" | grep -iE 'error|failed|did not follow the filename convention|No migrations found' | head -5; exit 1
  fi
  echo \"\$OUT\" | grep -E 'Migrating|Successfully|Version|Current version' | head -10
  echo '迁移执行完成'
"

echo "=== [4/4] 结构验证 ==="
ssh_prod "
  docker exec -e MYSQL_PWD="$DB_PASSWORD" ${APP_SLUG}-mysql mysql -uroot -N --default-character-set=utf8mb4 -e \"
    SELECT '表数', COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';
    SELECT '菜单', COUNT(*) FROM $DB_NAME.sys_menu;
    SELECT '客户', COUNT(*) FROM $DB_NAME.biz_customer;
    SELECT 'flyway记录', COUNT(*) FROM $DB_NAME.flyway_schema_history;
  \" 2>/dev/null
"
check_health
echo "✅ 数据库迁移完成"
