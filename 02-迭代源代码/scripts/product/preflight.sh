#!/usr/bin/env bash
# B1 交付壳 · 安装前依赖/资源预检（只读，不启动容器、不改系统）。
# 用法:
#   bash scripts/product/preflight.sh [--env-file PATH] [--ports 18080,13306,16379]
# 退出码: 0=全部通过; 1=有失败项; 2=参数错误。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE=""
PORTS="18080,13306,16379"
SKIP_PORTS=0
FAIL=0
PASS=0

usage() {
  sed -n '2,7p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

ok() { echo "PASS: $1"; PASS=$((PASS + 1)); }
bad() { echo "FAIL: $1" >&2; FAIL=$((FAIL + 1)); }
warn() { echo "WARN: $1" >&2; }

while [ $# -gt 0 ]; do
  case "$1" in
    --env-file) [ $# -ge 2 ] || { echo "--env-file 缺路径" >&2; exit 2; }; ENV_FILE="$2"; shift 2 ;;
    --ports) [ $# -ge 2 ] || { echo "--ports 缺值" >&2; exit 2; }; PORTS="$2"; shift 2 ;;
    --skip-ports) SKIP_PORTS=1; shift ;;
    -h|--help) usage ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

echo "== AIGICRM 安装预检 =="
echo "repo: $ROOT"

for cmd in docker curl openssl python3; do
  if command -v "$cmd" >/dev/null 2>&1; then ok "命令存在: $cmd"; else bad "缺少命令: $cmd"; fi
done

if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then ok "Docker daemon 可连接"; else bad "Docker daemon 不可连接（检查 Docker Desktop/权限）"; fi
  if docker compose version >/dev/null 2>&1; then
    ok "Docker Compose v2 可用（$(docker compose version --short 2>/dev/null || echo unknown)）"
  else
    bad "Docker Compose v2 不可用（需要 docker compose，不是 docker-compose）"
  fi
fi

# 磁盘：安装目录所在分区至少 5GB 可用
if command -v df >/dev/null 2>&1; then
  AVAIL_KB="$(df -Pk "$ROOT" | awk 'NR==2 {print $4}')"
  if [ -n "${AVAIL_KB:-}" ] && [ "$AVAIL_KB" -ge 5242880 ]; then
    ok "磁盘可用 $((AVAIL_KB / 1024 / 1024))GB >= 5GB"
  else
    bad "磁盘可用不足 5GB（当前 ${AVAIL_KB:-unknown}KB）"
  fi
fi

# 内存：macOS 用 sysctl；Linux 用 /proc/meminfo；至少 4GB
MEM_BYTES=""
if command -v sysctl >/dev/null 2>&1 && sysctl -n hw.memsize >/dev/null 2>&1; then
  MEM_BYTES="$(sysctl -n hw.memsize)"
elif [ -r /proc/meminfo ]; then
  MEM_BYTES="$(awk '/MemTotal/ {print $2 * 1024}' /proc/meminfo)"
fi
if [ -n "${MEM_BYTES:-}" ] && [ "$MEM_BYTES" -ge 4294967296 ]; then
  ok "内存 $((MEM_BYTES / 1024 / 1024 / 1024))GB >= 4GB"
else
  warn "未能确认内存 >= 4GB（${MEM_BYTES:-unknown} bytes）；请人工确认"
fi

# 端口：默认 18080/13306/16379，避开生产 80/443/3306/8080
if [ "$SKIP_PORTS" = "1" ]; then
  warn "跳过端口检查（安装器检测到同项目容器已存在，按幂等重跑处理）"
else
IFS=',' read -r -a PORT_ARR <<< "$PORTS"
for p in "${PORT_ARR[@]}"; do
  case "$p" in ''|*[!0-9]*) bad "非法端口: $p"; continue ;; esac
  if command -v lsof >/dev/null 2>&1; then
    if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then bad "端口被占用: $p"; else ok "端口可用: $p"; fi
  elif command -v nc >/dev/null 2>&1; then
    if nc -z 127.0.0.1 "$p" >/dev/null 2>&1; then bad "端口被占用: $p"; else ok "端口可用: $p"; fi
  else
    warn "无 lsof/nc，跳过端口检查: $p"
  fi
done
fi

# 可选：env 文件必需键（不打印值）
if [ -n "$ENV_FILE" ]; then
  if [ ! -r "$ENV_FILE" ]; then
    bad "env 文件不可读: $ENV_FILE"
  else
    for key in DB_PASSWORD APP_DB_USER APP_DB_PASSWORD JWT_SECRET TENANT_ENABLE; do
      if grep -Eq "^[[:space:]]*${key}=.+" "$ENV_FILE"; then ok "env 键存在: $key"; else bad "env 缺键: $key"; fi
    done
    if grep -Eq '^[[:space:]]*TENANT_ENABLE=(true|TRUE|1)[[:space:]]*$' "$ENV_FILE"; then
      ok "TENANT_ENABLE=true（生产租户 fail-closed 要求）"
    else
      bad "TENANT_ENABLE 必须为 true"
    fi
    if grep -Eq '^[[:space:]]*JWT_SECRET=.{32,}' "$ENV_FILE"; then
      ok "JWT_SECRET 长度 >= 32"
    else
      bad "JWT_SECRET 长度不足 32（jjwt HMAC 256 位要求）"
    fi
  fi
fi

echo
echo "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
