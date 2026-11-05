#!/bin/bash
# ============================================================
# 测试环境数据库迁移（Flyway）—— 补上测试环境缺失的迁移通道
#
# 【为什么需要本脚本】
#   本仓库存在两条部署路径，能力不对称：
#     · 生产：deploy-db.sh / deploy-backend.sh / deploy-frontend.sh
#             —— 走 ssh_prod + Flyway + 生产架构自检（check_deploy_arch 校验
#                /data/docker、NAS、mysql-data，均为**生产专有**布局）
#     · 测试：auto-pipeline.sh 的阶段[4] 只做 `git pull` + `docker compose up -d --build`；
#             阶段[5] 仅执行 sql/migration/20260804 下的两个**遗留 SQL**，
#             **完全不跑 Flyway**。
#   ⇒ 后果：新增的 db/migration/*.sql 永远不会到达测试库。
#     实测（2026-09-12）：V2026.09.11.3/.4/.5/.6 四支迁移在测试环境**无任何执行路径**，
#     导致"文件写权限码恢复"等修复即便代码已 push，测试环境仍不生效。
#
# 【本脚本做什么】
#   把 db/migration 整目录上传到测试服，用 flyway/flyway:9.22.3-alpine
#   （与生产 deploy-db.sh 同 tag、与 pom flyway-core 同版本）在测试库执行 migrate。
#
# 【与生产脚本的刻意差异】
#   · 走 ssh_test / scp_test，不碰 ssh_prod
#   · **不**调用 check_deploy_arch（那是生产磁盘布局标准，测试服不满足也不应满足）
#   · MySQL 容器默认 shared-mysql（测试服复用共享实例，见 docker-compose.yml 的 DB_HOST）
#   · 库名取 ${TEST_DB_NAME:-aigicrm_prod_20261016}
# 【修复 2026-11-05 产品化批】三处坏点清零(此前长期带病, 实际执行全靠手工绕过):
#   ① flyway docker run 漏 -e FLYWAY_PASSWORD → 容器内无口令必 Access denied;
#   ② --network host + 127.0.0.1 连不上无宿主端口的 shared-mysql → 改 --network shopjoy-net + 容器名直连;
#   ③ 默认库名 aigicrm 已废弃 → 改生产快照库 aigicrm_prod_20261016。
#   另增: 本地 env 无库凭据时从远端容器 Env 派生 root 口令(远端解析, 不回传不落盘)。
#
# 【凭据】需要以下三个键（~/.aigicrm-deploy.env）：
#     TEST_PASS                —— SSH 登录测试服
#     TEST_DB_ADMIN_USER       —— 测试库管理员（有 DDL 权限）
#     TEST_DB_ADMIN_PASSWORD
#   注意：仓库内另有两处使用 TEST_DB_ROOT_PASSWORD（check-charset.sh、
#   pre-delivery-check.sh），与 auto-pipeline.sh/gen-permission-dict.sh 的
#   TEST_DB_ADMIN_* 命名**不一致**，属既有命名分裂。本脚本沿用后者（与
#   auto-pipeline.sh 的测试库迁移一致），并在缺键时明确提示。
#
# 用法:
#   ./deploy-db-test.sh            # Flyway migrate 到测试库
#   ./deploy-db-test.sh --info     # 只读：查看测试库 Flyway 状态
#   ./deploy-db-test.sh --dry-run  # 只做本地前置检查与目录打包，不连接测试服
# ============================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

MODE="migrate"
case "${1:-}" in
  --info)    MODE="info" ;;
  --dry-run) MODE="dryrun" ;;
  "")        MODE="migrate" ;;
  -h|--help) sed -n '2,45p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) echo "未知参数: $1（支持 --info / --dry-run）" >&2; exit 2 ;;
esac

