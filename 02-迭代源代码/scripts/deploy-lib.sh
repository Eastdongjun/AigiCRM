#!/bin/bash
# YCMN 部署公共库: 凭据加载 + ssh/scp 封装 + 健康检查
# 注意: 库文件不设 errexit(调用方自行控制); 仅 unset 变量与管道检查
set -uo pipefail

# 加载凭据(不入 git)
ENV_FILE="$HOME/.ycmn-deploy.env"
if [ ! -f "$ENV_FILE" ]; then
  echo "错误: 缺少 $ENV_FILE (复制 scripts/ycmn-deploy.env.example 并填写)" >&2
  exit 1
fi
source "$ENV_FILE"

# DB 凭据必须存在（禁止脚本内硬编码，一律经 env 注入）
[ -n "${DB_PASSWORD:-}" ] || { echo "错误: 缺少 DB_PASSWORD（请在 $ENV_FILE 配置）" >&2; exit 1; }

# 健康检查/冒烟登录凭据 —— 值在 ~/.ycmn-deploy.env (HEALTH_CHECK_USER/HEALTH_CHECK_PASS)，禁止硬编码入仓。
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
    sshpass -p "$PROD_PASS" ssh $SSH_OPTS "${PROD_USER}@${PROD_HOST}" "$@" && return 0
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
scp_prod()  { sshpass -p "$PROD_PASS" scp $SSH_OPTS "$@"; }
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
  ssh_prod 'bash -s' <<'ARCH_CHECK'
R=$(docker info --format "{{.DockerRootDir}}" 2>/dev/null)
[ "$R" = "/data/docker" ] && echo "✅ docker 根=/data/docker" || echo "⚠️ docker 根=$R (标准: /data/docker)"
M=$(docker inspect ycmn-mysql --format "{{range .Mounts}}{{.Source}} {{end}}" 2>/dev/null | tr " " "\n" | grep -c "^/data/database/mysql-data$")
[ "$M" = "1" ] && echo "✅ mysql 数据=/data/database/mysql-data" || echo "⚠️ mysql 数据不在 /data/database/mysql-data"
L=$(readlink /root/ycmn 2>/dev/null)
[ "$L" = "/data/ycmn" ] && echo "✅ 部署目录软链 /root/ycmn → /data/ycmn" || echo "⚠️ /root/ycmn 非 /data/ycmn 软链 (当前: $L)"
U=$(docker inspect ycmn-backend --format "{{range .Mounts}}{{.Source}} {{end}}" 2>/dev/null | tr " " "\n" | grep -c "^/Aigicrm/files/uploads$")
[ "$U" = "1" ] && echo "✅ 上传文件=NAS /Aigicrm/files/uploads" || echo "⚠️ 上传文件不在 NAS"
ARCH_CHECK
  return 0
}

# 当前源码 commit(用于部署记录)
git_commit() {
  cd "/Users/donglai/东来/01-Projects/01-aigicrm/04-源码" && git rev-parse --short HEAD 2>/dev/null || echo "unknown"
}
