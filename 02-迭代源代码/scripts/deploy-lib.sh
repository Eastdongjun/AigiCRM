#!/bin/bash
# AIGICRM 部署公共库: 配置加载 + ssh/scp 封装 + 健康检查
# 注意: 库文件不设 errexit(调用方自行控制); 仅 unset 变量与管道检查
set -uo pipefail

# 加载部署配置/凭据(不入 git)。2026-09-11 起统一为 ~/.aigicrm-deploy.env（原 ~/.ycmn-deploy.env 兜底已移除）
ENV_FILE="${AIGICRM_DEPLOY_ENV:-${DEPLOY_ENV_FILE:-$HOME/.aigicrm-deploy.env}}"
if [ ! -f "$ENV_FILE" ]; then
  echo "错误: 缺少 $ENV_FILE (复制 scripts/aigicrm-deploy.env.example 并填写)" >&2
  exit 1
fi
source "$ENV_FILE"

APP_NAME="${APP_NAME:-AIGICRM}"
APP_SLUG="${APP_SLUG:-aigicrm}"
DB_NAME="${DB_NAME:-aigicrm}"
DEPLOY_DIR="${DEPLOY_DIR:-/root/aigicrm}"
DEPLOY_DATA_DIR="${DEPLOY_DATA_DIR:-/data/aigicrm}"

# DB 凭据必须存在（禁止脚本内硬编码，一律经 env 注入）
#
# 【2026-09-12】仅测试环境的脚本（如 deploy-db-test.sh）**不需要**生产 DB_PASSWORD，
#   而本库此前无条件强制它 ⇒ 只想跑测试库迁移的人被迫先持有生产口令。
#   故增加显式逃生：调用方在 source 本库**之前**置
#       AIGICRM_TEST_ONLY=1
#   即跳过"生产库口令/应用账号"类校验（其余能力不变，prod 脚本未置该变量，行为完全不变）。
if [ "${AIGICRM_TEST_ONLY:-0}" = "1" ]; then
  :
else
  [ -n "${DB_PASSWORD:-}" ] || { echo "错误: 缺少 DB_PASSWORD（请在 $ENV_FILE 配置）" >&2; exit 1; }
fi

# 健康检查/冒烟登录凭据 —— 值在部署 env (HEALTH_CHECK_USER/HEALTH_CHECK_PASS)，禁止硬编码入仓。
# 为空时登录检查会失败并提示，不会静默使用仓库内默认口令。
HEALTH_CHECK_USER="${HEALTH_CHECK_USER:-admin}"
HEALTH_CHECK_PASS="${HEALTH_CHECK_PASS:-}"

SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=20 -o StrictHostKeyChecking=no}"

# ssh 封装带重试(连接建立类瞬时失败自动重试; 3 次后放弃)。
# 注: 重试仅对"连接未建立"的失败安全——绝大多数瞬时失败(连接重置/拒绝)发生在此阶段, 远程命令未执行;
# 部署类命令自身幂等(备份覆盖/rm -rf 先行/容器重启), 重试不会造成重复副作用。
ssh_prod() {
  local attempt
  for attempt in 1 2 3; do
    if [ -f "$PROD_KEY" ]; then
      ssh -i "$PROD_KEY" -o BatchMode=yes $SSH_OPTS "${PROD_USER}@${PROD_HOST}" "$@" && return 0
    else
      sshpass -p "$PROD_PASS" ssh $SSH_OPTS "${PROD_USER}@${PROD_HOST}" "$@" && return 0
    fi
    sleep 2
  done
  return 1
}
ssh_test() {
  local attempt
  for attempt in 1 2 3; do
    sshpass -p "$TEST_PASS" ssh $SSH_OPTS "${TEST_USER}@${TEST_HOST}" "$@" && return 0
    sleep 2
  done
  return 1
}
# 【2026-09-23 生产通道修复】生产 sshd 已收敛为仅公钥(密码登录被服务端拒绝),
# ssh_prod/scp_prod 优先使用 PROD_KEY 密钥;未配置密钥时回退旧密码通道(测试机仍可用)。
PROD_KEY="${PROD_KEY:-/Users/dongjun/东来/项目/01-进行中/01-AIGICRM/06-部署与运维/阿里云环境/aigicrm.com.pem}"
# 生产实际部署目录(实测 2026-09-23:容器 WorkingDir=/data/ycmn,jar+compose 均在此;
# 旧默认 /root/aigicrm 为历史残留,env 未显式覆盖时以实测值为准)
if [ "${DEPLOY_DIR:-}" = "/root/aigicrm" ] || [ -z "${DEPLOY_DIR:-}" ]; then
  DEPLOY_DIR="/data/ycmn"
