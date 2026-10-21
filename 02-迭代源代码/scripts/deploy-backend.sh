#!/bin/bash
# 后端部署: 从测试服务器提取已验证 jar → 上传生产 → 重建镜像 → 重启 → 健康检查
#
# 【ops-3 2026-10-17 fail-closed 修复】
#   1) 旧 jar 备份失败不再吞掉（原 `cp ... 2>/dev/null && echo || echo` 把 cp 失败伪装成"首次部署"）；
#   2) 上传先落 .upload-<TS> 临时文件，校验大小/md5 后 mv 原子替换 app.jar；
#   3) 构建/启动/健康检查任一步失败，自动用已备份 jar 重建并重启，脚本返回非零；
#   4) 等待启动改为有限次数（不再无限 until），远端全部 set -euo pipefail。
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

# 【2026-09-12】生产环境前置硬门禁（见 deploy-lib.sh: assert_prod_env_pinned）
assert_prod_env_pinned
# 【ops-3】架构自检改为 fail-closed（见 deploy-lib.sh: check_deploy_arch）
check_deploy_arch
# 【D6 2026-10-21】破坏性动作(备份覆盖/容器重建)前只读 TLS 预检：健康检查已去 -k，
# 证书链在部署机系统 CA 下不可校验时在此失败，避免"部署成功→健康检查必败→误触发自动回滚"。
check_prod_tls

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
# 【2026-10-07 忘记密码修复】短信凭据必须存在——占位值会让 sendSms 必败(忘记密码验证码 500)。
# 真实凭据来自部署 env 的 OSS_ACCESS_KEY_ID/OSS_ACCESS_KEY_SECRET(RAM 用户 aigicrm-sms,
# 仅授 AliyunDysmsFullAccess；密钥名沿用 OSS 前缀因 SmsConfig 复用 OssConfig 凭据)。
if [ -z "${OSS_ACCESS_KEY_ID:-}" ] || [ -z "${OSS_ACCESS_KEY_SECRET:-}" ] \
   || [ "${OSS_ACCESS_KEY_ID}" = "your-key" ] || [ "${OSS_ACCESS_KEY_SECRET}" = "your-secret" ]; then
  echo "错误: 缺少或占位的 OSS_ACCESS_KEY_ID/OSS_ACCESS_KEY_SECRET（短信凭据，请在部署 env 配置）" >&2
  exit 1
fi

TS="$(date +%Y%m%d-%H%M%S)"
LOCAL_JAR="/tmp/${APP_SLUG}-backend-new-${TS}.jar"
REMOTE_STAGE="${DEPLOY_DIR}/.app.jar.upload-${TS}"
BACKUP_JAR="${NAS_BACKUP_DIR}/${APP_SLUG}-backend-jar-${TS}.jar"

cleanup_local() { rm -f "$LOCAL_JAR"; }
trap cleanup_local EXIT

# 把本地配置安全地写成远端 bash 可 source 的赋值（printf %q 处理空格/引号/特殊字符；
# 脚本经 stdin 传给远端，避免把 JWT/DB 口令放进 ssh 命令行 argv 被 ps 看到）。
emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_remote_env() {
  emit_var APP_SLUG "$APP_SLUG"
  emit_var DEPLOY_DIR "$DEPLOY_DIR"
  emit_var NAS_BACKUP_DIR "$NAS_BACKUP_DIR"
  emit_var BACKEND_TAG "$BACKEND_TAG"
  emit_var DB_NAME "$DB_NAME"
  emit_var APP_DB_USER "$APP_DB_USER"
  emit_var APP_DB_PASSWORD "$APP_DB_PASSWORD"
  emit_var JWT_SECRET "$JWT_SECRET"
  emit_var OSS_ACCESS_KEY_ID "$OSS_ACCESS_KEY_ID"
  emit_var OSS_ACCESS_KEY_SECRET "$OSS_ACCESS_KEY_SECRET"
  emit_var OSS_BUCKET_NAME "${OSS_BUCKET_NAME:-aigicrm-files}"
  emit_var BACKUP_JAR "$BACKUP_JAR"
  emit_var REMOTE_STAGE "$REMOTE_STAGE"
}

echo "=== [1/5] 从测试服务器提取已验证 jar (commit: $COMMIT) ==="
ssh_test "docker cp ${APP_SLUG}-backend:/app/app.jar /tmp/${APP_SLUG}-backend-new-${TS}.jar"
scp_test "${TEST_USER}@${TEST_HOST}:/tmp/${APP_SLUG}-backend-new-${TS}.jar" "$LOCAL_JAR"
SIZE=$(stat -f%z "$LOCAL_JAR")
echo "jar 大小: $SIZE 字节 ($(echo "scale=1; $SIZE/1024/1024" | bc)MB)"
if [ "$SIZE" -lt 50000000 ]; then
  echo "❌ jar 异常偏小,中止" >&2
  exit 1
