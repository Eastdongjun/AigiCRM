#!/usr/bin/env bash
# ops-alert-check.sh 的行为测试(GL-13, 2026-11-24)
#
# 这个脚本在生产服务器上由 cron 运行。本测试**不连接任何服务器**:
#   把 docker / df / mountpoint / openssl / curl 换成桩(放在 PATH 最前面的临时目录),
#   状态目录、日志、备份文件都指向临时目录。curl 桩会把要发给 webhook 的报文记进一个文件。
#
# 验证:
#   ① 全部正常 → 退出 0, 不发任何通知
#   ② 每一类故障各对应一个告警键, 一条通知(后端停 / 健康非 UP / 公网入口不通 / 错误日志 / 磁盘 /
#      NAS 掉盘 / 证书到期 / 备份缺失·过期·不完整)
#   ③ 冷却: 同一告警在冷却期内不重复通知; 过了冷却期提醒"仍在告警"; 恢复后通知一次, 之后静默
#   ④ 四种报文格式(企业微信 / 钉钉 / 飞书 / 通用)都是合法 JSON 且字段正确
#   ⑤ 没配 webhook 时只写日志; webhook 发送失败或对方拒收时退出码不同
#   ⑥ 自检自身缺工具(例如没有 docker) → 自成一个告警, 其余检查照常
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$TEST_SELF_DIR/ops-alert-check.sh"
fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$SCRIPT" ] || fail "缺少被测脚本 $SCRIPT"
# 被测脚本的环境由 run_check 用 env -i 显式给出, 不继承外层任何配置(包括外层可能存在的旧名告警变量)

T="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-ops-alert-test.")"
trap 'rm -rf "$T"' EXIT
BIN="$T/bin"; mkdir -p "$BIN"
CAP="$T/webhook-captured.txt"; : > "$CAP"
BACKUP="$T/backup/daily/20260101"; mkdir -p "$BACKUP"

# 被测脚本的 PATH 里只放"桩 + 明确允许的基础工具", 不继承本机 PATH。
# 否则把某个桩拿掉去模拟"缺这个工具"时, 会顺着 PATH 回落到本机真实的命令上 —— 本机就装着 docker。
# 基础工具用软链逐个放进隔离目录; 桩的 shebang 也因此写成 /bin/bash(不靠 PATH 找解释器)。
CORE="$T/core"; mkdir -p "$CORE"
for tool in hostname dirname mktemp rm mkdir tr grep head tail cut awk sed find ls cat; do
  for dir in /usr/bin /bin; do
    if [ -x "$dir/$tool" ]; then ln -sf "$dir/$tool" "$CORE/$tool"; break; fi
  done
  [ -e "$CORE/$tool" ] || fail "测试环境缺少基础工具 ${tool}, 搭不出隔离的 PATH"
done
for tool in docker curl df mountpoint openssl date; do
  [ -e "$CORE/$tool" ] && fail "基础工具清单里不应出现 ${tool}: 它必须只能来自桩"
done

# ---------------- 桩命令 ----------------
cat > "$BIN/docker" <<'STUB'
#!/bin/bash
case "$1" in
  inspect) if [ "${FAKE_DOCKER_RUNNING:-true}" = "true" ]; then echo true; else echo false; fi ;;
  logs)    n="${FAKE_DOCKER_ERRORS:-0}"; i=0
           while [ "$i" -lt "$n" ]; do echo "2026-11-24 10:00:00 ERROR something broke $i"; i=$((i + 1)); done
           echo "2026-11-24 10:00:01 INFO ordinary line" ;;
  restart) echo restart >> "${FAKE_DOCKER_CALLS:?}"; echo "restarted" ;;
  *) exit 1 ;;
