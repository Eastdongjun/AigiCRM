#!/bin/bash
# 后端部署: 从测试服务器提取已验证 jar → 上传生产 → 重建镜像 → 重启 → 健康检查
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

# 【2026-09-12】生产环境前置硬门禁（见 deploy-lib.sh: assert_prod_env_pinned）
assert_prod_env_pinned
check_deploy_arch

# 【IL2·2026-10-08 审查修复】生产镜像 tag 单一来源 BACKEND_TAG 定义在 deploy-lib.sh——
# 2026-10-08 复核遗留修复时上提：rollback.sh 回滚重建同样引用，杜绝"部署新版、回滚旧 tag"错位。

COMMIT=$(git_commit)
# JWT_SECRET 必须存在（否则应用启动失败）
if [ -z "${JWT_SECRET:-}" ]; then
  echo "错误: 缺少 JWT_SECRET（请在部署 env 配置）" >&2
  exit 1
fi
# 应用最小权限 DB 账号必须存在（2026-08-08 N1-B：应用不再用 root 连库）
if [ -z "${APP_DB_USER:-}" ] || [ -z "${APP_DB_PASSWORD:-}" ]; then
  echo "错误: 缺少 APP_DB_USER/APP_DB_PASSWORD（应用最小权限账号，请在部署 env 配置）" >&2
  exit 1
fi

echo "=== [1/5] 从测试服务器提取已验证 jar (commit: $COMMIT) ==="
ssh_test "docker cp ${APP_SLUG}-backend:/app/app.jar /tmp/${APP_SLUG}-backend-new.jar"
scp_test "${TEST_USER}@${TEST_HOST}:/tmp/${APP_SLUG}-backend-new.jar" "/tmp/${APP_SLUG}-backend-new.jar"
SIZE=$(stat -f%z "/tmp/${APP_SLUG}-backend-new.jar")
echo "jar 大小: $SIZE 字节 ($(echo "scale=1; $SIZE/1024/1024" | bc)MB)"
[ "$SIZE" -lt 50000000 ] && { echo "❌ jar 异常偏小,中止"; exit 1; }

echo "=== [2/5] 备份旧 jar 并上传生产 ==="
# 先备份旧 jar 再覆盖（2026-08-09 复盘：原顺序先覆盖后备份，备份到的是新 jar）
ssh_prod "cp $DEPLOY_DIR/app.jar $NAS_BACKUP_DIR/${APP_SLUG}-backend-jar-$(date +%Y%m%d-%H%M%S).jar 2>/dev/null && echo '旧 jar 已备份到 NAS' || echo '(无旧 jar 可备份,首次部署)'"
scp_prod "/tmp/${APP_SLUG}-backend-new.jar" "${PROD_USER}@${PROD_HOST}:$DEPLOY_DIR/app.jar"
ssh_prod "ls -la $DEPLOY_DIR/app.jar"

echo "=== [3/5] 重建镜像 ==="
# 【IM1·2026-10-08 审查修复】旧写法 `docker build ... 2>&1 | tail -1` 管道尾吞掉构建退出码——
# 构建失败时脚本照样往下走（同 jar 重部署场景下 md5 门禁可能侥幸通过，继续跑旧镜像）。
# 现改为远端落临时文件再取码：构建输出重定向到 /tmp/dbuild.out（含 stderr），构建后打印
# 该文件尾 3 行，并以构建退出码退出 ssh 命令（ssh 会透传远端退出码，本脚本 set -e 即中止；
# md5 门禁之后照跑，作双保险）。
ssh_prod "cd $DEPLOY_DIR && docker build -q -t ${APP_SLUG}-backend:${BACKEND_TAG} -f Dockerfile-run . >/tmp/dbuild.out 2>&1; rc=\$?; tail -3 /tmp/dbuild.out; exit \$rc"