fi
scp_prod() {
  if [ -f "$PROD_KEY" ]; then
    scp -i "$PROD_KEY" -o BatchMode=yes $SSH_OPTS "$@"
  else
    sshpass -p "$PROD_PASS" scp $SSH_OPTS "$@"
  fi
}
scp_test()  { sshpass -p "$TEST_PASS" scp $SSH_OPTS "$@"; }

# 生产健康检查: 登录 API 带 Origin 必须 200
check_health() {
  local base="${1:-https://www.aigicrm.com}"
  [ -n "$HEALTH_CHECK_PASS" ] || { echo "❌ 缺少 HEALTH_CHECK_PASS（请在 $ENV_FILE 配置）" >&2; return 1; }
  local code
  code=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$base/api/auth/login" \
    -H "Origin: $base" -H "Content-Type: application/json" \
    -d "{\"username\":\"$HEALTH_CHECK_USER\",\"password\":\"$HEALTH_CHECK_PASS\"}" --connect-timeout 10)
  if [ "$code" = "200" ]; then
    echo "✅ 健康检查通过 (登录 API $code)"
    return 0
  else
    echo "❌ 健康检查失败 (登录 API $code)" >&2
    return 1
  fi
}

# 部署架构自检(2026-08-15 磁盘架构迁移后强制执行):
# 标准布局 = 系统盘仅OS / 数据盘 /data(docker根+部署目录+数据库, 库与程序路径区分) / NAS=文件
# 只告警不阻断(允许临时偏差), 每次部署开始时输出, 部署者必须肉眼确认 ✅
check_deploy_arch() {
  echo "=== 部署架构自检(标准见 docs/生产部署规范.md) ==="
  ssh_prod "APP_SLUG='$APP_SLUG' DEPLOY_DIR='$DEPLOY_DIR' DEPLOY_DATA_DIR='$DEPLOY_DATA_DIR' bash -s" <<'ARCH_CHECK'
R=$(docker info --format "{{.DockerRootDir}}" 2>/dev/null)
[ "$R" = "/data/docker" ] && echo "✅ docker 根=/data/docker" || echo "⚠️ docker 根=$R (标准: /data/docker)"
M=$(docker inspect ${APP_SLUG}-mysql --format "{{range .Mounts}}{{.Source}} {{end}}" 2>/dev/null | tr " " "\n" | grep -c "^/data/database/mysql-data$")
[ "$M" = "1" ] && echo "✅ mysql 数据=/data/database/mysql-data" || echo "⚠️ mysql 数据不在 /data/database/mysql-data"
L=$(readlink "$DEPLOY_DIR" 2>/dev/null)
[ "$L" = "$DEPLOY_DATA_DIR" ] && echo "✅ 部署目录软链 $DEPLOY_DIR → $DEPLOY_DATA_DIR" || echo "⚠️ $DEPLOY_DIR 非 $DEPLOY_DATA_DIR 软链 (当前: $L)"
U=$(docker inspect ${APP_SLUG}-backend --format "{{range .Mounts}}{{.Source}} {{end}}" 2>/dev/null | tr " " "\n" | grep -c "^/Aigicrm/files/uploads$")
[ "$U" = "1" ] && echo "✅ 上传文件=NAS /Aigicrm/files/uploads" || echo "⚠️ 上传文件不在 NAS"
ARCH_CHECK
  return 0
}