esac
STUB
cat > "$BIN/df" <<'STUB'
#!/bin/bash
# 用法: df -P <路径> ; 使用率来自 FAKE_DISK_MAP("/=92,/data=50"), 没有配置的路径取 FAKE_DISK_USE
p="${!#}"; use="${FAKE_DISK_USE:-40}"
for pair in $(printf '%s' "${FAKE_DISK_MAP:-}" | tr ',' ' '); do case "$pair" in "$p="*) use="${pair#*=}" ;; esac; done
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n/dev/x 100 %s 8 %s%% %s\n' "$use" "$use" "$p"
STUB
cat > "$BIN/mountpoint" <<'STUB'
#!/bin/bash
# 用法: mountpoint -q <挂载点>; FAKE_MOUNT_MISSING 里列出的挂载点视为已掉盘
for m in ${FAKE_MOUNT_MISSING:-}; do [ "$m" = "${!#}" ] && exit 1; done
exit 0
STUB
cat > "$BIN/openssl" <<'STUB'
#!/bin/bash
expiring="${FAKE_CERT_EXPIRING:-0}"
for a in "$@"; do
  if [ "$a" = "-checkend" ]; then [ "$expiring" = "1" ] && exit 1; exit 0; fi
done
case "$*" in *x509*enddate*) echo "notAfter=Jan 13 07:59:59 2027 GMT" ;; esac
cat > /dev/null
STUB
cat > "$BIN/curl" <<'STUB'
#!/bin/bash
# 两种调用: 探测(可带 -o <文件> 与 -w <格式>) 与 发通知(-X POST … -d <报文>)
is_post=0; data=""; url=""; outfile=""; wfmt=""
while [ $# -gt 0 ]; do
  case "$1" in
    -X) [ "$2" = "POST" ] && is_post=1; shift 2 ;;
    -d) data="$2"; shift 2 ;;
    -o) outfile="$2"; shift 2 ;;
    -w) wfmt="$2"; shift 2 ;;
    -*) shift 2 ;;
    *) url="$1"; shift ;;
  esac
done
emit() { # $1=响应体 $2=HTTP 状态码(模仿真 curl: 响应体在前, -w 的输出在后, 由格式串里的 \n 分隔)
  if [ -n "$outfile" ]; then
    printf '%s' "$1" > "$outfile"
  else
    printf '%s' "$1"
    [ -n "$wfmt" ] && [ -n "$1" ] && printf '\n'
  fi
  [ -n "$wfmt" ] && printf '%s' "$2"
  return 0
}
if [ "$is_post" = 1 ]; then
  printf '%s\t%s\n' "$url" "$data" >> "${FAKE_CURL_CAPTURE:?}"
  emit "${FAKE_WEBHOOK_BODY:-{\"errcode\":0}}" "${FAKE_WEBHOOK_HTTP:-200}"
  exit "${FAKE_WEBHOOK_EXIT:-0}"
fi
case "$url" in
  *actuator/health*) if [ "${FAKE_HEALTH:-up}" = up ]; then emit '{"status":"UP"}' 200
                      else emit '{"status":"DOWN"}' 503; fi ;;
  *configs/public*)  case "${FAKE_PUBLIC:-ok}" in
                       ok)       emit '{"code":200,"data":{}}' 200 ;;
                       fallback) emit '<!doctype html><html>SPA</html>' 200 ;;
                       down)     emit '' 000 ;;
                     esac ;;
  *) emit '' 000 ;;
