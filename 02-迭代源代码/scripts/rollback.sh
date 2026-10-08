#!/bin/bash
# 一键回滚: 从前端 dist / 后端 jar / 数据库 dump 从 NAS 备份恢复
# 用法: ./rollback.sh <db|frontend|backend|all> [备份日期]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

TARGET="${1:-all}"
TS="${2:-latest}"
# 应用最小权限 DB 账号必须存在（N1-B：应用不用 root 连库）
[ -n "${APP_DB_USER:-}" ] && [ -n "${APP_DB_PASSWORD:-}" ] || { echo "错误: 缺少 APP_DB_USER/APP_DB_PASSWORD（请在部署 env 配置）" >&2; exit 1; }

rollback_db() {
  echo "=== 回滚数据库 (从 NAS 备份) ==="
  ssh_prod "
    latest=\$(ls -t $NAS_BACKUP_DIR/daily/*/${APP_SLUG}-db-*.sql $NAS_BACKUP_DIR/daily/*/aigicrm-db-*.sql 2>/dev/null | head -1)
    [ -z \"\$latest\" ] && { echo '❌ 无可用数据库备份'; exit 1; }
    echo \"使用备份: \$latest\"
    docker exec -i ${APP_SLUG}-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 $DB_NAME < \"\$latest\"
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
  # 【IH6/IL2 延伸·2026-10-08 复核遗留修复】回滚同样是生产执行路径，必须与 deploy-backend.sh 同口径：
  #   - CORS 按 09-22 收敛裁决（权威=仓库 docker-compose.prod.yml 的 https://www.aigicrm.com）——
  #     旧参数会在回滚时把 localhost:5173/3000 调试源重新带回生产 docker run；
  #   - 镜像 tag 引用 deploy-lib.sh 的 BACKEND_TAG 单一来源（原 2.7.0 硬编码，部署侧升版后此处会指向旧 tag）。
  ssh_prod "
    jar=\$(ls -t $NAS_BACKUP_DIR/${APP_SLUG}-backend-jar-*.jar $NAS_BACKUP_DIR/aigicrm-backend-jar-*.jar 2>/dev/null | head -1)
    [ -z \"\$jar\" ] && { echo '❌ 无可用 jar 备份'; exit 1; }
    cp \$jar $DEPLOY_DIR/app.jar
    cd $DEPLOY_DIR && docker build -q -t ${APP_SLUG}-backend:${BACKEND_TAG} -f Dockerfile-run .
    docker stop ${APP_SLUG}-backend && docker rm ${APP_SLUG}-backend
    docker run -d --name ${APP_SLUG}-backend --network ${APP_SLUG}_default --restart always \
      -p 127.0.0.1:8080:8080 \
      -e DB_HOST=mysql -e DB_PORT=3306 -e DB_NAME="$DB_NAME" -e DB_USER="$APP_DB_USER" -e DB_PASSWORD="$APP_DB_PASSWORD" \
      -e REDIS_HOST=redis -e REDIS_PORT=6379 \
      -e SPRING_PROFILES_ACTIVE=prod \
      -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
      -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_ACCESS_KEY_ID=your-key \
      -e OSS_ACCESS_KEY_SECRET=your-secret -e OSS_BUCKET_NAME=your-bucket \
      -e CORS_ORIGINS=https://www.aigicrm.com \
      -e JWT_SECRET="$JWT_SECRET" \
      -v /Aigicrm/files/uploads:/app/uploads \
      ${APP_SLUG}-backend:${BACKEND_TAG}
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
until ssh_prod "docker logs ${APP_SLUG}-backend 2>&1 | grep -q 'Started AigicrmApplication'" 2>/dev/null; do
  echo "  等待后端启动..."; sleep 8
done
check_health
echo "✅ 回滚完成"
