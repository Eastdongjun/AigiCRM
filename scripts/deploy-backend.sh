#!/bin/bash
# 后端部署: 从测试服务器提取已验证 jar → 上传生产 → 重建镜像 → 重启 → 健康检查
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

COMMIT=$(git_commit)
echo "=== [1/5] 从测试服务器提取已验证 jar (commit: $COMMIT) ==="
ssh_test "docker cp ycmn-backend:/app/app.jar /tmp/ycmn-backend-new.jar"
scp_test "${TEST_USER}@${TEST_HOST}:/tmp/ycmn-backend-new.jar" /tmp/ycmn-backend-new.jar
SIZE=$(stat -f%z /tmp/ycmn-backend-new.jar)
echo "jar 大小: $SIZE 字节 ($(echo "scale=1; $SIZE/1024/1024" | bc)MB)"
[ "$SIZE" -lt 50000000 ] && { echo "❌ jar 异常偏小,中止"; exit 1; }

echo "=== [2/5] 上传生产并备份旧 jar ==="
scp_prod /tmp/ycmn-backend-new.jar "${PROD_USER}@${PROD_HOST}:/root/ycmn/ycmn-backend-1.0.0.jar"
ssh_prod "cp /root/ycmn/ycmn-backend-1.0.0.jar $NAS_BACKUP_DIR/ycmn-backend-jar-$(date +%Y%m%d-%H%M%S).jar 2>/dev/null; ls -la /root/ycmn/ycmn-backend-1.0.0.jar"

echo "=== [3/5] 重建镜像 ==="
ssh_prod "cd /root/ycmn && docker build -q -t ycmn-backend:2.7.0 -f Dockerfile-run . 2>&1 | tail -1"

echo "=== [4/5] 重启后端容器 ==="
ssh_prod "
  docker stop ycmn-backend && docker rm ycmn-backend
  docker run -d --name ycmn-backend --network ycmn_default --restart always \
    -p 127.0.0.1:8080:8080 \
    -e DB_HOST=mysql -e DB_PORT=3306 -e DB_USER=root -e DB_PASSWORD=<redacted> \
    -e REDIS_HOST=redis -e REDIS_PORT=6379 \
    -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
    -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_ACCESS_KEY_ID=your-key \
    -e OSS_ACCESS_KEY_SECRET=your-secret -e OSS_BUCKET_NAME=your-bucket \
    -e CORS_ORIGINS=https://www.aigicrm.com,http://localhost:5173,http://localhost:3000 \
    -v upload-data:/app/uploads \
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
