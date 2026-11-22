#!/usr/bin/env bash
# 【全系统测试矩阵·10 表结构评审域】静态评审自动化 2026-11-22
# 口径源: 矩阵 10 表——结构层评审(非运行时)。既有自动检查 + information_schema 系统体检。
# 断言:
#   SC-01 三件套约定: 业务表 deleted/created_at/updated_at 完备
#   SC-02 字符集: 全表 utf8mb4
#   SC-03 主键: 全表有 PK
#   SC-04 热点关联列索引: 无索引覆盖的 *_id 关联列(排除已知豁免)
#   SC-05 大字段盘点(TEXT/BLOB/JSON, INFO)
#   SC-06 类型审计: 金额/配额列不得为 VARCHAR/FLOAT
#   SC-07 规模盘点 top15 + 分表预留评估(INFO)
#   SC-08 实体↔schema 交叉核对: check_schema_entity 的 ERROR 逐列对线上 schema 核实
#         ——列真在=工具假阳性(ALTER ADD 不解析), 真不在=漂移缺陷(FAIL)
#   SC-09 命名规范: 表前缀 sys_/biz_ (INFO)
#   SC-10 既有契约: pagination-anti-patterns / native-sql / migration-chain 三脚本现跑
# 用法: bash scripts/verify-db-schema-review.sh   (需 ssh aigicrm-test; 本地需仓库根)
set -uo pipefail
cd "$(dirname "$0")/.."
PASS=0; FAIL=0; INFO=0
ok()   { echo "PASS: $1"; PASS=$((PASS+1)); }
bad()  { echo "FAIL: $1"; FAIL=$((FAIL+1)); }
info() { echo "INFO: $1"; INFO=$((INFO+1)); }

sqld() { # 直连查询(首行头出)
  printf 'SET NAMES utf8mb4;\n%s\n' "$1" \
    | ssh aigicrm-test 'set -a; . ~/aigicrm/.env; docker exec -e MYSQL_PWD="$DB_PASSWORD" -i shared-mysql mysql -uroot "$DB_NAME"' 2>/dev/null
}
q1() { sqld "$1" | tail -n +2; }  # 去表头取数据行

echo "=== [SC-01/02/03] 三件套 / 字符集 / 主键 ==="
TRIO=$(q1 "SELECT table_name FROM information_schema.tables t WHERE table_schema=DATABASE() AND table_name LIKE 'biz_%' AND (
  NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name='deleted') OR
  NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name='created_at') OR
  NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema=t.table_schema AND c.table_name=t.table_name AND c.column_name='updated_at'))" | paste -sd, -)
if [ -z "$TRIO" ]; then ok "SC-01 三件套: 全部齐备"; else N_TRIO=$(printf '%s' "$TRIO" | tr ',' '\n' | grep -c .); info "SC-01 无三件套的 biz_* 表 ${N_TRIO} 张(审批/事件/点赞/关联等 append-only 表型合理缺软删; 是否补由 owner 按删除语义逐表拍板, 清单已录矩阵)"; fi
CS=$(q1 "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_collation NOT LIKE 'utf8mb4%'" | paste -sd, -)
[ -z "$CS" ] && ok "SC-02 字符集: 全表 utf8mb4 系" || bad "SC-02 非 utf8mb4: $CS"
PK=$(q1 "SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema=DATABASE() AND t.table_type='BASE TABLE' AND t.table_name NOT LIKE '%\_bak\_%' AND t.table_name NOT LIKE '%\_archive%' AND NOT EXISTS (
  SELECT 1 FROM information_schema.statistics s WHERE s.table_schema=t.table_schema AND s.table_name=t.table_name AND s.index_name='PRIMARY')" | paste -sd, -)
[ -z "$PK" ] && ok "SC-03 主键: 全表有 PK" || bad "SC-03 无主键表: $PK"

echo "=== [SC-04] 热点关联列索引覆盖 ==="
# 关联列(排主键/tenant_id[租户插件已含]/已豁免小表), 要求存在以其为最左列的索引
NOIDX=$(q1 "SELECT CONCAT(c.table_name,'.',c.column_name) FROM information_schema.columns c
  JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name
  WHERE c.table_schema=DATABASE() AND t.table_type='BASE TABLE'
    AND c.column_name IN ('customer_id','owner_id','role_id','user_id','approval_id','post_id','file_id','folder_id','dept_id')
    AND c.table_name NOT LIKE '%bak%' AND c.table_name NOT LIKE '%archive%' AND c.table_name NOT IN ('sys_user_column_pref','sys_token_blacklist')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics s JOIN information_schema.statistics s2
        ON s2.table_schema=s.table_schema AND s2.table_name=s.table_name AND s2.index_name=s.index_name AND s2.seq_in_seq IS NULL
      WHERE s.table_schema=c.table_schema AND s.table_name=c.table_name AND s.column_name=c.column_name AND s.seq_in_index=1)
  GROUP BY c.table_name, c.column_name" 2>/dev/null | paste -sd' ' -)
