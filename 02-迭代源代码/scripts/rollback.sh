#!/bin/bash
# 一键回滚: 从前端 dist / 后端 jar / 数据库 dump 从 NAS 备份恢复
#
# 用法:
#   ./rollback.sh <db|frontend|backend|all> [备份选择器]
#
# 备份选择器（第 2 参数，默认 latest）:
#   latest            最近一次备份（默认）
#   YYYYMMDD          该日期目录下的最新一份（如 20261017）
#   /绝对/路径         明确指定远端备份文件/目录（db: *.sql[.gz]；frontend: dist 目录；backend: *.jar）
#   dist-rollback-*   前端可传 NAS_BACKUP_DIR 下的备份目录名
#   *.jar             后端可传 NAS_BACKUP_DIR 下的备份文件名
#
# 【ops-0 2026-10-17 fail-closed 修复】
#   - 本地/远端全部 set -euo pipefail；任一恢复步骤失败即非零退出，不再用末尾 echo 掩盖；
#   - DB root 口令从 mysql 容器环境 MYSQL_ROOT_PASSWORD 读取（原脚本误用应用账号 DB_PASSWORD，必然 1045）；
#   - 第 2 参数真正生效（日期/明确备份路径），并在恢复前校验存在/非空/格式；
#   - 恢复后校验 DB 表计数、后端 Started、公网健康检查与首页 body；任一失败不打印成功；
#   - 前端/后端切换使用原子替换；失败自动恢复切换前版本。
#
# 兼容性说明：远端脚本统一先写本地临时文件再 `ssh < file` 执行。macOS bash 3.2 对
# "$( { ...; cat <<EOF ... } | ssh )" 这类"命令替换内含 heredoc"存在解析缺陷（实测 bash -n 报错），
# 因此不使用该写法；临时文件 600 权限并在退出时删除。
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

TARGET="${1:-all}"
SEL="${2:-latest}"
RUN_TS="$(date +%Y%m%d-%H%M%S)"
REMOTE_SCRIPT=""

cleanup_remote_script() {
  if [ -n "$REMOTE_SCRIPT" ]; then rm -f "$REMOTE_SCRIPT"; fi
  return 0
}
trap cleanup_remote_script EXIT

case "$TARGET" in
  db|frontend|backend|all) ;;
  *) echo "用法: rollback.sh <db|frontend|backend|all> [latest|YYYYMMDD|远端绝对路径]" >&2; exit 2 ;;
esac

# 回滚与部署同属生产破坏性执行路径：先钉住 env 路径门禁（防止在改名未完成的错位目录上操作）。
# 注意：这里**不调用** check_deploy_arch —— 后端容器缺失/架构偏航正是需要回滚的场景，
# 架构自检会把"后端容器不在"判为偏差而阻断恢复；架构合规由部署前自检负责。
assert_prod_env_pinned
# 【D6】TLS 预检在回滚路径只告警不阻断：证书链损坏时恰恰可能正是需要回滚的事故现场，
# 不应被 TLS 预检挡住恢复动作；但去 -k 后的公网验证会因此失败并如实报错。
if ! check_prod_tls; then
  echo "⚠️ TLS 预检未通过：回滚仍可继续(恢复路径)，但回滚后公网验证可能因证书校验失败而报错" >&2
fi

emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_common_env() {
  emit_var APP_SLUG "$APP_SLUG"
  emit_var DB_NAME "$DB_NAME"
  emit_var NAS_DIST_DIR "$NAS_DIST_DIR"
  emit_var NAS_BACKUP_DIR "$NAS_BACKUP_DIR"
}

# 执行本地临时文件中的远端脚本，保留 stdout 供调用方解析；失败时返回远端退出码。
run_remote_script() {
  ssh_prod_once "bash -s" < "$1"
}

FRONTEND_ENTRY=""
FRONTEND_EXPECTED_SHA=""

