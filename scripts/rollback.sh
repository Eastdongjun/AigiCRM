#!/bin/bash
# 一键回滚: 从前端 dist / 后端 jar / 数据库 dump 从 NAS 备份恢复
# 用法: ./rollback.sh <db|frontend|backend|all> [备份日期]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

TARGET="${1:-all}"
TS="${2:-latest}"
# 应用最小权限 DB 账号必须存在（N1-B：应用不用 root 连库）
[ -n "${APP_DB_USER:-}" ] && [ -n "${APP_DB_PASSWORD:-}" ] || { echo "错误: 缺少 APP_DB_USER/APP_DB_PASSWORD（请在 ~/.ycmn-deploy.env 配置）" >&2; exit 1; }

rollback_db() {
  echo "=== 回滚数据库 (从 NAS 备份) ==="
  ssh_prod "
    latest=\$(ls -t $NAS_BACKUP_DIR/daily/*/ycmn-db-*.sql 2>/dev/null | head -1)
    [ -z \"\$latest\" ] && { echo '❌ 无可用数据库备份'; exit 1; }
    echo \"使用备份: \$latest\"
    docker exec -i ycmn-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 < \"\$latest\"
    echo '数据库已恢复'
  "
}

rollback_frontend() {
  echo "=== 回滚前端 (从 NAS dist 备份) ==="
  ssh_prod "
    bak=\$(ls -dt $NAS_BACKUP_DIR/dist-rollback-* 2>/dev/null | head -1)
    [ -z \"\$bak\" ] && { echo '❌ 无可用 dist 备份'; exit 1; }
    echo \"使用备份: \$bak\"
    rm -rf $NAS_DIST_DIR/* && cp -r \$bak/* $NAS_DIST_DIR/
    echo \"入口: \$(grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' $NAS_DIST_DIR/index.html)\"
  "
}

rollback_backend() {
  echo "=== 回滚后端 (旧 jar 从 NAS 备份恢复) ==="
  ssh_prod "
    jar=\$(ls -t $NAS_BACKUP_DIR/ycmn-backend-jar-*.jar 2>/dev/null | head -1)
    [ -z \"\$jar\" ] && { echo '❌ 无可用 jar 备份'; exit 1; }
    cp \$jar /root/ycmn/ycmn-backend-1.0.0.jar
    cd /root/ycmn && docker build -q -t ycmn-backend:2.7.0 -f Dockerfile-run .
    docker stop ycmn-backend && docker rm ycmn-backend
    docker run -d --name ycmn-backend --network ycmn_default --restart always \
      -p 127.0.0.1:8080:8080 \
      -e DB_HOST=mysql -e DB_PORT=3306 -e DB_USER="$APP_DB_USER" -e DB_PASSWORD="$APP_DB_PASSWORD" \
      -e REDIS_HOST=redis -e REDIS_PORT=6379 \
      -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
      -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_ACCESS_KEY_ID=your-key \
      -e OSS_ACCESS_KEY_SECRET=your-secret -e OSS_BUCKET_NAME=your-bucket \
      -e CORS_ORIGINS=https://www.aigicrm.com,http://localhost:5173,http://localhost:3000 \
      -e JWT_SECRET="$JWT_SECRET" \
      -v upload-data:/app/uploads \
      ycmn-backend:2.7.0
    echo '后端容器已重建'
  "
}

case "$TARGET" in
  db) rollback_db ;;
  frontend) rollback_frontend ;;
  backend) rollback_backend ;;
  all)
    rollback_db
    rollback_frontend
    rollback_backend
    ;;
  *) echo "用法: rollback.sh <db|frontend|backend|all>"; exit 1 ;;
esac

echo "=== 回滚后健康检查 ==="
until ssh_prod "docker logs ycmn-backend 2>&1 | grep -q 'Started YcmnApplication'" 2>/dev/null; do
  echo "  等待后端启动..."; sleep 8
done
check_health
echo "✅ 回滚完成"
