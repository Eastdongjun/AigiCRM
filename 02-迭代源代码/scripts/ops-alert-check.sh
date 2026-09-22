#!/usr/bin/env bash
# 服务器侧最低配告警自检(上线批次 2026-09-22, GPT外审 OBS 域"指标无人消费"最小闭环)
# cron 建议: */5 * * * * /home/ubuntu/aigicrm/scripts/ops-alert-check.sh
# 检测: ①容器内 /actuator/health 非 UP ②近5分钟日志 ERROR/定时任务失败关键字
# 动作: 记录告警日志 + 可选 webhook(AIGI_ALERT_WEBHOOK env) + 自愈重启默认关闭
# 【教训 2026-09-22 实测】探测必须打容器内 localhost:8080——宿主机 80 是 nginx 前端 HTML 兜底,
# 永远非 UP JSON, 曾致误告警+误重启; 自愈(AIGI_ALERT_AUTOHEAL=1)默认关闭, 启用前必须人工评估。
set -uo pipefail
CT="${AIGI_BACKEND_CONTAINER:-ycmn-backend}"
STATE="/tmp/aigicrm-alert-state"
LOG="${AIGI_ALERT_LOG:-/home/ubuntu/aigicrm-alert.log}"
COOLDOWN_SEC=1800   # 自愈重启最小间隔

ts(){ date '+%F %T'; }
alert(){ echo "[$(ts)] ALERT: $1" >> "$LOG"; [ -n "${AIGI_ALERT_WEBHOOK:-}" ] && curl -s -m 10 -X POST "$AIGI_ALERT_WEBHOOK" -H 'Content-Type: application/json' -d "{\"text\":\"AIGICRM: $1\"}" >/dev/null 2>&1 || true; }

# 1) health(容器内直验, 同 verify-perm-matrix 口径; 容器不在线=docker exec 失败同样告警)
H=$(docker exec "$CT" curl -s -m 8 http://localhost:8080/actuator/health 2>/dev/null)
if ! echo "$H" | grep -q '"status":"UP"'; then
  alert "health 非 UP: ${H:-<容器不可达或无响应>}"
  if [ "${AIGI_ALERT_AUTOHEAL:-0}" = "1" ]; then
    LAST=0; [ -f "$STATE" ] && LAST=$(cat "$STATE" 2>/dev/null || echo 0)
    NOW=$(date +%s)
    if [ $((NOW - LAST)) -ge $COOLDOWN_SEC ]; then
      docker restart "$CT" >/dev/null 2>&1 && alert "已自愈重启 $CT(冷却 ${COOLDOWN_SEC}s)" && echo "$NOW" > "$STATE"
    else
      alert "冷却期内不重复重启"
    fi
  fi
  exit 1
fi

# 2) 近5分钟错误日志(容器日志 + 定时任务失败计数关键字)
ERRS=$(docker logs --since 5m "$CT" 2>&1 | grep -cE "ERROR|entered_sea.*failed|Duplicate entry" || true)
if [ "${ERRS:-0}" -gt 0 ]; then
  alert "近5分钟错误日志 ${ERRS} 条(docker logs --since 5m $CT, 详情见容器日志)"
  exit 2
fi

echo "[$(ts)] OK: health UP, 0 errors(5m)" >> "$LOG"
exit 0
