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

SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=20 -o StrictHostKeyChecking=no}"

ssh_prod()  { sshpass -p "$PROD_PASS" ssh $SSH_OPTS "${PROD_USER}@${PROD_HOST}" "$@"; }
ssh_test()  { sshpass -p "$TEST_PASS" ssh $SSH_OPTS "${TEST_USER}@${TEST_HOST}" "$@"; }
scp_prod()  { sshpass -p "$PROD_PASS" scp $SSH_OPTS "$@"; }
scp_test()  { sshpass -p "$TEST_PASS" scp $SSH_OPTS "$@"; }

# 生产健康检查: 登录 API 带 Origin 必须 200
check_health() {
  local base="${1:-https://www.aigicrm.com}"
  local code
  code=$(curl -sk -o /dev/null -w "%{http_code}" -X POST "$base/api/auth/login" \
    -H "Origin: $base" -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' --connect-timeout 10)
  if [ "$code" = "200" ]; then
    echo "✅ 健康检查通过 (登录 API $code)"
    return 0
  else
    echo "❌ 健康检查失败 (登录 API $code)" >&2
    return 1
  fi
}

# 当前源码 commit(用于部署记录)
git_commit() {
  cd "/Users/donglai/东来/01-Projects/01-aigicrm/04-源码" && git rev-parse --short HEAD 2>/dev/null || echo "unknown"
}
