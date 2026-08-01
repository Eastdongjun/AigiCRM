#!/bin/bash
# 数据字典生成: 从 MySQL information_schema 自动生成 docs/database/data-dictionary.md
# 用法: ./gen-data-dictionary.sh [prod|test]
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

ENV="${1:-prod}"
if [ "$ENV" = "prod" ]; then
  SSH_FN=ssh_prod; MYSQL_C=ycmn-mysql; LABEL="生产 39.99.35.218"
else
  SSH_FN=ssh_test; MYSQL_C=shared-mysql; LABEL="测试 111.229.37.220"
fi
OUT="/Users/donglai/东来/01-Projects/01-aigicrm/04-源码/docs/database/data-dictionary.md"
mkdir -p "$(dirname "$OUT")"

echo "=== 从 $LABEL 提取表结构 ==="
"$SSH_FN" "docker exec $MYSQL_C mysql -uroot -p<redacted> -N --default-character-set=utf8mb4 -e \"SELECT t.table_name, t.table_comment FROM information_schema.tables t WHERE t.table_schema='ycmn' ORDER BY t.table_name;\" 2>/dev/null" > /tmp/dict-tables.txt
"$SSH_FN" "docker exec $MYSQL_C mysql -uroot -p<redacted> -N --default-character-set=utf8mb4 -e \"SELECT c.table_name, c.column_name, c.column_type, c.is_nullable, c.column_default, c.column_key, c.column_comment FROM information_schema.columns c WHERE c.table_schema='ycmn' ORDER BY c.table_name, c.ordinal_position;\" 2>/dev/null" > /tmp/dict-cols.txt
echo "表数: $(wc -l < /tmp/dict-tables.txt) | 字段行数: $(wc -l < /tmp/dict-cols.txt)"

echo "=== 生成 markdown ==="
python3 "$SCRIPT_DIR/gen-dict.py" "$OUT" "$LABEL"