MIGRATION_DIR="$SCRIPT_DIR/../aigicrm-backend/src/main/resources/db/migration"
# 【修复 2026-11-05】测试库=生产快照库(2026-10-16 起), 旧默认 aigicrm 已废弃(迁错库假绿根因)
TEST_DB_NAME="${TEST_DB_NAME:-aigicrm_prod_20261016}"
# 【修复 2026-11-05】shared-mysql 无宿主端口, flyway 容器必须挂 shopjoy-net 以容器名直连
TEST_DB_NETWORK="${TEST_DB_NETWORK:-shopjoy-net}"
TEST_DB_CONTAINER="${TEST_DB_CONTAINER:-shared-mysql}"
FLYWAY_IMAGE="flyway/flyway:9.22.3-alpine"
REMOTE_SQL_DIR="/tmp/aigicrm-flyway-test"
PACK="/tmp/aigicrm-mig-$(date +%Y%m%d-%H%M%S).tgz"

# ---------- 本地前置检查（不依赖凭据，--dry-run 也走这里）----------
[ -d "$MIGRATION_DIR" ] || { echo "❌ 迁移目录不存在: $MIGRATION_DIR" >&2; exit 1; }
MIG_CNT=$(find "$MIGRATION_DIR" -maxdepth 1 -name '*.sql' | wc -l | tr -d ' ')
[ "$MIG_CNT" -gt 0 ] || { echo "❌ 迁移目录内无 .sql 文件" >&2; exit 1; }

# 迁移文件名契约复用既有脚本（连字符版本号会被 flyway 静默跳过，必须提前拦）
if [ -x "$SCRIPT_DIR/check-migration-contract.sh" ]; then
  echo "=== [0/5] 迁移契约自检 ==="
  bash "$SCRIPT_DIR/check-migration-contract.sh" || { echo "❌ 迁移契约失败，已中止" >&2; exit 1; }
fi

echo "=== [1/5] 本地打包迁移目录（$MIG_CNT 个 .sql）==="
# 【加固 2026-11-05 事故教训】排除数字旧版本迁移(V2~V33+, 其效果已含在 V1__baseline schema 快照内):
# 快照库(生产快照)的 flyway 历史若缺这些旧行(实测缺 V2~V11), migrate 会把它们当 pending
# 重新执行(2026-11-05 02:08 实证十支全部空转, 幂等守卫兜底未伤数据——属侥幸不可依赖)。
# V1 必须保留(空库靠它建 schema); 日期版(2026.*)不受影响。
tar czf "$PACK" --exclude='*/._*' --exclude='.DS_Store' \
  --exclude='V[2-9]__*.sql' --exclude='V[0-9][0-9]__*.sql' \
  -C "$MIGRATION_DIR" .
[ -s "$PACK" ] || { echo "❌ 打包失败或产物为空" >&2; exit 1; }
echo "  产物: $PACK ($(wc -c < "$PACK" | tr -d ' ') 字节)"

if [ "$MODE" = "dryrun" ]; then
  echo
  echo "✅ --dry-run 完成：本地前置检查通过（迁移契约 OK / 打包非空）。"
  echo "   未连接测试服。实跑请去掉 --dry-run。"
  rm -f "$PACK"
  exit 0
fi

# ---------- 连接测试服（以下均需凭据）----------
# 本脚本只操作**测试**环境，不读生产库口令 ⇒ 显式声明，避免 deploy-lib.sh 里
# "必须有 DB_PASSWORD（生产口令）"的校验把只想跑测试迁移的人挡在门外。
export AIGICRM_TEST_ONLY=1
source "$SCRIPT_DIR/deploy-lib.sh"   # 载入 env；ssh_test/scp_test 封装

# 【2026-09-12】测试库管理员凭据：仓库内存在**两套命名**，且用户 env 里可能一套都没有。
#   · auto-pipeline.sh / gen-permission-dict.sh 用 TEST_DB_ADMIN_USER / TEST_DB_ADMIN_PASSWORD
#   · check-charset.sh / pre-delivery-check.sh 用 TEST_DB_ROOT_PASSWORD
#   故这里做**有序回退**，并把最终采用的来源明确打印出来（避免"用了哪个"不清不楚）。
DB_ADMIN_USER=""
DB_ADMIN_PASS=""
DB_CRED_SRC=""
if [ -n "${TEST_DB_ADMIN_USER:-}" ] && [ -n "${TEST_DB_ADMIN_PASSWORD:-}" ]; then
  DB_ADMIN_USER="$TEST_DB_ADMIN_USER"; DB_ADMIN_PASS="$TEST_DB_ADMIN_PASSWORD"
  DB_CRED_SRC="TEST_DB_ADMIN_USER/PASSWORD"
