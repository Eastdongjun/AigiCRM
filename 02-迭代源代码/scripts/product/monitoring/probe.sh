#!/usr/bin/env bash
# B1 最小外部探针：供 cron/systemd timer 调用，输出 Prometheus textfile 指标。
# 只读 HTTP，不登录、不访问数据库；不打印凭据。
set -uo pipefail
API_URL="${AIGICRM_API_URL:-http://127.0.0.1:18080}"
WEB_URL="${AIGICRM_WEB_URL:-http://127.0.0.1:18082}"
OUT=""
PRINT=0
TIMEOUT="${AIGICRM_PROBE_TIMEOUT:-5}"

while [ $# -gt 0 ]; do
  case "$1" in
    --api-url) API_URL="$2"; shift 2 ;;
    --web-url) WEB_URL="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --print) PRINT=1; shift ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    -h|--help) sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

START_MS="$(python3 -c 'import time; print(int(time.time()*1000))')"
HEALTH="$(curl -fsS -m "$TIMEOUT" "$API_URL/actuator/health" 2>/dev/null || true)"
WEB="$(curl -fsS -m "$TIMEOUT" -o /tmp/aigicrm-probe-web.$$ -w '%{http_code}' "$WEB_URL/" 2>/dev/null || true)"
WEB_BODY="$(cat /tmp/aigicrm-probe-web.$$ 2>/dev/null || true)"
rm -f /tmp/aigicrm-probe-web.$$

API_UP=0
echo "$HEALTH" | grep -q '"status":"UP"' && API_UP=1
WEB_UP=0
[ "$WEB" = "200" ] && echo "$WEB_BODY" | grep -q '<div id="app">' && WEB_UP=1
END_MS="$(python3 -c 'import time; print(int(time.time()*1000))')"
DURATION=$((END_MS - START_MS))
TS="$(date +%s)"

render() {
  cat <<METRICS
# HELP aigicrm_api_up API health endpoint UP (1) / DOWN (0)
# TYPE aigicrm_api_up gauge
aigicrm_api_up $API_UP
# HELP aigicrm_web_up Frontend index reachable with app root (1/0)
# TYPE aigicrm_web_up gauge
aigicrm_web_up $WEB_UP
# HELP aigicrm_probe_duration_ms Probe duration in milliseconds
# TYPE aigicrm_probe_duration_ms gauge
aigicrm_probe_duration_ms $DURATION
# HELP aigicrm_last_probe_timestamp_seconds Unix time of last probe
# TYPE aigicrm_last_probe_timestamp_seconds gauge
aigicrm_last_probe_timestamp_seconds $TS
METRICS
}

if [ -n "$OUT" ]; then
  mkdir -p "$(dirname "$OUT")"
  render > "$OUT.tmp.$$" && mv "$OUT.tmp.$$" "$OUT"
fi
[ "$PRINT" = "1" ] && render
[ "$API_UP" = "1" ] && [ "$WEB_UP" = "1" ]
