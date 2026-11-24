#!/usr/bin/env bash
# deploy-lib.sh「后端运行时单一出处」的行为测试(GL-05 / GL-06, 2026-11-24)
#
# 背景: deploy-backend.sh(正向部署、健康检查失败后的自动回滚)与 rollback.sh(手工回滚)此前各自内联
# 一份启动函数, 几份互不一致 —— 自动回滚缺 FILE_GRANT_MODE 与导出目录, rollback.sh 写死了错误的
# 数据库与缓存主机名, 且没有把 DEPLOY_DIR 传给远端脚本。本测试保证这几处只能引用 deploy-lib.sh 里的同一份定义。
#
# 验证内容:
#   ① deploy-lib.sh 提供 emit_remote_backend_runtime 与 assert_file_grant_mode;
#   ② 渲染出的远端函数语法有效, start_backend 只定义一次;
#   ③ 在 docker 桩里执行 start_backend: 参数集合符合预期, 机密不进命令行, 两个 NAS 挂载都被自检;
#   ④ 负例: FILE_GRANT_MODE 未传入就不启动(也不留下含机密的临时文件); 任一 NAS 挂载缺失即失败; 主机名不再写死;
#   ⑤ assert_file_grant_mode: 未设置或取值非法时拒绝, legacy / dual / new 放行;
#   ⑥ 源码级守卫: 两个入口脚本不得再内联后端 docker run, 都引用单一出处, 且在远端动作之前校验 FILE_GRANT_MODE;
#      rollback.sh 必须把 DEPLOY_DIR 与 FILE_GRANT_MODE 传给远端脚本。
#
# 全程不联网、不接触任何服务器: deploy-lib.sh 只在隔离的临时配置下被 source, 不执行任何 ssh。
# 机密类取值一律运行时拼装, 本文件不出现可命中的凭据形态(见 check-secret-scan.sh)。
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_SELF_DIR/.." && pwd)"
LIB="$ROOT/scripts/deploy-lib.sh"
DEPLOY_BACKEND="$ROOT/scripts/deploy-backend.sh"
ROLLBACK="$ROOT/scripts/rollback.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$LIB" ] || fail "缺少被测库 $LIB"
[ -f "$DEPLOY_BACKEND" ] || fail "缺少 $DEPLOY_BACKEND"
[ -f "$ROLLBACK" ] || fail "缺少 $ROLLBACK"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-deploy-runtime-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

# ---- 假值(运行时拼装) ----
FX_USER='zz9-deploy'"-user-61"
FX_DB='zz9-deploy'"-db-62"
FX_JWT='zz9-deploy'"-jwt-63"
FX_OSS_ID='zz9-deploy'"-oss-id-64"
FX_OSS_KEY='zz9-deploy'"-oss-key-65"
printf 'APP_SLUG=ycmn\nDB_NAME=ycmn\nDEPLOY_DIR=/root/ycmn\nAPP_DB_USER=%s\nJWT_SECRET=%s\n' "$FX_USER" "$FX_JWT" > "$TMP/deploy.env"
: > "$TMP/known_hosts"

# ---- 在隔离配置下加载被测库(不触发任何 ssh) ----
export AIGICRM_DEPLOY_ENV="$TMP/deploy.env" AIGICRM_KNOWN_HOSTS="$TMP/known_hosts" AIGICRM_TEST_ONLY=1
# shellcheck disable=SC1090
source "$LIB" || fail "deploy-lib.sh 在隔离配置下无法加载"

# ---- ① 入口存在 ----
declare -F emit_remote_backend_runtime >/dev/null 2>&1 || fail "deploy-lib.sh 未提供 emit_remote_backend_runtime(后端运行时单一出处)"
declare -F assert_file_grant_mode >/dev/null 2>&1 || fail "deploy-lib.sh 未提供 assert_file_grant_mode(GL-06: FILE_GRANT_MODE 必须显式)"

# ---- ② 渲染结果语法有效, start_backend 只有一份 ----
RUNTIME="$TMP/runtime.sh"
emit_remote_backend_runtime > "$RUNTIME"
bash -n "$RUNTIME" || fail "emit_remote_backend_runtime 的输出不是合法 bash"
[ "$(grep -c '^start_backend()' "$RUNTIME")" -eq 1 ] || fail "渲染结果里 start_backend 应只定义一次"

