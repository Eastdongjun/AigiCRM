#!/bin/bash
# ============================================================
# 空库重放管道（一次性验证 / 新环境初始化演练）—— 2026-11-11 新增
#
# 【为什么需要本脚本】
#   deploy-db-test.sh 只服务**快照库**(数字版 V2~V33 全排除); 生产 deploy-db.sh 全量
#   但其空库重放从未被验证过。2026-11-11 审计 P0 实证: 空库重放存在系统性断裂——
#   V1__baseline 快照【不含】V12~V33 的效果(V16 sys_user 三列/V18 biz_incentive_grant/
#   V20/V24 表等), 链中部日期版迁移(如 V2026.09.19.9 引用 perm_version)直接崩。
#
# 【空库重放口径(本脚本)】= V1 + V12~V33 + 日期版, 仅排除 V2~V11:
#   · V2~V11: 效果 = V1 快照 ∪ V2026.09.11.1 补建 gap 表, 原文裸 CREATE, 再执行与 V1 撞表;
#   · V12~V33: 效果不在 V1 内, 必须执行(裸 ALTER 在空库上恰好生效, IF NOT EXISTS 幂等);
#   · 日期版: 全量。
#
# 【执行环境】测试服一次性容器(独立网络 + mysql:8.0 + flyway:9.22.3-alpine),
#   root 口令远端 openssl rand 生成(不回传不落盘), 结束自动清理。
#   空库重放属验证动作, 与快照库/生产库零交集。
#
# 【末态断言】(T1 审计整改 P0 的唯一实证通道)
#   ① flyway 全链 success 无 failed;
#   ② 断言码清单(scripts/empty-replay-codes.txt 单一源, 与 CI 共用)菜单行
#      status=1 且绑定角色数 ≥1; ③ admin 持全部断言码。
#
# 用法: ./replay-empty-db.sh   (需 ~/.aigicrm-deploy.env 的 TEST_HOST/TEST_USER/TEST_PASS)
# 退出码: 0=重放全绿; 1=断言失败或迁移失败; 2=环境缺失
# ============================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MIGRATION_DIR="$SCRIPT_DIR/../aigicrm-backend/src/main/resources/db/migration"
FLYWAY_IMAGE="flyway/flyway:9.22.3-alpine"
MYSQL_IMAGE="mysql:8.0"
NET=aigi-replay-net
DB=aigi-replay-mysql
REMOTE_DIR=/tmp/aigicrm-empty-replay

fail() { echo "❌ $*" >&2; exit 1; }

[ -d "$MIGRATION_DIR" ] || fail "迁移目录不存在: $MIGRATION_DIR"

echo "=== [1/4] 打包空库重放集(仅排除 V2~V11) ==="
PACK=/tmp/aigicrm-empty-replay-$(date +%Y%m%d-%H%M%S).tgz
tar czf "$PACK" --exclude='*/._*' --exclude='.DS_Store' \
  --exclude='V[2-9]__*.sql' --exclude='V10__*.sql' --exclude='V11__*.sql' \
  -C "$MIGRATION_DIR" .

# 【N7】断言码清单(与 CI 共用 scripts/empty-replay-codes.txt, 单一源防两处口径漂移)
CODES_FILE="$SCRIPT_DIR/empty-replay-codes.txt"
[ -f "$CODES_FILE" ] || { echo "❌ 缺少断言码清单: $CODES_FILE" >&2; exit 2; }
echo "  断言码: $(grep -cE '^[a-z]' "$CODES_FILE") 个(单一源 empty-replay-codes.txt)"
CNT=$(tar tzf "$PACK" | grep -c '\.sql$' || true)
echo "  包内 .sql: $CNT (预期 = 全量 - 10)"
trap 'rm -f "$PACK"' EXIT

export AIGICRM_TEST_ONLY=1
source "$SCRIPT_DIR/deploy-lib.sh"
for v in TEST_HOST TEST_USER TEST_PASS; do
  [ -n "$(eval echo "\${$v:-}")" ] || fail "缺少 $v (~/.aigicrm-deploy.env)"
done

echo "=== [2/4] 上传 + 起一次性容器 ==="
scp_test "$PACK" "${TEST_USER}@${TEST_HOST}:/tmp/mig-replay.tgz"
scp_test "$CODES_FILE" "${TEST_USER}@${TEST_HOST}:/tmp/replay-codes.txt"