rollback_db() {
  echo "=== 回滚数据库 (从 NAS 备份, 选择器: $SEL) ==="
  local rc
  REMOTE_SCRIPT="$(mktemp /tmp/aigicrm-rollback-db.XXXXXX)"
  {
    echo 'set -Eeuo pipefail'
    emit_common_env
    emit_var SEL "$SEL"
    cat <<'REMOTE'
MYSQL_C="${APP_SLUG}-mysql"
case "$SEL" in
  latest)
    dump=$(ls -t "$NAS_BACKUP_DIR"/daily/*/"${APP_SLUG}"-db-*.sql \
                "$NAS_BACKUP_DIR"/daily/*/aigicrm-db-*.sql 2>/dev/null | head -1 || true)
    ;;
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
    dump=$(ls -t "$NAS_BACKUP_DIR"/daily/"$SEL"/*-db-*.sql 2>/dev/null | head -1 || true)
    ;;
  /*)
    dump="$SEL"
    ;;
  *)
    echo "❌ 无法识别的备份选择器: $SEL (支持 latest/YYYYMMDD/远端绝对路径)" >&2
    exit 2
    ;;
esac
[ -n "$dump" ] && [ -f "$dump" ] && [ -s "$dump" ] \
  || { echo "❌ 备份文件不存在/为空: ${dump:-<未找到>}" >&2; exit 1; }
echo "使用数据库备份: $dump"

case "$dump" in
  *.gz) gzip -t "$dump" || { echo "❌ gzip 完整性校验失败: $dump" >&2; exit 1; }; READ_DUMP="gzip -dc" ;;
  *)    READ_DUMP="cat" ;;
esac
EXPECTED_TABLES="$($READ_DUMP "$dump" | grep -c '^CREATE TABLE' || true)"
case "$EXPECTED_TABLES" in
  ''|*[!0-9]*) echo "❌ 无法统计备份中的 CREATE TABLE" >&2; exit 1 ;;
esac
[ "$EXPECTED_TABLES" -gt 0 ] \
  || { echo "❌ 备份内容不像 SQL dump(无 CREATE TABLE): $dump" >&2; exit 1; }
echo "备份内含 CREATE TABLE: $EXPECTED_TABLES"

# root 口令只从容器环境读取（不落源码、不写日志）；缺失即失败，不回退到应用账号口令
ROOT_PW="$(docker exec "$MYSQL_C" printenv MYSQL_ROOT_PASSWORD 2>/dev/null || true)"
[ -n "$ROOT_PW" ] \
  || { echo "❌ 无法从容器 $MYSQL_C 读取 MYSQL_ROOT_PASSWORD，拒绝猜测口令" >&2; exit 1; }
[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_C" 2>/dev/null || true)" = "true" ] \
  || { echo "❌ MySQL 容器未运行: $MYSQL_C" >&2; exit 1; }
docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_C" mysql -uroot -N -B -e 'SELECT 1' >/dev/null \
  || { echo "❌ 容器内 root 口令连通性校验失败" >&2; exit 1; }

# 【D1 2026-10-21 审计修复】导入前先给当前生产库留快照——三条恢复路径中此前唯独 DB 没有
# "回滚前快照"（frontend 有 cp -a dist、backend 有 cp -p app.jar）。选错备份/备份过旧直接覆盖
# 现网且无退路。快照写到 NAS 备份目录，先落 .tmp 并校验(非空 + CREATE TABLE>0)后才 mv，
# 失败即 exit 1（现网未被改动）。
SNAP="$NAS_BACKUP_DIR/pre-restore-$(date +%Y%m%d-%H%M%S).sql"
mkdir -p "$NAS_BACKUP_DIR"
echo "回滚前生产库快照: $SNAP"
if ! docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_C" \
     mysqldump -uroot --default-character-set=utf8mb4 --single-transaction "$DB_NAME" > "$SNAP.tmp"; then
  rm -f "$SNAP.tmp"
  echo "❌ 回滚前快照 mysqldump 失败，拒绝导入(现网未被改动)" >&2
  exit 1
fi
SNAP_CT="$(grep -c '^CREATE TABLE' "$SNAP.tmp" || true)"
if [ ! -s "$SNAP.tmp" ]; then
  rm -f "$SNAP.tmp"
  echo "❌ 回滚前快照为空，拒绝导入" >&2
  exit 1
fi
case "$SNAP_CT" in
  ''|*[!0-9]*)
    rm -f "$SNAP.tmp"
    echo "❌ 无法统计回滚前快照中的 CREATE TABLE(ct=${SNAP_CT:-<空>})，拒绝导入" >&2
    exit 1
    ;;
esac
[ "$SNAP_CT" -gt 0 ] || { rm -f "$SNAP.tmp"; echo "❌ 回滚前快照无 CREATE TABLE(不像 SQL dump)，拒绝导入" >&2; exit 1; }
mv -f "$SNAP.tmp" "$SNAP"
echo "快照校验通过: CREATE TABLE=$SNAP_CT ($(wc -c < "$SNAP" | tr -d '[:space:]') bytes)"

echo "开始恢复（失败即中止）..."
$READ_DUMP "$dump" | docker exec -i -e MYSQL_PWD="$ROOT_PW" "$MYSQL_C" \
  mysql -uroot --default-character-set=utf8mb4 "$DB_NAME"

TABLES="$(docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_C" mysql -uroot -N -B \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME'")"
KEY_TABLES="$(docker exec -e MYSQL_PWD="$ROOT_PW" "$MYSQL_C" mysql -uroot -N -B \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name IN ('sys_user','biz_customer')")"
case "$TABLES$KEY_TABLES" in
  ''|*[!0-9]*) echo "❌ 恢复后表计数查询返回非数字(tables=$TABLES key=$KEY_TABLES)" >&2; exit 1 ;;
esac
[ "$TABLES" -gt 0 ] || { echo "❌ 恢复后库 $DB_NAME 无表" >&2; exit 1; }
[ "$TABLES" -ge "$EXPECTED_TABLES" ] \
  || { echo "❌ 恢复后表数($TABLES)少于 dump 中 CREATE TABLE($EXPECTED_TABLES)" >&2; exit 1; }
[ "$KEY_TABLES" = "2" ] \
  || { echo "❌ 关键表缺失(sys_user/biz_customer 命中 $KEY_TABLES/2)" >&2; exit 1; }
echo "DB_ROLLBACK_OK dump=$dump tables=$TABLES expected_create=$EXPECTED_TABLES pre_restore_snapshot=$SNAP"
REMOTE
  } > "$REMOTE_SCRIPT"
  set +e
  REMOTE_OUT="$(run_remote_script "$REMOTE_SCRIPT")"
  rc=$?
  set -e
  rm -f "$REMOTE_SCRIPT"; REMOTE_SCRIPT=""
  printf '%s\n' "$REMOTE_OUT"
  if [ "$rc" -ne 0 ]; then
    echo "❌ 数据库回滚失败(退出码 $rc)" >&2
    exit "$rc"
  fi
}

rollback_frontend() {
  echo "=== 回滚前端 (从 NAS dist 备份, 选择器: $SEL) ==="
  local rc
  REMOTE_SCRIPT="$(mktemp /tmp/aigicrm-rollback-frontend.XXXXXX)"
  {
    echo 'set -Eeuo pipefail'
    emit_common_env
    emit_var SEL "$SEL"
    emit_var RUN_TS "$RUN_TS"
    cat <<'REMOTE'
case "$SEL" in
  latest)
    bak=$(ls -dt "$NAS_BACKUP_DIR"/dist-rollback-[0-9]* 2>/dev/null | head -1 || true)
    ;;
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
    bak=$(ls -dt "$NAS_BACKUP_DIR"/dist-rollback-"$SEL"* 2>/dev/null | head -1 || true)
    ;;
  /*)
    bak="$SEL"
    ;;
  dist-rollback-*)
    bak="$NAS_BACKUP_DIR/$SEL"
    ;;
  *)
    echo "❌ 无法识别的备份选择器: $SEL" >&2
    exit 2
    ;;
esac
[ -n "$bak" ] && [ -d "$bak" ] && [ -s "$bak/index.html" ] \
  || { echo "❌ dist 备份不存在/缺少 index.html: ${bak:-<未找到>}" >&2; exit 1; }
ENTRY=$(grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' "$bak/index.html" | head -1)
[ -n "$ENTRY" ] && [ -s "$bak/$ENTRY" ] \
  || { echo "❌ dist 备份入口资源缺失: $bak/$ENTRY" >&2; exit 1; }
EXPECTED_SHA=$(sha256sum "$bak/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
echo "使用 dist 备份: $bak (entry=$ENTRY sha256=$EXPECTED_SHA)"

STAGE="${NAS_DIST_DIR%/}.restore-$RUN_TS"
PRE="$NAS_BACKUP_DIR/dist-rollback-pre-rollback-$RUN_TS"
SWITCHED=0
cleanup() { rm -rf "$STAGE"; }
restore_entry() {
  local src="$PRE" name f
  [ -s "$src/index.html" ] || src="$bak"
  for name in index.html version.txt; do
    if [ -s "$src/$name" ]; then
      f="$NAS_DIST_DIR/.$name.rollback.$RUN_TS"
      cp -a "$src/$name" "$f"
      mv -f "$f" "$NAS_DIST_DIR/$name"
    fi
  done
}
on_error() {
  rc=$?
  trap - ERR
  if [ "$SWITCHED" = "1" ]; then
    echo "❌ 回滚切换后步骤失败(退出码 $rc)，恢复切换前入口 ..." >&2
    restore_entry || echo "❌ 入口恢复失败，请人工使用 $PRE 或 $bak 回滚" >&2
  else
    echo "❌ 回滚切换前步骤失败(退出码 $rc)，线上入口未改动" >&2
  fi
  exit "$rc"
}
trap cleanup EXIT
trap on_error ERR

# 先给当前线上 dist 留快照（失败即中止；若线上本就无 index.html 则无快照可留）
mkdir -p "$NAS_DIST_DIR"
if [ -s "$NAS_DIST_DIR/index.html" ]; then
  mkdir -p "$PRE"
  cp -a "$NAS_DIST_DIR/." "$PRE/"
  [ -s "$PRE/index.html" ] || { echo "❌ 回滚前快照缺少 index.html" >&2; exit 1; }
  CUR_SHA=$(sha256sum "$NAS_DIST_DIR/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
  SNAP_SHA=$(sha256sum "$PRE/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
  [ "$CUR_SHA" = "$SNAP_SHA" ] \
    || { echo "❌ 回滚前快照校验不符(cur=$CUR_SHA snap=$SNAP_SHA)" >&2; exit 1; }
  echo "回滚前 dist 快照: $PRE"
fi

# 暂存区校验后再落盘；资源先就位，入口最后原子替换
mkdir -p "$STAGE"
cp -a "$bak/." "$STAGE/"
[ -s "$STAGE/index.html" ] || { echo "❌ 暂存备份缺少 index.html" >&2; exit 1; }
[ -s "$STAGE/$ENTRY" ] || { echo "❌ 暂存备份缺少入口资源 $ENTRY" >&2; exit 1; }
tar -C "$STAGE" --exclude='./index.html' --exclude='./version.txt' -cf - . | tar -C "$NAS_DIST_DIR" -xf -
mv -f "$STAGE/index.html" "$NAS_DIST_DIR/.index.html.restore.$RUN_TS"
mv -f "$NAS_DIST_DIR/.index.html.restore.$RUN_TS" "$NAS_DIST_DIR/index.html"
SWITCHED=1
if [ -s "$STAGE/version.txt" ]; then
  cp -a "$STAGE/version.txt" "$NAS_DIST_DIR/.version.txt.restore.$RUN_TS"
  mv -f "$NAS_DIST_DIR/.version.txt.restore.$RUN_TS" "$NAS_DIST_DIR/version.txt"
fi
LIVE_SHA=$(sha256sum "$NAS_DIST_DIR/index.html" | cut -d' ' -f1 | tr -d '\r\n ')
[ "$LIVE_SHA" = "$EXPECTED_SHA" ] \
  || { echo "❌ 线上 index.html 与备份 sha256 不符(期望=$EXPECTED_SHA 实际=$LIVE_SHA)" >&2; exit 1; }
echo "FRONTEND_ROLLBACK_OK from=$bak entry=$ENTRY expected_sha=$EXPECTED_SHA live_sha=$LIVE_SHA"
REMOTE
  } > "$REMOTE_SCRIPT"
  set +e
  REMOTE_OUT="$(run_remote_script "$REMOTE_SCRIPT")"
  rc=$?
  set -e
  rm -f "$REMOTE_SCRIPT"; REMOTE_SCRIPT=""
  printf '%s\n' "$REMOTE_OUT"
  if [ "$rc" -ne 0 ]; then
    echo "❌ 前端回滚失败(退出码 $rc)" >&2
    exit "$rc"
  fi
  FRONTEND_ENTRY="$(printf '%s\n' "$REMOTE_OUT" | sed -n 's/.* entry=\([^ ]*\) .*/\1/p' | tail -1)"
  FRONTEND_EXPECTED_SHA="$(printf '%s\n' "$REMOTE_OUT" | sed -n 's/.* expected_sha=\([0-9a-f]*\) .*/\1/p' | tail -1)"
}

rollback_backend() {
  echo "=== 回滚后端 (旧 jar 从 NAS 备份恢复, 选择器: $SEL) ==="
  [ -n "${APP_DB_USER:-}" ] && [ -n "${APP_DB_PASSWORD:-}" ] \
    || { echo "错误: 缺少 APP_DB_USER/APP_DB_PASSWORD（请在部署 env 配置）" >&2; exit 1; }
  [ -n "${JWT_SECRET:-}" ] \
    || { echo "错误: 缺少 JWT_SECRET（请在部署 env 配置）" >&2; exit 1; }
  if [ -z "${OSS_ACCESS_KEY_ID:-}" ] || [ -z "${OSS_ACCESS_KEY_SECRET:-}" ]; then
    echo "⚠️ 未配置 OSS 凭据：回滚容器可启动，但短信/OSS 功能可能不可用" >&2
  fi

  local rc
  REMOTE_SCRIPT="$(mktemp /tmp/aigicrm-rollback-backend.XXXXXX)"
  {
    echo 'set -Eeuo pipefail'
    emit_common_env
    emit_var BACKEND_TAG "$BACKEND_TAG"
    emit_var APP_DB_USER "$APP_DB_USER"
    emit_var APP_DB_PASSWORD "$APP_DB_PASSWORD"
    emit_var JWT_SECRET "$JWT_SECRET"
    emit_var OSS_ACCESS_KEY_ID "${OSS_ACCESS_KEY_ID:-}"
    emit_var OSS_ACCESS_KEY_SECRET "${OSS_ACCESS_KEY_SECRET:-}"
    emit_var OSS_BUCKET_NAME "${OSS_BUCKET_NAME:-aigicrm-files}"
    emit_var SEL "$SEL"
    emit_var RUN_TS "$RUN_TS"
    cat <<'REMOTE'
case "$SEL" in
  latest)
    jar=$(ls -t "$NAS_BACKUP_DIR"/"${APP_SLUG}"-backend-jar-*.jar \
               "$NAS_BACKUP_DIR"/aigicrm-backend-jar-*.jar 2>/dev/null | head -1 || true)
    ;;
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
    jar=$(ls -t "$NAS_BACKUP_DIR"/*backend-jar-"$SEL"*.jar 2>/dev/null | head -1 || true)
    ;;
  /*)
    jar="$SEL"
    ;;
  *.jar)
    jar="$NAS_BACKUP_DIR/$SEL"
    ;;
  *)
    echo "❌ 无法识别的备份选择器: $SEL" >&2
    exit 2
    ;;
esac
[ -n "$jar" ] && [ -f "$jar" ] && [ -s "$jar" ] \
  || { echo "❌ jar 备份不存在/为空: ${jar:-<未找到>}" >&2; exit 1; }
[ "$(head -c 2 "$jar")" = "PK" ] \
  || { echo "❌ 备份不是有效 jar(zip): $jar" >&2; exit 1; }
JAR_MD5=$(md5sum "$jar" | cut -d' ' -f1 | tr -d '\r\n ')
echo "使用 jar 备份: $jar (md5=$JAR_MD5)"

if [ -s "$DEPLOY_DIR/app.jar" ]; then
  PREV_JAR="$NAS_BACKUP_DIR/${APP_SLUG}-backend-jar-pre-rollback-$RUN_TS.jar"
  mkdir -p "$NAS_BACKUP_DIR"
  cp -p "$DEPLOY_DIR/app.jar" "$PREV_JAR"
  [ -s "$PREV_JAR" ] || { echo "❌ 当前 jar 快照为空" >&2; exit 1; }
  [ "$(md5sum "$DEPLOY_DIR/app.jar" | cut -d' ' -f1 | tr -d '\r\n ')" = \
    "$(md5sum "$PREV_JAR" | cut -d' ' -f1 | tr -d '\r\n ')" ] \
    || { echo "❌ 当前 jar 快照校验失败" >&2; exit 1; }
  echo "当前 jar 快照: $PREV_JAR"
else
  PREV_JAR=""
  echo "⚠️ 当前无 app.jar 可快照（首次部署形态）"
fi

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
  #（旧写法把明文口令作为 -e KEY=VALUE 参数写进远端 docker 进程 argv，同机 ps 可见）；非机密项保持 -e 便于排查。
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
  docker inspect "${APP_SLUG}-backend" --format '{{range .Mounts}}{{.Source}} {{end}}' \
    | grep -q '/Aigicrm/files/uploads' \
    || { echo "❌ 上传挂载不是 NAS 路径 /Aigicrm/files/uploads" >&2; return 1; }
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
restore_previous() {
  if [ -n "$PREV_JAR" ] && [ -s "$PREV_JAR" ]; then
    echo "↩ 恢复回滚前 jar: $PREV_JAR" >&2
    cp -p "$PREV_JAR" "$DEPLOY_DIR/app.jar" || return 1
    build_image || return 1
    [ "$(md5sum "$PREV_JAR" | cut -d' ' -f1 | tr -d '\r\n ')" = "$(image_jar_md5)" ] || return 1
    start_backend || return 1
    wait_started || return 1
    return 0
  fi
  echo "❌ 无回滚前 jar 可恢复，需人工介入" >&2
  return 1
}
on_error() {
  rc=$?
  trap - ERR
  if [ "$SWITCHED" = "1" ]; then
    echo "❌ 回滚执行失败(退出码 $rc)，尝试恢复回滚前版本 ..." >&2
    restore_previous || echo "❌ 恢复回滚前版本失败，需人工介入（快照: ${PREV_JAR:-无}）" >&2
  else
    echo "❌ 替换前步骤失败(退出码 $rc)，app.jar 未改动" >&2
  fi
  exit "$rc"
}
trap on_error ERR

# 原子替换 app.jar：先落同目录临时文件并校验 md5，再 rename
cp -p "$jar" "$DEPLOY_DIR/.app.jar.rollback.$RUN_TS"
[ "$(md5sum "$DEPLOY_DIR/.app.jar.rollback.$RUN_TS" | cut -d' ' -f1 | tr -d '\r\n ')" = "$JAR_MD5" ] \
  || { echo "❌ 临时 jar 校验失败" >&2; exit 1; }
mv -f "$DEPLOY_DIR/.app.jar.rollback.$RUN_TS" "$DEPLOY_DIR/app.jar"
SWITCHED=1

build_image
IMAGE_MD5=$(image_jar_md5)
[ "$JAR_MD5" = "$IMAGE_MD5" ] \
  || { echo "❌ 回滚镜像内 jar 校验不符(jar=$JAR_MD5 image=$IMAGE_MD5)" >&2; exit 1; }
start_backend
wait_started
echo "BACKEND_ROLLBACK_OK jar=$jar md5=$JAR_MD5"
REMOTE
  } > "$REMOTE_SCRIPT"
  set +e
  REMOTE_OUT="$(run_remote_script "$REMOTE_SCRIPT")"
  rc=$?
  set -e
  rm -f "$REMOTE_SCRIPT"; REMOTE_SCRIPT=""
  printf '%s\n' "$REMOTE_OUT"
  if [ "$rc" -ne 0 ]; then
    echo "❌ 后端回滚失败(退出码 $rc)" >&2
    exit "$rc"
  fi
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
esac

echo "=== 回滚后验证（有限等待 + body 断言） ==="
wait_backend_started
check_health_retry

BODY_FILE="$(mktemp /tmp/aigicrm-rollback-check.XXXXXX)"
trap 'rm -f "$BODY_FILE"; cleanup_remote_script' EXIT
# 【D6 2026-10-21】去掉 -k：证书过期/域不符/被替换必须让回滚后验证失败(如实报错)；
# 部署机 CA 不含生产链时用 AIGICRM_CACERT 显式指定。
CODE="$(curl -s $CURL_CA_OPTS --connect-timeout 10 --max-time 30 -H 'Cache-Control: no-cache' \
  -o "$BODY_FILE" -w '%{http_code}' "https://www.aigicrm.com/?rollback=$RUN_TS")"
ENTRY_LIVE="$(grep -oE 'assets/index-[A-Za-z0-9_-]+\.js' "$BODY_FILE" 2>/dev/null | head -1 || true)"
[ "$CODE" = "200" ] \
  || { echo "❌ 回滚后首页 HTTP $CODE" >&2; exit 1; }
grep -q 'id="app"' "$BODY_FILE" \
  || { echo "❌ 回滚后首页 body 缺少 app 根节点" >&2; exit 1; }
[ -n "$ENTRY_LIVE" ] \
  || { echo "❌ 回滚后首页 body 未引用 assets/index-*.js" >&2; exit 1; }
[ "$(curl -s $CURL_CA_OPTS --connect-timeout 10 --max-time 30 -o /dev/null -w '%{http_code}' "https://www.aigicrm.com/$ENTRY_LIVE")" = "200" ] \
  || { echo "❌ 回滚后入口资源 $ENTRY_LIVE 非 200" >&2; exit 1; }
if [ -n "$FRONTEND_ENTRY" ]; then
  [ "$ENTRY_LIVE" = "$FRONTEND_ENTRY" ] \
    || { echo "❌ 回滚后首页入口($ENTRY_LIVE)与备份入口($FRONTEND_ENTRY)不一致" >&2; exit 1; }
fi
if [ -n "$FRONTEND_EXPECTED_SHA" ]; then
  BODY_SHA="$(shasum -a 256 "$BODY_FILE" | awk '{print $1}')"
  [ "$BODY_SHA" = "$FRONTEND_EXPECTED_SHA" ] \
    || { echo "❌ 回滚后首页 body sha256 与备份 index.html 不一致(期望=$FRONTEND_EXPECTED_SHA 实际=$BODY_SHA)" >&2; exit 1; }
fi

echo "✅ 回滚完成 (target=$TARGET selector=$SEL)"