# ---- ③ / ④ 在 docker 桩里执行 ----
# 用法: run_in_stub <docker inspect 返回的挂载源列表> <FILE_GRANT_MODE, 传空串表示不传>
# 桩把每条 docker 命令的参数记进 argv.log; 临时目录单独指定, 以便核对"失败时不留机密文件"。
run_in_stub() {
  : > "$TMP/argv.log"
  rm -rf "$TMP/remote-tmp"; mkdir -p "$TMP/remote-tmp"
  (
    export STUB_ARGV_LOG="$TMP/argv.log" STUB_MOUNT_SOURCES="$1" TMPDIR="$TMP/remote-tmp"
    export APP_SLUG=ycmn DB_NAME=ycmn APP_DB_USER="$FX_USER" APP_DB_PASSWORD="$FX_DB" \
           JWT_SECRET="$FX_JWT" OSS_ACCESS_KEY_ID="$FX_OSS_ID" OSS_ACCESS_KEY_SECRET="$FX_OSS_KEY" \
           OSS_BUCKET_NAME=zz9-bucket BACKEND_TAG=t-abc
    if [ -n "$2" ]; then export FILE_GRANT_MODE="$2"; else unset FILE_GRANT_MODE; fi
    bash -c '
      set -Eeuo pipefail
      source "$1"
      docker() {
        printf "%s\n" "$*" >> "$STUB_ARGV_LOG"
        case "$1" in
          inspect) printf "%s \n" "$STUB_MOUNT_SOURCES" ;;
        esac
        return 0
      }
      start_backend
    ' _ "$RUNTIME"
  )
}
RUN_LINE_OF() { grep -E '^run ' "$TMP/argv.log" | head -1; }

rc=0; run_in_stub "/Aigicrm/files/uploads /Aigicrm/files/ycmn-exports" "new" || rc=$?
[ "$rc" -eq 0 ] || fail "正向: start_backend 在参数齐全时应当成功, 实际 rc=$rc"
LINE="$(RUN_LINE_OF)"
[ -n "$LINE" ] || fail "正向: docker 桩没有收到 run 调用"
for need in "-e DB_HOST=ycmn-mysql" "-e REDIS_HOST=ycmn-redis" "-e DB_NAME=ycmn" "-e DB_USER=$FX_USER" \
            "-e SPRING_PROFILES_ACTIVE=prod" "-e FILE_GRANT_MODE=new" "-e EXPORT_DIR=/app/exports" \
            "-e TENANT_ENABLE=true" "-e UPLOAD_DIR=/app/uploads" "-e CORS_ORIGINS=https://www.aigicrm.com" \
            "--network ycmn_default" "-p 127.0.0.1:8080:8080" "--restart always" \
            "-v /Aigicrm/files/uploads:/app/uploads" "-v /Aigicrm/files/ycmn-exports:/app/exports" \
            "-v ycmn-logs:/app/logs" "ycmn-backend:t-abc"; do
  printf '%s' "$LINE" | grep -Fq -- "$need" || fail "正向: docker run 缺少参数 [$need]; 实际: $LINE"
done
printf '%s' "$LINE" | grep -Fq -- "--env-file" || fail "正向: 机密必须经 env-file 注入 [--env-file]; 实际: $LINE"
for secret in "$FX_DB" "$FX_JWT" "$FX_OSS_ID" "$FX_OSS_KEY"; do
  grep -Fq -- "$secret" "$TMP/argv.log" && fail "机密取值出现在 docker 命令行里(同机 ps 可见)"
done
printf '%s' "$LINE" | grep -Eq -- '(DB_HOST=mysql|REDIS_HOST=redis)( |$)' && fail "主机名仍然写死: $LINE"
# 正向成功后, 含机密的临时 env-file 必须已被删除
[ -z "$(ls -A "$TMP/remote-tmp")" ] || fail "正向: start_backend 成功后仍在临时目录留下文件: $(ls -A "$TMP/remote-tmp")"

# 负例 1: FILE_GRANT_MODE 未传入 → 不启动, 且不留下任何文件
rc=0; run_in_stub "/Aigicrm/files/uploads /Aigicrm/files/ycmn-exports" "" || rc=$?
[ "$rc" -ne 0 ] || fail "负例: FILE_GRANT_MODE 未传入时 start_backend 应失败"
[ -z "$(RUN_LINE_OF)" ] || fail "负例: FILE_GRANT_MODE 未传入时不应执行 docker run"
[ -z "$(ls -A "$TMP/remote-tmp")" ] || fail "负例: FILE_GRANT_MODE 未传入时不应在临时目录留下含机密的文件"

# 负例 2 / 3: 任一 NAS 挂载缺失 → 失败
rc=0; run_in_stub "/Aigicrm/files/uploads " "new" || rc=$?
[ "$rc" -ne 0 ] || fail "负例: 导出目录未挂载时 start_backend 应失败"
rc=0; run_in_stub "/Aigicrm/files/ycmn-exports " "new" || rc=$?
[ "$rc" -ne 0 ] || fail "负例: 上传目录未挂载时 start_backend 应失败"

# ---- ⑤ assert_file_grant_mode ----
rc=0; ( unset FILE_GRANT_MODE; assert_file_grant_mode ) >/dev/null 2>&1 || rc=$?
[ "$rc" -ne 0 ] || fail "负例: FILE_GRANT_MODE 未设置时应被拒绝"
rc=0; ( FILE_GRANT_MODE=bogus; assert_file_grant_mode ) >/dev/null 2>&1 || rc=$?
[ "$rc" -ne 0 ] || fail "负例: FILE_GRANT_MODE 取值非法时应被拒绝"
for okv in legacy dual new; do
  rc=0; ( export FILE_GRANT_MODE="$okv"; assert_file_grant_mode ) >/dev/null 2>&1 || rc=$?
  [ "$rc" -eq 0 ] || fail "FILE_GRANT_MODE=$okv 是合法取值, 应放行"
