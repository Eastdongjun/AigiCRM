#!/bin/bash
# 数据字典生成: 从 MySQL information_schema 自动生成 docs/database/data-dictionary.md
# 用法: ./gen-data-dictionary.sh [prod|test|local]
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
    echo "错误: local 数据字典生成需要 LOCAL_DB_PASSWORD（仅本机使用，不写入仓库）" >&2
    exit 1
  fi
  query() {
    docker exec -e "MYSQL_PWD=$DB_PASS" "$DB_C" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N --default-character-set=utf8mb4 -e "$1"
  }
elif [ "$ENV" = "prod" ]; then
  SSH_FN=ssh_prod; MYSQL_C=${APP_SLUG:-aigicrm}-mysql; LABEL="生产 39.99.35.218"; DB_USER=root; DB_PASS="$DB_PASSWORD"
else
  SSH_FN=ssh_test; MYSQL_C=shared-mysql; LABEL="测试 111.229.37.220"; DB_USER="${TEST_DB_ADMIN_USER:-}"; DB_PASS="${TEST_DB_ADMIN_PASSWORD:-}"
  if [ -z "$DB_USER" ] || [ -z "$DB_PASS" ]; then
    echo "错误: 测试数据字典生成需要 TEST_DB_ADMIN_USER 和 TEST_DB_ADMIN_PASSWORD（仅 SSH 测试账号不足）" >&2
    exit 1
  fi
fi
OUT="${OUT:-$SCRIPT_DIR/../docs/database/data-dictionary.md}"
mkdir -p "$(dirname "$OUT")"

echo "=== 从 $LABEL 提取表结构 ==="
if [ "$ENV" = "local" ]; then
  query "SELECT t.table_name, t.table_comment FROM information_schema.tables t WHERE t.table_schema='$DB_NAME' ORDER BY t.table_name;" > /tmp/dict-tables.txt
  query "SELECT c.table_name, c.column_name, c.column_type, c.is_nullable, c.column_default, c.column_key, c.column_comment FROM information_schema.columns c WHERE c.table_schema='$DB_NAME' ORDER BY c.table_name, c.ordinal_position;" > /tmp/dict-cols.txt
else
  # 库名不得硬编码（原为 aigicrm）：生产实际库名是 ycmn（改名窗口未开），
  # 测试为 aigicrm_prod_20261016。硬编码会让 remote 模式查错库 ⇒ 查询返回空 ⇒ 生成失败。
  # 【修复 2026-11-11】remote 分支嵌套引号经 ssh 被吃致 SQL 断裂(实测 prod/test 双分支空结果);
  # 改 stdin 直灌(printf SQL | ssh docker exec -i mysql), 密码用容器内 env MYSQL_ROOT_PASSWORD
  # (deploy.env 的 DB_NAME/口令与容器实况并非总一致), 默认库名按环境给。
  RM_DB="${DB_NAME:-$([ "$ENV" = "prod" ] && echo ycmn || echo aigicrm_prod_20261016)}"
  SQL_TABLES="SELECT t.table_name, t.table_comment FROM information_schema.tables t WHERE t.table_schema='$RM_DB' ORDER BY t.table_name;"
  SQL_COLS="SELECT c.table_name, c.column_name, c.column_type, c.is_nullable, c.column_default, c.column_key, c.column_comment FROM information_schema.columns c WHERE c.table_schema='$RM_DB' ORDER BY c.table_name, c.ordinal_position;"
  printf '%s\n' "$SQL_TABLES" | "$SSH_FN" "docker exec -i $MYSQL_C sh -c 'mysql -uroot -p\$MYSQL_ROOT_PASSWORD -N --default-character-set=utf8mb4'" 2>/dev/null > /tmp/dict-tables.txt
  printf '%s\n' "$SQL_COLS" | "$SSH_FN" "docker exec -i $MYSQL_C sh -c 'mysql -uroot -p\$MYSQL_ROOT_PASSWORD -N --default-character-set=utf8mb4'" 2>/dev/null > /tmp/dict-cols.txt
fi
if [ ! -s /tmp/dict-tables.txt ] || [ ! -s /tmp/dict-cols.txt ]; then
  echo "错误: 数据库查询未返回完整的数据字典结果，文档生成未完成" >&2
  exit 1
fi
echo "表数: $(wc -l < /tmp/dict-tables.txt) | 字段行数: $(wc -l < /tmp/dict-cols.txt)"

echo "=== 生成 markdown ==="
python3 "$SCRIPT_DIR/gen-dict.py" "$OUT" "$LABEL"
