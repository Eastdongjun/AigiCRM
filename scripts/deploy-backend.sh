#!/bin/bash
# 后端部署: 从测试服务器提取已验证 jar → 上传生产 → 重建镜像 → 重启 → 健康检查
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

COMMIT=$(git_commit)
# JWT_SECRET 必须存在（否则应用启动失败）
if [ -z "${JWT_SECRET:-}" ]; then
  echo "错误: 缺少 JWT_SECRET（请在 ~/.ycmn-deploy.env 配置）" >&2
  exit 1
fi
# 应用最小权限 DB 账号必须存在（2026-08-08 N1-B：应用不再用 root 连库）
if [ -z "${APP_DB_USER:-}" ] || [ -z "${APP_DB_PASSWORD:-}" ]; then
  echo "错误: 缺少 APP_DB_USER/APP_DB_PASSWORD（应用最小权限账号，请在 ~/.ycmn-deploy.env 配置）" >&2
  exit 1
fi

echo "=== [1/5] 从测试服务器提取已验证 jar (commit: $COMMIT) ==="
ssh_test "docker cp ycmn-backend:/app/app.jar /tmp/ycmn-backend-new.jar"
scp_test "${TEST_USER}@${TEST_HOST}:/tmp/ycmn-backend-new.jar" /tmp/ycmn-backend-new.jar
SIZE=$(stat -f%z /tmp/ycmn-backend-new.jar)
echo "jar 大小: $SIZE 字节 ($(echo "scale=1; $SIZE/1024/1024" | bc)MB)"
[ "$SIZE" -lt 50000000 ] && { echo "❌ jar 异常偏小,中止"; exit 1; }

echo "=== [2/5] 备份旧 jar 并上传生产 ==="
# 先备份旧 jar 再覆盖（2026-08-09 复盘：原顺序先覆盖后备份，备份到的是新 jar）
ssh_prod "cp /root/ycmn/ycmn-backend-1.0.0.jar $NAS_BACKUP_DIR/ycmn-backend-jar-$(date +%Y%m%d-%H%M%S).jar 2>/dev/null && echo '旧 jar 已备份到 NAS' || echo '(无旧 jar 可备份,首次部署)'"
scp_prod /tmp/ycmn-backend-new.jar "${PROD_USER}@${PROD_HOST}:/root/ycmn/ycmn-backend-1.0.0.jar"
ssh_prod "ls -la /root/ycmn/ycmn-backend-1.0.0.jar"

echo "=== [3/5] 重建镜像 ==="
ssh_prod "cd /root/ycmn && docker build -q -t ycmn-backend:2.7.0 -f Dockerfile-run . 2>&1 | tail -1"

echo "=== [4/5] 重启后端容器 ==="
ssh_prod "
  docker stop ycmn-backend && docker rm ycmn-backend
  docker run -d --name ycmn-backend --network ycmn_default --restart always \
    -p 127.0.0.1:8080:8080 \
    -e DB_HOST=mysql -e DB_PORT=3306 -e DB_USER="$APP_DB_USER" -e DB_PASSWORD="$APP_DB_PASSWORD" \
    -e REDIS_HOST=redis -e REDIS_PORT=6379 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
    -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_ACCESS_KEY_ID=your-key \
    -e OSS_ACCESS_KEY_SECRET=your-secret -e OSS_BUCKET_NAME=your-bucket \
    -e CORS_ORIGINS=https://www.aigicrm.com,http://localhost:5173,http://localhost:3000 \
    -e JWT_SECRET="$JWT_SECRET" \
    -v upload-data:/app/uploads \
    -v ycmn-logs:/app/logs \
    ycmn-backend:2.7.0
  echo '等待启动...'
"
echo "=== [5/5] 启动确认 + 健康检查 ==="
until ssh_prod "docker logs ycmn-backend 2>&1 | grep -q 'Started YcmnApplication'"; do
  echo "  等待后端启动..."; sleep 8
done
ssh_prod "docker logs ycmn-backend 2>&1 | grep -E 'Started YcmnApplication' | tail -1"
check_health
ssh_prod "echo \"$(date +%Y-%m-%d\ %H:%M:%S) backend deploy commit=$COMMIT\" >> /root/ycmn/deploy.log"
echo "✅ 后端部署完成 (commit: $COMMIT)"
