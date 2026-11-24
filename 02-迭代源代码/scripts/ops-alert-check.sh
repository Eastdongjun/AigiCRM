#!/usr/bin/env bash
# ============================================================
# 服务器侧最小告警自检(GL-13 2026-11-24 升级; 前身是 2026-09-22 的两检查版本)
#
# 在哪里跑: 生产服务器本机, cron 每 5 分钟一次。安装与定时见 docs/ops/incident.md 第五节(接线需单独授权)。
#   全部检查都只读: 不改配置、不重启服务(AIGI_ALERT_AUTOHEAL=1 时例外, 默认关闭)。
#
# 检查项(每一项是一个告警键; 同一键在冷却期内不重复通知, 恢复后通知一次):
#   backend_down              后端容器不在运行
#   health_down               宿主机直连后端 /actuator/health 不是 UP
#   public_unreachable        公网入口 /api/system/configs/public 不是 200, 或响应体不是接口响应
#   error_log                 近 N 分钟容器日志里的错误关键字超过阈值
#   disk:<路径>               该文件系统使用率达到阈值(正好等于阈值也算)
#   nas_unmounted:<挂载点>    NAS 挂载点掉盘
#   cert_expiring             证书将在 N 天内到期
#   backup_missing / backup_stale / backup_incomplete  当日数据库备份缺失 / 过期 / 不完整
#   selfcheck                 自检自身缺工具、探针跑不起来或状态目录不可写(检查器失效也要出声)
#
# 为什么探活不再用容器内 curl: 生产镜像是 eclipse-temurin:21-jre, 里面有没有 curl 没有验证过;
#   后端端口本来就发布在宿主机 127.0.0.1:8080, 宿主机直接连更可靠。
#   公网 /actuator/health 会被 nginx 回退成前端首页(HTTP 200 的假阳性), 所以公网这一环查
#   /api/system/configs/public 并校验响应体 —— 与部署脚本 deploy-lib.sh 的 check_health 同口径。
#   (旧版本踩过的坑: 探测宿主机 80 端口拿到的是前端 HTML, 曾致误告警。)
#
# 通知: 企业微信 / 钉钉 / 飞书 机器人的文本消息, 或通用 {"text": …}。没有配置 webhook 时只写日志。
#   全部正常时不发任何通知。对方回执报错也算发送失败。
#
# 配置: 环境变量, 或 AIGICRM_ALERT_CONF 指向的配置文件(环境变量优先)。下面是全部配置项与默认值。
#   AIGICRM_ALERT_WEBHOOK                 机器人 webhook 地址。不填 = 只写日志
#   AIGICRM_ALERT_CHANNEL                 wecom | dingtalk | feishu | generic(默认)
#   AIGICRM_ALERT_ENV                     通知里的环境名, 默认 prod
#   AIGICRM_ALERT_BACKEND_CONTAINER       默认 ycmn-backend(兼容旧名 AIGI_BACKEND_CONTAINER)
#   AIGICRM_ALERT_HEALTH_URL              默认 http://127.0.0.1:8080/actuator/health
#   AIGICRM_ALERT_PUBLIC_URL              默认 https://www.aigicrm.com/api/system/configs/public; 设为空串则不查
#   AIGICRM_ALERT_DOMAIN                  证书检查用的域名, 默认 www.aigicrm.com; 设为空串则不查
#   AIGICRM_ALERT_DISK_PATHS              默认 "/ /data"(空格分隔)
#   AIGICRM_ALERT_DISK_PCT                默认 85
#   AIGICRM_ALERT_MOUNTS                  默认 "/Aigicrm/files/uploads /Aigicrm/files/ycmn-exports"(空格分隔)
#   AIGICRM_ALERT_BACKUP_GLOB             默认 /Aigicrm/files/backup/daily/*/ycmn-db-*.sql
#   AIGICRM_ALERT_BACKUP_MAX_AGE_HOURS    默认 26
#   AIGICRM_ALERT_CERT_DAYS               默认 21
#   AIGICRM_ALERT_ERROR_LOG_WINDOW        默认 5m
#   AIGICRM_ALERT_ERROR_LOG_MAX           默认 0(有 1 条就告警)
#   AIGICRM_ALERT_KEYWORDS                错误关键字正则, 默认 ERROR|entered_sea.*failed|Duplicate entry|OutOfMemoryError
#   AIGICRM_ALERT_REPEAT_MIN              同一告警重复提醒的间隔(分钟), 默认 60
#   AIGICRM_ALERT_STATE_DIR               冷却状态, 默认 /var/lib/aigicrm-alert
#   AIGICRM_ALERT_LOG                     默认 /var/log/aigicrm-alert.log(兼容旧名 AIGI_ALERT_LOG)
#   AIGI_ALERT_AUTOHEAL                   =1 时后端探活失败会尝试重启容器(冷却 30 分钟)。默认关闭, 启用前必须人工评估
#
# 退出码: 0 全部正常; 1 有告警(新发或持续); 2 自检自身失效; 3 有告警但通知发送失败。
# 行为测试见 scripts/test-ops-alert-check.sh(桩命令 + 临时目录, 不连任何服务器)。
# ============================================================
set -uo pipefail

