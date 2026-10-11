#!/bin/bash
# AIGICRM 交付门禁: 部署/交付前必须通过的全部确定性检查
# 用途: 防止"代码写不对、验证不足"的交付 —— 任一 FAIL 即阻止交付
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"

PASS=0; FAIL=0
ok()   { echo "✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "❌ $1"; FAIL=$((FAIL+1)); }

echo "════════ AIGICRM 交付门禁 $(date '+%Y-%m-%d %H:%M:%S') ════════"

# [1] git 状态: 工作区干净 + 本地=远程
echo "--- [1] git 一致性 ---"
REPO_ROOT="$(git -C "$SCRIPT_DIR/.." rev-parse --show-toplevel 2>/dev/null || true)"
[ -n "$REPO_ROOT" ] || { bad "Git 根目录不可达"; exit 1; }
cd "$REPO_ROOT" || { bad "源码目录不可达"; exit 1; }
[ -z "$(git status --porcelain)" ] && ok "工作区干净" || bad "工作区有未提交修改: $(git status --porcelain | head -2)"
L=$(git rev-parse --short HEAD); R=$(git rev-parse --short origin/main)
[ "$L" = "$R" ] && ok "本地=远程 ($L)" || bad "未推送: 本地 $L vs 远程 $R"

# [2] 生产健康: 首页 + 匿名公共配置接口(后端全链) + CORS 预检
# 【2026-09-23】旧法"真实登录 200"因 admin 初口令首登强制改密后必 401 假 FAIL(提示语还误导去查 CORS);
# 改为无凭据三段硬门禁 + 可选深度登录(软提示不阻断):
#   ① 公共配置接口=SecurityConfig permitAll 只读接口直查 DB,覆盖 nginx→后端→MySQL 全链;
#   ② CORS 意图由 OPTIONS 预检承担(匿名,直接断言 ACAO 响应头,比"登录 200"更精准地检 CORS_ORIGINS)。
echo "--- [2] 生产 API 健康 ---"
HOME_CODE=$(curl -sk -o /dev/null -w "%{http_code}" --connect-timeout 10 https://www.aigicrm.com/)
CONFIG_RESP=$(curl -sk --connect-timeout 10 -w "\n%{http_code}" https://www.aigicrm.com/api/system/configs/public)
CONFIG_CODE="${CONFIG_RESP##*$'\n'}"
ACAO=$(curl -sk -i -X OPTIONS https://www.aigicrm.com/api/auth/login \
  -H "Origin: https://www.aigicrm.com" -H "Access-Control-Request-Method: POST" --connect-timeout 10 \
  | tr -d '\r' | grep -i '^access-control-allow-origin:' | head -1 | tr -d '\n')
[ "$HOME_CODE" = "200" ] && ok "首页 200" || bad "首页 $HOME_CODE"
if [ "$CONFIG_CODE" = "200" ] && printf '%s' "$CONFIG_RESP" | grep -qE '"code":[[:space:]]*200'; then
  ok "公共配置接口 200 (nginx→后端→DB 全链)"
else
  bad "公共配置接口 HTTP $CONFIG_CODE —— 后端/DB 不可达"
fi
if [ -n "$ACAO" ]; then ok "CORS 预检通过 ($ACAO)"
else bad "CORS 预检缺 Access-Control-Allow-Origin —— 检查 CORS_ORIGINS"; fi
if [ -n "${HEALTH_CHECK_PASS:-}" ]; then
  LOGIN_CODE=$(curl -sk -o /dev/null -w "%{http_code}" --connect-timeout 10 -X POST https://www.aigicrm.com/api/auth/login \
    -H "Origin: https://www.aigicrm.com" -H "Content-Type: application/json" \
    -d "{\"username\":\"${HEALTH_CHECK_USER:-admin}\",\"password\":\"$HEALTH_CHECK_PASS\"}")
  if [ "$LOGIN_CODE" = "200" ]; then ok "深度登录(含Origin) 200"
  else echo "⚠️  深度登录 $LOGIN_CODE (HEALTH_CHECK_PASS 已失效则更新为当前口令,不阻断交付)"; fi
else
  echo "⚠️  深度登录跳过: 缺少 HEALTH_CHECK_PASS(可选,不影响交付门禁)"
fi

# [3] 生产配置: compose 与实况一致
echo "--- [3] compose 一致性 ---"
# 【修正 2026-10-11】生产容器实名 ycmn-backend(历史 aigicrm-backend 已改名), 原脚本只 inspect
# 旧名 → 恒 "≠ 运行()" 与 "CORS_ORIGINS 缺失" 双假 FAIL(检查姿势错, 非生产问题)。
BACKEND_CTR=$(ssh_prod "docker ps --format '{{.Names}}' | grep -E '^(aigicrm|ycmn)-backend$' | head -1" 2>/dev/null | tail -1)
COMPOSE_IMG=$(ssh_prod "grep -E '^  backend:' -A 5 ${DEPLOY_DIR}/docker-compose.yml | grep 'image:' | awk '{print \$2}' | head -1" 2>/dev/null)
RUN_IMG=$(ssh_prod "docker inspect ${BACKEND_CTR:-ycmn-backend} --format '{{.Config.Image}}'" 2>/dev/null | tail -1)
[ -n "$COMPOSE_IMG" ] && [ "$COMPOSE_IMG" = "$RUN_IMG" ] && ok "compose 镜像=$RUN_IMG 与运行一致" || bad "compose($COMPOSE_IMG) ≠ 运行($RUN_IMG)"
CORS_OK=$(ssh_prod "docker inspect ${BACKEND_CTR:-ycmn-backend} --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -c CORS_ORIGINS" 2>/dev/null | tail -1)
[ "${CORS_OK:-0}" -ge 1 ] 2>/dev/null && ok "CORS_ORIGINS 已配置" || bad "CORS_ORIGINS 缺失"

# [4] 存储挂载
echo "--- [4] 存储 ---"
NAS_OK=$(ssh_prod "mountpoint -q /Aigicrm/files && echo yes || echo no" 2>/dev/null)
DATA_OK=$(ssh_prod "mountpoint -q /data && echo yes || echo no" 2>/dev/null)
[ "$NAS_OK" = "yes" ] && ok "NAS 已挂载" || bad "NAS 未挂载"
[ "$DATA_OK" = "yes" ] && ok "数据盘已挂载" || bad "数据盘未挂载"

# [5] 备份机制
echo "--- [5] 备份 ---"
# 【修正 2026-10-11】生产实际脚本/文件名是 ycmn-daily-backup.sh 与 ycmn-db-*.sql(历史命名),
# 原检查只 grep aigicrm-* 关键词 → 每日备份明明在跑却恒报"缺失"(检查姿势错,非生产问题)。
# 改为双命名兼容;并防 SSH 间歇时段输出被污染成多行导致 [: integer expression 假 FAIL。
CRON_OK=$(ssh_prod "crontab -l 2>/dev/null | grep -cE 'aigicrm-daily-backup|ycmn-daily-backup'" 2>/dev/null | tail -1)
TODAY_BACKUP=$(ssh_prod "ls /Aigicrm/files/backup/daily/\$(date +%Y%m%d)/*-db-*.sql 2>/dev/null | wc -l" 2>/dev/null | tail -1)
[ "${CRON_OK:-0}" -ge 1 ] 2>/dev/null && ok "每日备份 cron 已配置" || bad "备份 cron 缺失"
[ "${TODAY_BACKUP:-0}" -ge 1 ] 2>/dev/null && ok "今日数据库备份存在" || bad "今日备份缺失(注意:凌晨 02:30 前运行属正常)"

echo "══════════════════════════════════════════════════"
echo "结果: PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -gt 0 ]; then
  echo "❌ 交付门禁未通过 —— 修复上方 FAIL 项后再交付"
  exit 1
else
  echo "✅ 交付门禁全部通过 —— 可以交付"
  exit 0
fi