elif [ -n "${TEST_DB_ROOT_PASSWORD:-}" ]; then
  DB_ADMIN_USER="${TEST_DB_ADMIN_USER:-root}"; DB_ADMIN_PASS="$TEST_DB_ROOT_PASSWORD"
  DB_CRED_SRC="TEST_DB_ROOT_PASSWORD(回退，user=${DB_ADMIN_USER})"
fi
# 【修复 2026-11-05】移除"DB_PASSWORD(生产口令)回退测试库"旧通道——生产口令对快照库 root 必然
# Access denied(1045 实证), 且拿生产口令试测试库属于凭据越域使用; 专用凭据缺失时改走远端派生。
USE_REMOTE_SECRET=0
if [ -z "$DB_ADMIN_USER" ]; then
  echo "  本地未配置测试库专用凭据 → 回退: 从远端 ${TEST_DB_CONTAINER} 容器 Env 派生 root 口令(远端解析, 不回传不落盘)"
  DB_ADMIN_USER="root"; USE_REMOTE_SECRET=1
fi

for v in TEST_HOST TEST_USER TEST_PASS; do
  if [ -z "$(eval echo "\${$v:-}")" ]; then
    echo "❌ 缺少 $v —— 请在 $ENV_FILE 配置" >&2
    rm -f "$PACK"; exit 1
  fi
done

echo "  测试库凭据来源: $DB_CRED_SRC"

echo "=== [2/5] 探测测试服 MySQL 容器 ==="
CT="${TEST_DB_CONTAINER}"
if ! ssh_test "docker inspect $CT >/dev/null 2>&1"; then
  echo "⚠️  ${CT} 不可见，回退为自动探测（docker ps 中第一个含 mysql 的容器）"
  CT=$(ssh_test "docker ps --format '{{.Names}}'" | grep -i mysql | head -1 || true)
fi
[ -n "$CT" ] || { echo "❌ 未能在测试服找到 MySQL 容器（可用 TEST_DB_CONTAINER 指定）" >&2; rm -f "$PACK"; exit 1; }
echo "  使用容器: $CT / 目标库: $TEST_DB_NAME"

echo "=== [3/5] 上传迁移目录 ==="
scp_test "$PACK" "${TEST_USER}@${TEST_HOST}:/tmp/mig.tgz"
rm -f "$PACK"
ssh_test "rm -rf $REMOTE_SQL_DIR && mkdir -p $REMOTE_SQL_DIR && tar xzf /tmp/mig.tgz -C $REMOTE_SQL_DIR && find $REMOTE_SQL_DIR -name '._*' -delete 2>/dev/null; true"
UP_CNT=$(ssh_test "ls $REMOTE_SQL_DIR/*.sql 2>/dev/null | wc -l" | tr -d '[:space:]')
echo "  远端 .sql 数: ${UP_CNT}（本地 ${MIG_CNT}）"
[ "$UP_CNT" -gt 0 ] || { echo "❌ 远端未收到 .sql（上传失败）" >&2; exit 1; }