# ---------- 配置 ----------
if [ -n "${AIGICRM_ALERT_CONF:-}" ]; then
  # shellcheck disable=SC1090
  . "$AIGICRM_ALERT_CONF" || { echo "配置文件无法加载: $AIGICRM_ALERT_CONF" >&2; exit 2; }
fi
WEBHOOK="${AIGICRM_ALERT_WEBHOOK:-${AIGI_ALERT_WEBHOOK:-}}"
CHANNEL="${AIGICRM_ALERT_CHANNEL:-generic}"
ENV_NAME="${AIGICRM_ALERT_ENV:-prod}"
CT="${AIGICRM_ALERT_BACKEND_CONTAINER:-${AIGI_BACKEND_CONTAINER:-ycmn-backend}}"
HEALTH_URL="${AIGICRM_ALERT_HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
# 带 "-" 而不是 ":-" 的两项: 显式设成空串表示"不查", 不能被默认值顶掉
PUBLIC_URL="${AIGICRM_ALERT_PUBLIC_URL-https://www.aigicrm.com/api/system/configs/public}"
DOMAIN="${AIGICRM_ALERT_DOMAIN-www.aigicrm.com}"
DISK_PATHS="${AIGICRM_ALERT_DISK_PATHS:-/ /data}"
DISK_PCT="${AIGICRM_ALERT_DISK_PCT:-85}"
MOUNTS="${AIGICRM_ALERT_MOUNTS:-/Aigicrm/files/uploads /Aigicrm/files/ycmn-exports}"
BACKUP_GLOB="${AIGICRM_ALERT_BACKUP_GLOB:-/Aigicrm/files/backup/daily/*/ycmn-db-*.sql}"
BACKUP_MAX_AGE_HOURS="${AIGICRM_ALERT_BACKUP_MAX_AGE_HOURS:-26}"
CERT_DAYS="${AIGICRM_ALERT_CERT_DAYS:-21}"
ERR_WINDOW="${AIGICRM_ALERT_ERROR_LOG_WINDOW:-5m}"
ERR_MAX="${AIGICRM_ALERT_ERROR_LOG_MAX:-0}"
ERR_KEYWORDS="${AIGICRM_ALERT_KEYWORDS:-ERROR|entered_sea.*failed|Duplicate entry|OutOfMemoryError}"
REPEAT_MIN="${AIGICRM_ALERT_REPEAT_MIN:-60}"
STATE="${AIGICRM_ALERT_STATE_DIR:-/var/lib/aigicrm-alert}"
LOG="${AIGICRM_ALERT_LOG:-${AIGI_ALERT_LOG:-/var/log/aigicrm-alert.log}}"
AUTOHEAL="${AIGI_ALERT_AUTOHEAL:-0}"
HEALTH_TIMEOUT=8
AUTOHEAL_COOLDOWN_SEC=1800