fi
# jar 本质是 zip：校验魔数，防止把空文件/HTML 错误页当 jar 发布。
if [ "$(head -c 2 "$LOCAL_JAR")" != "PK" ]; then
  echo "❌ 提取物不是有效 jar(zip) 文件,中止" >&2
  exit 1
fi
LOCAL_MD5="$(md5 -q "$LOCAL_JAR")"
echo "本地 jar md5: $LOCAL_MD5"

echo "=== [2/5] 备份旧 jar（失败即中止）并上传 ==="
# 2026-08-09 复盘：必须先备份旧 jar 再覆盖。ops-3: 备份 cp/校验失败必须终止，
# 不允许再以 "(无旧 jar 可备份,首次部署)" 吞掉失败。首次部署需显式
# AIGICRM_ALLOW_FIRST_BACKEND_DEPLOY=1。
{
  echo 'set -Eeuo pipefail'
  emit_remote_env
  emit_var ALLOW_FIRST "${AIGICRM_ALLOW_FIRST_BACKEND_DEPLOY:-0}"
  cat <<'REMOTE_BACKUP'
trap 'rm -f "$REMOTE_STAGE" "$DEPLOY_DIR/app.jar.restore-tmp"' EXIT
if [ -s "$DEPLOY_DIR/app.jar" ]; then
  mkdir -p "$NAS_BACKUP_DIR"
  cp -p "$DEPLOY_DIR/app.jar" "$BACKUP_JAR"
  [ -s "$BACKUP_JAR" ] || { echo "❌ 旧 jar 备份为空: $BACKUP_JAR" >&2; exit 1; }
  OLD_MD5=$(md5sum "$DEPLOY_DIR/app.jar" | cut -d' ' -f1 | tr -d '\r\n ')
  BAK_MD5=$(md5sum "$BACKUP_JAR" | cut -d' ' -f1 | tr -d '\r\n ')
  [ -n "$OLD_MD5" ] && [ "$OLD_MD5" = "$BAK_MD5" ] \
    || { echo "❌ 旧 jar 备份校验失败(old=$OLD_MD5 backup=$BAK_MD5)" >&2; exit 1; }
  echo "旧 jar 已备份: $BACKUP_JAR (md5=$BAK_MD5)"
else
  if [ "$ALLOW_FIRST" != "1" ]; then
    echo "❌ 生产无旧 jar 可备份；若确属首次部署，请显式设置 AIGICRM_ALLOW_FIRST_BACKEND_DEPLOY=1" >&2
    exit 1
  fi
  echo "⚠️ 首次部署：无旧 jar 可备份（AIGICRM_ALLOW_FIRST_BACKEND_DEPLOY=1）"
fi
REMOTE_BACKUP
} | ssh_prod_once "bash -s"

scp_prod "$LOCAL_JAR" "${PROD_USER}@${PROD_HOST}:$REMOTE_STAGE"