echo "=== [4/5] 测试库备份（迁移前）==="
# 【D3 2026-10-21】口令经 stdin(bash -s)注入远端脚本，不进本地 ssh argv(旧写法本地 ps 可见)；
# 【D2 同款护栏】备份先写 .tmp 并校验(非空+CREATE TABLE>0)才 mv——不留 0 字节"最新备份"。
# stdin 型调用用 ssh_test_once(重试型 ssh_test 第二次尝试会读到 EOF)。
emit_var() { printf '%s=%q\n' "$1" "$2"; }
# 凭据注入：值由 %q 转义后随 stdin 下发，源码不出现 KEY="值" 形态的口令赋值
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }
{
  echo 'set -Eeuo pipefail'
  emit_var CT "$CT"
  emit_var DB_ADMIN_USER "$DB_ADMIN_USER"
  emit_secret DB_ADMIN_PASS DB_ADMIN_PASS
  emit_var TEST_DB_NAME "$TEST_DB_NAME"
  emit_var USE_REMOTE_SECRET "$USE_REMOTE_SECRET"
  cat <<'REMOTE'
TS="$(date +%Y%m%d-%H%M%S)"
if [ "$USE_REMOTE_SECRET" = "1" ]; then
  DB_ADMIN_PASS="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
fi
BK="/tmp/pre-migrate-test-$TS.sql"
if ! docker exec -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysqldump -u"$DB_ADMIN_USER" --default-character-set=utf8mb4 --single-transaction "$TEST_DB_NAME" > "$BK.tmp"; then
  rm -f "$BK.tmp"; echo "❌ 测试库 mysqldump 失败"; exit 1
fi
if [ ! -s "$BK.tmp" ]; then rm -f "$BK.tmp"; echo "❌ 测试库备份为空"; exit 1; fi
CT_N="$(grep -c '^CREATE TABLE' "$BK.tmp" || true)"
case "$CT_N" in ''|*[!0-9]*) rm -f "$BK.tmp"; echo "❌ 无法统计测试库备份 CREATE TABLE"; exit 1 ;; esac
[ "$CT_N" -gt 0 ] || { rm -f "$BK.tmp"; echo "❌ 测试库备份无 CREATE TABLE(疑似截断)"; exit 1; }
mv -f "$BK.tmp" "$BK"
echo "备份完成: $BK (CREATE TABLE=$CT_N)"
REMOTE
} | ssh_test_once "bash -s"

if [ "$MODE" = "info" ]; then
  echo "=== [5/5] Flyway info（只读）==="
  # 【D3 2026-10-21】Flyway 口令以 FLYWAY_PASSWORD 官方环境变量透传(export 后 docker run -e 仅给变量名)，
  # 旧 -password 写法把口令写进远端 flyway 容器进程 argv(ps 可见)。
  {
    emit_var REMOTE_SQL_DIR "$REMOTE_SQL_DIR"
    emit_var FLYWAY_IMAGE "$FLYWAY_IMAGE"
    emit_var TEST_DB_NAME "$TEST_DB_NAME"
    emit_var DB_ADMIN_USER "$DB_ADMIN_USER"
    emit_var CT "$CT"
    emit_var TEST_DB_NETWORK "$TEST_DB_NETWORK"
    emit_var USE_REMOTE_SECRET "$USE_REMOTE_SECRET"
    emit_secret FLYWAY_PASSWORD DB_ADMIN_PASS
    cat <<'REMOTE'
if [ "$USE_REMOTE_SECRET" = "1" ]; then
  DB_ADMIN_PASS="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
fi
export FLYWAY_PASSWORD="$DB_ADMIN_PASS"
docker run --rm --network "$TEST_DB_NETWORK" -e FLYWAY_PASSWORD -v "$REMOTE_SQL_DIR":/flyway/sql "$FLYWAY_IMAGE" \
  -url="jdbc:mysql://$CT:3306/$TEST_DB_NAME?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true" \
  -user="$DB_ADMIN_USER" \
  -connectRetries=10 -validateMigrationNaming=true info
REMOTE
  } | ssh_test_once "bash -s"
  echo "✅ info 完成（未修改数据库）"
  exit 0
fi

echo "=== [5/5] Flyway repair + migrate（-validateMigrationNaming=true）==="
# repair 先行：容忍历史手工执行造成的小偏差；随后 migrate。
# -validateMigrationNaming=true：把"坏文件名被静默跳过"变成硬失败
#   （实测连字符版本号 V2026-09-11-v1 会被静默忽略且退出码 0）。
# 【D3】口令一律 FLYWAY_PASSWORD env 透传，不进容器 argv。
{
  emit_var REMOTE_SQL_DIR "$REMOTE_SQL_DIR"
  emit_var FLYWAY_IMAGE "$FLYWAY_IMAGE"
  emit_var TEST_DB_NAME "$TEST_DB_NAME"
  emit_var DB_ADMIN_USER "$DB_ADMIN_USER"
    emit_var CT "$CT"
    emit_var TEST_DB_NETWORK "$TEST_DB_NETWORK"
    emit_var USE_REMOTE_SECRET "$USE_REMOTE_SECRET"
  emit_secret FLYWAY_PASSWORD DB_ADMIN_PASS
  cat <<'REMOTE'
if [ "$USE_REMOTE_SECRET" = "1" ]; then
  DB_ADMIN_PASS="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
fi
export FLYWAY_PASSWORD="$DB_ADMIN_PASS"
docker run --rm --network "$TEST_DB_NETWORK" -e FLYWAY_PASSWORD -v "$REMOTE_SQL_DIR":/flyway/sql "$FLYWAY_IMAGE" \
  -url="jdbc:mysql://$CT:3306/$TEST_DB_NAME?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true" \
  -user="$DB_ADMIN_USER" \
  -connectRetries=10 -validateMigrationNaming=true repair
REMOTE
} | ssh_test_once "bash -s" || echo "⚠️ repair 非零（可能仅无操作），继续 migrate"