is_uint() { case "$1" in ''|*[!0-9]*) return 1 ;; *) return 0 ;; esac; }
bad_config() { echo "配置错误: $*" >&2; exit 2; }
for pair in "重复提醒间隔(分钟)=${REPEAT_MIN}" "磁盘阈值=${DISK_PCT}" "备份最长时限(小时)=${BACKUP_MAX_AGE_HOURS}" \
            "证书提前天数=${CERT_DAYS}" "错误日志阈值=${ERR_MAX}"; do
  is_uint "${pair#*=}" || bad_config "${pair%%=*} 必须是非负整数, 实际 [${pair#*=}]"
done
case "$ERR_WINDOW" in
  ''|*[!0-9smhd]*) bad_config "日志时间窗格式不对: [${ERR_WINDOW}](应为数字加 s/m/h/d, 例如 5m)" ;;
esac
case "$ERR_WINDOW" in [0-9]*[smhd]) ;; *) bad_config "日志时间窗格式不对: [${ERR_WINDOW}](应为数字加 s/m/h/d, 例如 5m)" ;; esac
case "$CHANNEL" in
  wecom|dingtalk|feishu|generic) ;;
  *) bad_config "AIGICRM_ALERT_CHANNEL 只认 wecom / dingtalk / feishu / generic, 实际 [${CHANNEL}]" ;;
esac
REPEAT_SEC=$((REPEAT_MIN * 60))

# ---------- 基础设施 ----------
TMP_BASE="${TMPDIR:-/tmp}"
WORK="$(mktemp -d "$TMP_BASE/aigicrm-alert.")"
trap 'rm -rf "$WORK"' EXIT

now() { date +%s; }
ts() { date '+%F %T'; }
has_tool() { command -v "$1" >/dev/null 2>&1; }
HOST_NAME="$(hostname 2>/dev/null || echo unknown-host)"
mkdir -p "$(dirname "$LOG")" 2>/dev/null
if ! : >> "$LOG" 2>/dev/null; then LOG="$WORK/alert.log"; fi     # 日志写不进去就退到临时目录, 不因为日志而漏检
log() { printf '[%s] %s\n' "$(ts)" "$1" >> "$LOG" 2>/dev/null || true; }

# 冷却状态。固定目录写不进去(权限)时退到临时目录: 检查照做, 但冷却跨不过进程, 如实记进 selfcheck。
STATE_WRITABLE=1
if ! mkdir -p "$STATE" 2>/dev/null || ! : > "$STATE/.probe" 2>/dev/null; then
  STATE_WRITABLE=0
  STATE="$WORK/state"
  mkdir -p "$STATE"
else
  rm -f "$STATE/.probe"
fi

# ---------- 发现 ----------
KEYS=()
DETAILS=()
CHECKS_RUN=0
SELF_ISSUES=""
add_finding() { # $1=告警键 $2=详情; 同一键再次出现时只合并详情(一个键一轮只通知一次)
  local i
  if [ "${#KEYS[@]}" -gt 0 ]; then
    for i in "${!KEYS[@]}"; do
      if [ "${KEYS[$i]}" = "$1" ]; then DETAILS[$i]="${DETAILS[$i]}; $2"; return 0; fi
    done
  fi
  KEYS+=("$1")
  DETAILS+=("$2")
}
is_current() { # $1=键; 本轮是否有这个告警
  local i
  [ "${#KEYS[@]}" -gt 0 ] || return 1
  for i in "${!KEYS[@]}"; do [ "${KEYS[$i]}" = "$1" ] && return 0; done
  return 1
}

# 0) 自检自身: 缺了工具, 对应的检查就做不成。必须出声, 否则"检查器不响了"会被当成"一切正常"。
for t in curl date grep; do has_tool "$t" || SELF_ISSUES="${SELF_ISSUES} 缺少 ${t}"; done
has_tool docker || SELF_ISSUES="${SELF_ISSUES} 缺少 docker(无法查后端容器与容器日志)"
[ "$STATE_WRITABLE" = 1 ] || SELF_ISSUES="${SELF_ISSUES} 状态目录不可写, 冷却与恢复通知将不生效"