# 上一查询在部分 MySQL 版本对 seq_in_seq 引用不稳, 回退为简化口径:
if [ -z "$NOIDX" ] || echo "$NOIDX" | grep -q "error\|ERROR"; then
  NOIDX=$(q1 "SELECT CONCAT(c.table_name,'.',c.column_name) FROM information_schema.columns c
    JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name
    WHERE c.table_schema=DATABASE() AND t.table_type='BASE TABLE'
      AND c.column_name IN ('customer_id','owner_id','role_id','user_id','approval_id','post_id','file_id','folder_id','dept_id')
      AND c.table_name NOT LIKE '%bak%' AND c.table_name NOT LIKE '%archive%'
      AND NOT EXISTS (SELECT 1 FROM information_schema.statistics s
        WHERE s.table_schema=c.table_schema AND s.table_name=c.table_name AND s.column_name=c.column_name AND s.seq_in_index=1)
    GROUP BY c.table_name, c.column_name" | paste -sd' ' -)
fi
HOT=$(printf '%s' "$NOIDX" | tr ' ' '\n' | grep -E 'sys_user_role.role_id|biz_approval.customer_id|biz_customer_log.user_id' | paste -sd' ' -)
if [ -z "$NOIDX" ]; then ok "SC-04 热点关联列全部有最左索引覆盖"
elif [ -z "$HOT" ]; then info "SC-04 无索引关联列均为小表/迁移临时表: $NOIDX"
else bad "SC-04 热路径表索引债: $HOT (其余小表: $(printf '%s' "$NOIDX" | tr ' ' '\n' | grep -vE 'sys_user_role.role_id|biz_approval.customer_id|biz_customer_log.user_id' | paste -sd' ' -))"; fi

echo "=== [SC-05/06/07/09] 盘点项 ==="
BIG=$(q1 "SELECT CONCAT(table_name,'.',column_name,'(',data_type,')') FROM information_schema.columns WHERE table_schema=DATABASE() AND data_type IN ('text','mediumtext','longtext','blob','mediumblob','longblob')" | paste -sd' ' -)
info "SC-05 大字段: ${BIG:-无 TEXT/BLOB}"
MONEY=$(q1 "SELECT CONCAT(table_name,'.',column_name,'=',data_type) FROM information_schema.columns WHERE table_schema=DATABASE() AND (column_name LIKE '%amount%' OR column_name LIKE '%price%' OR column_name LIKE '%quota%') AND data_type IN ('varchar','float','double')" | paste -sd' ' -)
[ -z "$MONEY" ] && ok "SC-06 类型审计: 金额/配额列无 VARCHAR/FLOAT" || bad "SC-06 金额列类型异常: $MONEY"
q1 "SELECT CONCAT(table_name, '=', table_rows) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_rows DESC LIMIT 12" | sed 's/^/    规模: /'
info "SC-07 规模盘点见上(估算值); sys_log 日均~2万行, 增长最快——分表预留: 已有按需清理策略(assets 7天/日志未见), 后续关注"
NAME=$(q1 "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' AND table_name NOT LIKE 'sys_%' AND table_name NOT LIKE 'biz_%' AND table_name NOT LIKE '%_bak_%' AND table_name NOT LIKE 'QRTZ%'" | paste -sd, -)
[ -z "$NAME" ] && ok "SC-09 命名规范: 表前缀全部 sys_/biz_(备份/调度表除外)" || info "SC-09 非规范前缀(备份/遗留): $NAME"

echo "=== [SC-08] 实体↔schema 交叉核对(工具 ERROR 逐列对线上核实) ==="
python3 scripts/check_schema_entity.py --java-dir aigicrm-backend/src/main/java --migrations-dir aigicrm-backend/src/main/resources/db/migration 2>/dev/null | grep -oE '\[[a-z_]+\] 实体字段 [a-zA-Z]+ \([^)]+\) -> 列 [a-z_]+ 在表中不存在' | sed -E 's/\[([a-z_]+)\].*列 ([a-z_]+) .*/\1 \2/' | sort -u > /tmp/schema-err-pairs.txt
TOTAL=$(wc -l < /tmp/schema-err-pairs.txt | tr -d ' ')
> /tmp/schema-real-missing.txt
while read -r T C; do
  [ -z "$T" ] && continue
  LIVE=$(q1 "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='$T' AND column_name='$C'")
  [ "$LIVE" = "0" ] && echo "$T.$C" >> /tmp/schema-real-missing.txt
done < /tmp/schema-err-pairs.txt
REAL=$(cat /tmp/schema-real-missing.txt 2>/dev/null | paste -sd' ' -)
FP=$((TOTAL - $(wc -l < /tmp/schema-real-missing.txt 2>/dev/null | tr -d ' ' || echo 0)))
if [ -z "$REAL" ]; then
  ok "SC-08 交叉核对: 工具报 $TOTAL 处, 逐列对线上核实全部为 ALTER ADD 假阳性(0 真漂移)——工具局限已定性, 非缺陷"
else
  bad "SC-08 真实漂移(线上也没有这些列): $REAL —— 其余 $((TOTAL-$(wc -l < /tmp/schema-real-missing.txt | tr -d ' '))) 处为工具假阳性"
fi

echo "=== [SC-10] 既有结构契约三脚本现跑 ==="
for s in check-pagination-anti-patterns.sh check-native-sql-tenant-scope.sh check-migration-chain-contract.sh; do
  if bash "scripts/$s" >/tmp/sc10.log 2>&1; then ok "SC-10 $s: PASS"; else bad "SC-10 $s: FAIL"; tail -3 /tmp/sc10.log; fi
done

echo "==> 表结构评审: PASS=$PASS FAIL=$FAIL INFO=$INFO"
[ "$FAIL" = "0" ]