OUT="$({
  emit_var REMOTE_SQL_DIR "$REMOTE_SQL_DIR"
  emit_var FLYWAY_IMAGE "$FLYWAY_IMAGE"
  emit_var TEST_DB_NAME "$TEST_DB_NAME"
  emit_var DB_ADMIN_USER "$DB_ADMIN_USER"
    emit_var CT "$CT"
    emit_var TEST_DB_NETWORK "$TEST_DB_NETWORK"
    emit_var USE_REMOTE_SECRET "$USE_REMOTE_SECRET"
  emit_secret FLYWAY_PASSWORD DB_ADMIN_PASS
  cat <<'REMOTE'
if [ "$USE_REMOTE_SECRET" = "1" ]; then
  DB_ADMIN_PASS="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
fi
export FLYWAY_PASSWORD="$DB_ADMIN_PASS"
docker run --rm --network "$TEST_DB_NETWORK" -e FLYWAY_PASSWORD -v "$REMOTE_SQL_DIR":/flyway/sql "$FLYWAY_IMAGE" \
  -url="jdbc:mysql://$CT:3306/$TEST_DB_NAME?characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true" \
  -user="$DB_ADMIN_USER" \
  -connectRetries=10 -validateMigrationNaming=true migrate
REMOTE
} | ssh_test_once "bash -s" 2>&1)" || true
echo "$OUT"

# ---------- 伪成功护栏（与 deploy-db.sh 同口径）----------
FAIL=0
case "$OUT" in
  *"did not follow the filename convention"*) echo "❌ 存在非法迁移文件名（被静默跳过）" >&2; FAIL=1 ;;
  *"No migrations found"*)                   echo "❌ 未发现任何迁移（上传或目录错误）" >&2;   FAIL=1 ;;
esac
case "$OUT" in
  *"Successfully applied"*|*"No migration necessary"*|*"Schema"*) : ;;
  *) echo "❌ 未检测到成功标志（可能空跑）" >&2; FAIL=1 ;;
esac
[ "$FAIL" -eq 0 ] || exit 1

echo
echo "=== 迁移后核对：文件写权限码状态 ==="
# 【D3】口令经 stdin 注入，不进 ssh argv；SQL 一并经 stdin 免转义
{
  echo 'set -euo pipefail'
  emit_var CT "$CT"
  emit_var DB_ADMIN_USER "$DB_ADMIN_USER"
  emit_secret DB_ADMIN_PASS DB_ADMIN_PASS
  emit_var TEST_DB_NAME "$TEST_DB_NAME"
  emit_var USE_REMOTE_SECRET "$USE_REMOTE_SECRET"
  cat <<'REMOTE'
if [ "$USE_REMOTE_SECRET" = "1" ]; then
  DB_ADMIN_PASS="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
fi
docker exec -e MYSQL_PWD="$DB_ADMIN_PASS" "$CT" mysql -u"$DB_ADMIN_USER" \
  --default-character-set=utf8mb4 -N -B "$TEST_DB_NAME" 2>/dev/null <<'SQL'
SELECT permission, status, COUNT(*) FROM sys_menu
WHERE permission IN ('file:upload','file:delete','file:share','file:folder:manage')
GROUP BY permission, status ORDER BY permission;
SQL
REMOTE
} | ssh_test_once "bash -s" || true
echo "  预期：4 行全部 status=1（否则 apply 未生效）; file:edit 已随 2026-11-05 用户令删除(V2026.11.05.9), 不在清单"
echo
echo "✅ 测试库迁移完成（库=${TEST_DB_NAME} 容器=${CT}）"