# 1) 后端容器在运行
BACKEND_UP=0
if has_tool docker; then
  CHECKS_RUN=$((CHECKS_RUN + 1))
  running="$(docker inspect -f '{{.State.Running}}' "$CT" 2>/dev/null | tr -d '\r[:space:]')"
  if [ "$running" = "true" ]; then
    BACKEND_UP=1
  else
    add_finding backend_down "后端容器 ${CT} 不在运行(docker inspect 返回 [${running:-空}])"
  fi
fi

# 2) 健康检查 + 3) 错误日志(容器在运行才有意义; 停了就只报 backend_down, 同一次故障不叠两个键)
if [ "$BACKEND_UP" = 1 ]; then
  if has_tool curl; then
    CHECKS_RUN=$((CHECKS_RUN + 1))
    body="$(curl -s -m "$HEALTH_TIMEOUT" "$HEALTH_URL" 2>/dev/null | head -c 400 || true)"
    if ! printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      add_finding health_down "后端健康检查不是 UP: ${HEALTH_URL} 返回 [${body:-无响应}]"
    fi
  fi
  CHECKS_RUN=$((CHECKS_RUN + 1))
  err_cnt="$(docker logs --since "$ERR_WINDOW" "$CT" 2>&1 | grep -cE "$ERR_KEYWORDS" || true)"
  err_cnt="$(printf '%s' "$err_cnt" | tr -d '\r[:space:]')"
  if ! is_uint "$err_cnt"; then
    SELF_ISSUES="${SELF_ISSUES} 容器错误日志统计不出来(得到 [${err_cnt:-空}])"
  elif [ "$err_cnt" -gt "$ERR_MAX" ]; then
    sample="$(docker logs --since "$ERR_WINDOW" "$CT" 2>&1 | grep -E "$ERR_KEYWORDS" | tail -n 1 | cut -c1-160)"
    add_finding error_log "近 ${ERR_WINDOW} 容器日志里有 ${err_cnt} 条错误(阈值 ${ERR_MAX}); 样例: ${sample}"
  fi
fi

# 4) 公网入口: nginx → 后端 → 库 的全链路。判据是 HTTP 200 且响应体是接口响应(防被回退成前端页面)。
if [ -n "$PUBLIC_URL" ] && has_tool curl; then
  CHECKS_RUN=$((CHECKS_RUN + 1))
  code="$(curl -s -m 10 -o "$WORK/public.body" -w '%{http_code}' "$PUBLIC_URL" 2>/dev/null)" || code="000"
  code="$(printf '%s' "$code" | tr -d '\r[:space:]')"
  if [ "$code" != "200" ]; then
    add_finding public_unreachable "公网入口不通: ${PUBLIC_URL} 返回 HTTP [${code:-空}]"
  elif ! head -c 400 "$WORK/public.body" 2>/dev/null | grep -q '"code"[[:space:]]*:[[:space:]]*200'; then
    add_finding public_unreachable "公网入口返回的不是接口响应(疑似被回退成前端页面): ${PUBLIC_URL}"
  fi
fi

# 5) 磁盘
for p in $DISK_PATHS; do
  CHECKS_RUN=$((CHECKS_RUN + 1))
  use="$(df -P "$p" 2>/dev/null | awk 'NR==2 { gsub(/%/, "", $5); print $5 }')"
  if ! is_uint "$use"; then
    SELF_ISSUES="${SELF_ISSUES} 磁盘使用率读不到(${p})"
  elif [ "$use" -ge "$DISK_PCT" ]; then
    add_finding "disk:${p}" "文件系统 ${p} 使用率 ${use}%(阈值 ${DISK_PCT}%)"
  fi
done

# 6) NAS 挂载
for m in $MOUNTS; do
  CHECKS_RUN=$((CHECKS_RUN + 1))
  if ! has_tool mountpoint; then
    SELF_ISSUES="${SELF_ISSUES} 缺少 mountpoint(无法查 NAS 挂载)"
    break
  fi
  mountpoint -q "$m" || add_finding "nas_unmounted:${m}" "挂载点 ${m} 已掉盘(上传与导出会失败)"
