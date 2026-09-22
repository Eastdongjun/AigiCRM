#!/usr/bin/env bash
# Clean Room 冒烟: 等 health → Flyway 迁移数核对 → 登录/信息/客户列表/统计 → 断言全过才 exit 0
# 凭据只从 .env 读取;mysql 访问经 docker exec -e 注入(不拼接 shell 命令);输出不含明文口令/token。
set -uo pipefail
cd "$(dirname "$0")"
. ./.env
BASE="http://127.0.0.1:38080"
PASS=0; FAIL=0
ok(){ echo "PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "FAIL: $1"; FAIL=$((FAIL+1)); }

echo "== 1) 等待 /actuator/health =="
H=""
for i in $(seq 1 90); do
  H=$(curl -s -m 5 "$BASE/actuator/health" 2>/dev/null)
  echo "$H" | grep -q '"status":"UP"' && break
  sleep 5
done
if echo "$H" | grep -q '"status":"UP"'; then ok "health UP: $H"; else bad "health 未达 UP(最后响应: $H)"; echo "FAIL_FAST"; exit 1; fi

echo "== 2) health 匿名无详情(不泄漏组件信息) =="
D=$(curl -s -m 5 "$BASE/actuator/health")
if ! echo "$D" | grep -qE 'db|redis|diskSpace'; then ok "health 响应无组件细节"; else bad "health 泄漏组件细节: $D"; fi

echo "== 3) Flyway 迁移数 =="
FLY_TOTAL=$(docker exec -e MYSQL_PWD="$CR_DB_ROOT_PASSWORD" cleanroom-mysql mysql -uroot -N -B -e 'SELECT count(*) FROM aigicrm.flyway_schema_history WHERE success=1' 2>/dev/null | tr -d '[:space:]')
DISK_TOTAL=$(ls ../../aigicrm-backend/src/main/resources/db/migration/*.sql | wc -l | tr -d '[:space:]')
if [ -n "$FLY_TOTAL" ] && [ "$FLY_TOTAL" = "$DISK_TOTAL" ] && [ "$FLY_TOTAL" -gt 0 ]; then
  ok "Flyway 应用 $FLY_TOTAL/$DISK_TOTAL 全量成功"
else
  bad "Flyway 数不符: 库=$FLY_TOTAL 磁盘=$DISK_TOTAL"
fi

echo "== 3.5) 组织/admin 种子(Flyway 建表后,幂等) =="
SEED=$(docker exec -i -e MYSQL_PWD="$CR_DB_ROOT_PASSWORD" cleanroom-mysql mysql -uroot aigicrm < build/02-seed-org.sql 2>&1)
[ -z "$SEED" ] && ok "02-seed-org.sql 应用成功" || bad "种子失败: $SEED"

echo "== 4) 登录(admin) =="
LOGIN=$(curl -s -m 10 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$CR_ADMIN_USER\",\"password\":\"$CR_ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("token") or "")' 2>/dev/null)
[ -n "$TOKEN" ] && ok "admin 登录成功(token 长度 ${#TOKEN})" || { bad "登录失败: $(echo "$LOGIN" | head -c 200)"; exit 1; }

echo "== 5) auth/info =="
INFO=$(curl -s -m 10 "$BASE/api/auth/info" -H "Authorization: Bearer $TOKEN")
U=$(echo "$INFO" | python3 -c 'import json,sys; d=json.load(sys.stdin); u=(d.get("data") or {}); print(u.get("username") or "")' 2>/dev/null)
[ "$U" = "admin" ] && ok "auth/info username=admin(角色/权限链可用)" || bad "auth/info 异常: $(echo "$INFO" | head -c 200)"

echo "== 6) 客户列表(空库应 HTTP200 total=0) =="
CL=$(curl -s -m 10 -w '\n%{http_code}' "$BASE/api/customer/list?size=1" -H "Authorization: Bearer $TOKEN")
CODE=$(echo "$CL" | tail -1); BODY=$(echo "$CL" | head -1)
TOTAL=$(echo "$BODY" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("total"))' 2>/dev/null)
[ "$CODE" = "200" ] && ok "customer/list HTTP200(空库 total=$TOTAL)" || bad "customer/list HTTP$CODE: $(echo "$BODY" | head -c 120)"

echo "== 7) 统计口径(空库) =="
ST=$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE/api/statistics/indicator-stats" -H "Authorization: Bearer $TOKEN")
[ "$ST" = "200" ] && ok "indicator-stats HTTP200" || bad "indicator-stats HTTP$ST"

echo "== 8) 未认证拒绝 =="
# SecurityConfig BS-A09: anyRequest/@PreAuthorize 拒绝统一 403 语义(匿名亦 403, 与测试服实测一致)
NA=$(curl -s -m 10 -o /dev/null -w '%{http_code}' "$BASE/api/customer/list?size=1")
[ "$NA" = "403" ] && ok "匿名访问受保护接口 403(统一拒绝语义)" || bad "匿名访问返回 HTTP$NA(期望403)"

echo "== 9) 上传目录存在 =="
UP=$(docker exec cleanroom-backend ls -ld /app/uploads 2>/dev/null | wc -l | tr -d '[:space:]')
[ "$UP" = "1" ] && ok "uploads 目录存在" || bad "uploads 目录缺失"

echo
echo "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