echo "=== [3/5] 校验上传并原子替换, 重建镜像 (jar md5 门禁) ==="
{
  echo 'set -Eeuo pipefail'
  emit_remote_env
  emit_var LOCAL_MD5 "$LOCAL_MD5"
  cat <<'REMOTE_BUILD'
SWITCHED=0
build_image() {
  cd "$DEPLOY_DIR"
  if ! docker build -q -t "${APP_SLUG}-backend:${BACKEND_TAG}" -f Dockerfile-run . >/tmp/dbuild.out 2>&1; then
    tail -5 /tmp/dbuild.out >&2
    return 1
  fi
  tail -3 /tmp/dbuild.out >&2 || true
}
image_jar_md5() {
  docker run --rm --entrypoint sh "${APP_SLUG}-backend:${BACKEND_TAG}" -c 'md5sum /app/app.jar' 2>/dev/null \
    | cut -d' ' -f1 | tr -d '\r\n '
}
start_backend() {
  docker stop "${APP_SLUG}-backend" >/dev/null 2>&1 || true
  docker rm "${APP_SLUG}-backend" >/dev/null 2>&1 || true
  # 【D3 2026-10-21】机密(DB/JWT/OSS 口令)经 0600 env-file 注入，不进 docker run argv
  #（旧写法把明文口令作为 -e KEY=VALUE 参数写进远端 docker 进程 argv，同机 ps 可见）。
  local envf
  envf="$(mktemp /tmp/aigicrm-backend-env.XXXXXX)"
  chmod 600 "$envf"
  {
    printf 'DB_PASSWORD=%s\n' "$APP_DB_PASSWORD"
    printf 'JWT_SECRET=%s\n' "$JWT_SECRET"
    printf 'OSS_ACCESS_KEY_ID=%s\n' "$OSS_ACCESS_KEY_ID"
    printf 'OSS_ACCESS_KEY_SECRET=%s\n' "$OSS_ACCESS_KEY_SECRET"
  } > "$envf"
  if ! docker run -d --name "${APP_SLUG}-backend" --network "${APP_SLUG}_default" --restart always \
    -p 127.0.0.1:8080:8080 \
    --env-file "$envf" \
    -e DB_HOST=mysql -e DB_PORT=3306 -e DB_NAME="$DB_NAME" -e DB_USER="$APP_DB_USER" \
    -e REDIS_HOST=redis -e REDIS_PORT=6379 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e TENANT_ENABLE=true \
    -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
    -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_BUCKET_NAME="$OSS_BUCKET_NAME" \
    -e CORS_ORIGINS=https://www.aigicrm.com \
    -v /Aigicrm/files/uploads:/app/uploads \
    -v "${APP_SLUG}-logs:/app/logs" \
    "${APP_SLUG}-backend:${BACKEND_TAG}" >/dev/null; then
    rm -f "$envf"
    echo "❌ docker run 后端容器失败" >&2
    return 1
  fi
  rm -f "$envf"
  # 上传文件必须在 NAS；误挂旧 upload-data 卷会造成"文件消失"假象
  if ! docker inspect "${APP_SLUG}-backend" --format '{{range .Mounts}}{{.Source}} {{end}}' | grep -q '/Aigicrm/files/uploads'; then
    echo "❌ 上传挂载不是 NAS 路径 /Aigicrm/files/uploads" >&2
    return 1
  fi
}
wait_started() {
  local i
  for i in $(seq 1 60); do
    if [ "$(docker inspect -f '{{.State.Running}}' "${APP_SLUG}-backend" 2>/dev/null)" = "true" ] \
       && docker logs "${APP_SLUG}-backend" 2>&1 | grep 'Started AigicrmApplication' >/dev/null; then
      return 0
    fi
    sleep 5
  done
  echo "❌ 后端 300 秒内未就绪" >&2
  docker logs "${APP_SLUG}-backend" --tail 50 >&2 2>&1 || true
  return 1
}
rollback_to_backup() {
  [ -s "$BACKUP_JAR" ] || { echo "❌ 备份 jar 不存在/为空: $BACKUP_JAR" >&2; return 1; }
  echo "↩ 使用备份 jar 回滚: $BACKUP_JAR" >&2
  cp -p "$BACKUP_JAR" "$DEPLOY_DIR/app.jar" || return 1
  build_image || return 1
  local exp act
  exp=$(md5sum "$BACKUP_JAR" | cut -d' ' -f1 | tr -d '\r\n ')
  act=$(image_jar_md5)
  [ "$exp" = "$act" ] || { echo "❌ 回滚镜像内 jar 校验不符(exp=$exp act=$act)" >&2; return 1; }
  start_backend || return 1
  wait_started || return 1
}
on_error() {
  rc=$?
  trap - ERR
  rm -f "$REMOTE_STAGE"
  if [ "$SWITCHED" = "1" ]; then
    echo "❌ 部署失败(阶段退出码 $rc)，自动回滚到备份 jar ..." >&2
    if rollback_to_backup; then
      echo "↩ 已回滚旧 jar 并重启，服务已恢复" >&2
    else
      echo "❌ 自动回滚失败，需人工介入：备份 jar=$BACKUP_JAR" >&2
    fi
  fi
  exit "$rc"
}
trap on_error ERR

# --- 上传产物校验 + 原子替换 ---
[ -s "$REMOTE_STAGE" ] || { echo "❌ 远端上传文件不存在/为空: $REMOTE_STAGE" >&2; exit 1; }
REMOTE_MD5=$(md5sum "$REMOTE_STAGE" | cut -d' ' -f1 | tr -d '\r\n ')
[ "$REMOTE_MD5" = "$LOCAL_MD5" ] || { echo "❌ 上传 jar md5 不符(本地=$LOCAL_MD5 远端=$REMOTE_MD5)" >&2; exit 1; }
mv -f "$REMOTE_STAGE" "$DEPLOY_DIR/app.jar"
SWITCHED=1
echo "app.jar 已原子替换 (md5=$REMOTE_MD5)"

# --- 构建 + 镜像内 jar 校验（2026-10-06 事故硬门禁）---
build_image
IMAGE_MD5=$(image_jar_md5)
echo "jar 校验: 本地=$LOCAL_MD5 镜像内=$IMAGE_MD5"
[ "$LOCAL_MD5" = "$IMAGE_MD5" ] \
  || { echo "❌ 镜像内 jar 与本次上传不一致(构建吃了陈旧文件/COPY 源错位), 中止部署" >&2; exit 1; }

# --- 重启 + 有限等待 ---
start_backend
wait_started
echo "后端容器已启动"
REMOTE_BUILD
} | ssh_prod_once "bash -s"