done

# 7) 证书到期
if [ -n "$DOMAIN" ]; then
  CHECKS_RUN=$((CHECKS_RUN + 1))
  if ! has_tool openssl; then
    SELF_ISSUES="${SELF_ISSUES} 缺少 openssl(无法查证书)"
  else
    CERT="$WORK/cert.pem"
    echo | openssl s_client -servername "$DOMAIN" -connect "$DOMAIN:443" 2>/dev/null > "$CERT"
    not_after="$(openssl x509 -noout -enddate < "$CERT" 2>/dev/null | sed -n 's/^notAfter=//p')"
    if [ -z "$not_after" ]; then
      SELF_ISSUES="${SELF_ISSUES} 取不到 ${DOMAIN} 的证书(握手失败或域名解析不了)"
    elif ! openssl x509 -noout -checkend "$((CERT_DAYS * 86400))" < "$CERT" >/dev/null 2>&1; then
      add_finding cert_expiring "证书将在 ${CERT_DAYS} 天内到期: ${DOMAIN}(到期时间 ${not_after})"
    fi
  fi
fi

# 8) 当日数据库备份: 存在、没有过期、带完整的结尾标记
CHECKS_RUN=$((CHECKS_RUN + 1))
latest="$(ls -1t $BACKUP_GLOB 2>/dev/null | head -n 1)"
if [ -z "$latest" ]; then
  add_finding backup_missing "没有找到数据库备份(${BACKUP_GLOB})"
else
  if [ ! -s "$latest" ]; then
    add_finding backup_incomplete "最新的数据库备份是空文件: ${latest}"
  elif ! tail -n 1 "$latest" | grep -q 'Dump completed'; then
    add_finding backup_incomplete "最新的数据库备份缺少结尾标记, 可能被截断: ${latest}"
  fi
  if [ -n "$(find "$latest" -mmin +"$((BACKUP_MAX_AGE_HOURS * 60))" 2>/dev/null)" ]; then
    add_finding backup_stale "最新的数据库备份已超过 ${BACKUP_MAX_AGE_HOURS} 小时: ${latest}(修改时间 $(date -r "$latest" '+%F %T' 2>/dev/null || echo 未知))"
  fi
fi

# 自检问题汇总成一个键, 放在最后
[ -n "$SELF_ISSUES" ] && add_finding selfcheck "自检不完整, 以下检查没有做成:${SELF_ISSUES}"

# ---------- 通知 ----------
json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\r'//}"
  s="${s//$'\t'/ }"
  printf '%s' "$s" | LC_ALL=C tr -d '\000-\010\013\014\016-\037'
}
build_payload() { # $1=消息; 输出该机器人的文本消息报文
  local msg
  msg="[AIGICRM ${ENV_NAME}] $(json_escape "$1")"
  case "$CHANNEL" in
    wecom|dingtalk) printf '{"msgtype":"text","text":{"content":"%s"}}' "$msg" ;;
    feishu)         printf '{"msg_type":"text","content":{"text":"%s"}}' "$msg" ;;
    *)              printf '{"text":"%s"}' "$msg" ;;
  esac
}
compose() { # $1=种类 $2=告警键 $3=详情
  printf '%s %s: %s (环境 %s, 主机 %s, %s)' "$1" "$2" "$3" "$ENV_NAME" "$HOST_NAME" "$(ts)"
}
notify() { # $1=消息; 返回 0 已送达, 1 发送失败或对方拒收, 2 没有配置 webhook
  [ -n "$WEBHOOK" ] || return 2
  local payload resp code
  payload="$(build_payload "$1")"
  resp="$(curl -s -m 10 -X POST "$WEBHOOK" -H 'Content-Type: application/json' -d "$payload" -w '\n%{http_code}' 2>/dev/null)" || return 1
  code="$(printf '%s\n' "$resp" | tail -n 1 | tr -d '\r[:space:]')"
  case "$code" in
    2[0-9][0-9]) ;;
    *) return 1 ;;
  esac
  case "$CHANNEL" in
    wecom|dingtalk) printf '%s\n' "$resp" | grep -q '"errcode"[[:space:]]*:[[:space:]]*0' ;;
    feishu)         printf '%s\n' "$resp" | grep -qE '"(code|StatusCode)"[[:space:]]*:[[:space:]]*0' ;;
    *)              return 0 ;;
  esac
}
NOTIFY_FAILED=0
announce() { # $1=消息 $2=告警键: 发通知并记账
  local rc=0
  notify "$1" || rc=$?
  if [ "$rc" -eq 1 ]; then NOTIFY_FAILED=1; log "通知发送失败: $2"; fi
}