# 【2026-10-06 事故硬门禁】镜像内 jar 校验和必须等于刚上传的 jar——
# 根因: 服务器 Dockerfile-run 的 COPY 文件名与上传文件名不一致(app.jar vs ycmn-backend-1.0.0.jar),
# docker build 静默使用构建目录里的陈旧 jar, 9-23 起多次"部署成功"实际都在发旧代码(15:27 才修复)。
# 本校验让任何 COPY 源错位立刻 fail-closed, 而不是无声上旧包。
LOCAL_MD5=$(md5 -q "/tmp/${APP_SLUG}-backend-new.jar")
IMAGE_MD5=$(ssh_prod "docker run --rm --entrypoint sh ${APP_SLUG}-backend:${BACKEND_TAG} -c 'md5sum /app/app.jar' | cut -d' ' -f1" | tr -d '\r\n ')
echo "jar 校验: 本地=${LOCAL_MD5} 镜像内=${IMAGE_MD5}"
if [ "$LOCAL_MD5" != "$IMAGE_MD5" ]; then
  echo "❌ 镜像内 jar 与本次上传不一致(构建吃了陈旧文件/COPY 源错位), 中止部署" >&2
  exit 1
fi

echo "=== [4/5] 重启后端容器 ==="
# 【IH6·2026-10-08 审查修复】CORS 按 09-22 收敛裁决（权威=仓库 02-迭代源代码/docker-compose.prod.yml
# 的 CORS_ORIGINS: https://www.aigicrm.com）落到 docker run 执行路径——生产由 docker run 直起，
# 此前参数仍带 localhost:5173/3000 调试源，裁决形同未生效。
ssh_prod "
  docker stop ${APP_SLUG}-backend && docker rm ${APP_SLUG}-backend
  docker run -d --name ${APP_SLUG}-backend --network ${APP_SLUG}_default --restart always \
    -p 127.0.0.1:8080:8080 \
    -e DB_HOST=mysql -e DB_PORT=3306 -e DB_NAME="$DB_NAME" -e DB_USER="$APP_DB_USER" -e DB_PASSWORD="$APP_DB_PASSWORD" \
    -e REDIS_HOST=redis -e REDIS_PORT=6379 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e TENANT_ENABLE=true \
    -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
    -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_ACCESS_KEY_ID=your-key \
    -e OSS_ACCESS_KEY_SECRET=your-secret -e OSS_BUCKET_NAME=your-bucket \
    -e CORS_ORIGINS=https://www.aigicrm.com \
    -e JWT_SECRET="$JWT_SECRET" \
    -v /Aigicrm/files/uploads:/app/uploads \
    -v ${APP_SLUG}-logs:/app/logs \
    ${APP_SLUG}-backend:${BACKEND_TAG}
  echo '等待启动...'
  # 【2026-08-14 Wave B】上传文件已迁移 NAS（用户规划：数据盘放程序/NAS放文件）。
  # 历史 upload-data 卷保留为回滚副本，不再挂载——若误挂旧卷会导致 NAS 文件"消失"假象
  docker inspect ${APP_SLUG}-backend --format '{{range .Mounts}}{{.Source}} {{end}}' | grep -q '/Aigicrm/files/uploads' \
    || echo '⚠️ 警告: 上传挂载不是 NAS 路径 /Aigicrm/files/uploads，请检查'
"
echo "=== [5/5] 启动确认 + 健康检查 ==="
until ssh_prod "docker logs ${APP_SLUG}-backend 2>&1 | grep -q 'Started AigicrmApplication'"; do
  echo "  等待后端启动..."; sleep 8
done
ssh_prod "docker logs ${APP_SLUG}-backend 2>&1 | grep -E 'Started AigicrmApplication' | tail -1"
check_health
ssh_prod "echo \"$(date +%Y-%m-%d\ %H:%M:%S) backend deploy commit=$COMMIT\" >> $DEPLOY_DIR/deploy.log"
echo "✅ 后端部署完成 (commit: $COMMIT)"