echo "=== [4/5] 公网健康检查（失败自动回滚） ==="
if ! check_health_retry; then
  echo "❌ 健康检查失败，正在回滚到备份 jar ..." >&2
  {
    echo 'set -Eeuo pipefail'
    emit_remote_env
    cat <<'REMOTE_ROLLBACK'
build_image() {
  cd "$DEPLOY_DIR"
  docker build -q -t "${APP_SLUG}-backend:${BACKEND_TAG}" -f Dockerfile-run . >/tmp/dbuild.out 2>&1 || { tail -5 /tmp/dbuild.out >&2; return 1; }
}
start_backend() {
  docker stop "${APP_SLUG}-backend" >/dev/null 2>&1 || true
  docker rm "${APP_SLUG}-backend" >/dev/null 2>&1 || true
  # 【D3 2026-10-21】同上：机密经 0600 env-file 注入，不进 docker run argv
  local envf
  envf="$(mktemp /tmp/aigicrm-backend-env.XXXXXX)"
  chmod 600 "$envf"
  {
    printf 'DB_PASSWORD=%s\n' "$APP_DB_PASSWORD"
    printf 'JWT_SECRET=%s\n' "$JWT_SECRET"
    printf 'OSS_ACCESS_KEY_ID=%s\n' "$OSS_ACCESS_KEY_ID"
    printf 'OSS_ACCESS_KEY_SECRET=%s\n' "$OSS_ACCESS_KEY_SECRET"
  } > "$envf"
  if ! docker run -d --name "${APP_SLUG}-backend" --network "${APP_SLUG}_default" --restart always \
    -p 127.0.0.1:8080:8080 \
    --env-file "$envf" \
    -e DB_HOST=mysql -e DB_PORT=3306 -e DB_NAME="$DB_NAME" -e DB_USER="$APP_DB_USER" \
    -e REDIS_HOST=redis -e REDIS_PORT=6379 -e SPRING_PROFILES_ACTIVE=prod -e TENANT_ENABLE=true \
    -e UPLOAD_DIR=/app/uploads -e STORAGE_TYPE=local -e TZ=Asia/Shanghai \
    -e OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com -e OSS_BUCKET_NAME="$OSS_BUCKET_NAME" \
    -e CORS_ORIGINS=https://www.aigicrm.com \
    -v /Aigicrm/files/uploads:/app/uploads -v "${APP_SLUG}-logs:/app/logs" \
    "${APP_SLUG}-backend:${BACKEND_TAG}" >/dev/null; then
    rm -f "$envf"
    echo "❌ docker run 后端容器失败" >&2
    return 1
  fi
  rm -f "$envf"
}
wait_started() {
  local i
  for i in $(seq 1 60); do
    if [ "$(docker inspect -f '{{.State.Running}}' "${APP_SLUG}-backend" 2>/dev/null)" = "true" ] \
       && docker logs "${APP_SLUG}-backend" 2>&1 | grep 'Started AigicrmApplication' >/dev/null; then
      return 0
    fi
    sleep 5
  done
  docker logs "${APP_SLUG}-backend" --tail 50 >&2 2>&1 || true
  return 1
}
[ -s "$BACKUP_JAR" ] || { echo "❌ 备份 jar 不存在，无法回滚" >&2; exit 1; }
cp -p "$BACKUP_JAR" "$DEPLOY_DIR/app.jar"
build_image
[ "$(md5sum "$BACKUP_JAR" | cut -d' ' -f1 | tr -d '\r\n ')" = \
  "$(docker run --rm --entrypoint sh "${APP_SLUG}-backend:${BACKEND_TAG}" -c 'md5sum /app/app.jar' | cut -d' ' -f1 | tr -d '\r\n ')" ] \
  || { echo "❌ 回滚镜像内 jar 校验不符" >&2; exit 1; }
start_backend
wait_started
echo "↩ 已回滚旧 jar 并重启"
REMOTE_ROLLBACK
  } | ssh_prod_once "bash -s" || {
    echo "❌ 自动回滚失败，需人工介入：备份 jar=$BACKUP_JAR" >&2
    exit 1
  }
  if ! check_health_retry; then
    echo "❌ 回滚后健康检查仍失败，需人工介入" >&2
  fi
  exit 1
fi

echo "=== [5/5] 部署记录 ==="
ssh_prod "echo \"$(date +%Y-%m-%d\ %H:%M:%S) backend deploy commit=$COMMIT\" >> $DEPLOY_DIR/deploy.log"
echo "✅ 后端部署完成 (commit: $COMMIT, backup: $BACKUP_JAR)"