# ---------- 冷却: 首次通知 → 冷却期内不重复 → 到点提醒 → 恢复通知一次 ----------
NOW="$(now)"
state_file() { printf '%s/%s.active' "$STATE" "$(printf '%s' "$1" | LC_ALL=C tr -c 'A-Za-z0-9_.-' '_')"; }

if [ "${#KEYS[@]}" -gt 0 ]; then
  for i in "${!KEYS[@]}"; do
    key="${KEYS[$i]}"
    detail="${DETAILS[$i]}"
    sf="$(state_file "$key")"
    first=""; last=0
    if [ -f "$sf" ]; then read -r first last < "$sf" 2>/dev/null || true; fi
    is_uint "$first" || first=""
    is_uint "$last" || last="$NOW"
    if [ -z "$first" ]; then
      log "ALERT ${key}: ${detail}"
      announce "$(compose 告警 "${key}" "${detail}")" "${key}"
      printf '%s %s\n%s\n' "$NOW" "$NOW" "${key}" > "$sf" 2>/dev/null || true
    elif [ $((NOW - last)) -ge "$REPEAT_SEC" ]; then
      log "STILL ${key}: ${detail}"
      announce "$(compose 持续 "${key}" "${detail}")" "${key}"
      printf '%s %s\n%s\n' "$first" "$NOW" "${key}" > "$sf" 2>/dev/null || true
    fi
  done
fi
for sf in "$STATE"/*.active; do
  [ -f "$sf" ] || continue
  first=""; last=0; key=""
  { read -r first last; read -r key; } < "$sf" 2>/dev/null || true
  if [ -z "$key" ] || ! is_uint "$first"; then rm -f "$sf" 2>/dev/null || true; continue; fi
  if ! is_current "$key"; then
    mins=$(( (NOW - first) / 60 ))
    log "RECOVERED ${key}: 持续 ${mins} 分钟后恢复"
    announce "$(compose 恢复 "${key}" "已恢复正常(此前持续 ${mins} 分钟)")" "${key}"
    rm -f "$sf" 2>/dev/null || true
  fi
done

# ---------- 自愈重启(沿用旧脚本的可选项, 默认关闭; 启用前必须人工评估) ----------
if [ "$AUTOHEAL" = "1" ] && has_tool docker; then
  if is_current backend_down || is_current health_down; then
    heal_state="$STATE/autoheal.last"
    last_heal=0
    [ -f "$heal_state" ] && read -r last_heal < "$heal_state" 2>/dev/null || true
    is_uint "$last_heal" || last_heal=0
    if [ $((NOW - last_heal)) -ge "$AUTOHEAL_COOLDOWN_SEC" ]; then
      if docker restart "$CT" >/dev/null 2>&1; then
        log "AUTOHEAL: 已重启 ${CT}"
        announce "$(compose 自愈 "backend_restart" "探活失败, 已自动重启容器 ${CT}")" "backend_restart"
        printf '%s\n' "$NOW" > "$heal_state" 2>/dev/null || true
      else
        log "AUTOHEAL: 重启 ${CT} 失败"
      fi
    else
      log "AUTOHEAL: 冷却期内不重复重启"
    fi
  fi
fi

# ---------- 收尾 ----------
if [ "${#KEYS[@]}" -eq 0 ]; then
  log "OK: 全部正常(共 ${CHECKS_RUN} 项检查)"
  exit 0
fi
is_current selfcheck && exit 2
[ "$NOTIFY_FAILED" -eq 1 ] && exit 3
exit 1
