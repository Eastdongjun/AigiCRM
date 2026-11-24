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
# 【GL-06 2026-11-24】FILE_GRANT_MODE 必须在部署 env 里显式设置(生产现值为 new)。
# 未设置即中止 —— 旧写法默认 legacy, 部署或自动回滚会把生产静默降回旧链。
assert_file_grant_mode
# 【GL-09 2026-11-24】制品溯源: 当前 HEAD 必须是冻结清单登记的提交, 工作区必须干净。
#   从测试服取回的 jar 还要过指纹门(见 [1/5] 末尾的 assert_backend_artifact), 在备份与上传之前。
assert_release_manifest
assert_worktree_clean

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

# 【退出码守卫 2026-11-24】macOS 自带的 bash 3.2 有一个缺陷: set -e 与 EXIT 陷阱同时存在时, 未定义变量(set -u)
#   引起的中止会以退出码 0 结束 —— 部署中途死掉, 调用方却看到"成功"。陷阱里也拿不到真实退出码, 只能看
#   "是否走到了最后一行": 没走到却要退出, 一律按失败。DONE=1 只在脚本最后一行设置。
DONE=0
cleanup_local() { rm -f "$LOCAL_JAR"; }
on_exit() {
  local rc=$?
  cleanup_local || echo "⚠️ 本地临时文件清理失败(不影响本次结论)" >&2
  if [ "$rc" -eq 0 ] && [ "${DONE:-0}" -ne 1 ]; then
    echo "❌ 脚本在收尾之前异常终止(退出码被 bash 3.2 吞成 0), 按失败处理" >&2
    rc=1
  fi
  exit "$rc"
}
trap on_exit EXIT

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
# 【GL-09 2026-11-24】这个 jar 必须是冻结清单登记的那个版本(md5 与 sha256 都要对上), 否则不进入备份与上传。
# 此前只校验它"像个 jar"和"上传前后一致"; 部署日志里的 commit 记的是本机 HEAD, 与 jar 实际由哪个提交构建无关。
assert_backend_artifact "$LOCAL_JAR"

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
  emit_var FILE_GRANT_MODE "$FILE_GRANT_MODE"
  # 【GL-05 2026-11-24】build_image / start_backend / wait_started / rollback_to_backup 等远端函数
  # 来自 deploy-lib.sh 的单一出处(健康检查失败后的回滚块与 rollback.sh 引用同一份), 此前各处内联且已漂移。
  emit_remote_backend_runtime
  cat <<'REMOTE_BUILD'
SWITCHED=0
on_error() {
  rc=$?
  trap - ERR
  rm -f "$REMOTE_STAGE"
  echo "RESULT=FAIL rc=$rc" >&2
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
    # 【GL-05 2026-11-24】回滚用的远端函数与正向部署是同一份(deploy-lib.sh)。此前这里另抄了一份,
    # 漏了 FILE_GRANT_MODE、导出目录及其挂载 —— 自动回滚后的容器会与正向部署的容器行为不同。
    emit_remote_backend_runtime
    cat <<'REMOTE_ROLLBACK'
[ -s "$BACKUP_JAR" ] || { echo "❌ 备份 jar 不存在，无法回滚" >&2; exit 1; }
rollback_to_backup
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
echo "RESULT=PASS"
DONE=1