ssh_test_once "bash -s" <<'REMOTE'
set -Eeuo pipefail
NET=aigi-replay-net; DB=aigi-replay-mysql; REMOTE_DIR=/tmp/aigicrm-empty-replay
cleanup() { docker rm -f $DB >/dev/null 2>&1 || true; docker network rm $NET >/dev/null 2>&1 || true; rm -rf $REMOTE_DIR /tmp/mig-replay.tgz; }
trap cleanup EXIT
docker network create $NET >/dev/null 2>&1 || true
PW="$(openssl rand -hex 16)"
docker run -d --name $DB --network $NET \
  -e MYSQL_ROOT_PASSWORD="$PW" -e MYSQL_DATABASE=ycmn \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci >/dev/null
# ↑ server 级字符集/排序规则对齐生产(ycmn-mysql 实测 utf8mb4/utf8mb4_general_ci):
#   mysql:8.0 默认 0900_ai_ci 会让 CAST/字面量比较产生 Illegal mix of collations,
#   新环境初始化必须按生产参数演练才有效度(2026-11-11 空库重放实证断点)。
ok=0
for i in $(seq 1 90); do
  docker exec -e MYSQL_PWD="$PW" $DB mysql -uroot -e "SELECT 1" >/dev/null 2>&1 && { ok=1; break; }
  sleep 2
done
[ "$ok" = "1" ] || { echo "MySQL 180s 未就绪"; docker logs $DB 2>&1 | tail -5; exit 1; }
rm -rf $REMOTE_DIR && mkdir -p $REMOTE_DIR
tar xzf /tmp/mig-replay.tgz -C $REMOTE_DIR
find $REMOTE_DIR -name '._*' -delete 2>/dev/null || true

echo "=== [3/4] flyway migrate 全链 ==="
docker run --rm --network $NET -e FLYWAY_PASSWORD="$PW" -v $REMOTE_DIR:/flyway/sql flyway/flyway:9.22.3-alpine \
  -url="jdbc:mysql://$DB:3306/ycmn?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true" \
  -user=root -connectRetries=10 -validateMigrationNaming=true migrate 2>&1 \
  | grep -vE "^WARNING|release notes" | tail -6

echo "=== [4/4] 末态断言 ==="
M() { docker exec -e MYSQL_PWD="$PW" $DB mysql -uroot ycmn "$@"; }
# 【N7】断言码自单一源清单拼装(与 CI empty-db-replay 同一文件)
CODES_IN=$(grep -vE '^#|^$' /tmp/replay-codes.txt | sed "s/.*/'&',/" | tr -d '\n' | sed 's/,$//')
[ -n "$CODES_IN" ] || { echo "断言码清单为空"; exit 1; }
TOTAL_CODES=$(grep -cE "^[a-z]" /tmp/replay-codes.txt)
rm -f /tmp/replay-codes.txt
rc=0
# ① 全链 success 无 failed
FAILED=$(M -N -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success<>1;")
TOTAL=$(M -N -e "SELECT COUNT(*) FROM flyway_schema_history;")
echo "  [1] flyway: applied=$TOTAL failed=$FAILED"
[ "$FAILED" = "0" ] || { echo "  ❌ 存在 failed 迁移"; rc=1; }
# ② 7 码 status=1 且绑定≥1
M -t -e "
SELECT m.permission, m.status, COUNT(rm.role_id) AS bound_roles
FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id=m.id
WHERE m.permission IN ($CODES_IN)
  AND m.menu_type=3
GROUP BY m.permission, m.status ORDER BY m.permission;"
BAD=$(M -N -e "
SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_role_menu rm ON rm.menu_id=m.id
WHERE m.permission IN ($CODES_IN)
  AND m.menu_type=3 AND (m.status<>1 OR rm.id IS NULL);")
echo "  [2] 断言码末态异常行: $BAD (预期 0)"
[ "$BAD" = "0" ] || { echo "  ❌ 存在停用或零绑定码"; rc=1; }
# ③ admin 持全部 7 码
ADMIN_N=$(M -N -e "
SELECT COUNT(DISTINCT m.permission) FROM sys_role_menu rm
JOIN sys_menu m ON m.id=rm.menu_id JOIN sys_role r ON r.id=rm.role_id
WHERE r.role_code='admin' AND m.menu_type=3
  AND m.permission IN ($CODES_IN);")
echo "  [3] admin 持码数: $ADMIN_N/$TOTAL_CODES"
[ "$ADMIN_N" = "$TOTAL_CODES" ] || { echo "  ❌ admin 未持有全部审计码"; rc=1; }
[ "$rc" = "0" ] && echo "✅ 空库重放全绿" || echo "❌ 空库重放断言失败"
exit $rc
REMOTE
