#!/bin/bash
# 应急单文件迁移（2026-08-09 从 deploy-db.sh 拆出，Flyway 化后旧路径保留作应急通道）
# 用法: ./deploy-db-legacy.sh <迁移SQL路径(本地)>
# ⚠️ 该通道不经 Flyway 版本化，仅应急使用；常规迁移走 ./deploy-db.sh（Flyway 迁移目录）
#
# 【D2 2026-10-21 审计修复】远端片段此前无 set -e：mysqldump 失败仍打印"备份完成"，
# 0 字节文件成为"最新备份"挤掉上一次真备份，随后仍以 root 执行未版本化 SQL。现修复：
#   - 远端脚本 set -Eeuo pipefail；备份先写 *.tmp，校验(退出码/非空/CREATE TABLE>0)通过才 mv，失败 rm + exit 1；
#   - 迁移执行前断言"本次"备份文件存在且非空；
#   - root 口令一律远端从容器环境 MYSQL_ROOT_PASSWORD 读取（同 deploy-db.sh:39-47 的做法），
#     不再把 $DB_PASSWORD 拼进本地 ssh argv / 远端 docker argv（D3）；
#   - 补 assert_prod_env_pinned（应急通道与常规通道同等生产门禁）。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

assert_prod_env_pinned
# 【D6】应急迁移同样在破坏性动作(备份/root 执行 SQL)前做只读 TLS 预检（理由同 deploy-db.sh）
check_prod_tls

MIGRATION_SQL="${1:?用法: deploy-db.sh <迁移SQL路径>}"
[ -f "$MIGRATION_SQL" ] || { echo "❌ 迁移脚本不存在: $MIGRATION_SQL"; exit 1; }

RUN_TS="$(date +%Y%m%d-%H%M%S)"
BACKUP_SQL="$DEPLOY_DIR/backup-pre-migrate-$RUN_TS.sql"

# 把本地变量以 %q 转义注入远端脚本；远端脚本经 stdin(bash -s)传递，任何口令都不进 ssh argv
emit_var() { printf '%s=%q\n' "$1" "$2"; }

echo "=== [1/4] 生产数据库备份 ==="
{
  echo 'set -Eeuo pipefail'
  emit_var MYSQL_C "${APP_SLUG}-mysql"
  emit_var DB_NAME "$DB_NAME"
  emit_var BK "$BACKUP_SQL"
  cat <<'REMOTE'
# root 口令只从容器环境读取（不进 ssh/docker argv、不落盘）；读不到即失败，不回退本地 DB_PASSWORD
PW="$(docker exec "$MYSQL_C" sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
[ -n "$PW" ] || { echo "❌ 无法从容器 $MYSQL_C 读取 MYSQL_ROOT_PASSWORD" >&2; exit 1; }

# 备份先写 .tmp：mysqldump 中途失败/磁盘满不产生 0 字节"最新备份"，校验通过才 mv 成正式文件
if ! docker exec -e MYSQL_PWD="$PW" "$MYSQL_C" mysqldump -uroot --default-character-set=utf8mb4 --single-transaction "$DB_NAME" > "$BK.tmp"; then
  rm -f "$BK.tmp"
  echo "❌ mysqldump 失败, 中止(不得依赖旧备份继续迁移)" >&2
  exit 1
fi
[ -s "$BK.tmp" ] || { rm -f "$BK.tmp"; echo "❌ 备份文件为空, 中止" >&2; exit 1; }
CT="$(grep -c '^CREATE TABLE' "$BK.tmp" || true)"
case "$CT" in ''|*[!0-9]*) rm -f "$BK.tmp"; echo "❌ 无法统计备份内 CREATE TABLE, 中止" >&2; exit 1 ;; esac
[ "$CT" -gt 0 ] || { rm -f "$BK.tmp"; echo "❌ 备份不像 SQL dump(无 CREATE TABLE), 中止" >&2; exit 1; }
mv -f "$BK.tmp" "$BK"
echo "备份完成: $BK (CREATE TABLE=$CT)"
REMOTE
} | ssh_prod_once "bash -s"

echo "=== [2/4] 上传迁移脚本 ==="
scp_prod "$MIGRATION_SQL" "${PROD_USER}@${PROD_HOST}:$DEPLOY_DIR/migration-current.sql"

echo "=== [3/4] 执行迁移 ==="
{
  echo 'set -Eeuo pipefail'
  emit_var MYSQL_C "${APP_SLUG}-mysql"
  emit_var DB_NAME "$DB_NAME"
  emit_var DEPLOY_DIR "$DEPLOY_DIR"
  emit_var BK "$BACKUP_SQL"
  cat <<'REMOTE'
# 迁移前断言"本次"备份仍在且非空（备份被误删/写错路径时不得继续）
[ -s "$BK" ] || { echo "❌ 迁移前断言失败: 本次备份不存在/为空: $BK" >&2; exit 1; }
PW="$(docker exec "$MYSQL_C" sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
[ -n "$PW" ] || { echo "❌ 无法从容器 $MYSQL_C 读取 MYSQL_ROOT_PASSWORD" >&2; exit 1; }
echo "迁移前: 表数 $(docker exec -e MYSQL_PWD="$PW" "$MYSQL_C" mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'" 2>/dev/null)"
# 必须带库名 $DB_NAME（否则 UPDATE/ALTER 报 1046 No database selected）
if ! OUT="$(docker exec -i -e MYSQL_PWD="$PW" "$MYSQL_C" mysql -uroot --default-character-set=utf8mb4 "$DB_NAME" < "$DEPLOY_DIR/migration-current.sql" 2>&1)"; then
  echo "❌ 迁移执行失败(mysql 非零退出):" >&2
  printf '%s\n' "$OUT" | head -5 >&2
  exit 1
fi
if printf '%s\n' "$OUT" | grep -qi 'error'; then
  echo "❌ 迁移执行失败:" >&2
  printf '%s\n' "$OUT" | grep -i error | head -5 >&2
  exit 1
fi
[ -n "$OUT" ] && echo "迁移输出: $(printf '%s\n' "$OUT" | head -3)"
echo "迁移执行完成"
REMOTE
} | ssh_prod_once "bash -s"

echo "=== [4/4] 结构验证 ==="
{
  echo 'set -Eeuo pipefail'
  emit_var MYSQL_C "${APP_SLUG}-mysql"
  emit_var DB_NAME "$DB_NAME"
  cat <<'REMOTE'
PW="$(docker exec "$MYSQL_C" sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
[ -n "$PW" ] || { echo "❌ 无法从容器 $MYSQL_C 读取 MYSQL_ROOT_PASSWORD" >&2; exit 1; }
docker exec -e MYSQL_PWD="$PW" "$MYSQL_C" mysql -uroot -N --default-character-set=utf8mb4 -e "
  SELECT '表数', COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';
  SELECT '菜单', COUNT(*) FROM $DB_NAME.sys_menu;
  SELECT '客户', COUNT(*) FROM $DB_NAME.biz_customer;
" 2>/dev/null
REMOTE
} | ssh_prod_once "bash -s"
check_health
echo "✅ 数据库迁移完成(应急通道)"