# ============================================================
# 【2026-09-12 新增】生产前置硬门禁：防止"改名未执行 → 部署静默空转"
#
# 背景（PROD-RENAME-PLAN-NEXT-VERSION.md §0 记录的发布阻断项）：
#   ~/.aigicrm-deploy.env 的 APP_SLUG / DB_NAME / DEPLOY_DIR / NAS_DIST_DIR
#   当前是**目标态**（aigicrm 系），而生产实际仍是 ycmn 系（改名窗口未开）。
#   若在此状态下直接跑生产部署脚本，会发生**静默空转** —— 最危险的一处是
#   deploy-frontend.sh：`mkdir -p $NAS_BACKUP_DIR/dist-rollback-...` 会**新建**目标目录，
#   随后 `rm -rf $NAS_DIST_DIR/* && tar xzf ... -C $NAS_DIST_DIR` 在一个
#   **新建的空目录**里"成功"完成，而生产实际服务目录毫无变化 —— 脚本却报成功。
#
# 本门禁在**任何破坏性动作之前**断言"env 指向的路径与生产实际一致"，
# 不一致则 fail-closed 退出，并给出两种解除方式。
#
# 用法：在各 prod 部署脚本的破坏性动作之前调用 assert_prod_env_pinned。
# 仅做字符串比较，无副作用、不接触网络。
# ============================================================
assert_prod_env_pinned() {
  local expected_slug="ycmn" expected_db="ycmn" expected_dir="/root/ycmn"
  local expected_dist="/Aigicrm/files/ycmn-dist" expected_data="/data/ycmn"
  local bad=0

  [ "${APP_SLUG:-}" = "$expected_slug" ] || {
    echo "❌ PROD_ENV_MISMATCH: APP_SLUG='${APP_SLUG:-}' 但生产实际是 '$expected_slug'" >&2; bad=1; }
  [ "${DB_NAME:-}" = "$expected_db" ] || {
    echo "❌ PROD_ENV_MISMATCH: DB_NAME='${DB_NAME:-}' 但生产实际是 '$expected_db'" >&2; bad=1; }
  [ "${DEPLOY_DIR:-}" = "$expected_dir" ] || {
    echo "❌ PROD_ENV_MISMATCH: DEPLOY_DIR='${DEPLOY_DIR:-}' 但生产实际是 '$expected_dir'" >&2; bad=1; }
  [ "${NAS_DIST_DIR:-}" = "$expected_dist" ] || {
    echo "❌ PROD_ENV_MISMATCH: NAS_DIST_DIR='${NAS_DIST_DIR:-}' 但生产实际是 '$expected_dist'" >&2; bad=1; }
  [ "${DEPLOY_DATA_DIR:-}" = "$expected_data" ] || {
    echo "⚠️  PROD_ENV_NOTE: DEPLOY_DATA_DIR='${DEPLOY_DATA_DIR:-}'，生产记录为 '$expected_data'（软链目标，请确认）" >&2; }

  if [ "$bad" -ne 0 ]; then
    cat >&2 <<'EOF'

⛔ 已中止：部署 env 指向"改名后的目标态"，而生产尚未改名。
   继续执行会造成**静默空转**（最坏情形：前端脚本在新建的空目录里"成功"，
   生产实际目录未变，脚本却报成功）。

   两种解除方式（二选一）：
   (A) 钉回生产现值（推荐，改名另排窗口）—— 在 ~/.aigicrm-deploy.env 中设置：
         APP_SLUG=ycmn
         DB_NAME=ycmn
         DEPLOY_DIR=/root/ycmn
         DEPLOY_DATA_DIR=/data/ycmn
         NAS_DIST_DIR=/Aigicrm/files/ycmn-dist
   (B) 先执行改名窗口（见 PROD-RENAME-PLAN-NEXT-VERSION.md §4 runbook），
       改名完成后再把上述值切回 aigicrm 系。

   参考：PROD-RENAME-PLAN-NEXT-VERSION.md §0 / §2 / §5
EOF
    echo "PROD_ENV_GUARD=FAIL" >&2
    return 1
  fi

  # 注意：变量后紧跟全角标点时必须用 ${VAR} —— macOS bash 3.2 不会把全角字符
  # 当作变量名边界，$VAR）会被解析成变量名 "VAR）" 而报 unbound variable。
  echo "✅ 生产 env 已与生产现值一致（APP_SLUG=${APP_SLUG} DB_NAME=${DB_NAME} DEPLOY_DIR=${DEPLOY_DIR} NAS_DIST_DIR=${NAS_DIST_DIR}）"
  return 0
}


# 当前源码 commit(用于部署记录)
git_commit() {
  cd "${SOURCE_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}" && git rev-parse --short HEAD 2>/dev/null || echo "unknown"
}