esac
STUB
cat > "$BIN/date" <<'STUB'
#!/bin/bash
# date +%s → FAKE_NOW(纪秒, 由测试控制时间, 用来验证冷却); 其它格式交给真实的 /bin/date
if [ "${1:-}" = "+%s" ] && [ -n "${FAKE_NOW:-}" ]; then printf '%s\n' "$FAKE_NOW"; exit 0; fi
exec /bin/date "$@"
STUB
chmod 700 "$BIN"/*
DOCKER_STUB="$(cat "$BIN/docker")"
CURL_STUB="$(cat "$BIN/curl")"
without_tool() { # $1=工具名: 暂时拿掉它的桩, 模拟服务器上没有这个命令(隔离的 PATH 保证不会回落到真实命令)
  rm -f "$BIN/$1"
}
restore_tool() { # $1=工具名 $2=桩源码
  printf '%s\n' "$2" > "$BIN/$1"; chmod 700 "$BIN/$1"
}

# ---------------- 运行被测脚本 ----------------
STATE="$T/state"; LOG="$T/alert.log"; : > "$LOG"
CALLS="$T/docker-calls.txt"; : > "$CALLS"
run_check() { # 其余配置由下面的数组逐项给出; 输出进 $T/out, 通知条数与内容见 $CAP, 重启记录见 $CALLS
  : > "$CAP"; : > "$CALLS"
  # 用 env -i 给被测脚本一份"只有这些变量"的环境: 不继承外层任何配置, PATH 里只有桩与允许的基础工具。
  # (不写成"变量=值 … 命令"的前缀形式: 系统 bash 3.2 下, 前缀里有一段展开为空时, 其后的赋值会被当成命令名)
  local -a cmd_env
  cmd_env=(
    "PATH=$BIN:$CORE"
    "FAKE_NOW=${NOW:-}" "FAKE_DOCKER_CALLS=$CALLS" "FAKE_CURL_CAPTURE=$CAP"
    "AIGICRM_ALERT_STATE_DIR=$STATE" "AIGICRM_ALERT_LOG=$LOG"
    "AIGICRM_ALERT_WEBHOOK=${WEBHOOK-https://robot.example.test/hook}"
    "AIGICRM_ALERT_CHANNEL=${CHANNEL:-wecom}"
    "AIGICRM_ALERT_MOUNTS=${MOUNTS:-$T/mnt/uploads $T/mnt/exports}"
    "AIGICRM_ALERT_BACKUP_GLOB=$T/backup/daily/*/ycmn-db-*.sql"
    "AIGICRM_ALERT_DOMAIN=${DOMAIN-www.aigicrm.com}"
    "AIGICRM_ALERT_REPEAT_MIN=${REPEAT_MIN:-60}"
    "FAKE_DOCKER_RUNNING=${DOCKER_RUNNING:-true}" "FAKE_DOCKER_ERRORS=${DOCKER_ERRORS:-0}"
    "FAKE_HEALTH=${HEALTH:-up}" "FAKE_PUBLIC=${PUBLIC:-ok}"
    "FAKE_DISK_MAP=${DISK_MAP:-/=40}" "FAKE_MOUNT_MISSING=${MOUNT_MISSING:-}"
    "FAKE_CERT_EXPIRING=${CERT_EXPIRING:-0}"
    "FAKE_WEBHOOK_BODY=${WEBHOOK_BODY:-{\"errcode\":0}}" "FAKE_WEBHOOK_EXIT=${WEBHOOK_EXIT:-0}" "FAKE_WEBHOOK_HTTP=${WEBHOOK_HTTP:-200}"
  )
  # 自愈开关留空时根本不传这个变量, 被测脚本才能用自己的默认值
  [ -n "${AUTOHEAL:-}" ] && cmd_env+=("AIGI_ALERT_AUTOHEAL=$AUTOHEAL")
  /usr/bin/env -i "${cmd_env[@]}" /bin/bash "$SCRIPT" > "$T/out" 2>&1 < /dev/null
  RC=$?
}
fresh_backup() { printf 'CREATE TABLE a (id int);\n-- Dump completed on 2026-11-24  2:30:01\n' > "$BACKUP/ycmn-db-20260101.sql"; touch "$BACKUP/ycmn-db-20260101.sql"; }
reset_vars() {  # 每个场景开始前把场景变量与夹具文件都恢复成"全部正常"
  WEBHOOK=https://robot.example.test/hook; CHANNEL=wecom; REPEAT_MIN=60; DOMAIN=www.aigicrm.com
  MOUNTS="$T/mnt/uploads $T/mnt/exports"
  DOCKER_RUNNING=true; DOCKER_ERRORS=0; HEALTH=up; PUBLIC=ok; DISK_MAP=/=40; MOUNT_MISSING=""
  CERT_EXPIRING=0; WEBHOOK_BODY='{"errcode":0}'; WEBHOOK_EXIT=0; WEBHOOK_HTTP=200
  AUTOHEAL=""          # 留空 = 不传这个变量, 让被测脚本用自己的默认值
  NOW=""               # 留空 = 用真实时钟; 冷却一节里会设成固定的时间点
  rm -rf "$STATE"; : > "$LOG"
  rm -f "$BACKUP"/*.sql; fresh_backup
}
notified() { /usr/bin/grep -c . "$CAP"; }                       # 本轮发出的通知条数
notices() { cut -f2 "$CAP"; }                                   # 本轮发出的报文(每行一条)
reset_vars; fresh_backup

# ===== ① 全部正常 =====
run_check
[ "$RC" -eq 0 ] || fail "全部正常时应退出 0, 实际 rc=${RC}; 输出: $(cat "$T/out")"
[ "$(notified)" -eq 0 ] || fail "全部正常时不应发任何通知, 实际发了: $(notices)"
/usr/bin/grep -q 'OK' "$LOG" || fail "全部正常时日志应有一行 OK(证明定时任务在跑): $(cat "$LOG")"
[ -z "$(ls "$STATE" 2>/dev/null)" ] || fail "全部正常且没有需要恢复的告警时, 状态目录应为空: $(ls "$STATE")"

# ===== ② 每类故障各一个告警键 =====
run_case() { # $1=场景说明 $2=期望退出码 $3=期望的通知条数 其余=应出现在通知里的片段
  local label="$1" want="$2" count="$3"; shift 3
  run_check
  [ "$RC" -eq "$want" ] || fail "${label}: 应退出 ${want}, 实际 ${RC}; 输出: $(cat "$T/out"); 通知: $(notices); 日志: $(tail -n 4 "$LOG")"
  [ "$(notified)" -eq "$count" ] || fail "${label}: 应发 ${count} 条通知, 实际 $(notified) 条; 输出: $(cat "$T/out"); 通知: $(notices)"
  for frag in "$@"; do
    /usr/bin/grep -qF -- "$frag" "$CAP" || fail "${label}: 通知里应有 [${frag}]; 通知: $(notices); 输出: $(cat "$T/out")"
  done
}
reset_vars; DOCKER_RUNNING=false; run_case "后端容器停止" 1 1 "告警 backend_down"
reset_vars; HEALTH=down;          run_case "健康检查不是 UP" 1 1 "告警 health_down"
reset_vars; PUBLIC=fallback;      run_case "公网入口被回退成前端页面" 1 1 "告警 public_unreachable"
reset_vars; PUBLIC=down;          run_case "公网入口不通" 1 1 "告警 public_unreachable"
reset_vars; DOCKER_ERRORS=3;      run_case "错误日志超阈值" 1 1 "告警 error_log" "3 条"
reset_vars; DOCKER_ERRORS=1;      run_case "错误日志 1 条(阈值 0)" 1 1 "告警 error_log"
reset_vars; DOCKER_ERRORS=0;      run_case "错误日志 0 条不告警" 0 0
reset_vars; DISK_MAP='/=40,/data=91'; run_case "数据盘使用率 91%" 1 1 "告警 disk:/data" "91%"
reset_vars; MOUNT_MISSING="$T/mnt/uploads"; run_case "NAS 上传目录掉盘" 1 1 "告警 nas_unmounted:$T/mnt/uploads"
reset_vars; CERT_EXPIRING=1;      run_case "证书 21 天内到期" 1 1 "告警 cert_expiring" "Jan 13 07:59:59 2027"
reset_vars; rm -f "$BACKUP"/*.sql; run_case "没有数据库备份" 1 1 "告警 backup_missing"
reset_vars; printf 'CREATE TABLE a (id int);\n' > "$BACKUP/ycmn-db-20260101.sql"
                                    run_case "最新备份不完整(没有结尾标记)" 1 1 "告警 backup_incomplete"
reset_vars; : > "$BACKUP/ycmn-db-20260101.sql"; run_case "最新备份是空文件" 1 1 "告警 backup_incomplete"
reset_vars; fresh_backup; touch -t 202611200300 "$BACKUP/ycmn-db-20260101.sql"
                                    run_case "最新备份超过 26 小时" 1 1 "告警 backup_stale"
# 多个故障同时出现: 每个键各一条
reset_vars; rm -f "$BACKUP"/*.sql; DISK_MAP='/=95'
run_case "多个故障同时出现" 1 2 "告警 disk:/" "告警 backup_missing"
# 关掉的可选项不检查
reset_vars; DOMAIN=""; CERT_EXPIRING=1; run_case "不配置域名就不查证书" 0 0
# 后端停了的时候不再重复探活(同一次故障只报一个键)
reset_vars; DOCKER_RUNNING=false; HEALTH=down; run_case "后端停止时不叠加健康检查告警" 1 1 "告警 backend_down"
/usr/bin/grep -q 'health_down' "$CAP" && fail "后端停止时不应再报 health_down: $(notices)"
# 阈值边界: 容量告警按"达到即告警" —— 正好等于阈值也要告警, 低于阈值不告警
reset_vars; DISK_MAP='/=85'; run_case "磁盘使用率正好等于阈值 85" 1 1 "告警 disk:/"
reset_vars; DISK_MAP='/=84'; run_case "磁盘使用率 84 低于阈值 85" 0 0

# ===== ③ 冷却与恢复(用 date 桩把时钟钉在确定的时间点上; 重复提醒间隔 60 分钟) =====
T0=1700000000
reset_vars; HEALTH=down
NOW=$T0; run_check
[ "$RC" -eq 1 ] && [ "$(notified)" -eq 1 ] || fail "首次故障应发 1 条通知: rc=${RC}, $(notices)"
/usr/bin/grep -q '告警 health_down' "$CAP" || fail "首次故障的通知应是告警: $(notices)"
NOW=$((T0 + 1799)); run_check        # 29 分 59 秒后
[ "$RC" -eq 1 ] && [ "$(notified)" -eq 0 ] || fail "冷却期内(29分59秒)不应重复通知, 仍退出 1: rc=${RC}, $(notices)"
NOW=$((T0 + 3599)); run_check        # 59 分 59 秒后
[ "$(notified)" -eq 0 ] || fail "差 1 秒到间隔也不应提醒: $(notices)"
NOW=$((T0 + 3600)); run_check        # 60 分整
[ "$RC" -eq 1 ] && [ "$(notified)" -eq 1 ] && /usr/bin/grep -q '持续 health_down' "$CAP" \
  || fail "到了间隔应提醒仍在告警: rc=${RC}, $(notices)"
NOW=$((T0 + 3601)); run_check        # 提醒后 1 秒
[ "$(notified)" -eq 0 ] || fail "提醒必须刷新计时, 提醒后 1 秒不应再提醒: $(notices)"
NOW=$((T0 + 7199)); run_check        # 距上次提醒 59 分 59 秒
[ "$(notified)" -eq 0 ] || fail "提醒之后的间隔应从提醒那一刻重新算: $(notices)"
NOW=$((T0 + 7200)); run_check        # 距上次提醒 60 分整
[ "$(notified)" -eq 1 ] && /usr/bin/grep -q '持续 health_down' "$CAP" || fail "应再次提醒: $(notices)"
HEALTH=up; NOW=$((T0 + 7260)); run_check
[ "$RC" -eq 0 ] && [ "$(notified)" -eq 1 ] && /usr/bin/grep -q '恢复 health_down' "$CAP" \
  || fail "恢复后应通知一次并退出 0: rc=${RC}, $(notices)"
/usr/bin/grep -q '持续 121 分钟' "$CAP" || fail "恢复通知应说明此前持续了多久(121 分钟): $(notices)"
NOW=$((T0 + 7320)); run_check
[ "$RC" -eq 0 ] && [ "$(notified)" -eq 0 ] || fail "恢复通知只发一次, 之后静默: $(notices)"
# 不同告警键各自冷却: 另一个键首次出现时照常通知
reset_vars; HEALTH=down; run_check
HEALTH=up; DISK_MAP='/=95'; run_check
/usr/bin/grep -q '恢复 health_down' "$CAP" && /usr/bin/grep -q '告警 disk:/' "$CAP" || fail "同一轮里既有恢复又有新告警时两条都要发: $(notices)"

# ===== ④ 报文格式 =====
for ch in wecom dingtalk feishu generic; do
  reset_vars; CHANNEL="$ch"; HEALTH=down; run_check
  [ "$(notified)" -eq 1 ] || fail "${ch}: 应发 1 条通知; 输出: $(cat "$T/out")"
  python3 - "$CAP" "$ch" <<'PY' || fail "${ch}: 报文不符合该机器人的文本消息格式; 报文: $(notices)"
import json, sys
payload = json.loads(open(sys.argv[1], encoding='utf-8').read().split('\t', 1)[1].rstrip('\n'))
ch = sys.argv[2]
if ch in ('wecom', 'dingtalk'):
    assert payload['msgtype'] == 'text' and 'health_down' in payload['text']['content'], payload
elif ch == 'feishu':
    assert payload['msg_type'] == 'text' and 'health_down' in payload['content']['text'], payload
else:
    assert 'health_down' in payload['text'], payload
PY
done
# 报文里的特殊字符必须被转义, 否则整条通知因 JSON 不合法被机器人拒收(挂载点路径里带引号和反斜杠)
reset_vars; MOUNTS='/mnt/a"b\c'; MOUNT_MISSING='/mnt/a"b\c'; run_check
[ "$RC" -eq 1 ] && [ "$(notified)" -eq 1 ] || fail "含特殊字符的告警: 应发 1 条通知, rc=${RC}; 输出: $(cat "$T/out")"
python3 - "$CAP" <<'PY' || fail "含引号与反斜杠的告警文本应被正确转义; 报文: $(notices)"
import json, sys
payload = json.loads(open(sys.argv[1], encoding='utf-8').read().split('\t', 1)[1].rstrip('\n'))
assert '/mnt/a"b\\c' in payload['text']['content'], payload
PY

# ===== ⑤ 没配 webhook / 通知失败 =====
reset_vars; WEBHOOK=""; HEALTH=down; run_check
[ "$RC" -eq 1 ] && [ "$(notified)" -eq 0 ] && /usr/bin/grep -q 'ALERT health_down' "$LOG" || fail "没配 webhook: 只写日志、不发通知、仍退出 1: rc=${RC}; $(notices); 日志: $(cat "$LOG")"
reset_vars; WEBHOOK_BODY='{"errcode":93000,"errmsg":"invalid webhook url"}'; HEALTH=down; run_case "机器人拒收(errcode 非 0)" 3 1 "告警 health_down"
reset_vars; WEBHOOK_HTTP=500;        HEALTH=down; run_case "webhook 返回 HTTP 500" 3 1 "告警 health_down"
reset_vars; WEBHOOK_EXIT=7;          HEALTH=down; run_case "webhook 连不上(curl 退出 7)" 3 1 "告警 health_down"
reset_vars; CHANNEL=feishu; WEBHOOK_BODY='{"code":0,"msg":"success"}'; HEALTH=down; run_case "飞书回执 code 0 视为已接收" 1 1 "告警 health_down"
reset_vars; CHANNEL=dingtalk; WEBHOOK_BODY='{"errcode":0,"errmsg":"ok"}'; HEALTH=down; run_case "钉钉回执 errcode 0 视为已接收" 1 1 "告警 health_down"
# 自检失效优先于"通知失败": 退出码 2, 让人知道是检查器自己坏了
reset_vars; without_tool docker; WEBHOOK_EXIT=7
run_check
[ "$RC" -eq 2 ] || fail "自检失效且通知失败时应退出 2(自检失效优先), 实际 ${RC}; 输出: $(cat "$T/out")"
restore_tool docker "$DOCKER_STUB"

# ===== ⑥ 自检自身缺工具 =====
reset_vars; without_tool docker; HEALTH=down
run_check
[ "$RC" -eq 2 ] || fail "缺 docker 时(自检不完整)应退出 2, 实际 ${RC}; 输出: $(cat "$T/out")"
[ "$(notified)" -eq 1 ] && /usr/bin/grep -q '告警 selfcheck' "$CAP" && /usr/bin/grep -q 'docker' "$CAP" \
  || fail "缺 docker 应自成一条 selfcheck 告警(且只此一条): $(notices); 输出: $(cat "$T/out")"
/usr/bin/grep -q 'health_down' "$CAP" && fail "缺 docker 时不应把没查的健康检查报成故障: $(notices)"
restore_tool docker "$DOCKER_STUB"
# 缺 curl: 探活与通知都做不成。检查器自己出声的办法只剩日志与退出码。
reset_vars; without_tool curl; run_check
[ "$RC" -eq 2 ] && [ "$(notified)" -eq 0 ] || fail "缺 curl 时应退出 2 且发不出通知: rc=${RC}; $(notices)"
/usr/bin/grep -q 'ALERT selfcheck' "$LOG" && /usr/bin/grep -q 'curl' "$LOG" || fail "缺 curl 时日志里应有 selfcheck 告警: $(cat "$LOG")"
restore_tool curl "$CURL_STUB"
# 隔离自检: 恢复之后, 被测脚本看到的必须是桩, 而不是本机真实的 docker / curl
[ "$(PATH="$BIN:$CORE" command -v docker)" = "$BIN/docker" ] || fail "隔离失败: docker 没有解析到桩"
[ "$(PATH="$BIN:$CORE" command -v curl)" = "$BIN/curl" ] || fail "隔离失败: curl 没有解析到桩"
PATH="$BIN:$CORE" command -v ssh >/dev/null 2>&1 && fail "隔离失败: 被测脚本的 PATH 里不应有 ssh"
# 探针返回了无法解读的结果, 也算自检问题, 不能当成正常
reset_vars; cat > "$BIN/df" <<'STUB'
#!/bin/bash
echo "garbage without numbers"
STUB
chmod 700 "$BIN/df"
run_check
[ "$RC" -eq 2 ] && /usr/bin/grep -q '告警 selfcheck' "$CAP" || fail "磁盘探针输出无法解读时应报 selfcheck: rc=${RC}; $(notices)"

# ===== ⑦ 自愈重启: 默认关闭; 显式开启后, 同一故障在冷却期内只重启一次 =====
restarts() { /usr/bin/grep -c '^restart$' "$CALLS"; }
reset_vars; DOCKER_RUNNING=false; run_check
[ "$(restarts)" -eq 0 ] || fail "没有显式开启自愈(AIGI_ALERT_AUTOHEAL=1)时不得重启容器: $(cat "$CALLS")"
AUTOHEAL=1; run_check
[ "$(restarts)" -eq 1 ] || fail "开启自愈后, 后端停止应重启一次: 记录 [$(cat "$CALLS")]"
/usr/bin/grep -q '自愈 backend_restart' "$CAP" || fail "自愈重启应当即发通知: $(notices)"
run_check
[ "$(restarts)" -eq 0 ] || fail "冷却期内不应重复重启: $(cat "$CALLS")"
/usr/bin/grep -q '自愈 backend_restart' "$CAP" && fail "冷却期内不应重复发自愈通知: $(notices)"
AUTOHEAL=""

# ---- 自检: 测试没有留下桩进程或临时目录之外的文件 ----
echo "PASS: ops-alert-check.sh 行为测试(全部正常静默 / 各类故障各成一个告警 / 冷却·提醒·恢复 / 四种报文格式 / 通知失败与自检失效都有独立退出码)。"