done

# ---- ⑥ 源码级守卫 ----
for f in "$DEPLOY_BACKEND" "$ROLLBACK"; do
  n="$(grep -c 'docker run -d --name' "$f")"
  [ "$n" -eq 0 ] || fail "${f##*/} 仍有 $n 处内联的后端 docker run —— 启动参数只能来自 deploy-lib.sh 的单一出处"
done
[ "$(grep -c 'emit_remote_backend_runtime' "$DEPLOY_BACKEND")" -ge 2 ] \
  || fail "deploy-backend.sh 应在正向部署与健康检查失败回滚两处都引用 emit_remote_backend_runtime"
[ "$(grep -c 'emit_remote_backend_runtime' "$ROLLBACK")" -ge 1 ] \
  || fail "rollback.sh 应引用 emit_remote_backend_runtime"
grep -Eq 'emit_var DEPLOY_DIR ' "$ROLLBACK" || fail "rollback.sh 没有把 DEPLOY_DIR 传给远端脚本(远端 set -u 下必炸)"
grep -Eq 'emit_var FILE_GRANT_MODE ' "$ROLLBACK" || fail "rollback.sh 没有把 FILE_GRANT_MODE 传给远端脚本"
# deploy-backend.sh: 校验必须发生在任何远端动作之前
call_line="$(grep -nE '^[[:space:]]*assert_file_grant_mode[[:space:]]*$' "$DEPLOY_BACKEND" | head -1 | cut -d: -f1)"
[ -n "$call_line" ] || fail "deploy-backend.sh 必须调用 assert_file_grant_mode(单独一行, 调用方 set -e)"
first_remote="$(grep -nE '(^|[^a-z_])(ssh_prod|ssh_test|scp_prod|scp_test)(_once)?[[:space:]]' "$DEPLOY_BACKEND" | head -1 | cut -d: -f1)"
[ -n "$first_remote" ] || fail "deploy-backend.sh 里找不到远端调用, 无法判断校验顺序"
[ "$call_line" -lt "$first_remote" ] || fail "deploy-backend.sh 的 assert_file_grant_mode(第 $call_line 行)必须早于第一个远端动作(第 $first_remote 行)"
# rollback.sh: 后端回滚函数里必须先校验
rb_start="$(grep -nE '^rollback_backend\(\) \{' "$ROLLBACK" | cut -d: -f1)"
rb_call="$(awk -v s="$rb_start" 'NR > s && /^[[:space:]]*assert_file_grant_mode[[:space:]]*$/ { print NR; exit }' "$ROLLBACK")"
[ -n "$rb_call" ] || fail "rollback.sh 的后端回滚必须调用 assert_file_grant_mode"

# ---- ⑦ 远端脚本语法 ----
# 远端脚本是嵌在外层脚本里的文本, 外层 bash -n 查不出它们内部的语法错误, 要到生产服务器上执行时才暴露。
# 这里把每一段远端正文抽出来, 拼上公共函数后单独做语法检查(只检查, 不执行)。
extract_heredocs() {
  # 起始标记作为变量传入, awk 程序本身不含单引号(含单引号会截断外层的单引号字符串)
  awk -v start="cat <<'$2'" -v term="$2" -v out="$TMP/heredoc-$3" '
    index($0, start) > 0 { inf=1; n++; next }
    inf && $0 == term { inf=0; next }
    inf { print > (out "-" n ".sh") }
  ' "$1"
}
check_remote_syntax() { # $1=入口脚本 $2=heredoc 终止符 $3=标签 $4=期望段数
  extract_heredocs "$1" "$2" "$3"
  got="$(ls "$TMP"/heredoc-"$3"-*.sh 2>/dev/null | wc -l | tr -d ' ')"
  [ "$got" -eq "$4" ] || fail "${1##*/} 的远端脚本应抽出自 $4 段(终止符 $2), 实际 $got 段"
  for f in "$TMP"/heredoc-"$3"-*.sh; do
    { cat "$RUNTIME"; printf '\n'; cat "$f"; } | bash -n 2>"$TMP/syn.err" \
      || fail "${1##*/} 的远端脚本 ${f##*/} 语法错误: $(head -2 "$TMP/syn.err")"
  done
}
check_remote_syntax "$DEPLOY_BACKEND" REMOTE_BACKUP backup 1
check_remote_syntax "$DEPLOY_BACKEND" REMOTE_BUILD build 1
check_remote_syntax "$DEPLOY_BACKEND" REMOTE_ROLLBACK health-rollback 1
check_remote_syntax "$ROLLBACK" REMOTE rollback 3

echo "PASS: deploy-lib.sh 后端运行时单一出处(参数集合/机密不进命令行且不留临时文件/NAS 挂载自检/FILE_GRANT_MODE fail-closed/入口不再内联/远端脚本语法有效)。"
