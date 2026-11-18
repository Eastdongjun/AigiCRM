#!/bin/bash
# D6 清理 + 基线断言（bash -s 送达，避免嵌套引号）
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
source "$REPO/scripts/deploy-lib.sh"
ssh_test_once "bash -s" <<'REMOTE'
set -Eeuo pipefail
echo "===== 清理残留 ====="
docker exec -u 0 ycmn-backend sh -c 'rm -rf /app/uploads/private/99 /app/uploads/private/100 /app/uploads/private/101 /app/uploads/private/102 2>/dev/null || true'
LEFT=$(docker exec ycmn-backend sh -c 'ls /app/uploads/private/ 2>/dev/null' | grep -cE '^(99|100|101|102)$' || true)
echo "uploads_probe_dirs_left=$LEFT（期望 0）"
RC=$(docker ps --format '{{.Names}}' | grep -i redis | head -1)
docker exec "$RC" sh -c 'redis-cli --scan --pattern "perm:ctx:*" | xargs -r redis-cli DEL >/dev/null 2>&1 || true'
rm -f /tmp/a2-app.jar /tmp/a2d-env.txt /tmp/a2d-env2.txt /tmp/c6.json /tmp/c5.json /tmp/a2f.txt /tmp/a2f2.txt /tmp/a2f3.txt /tmp/a2grant.json /tmp/a.txt /tmp/b.txt
echo "tmp_cleaned"
echo "===== D6 基线断言 ====="
PW="$(docker inspect shared-mysql --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
MYQ() { docker exec -e MYSQL_PWD="$PW" shared-mysql mysql -uroot -N -B aigicrm_prod_20261016 -e "$1" 2>/dev/null || true; }
echo "flyway_tail=$(MYQ 'SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1')  期望 2026.11.16.9"
echo "dot17_18=$(MYQ 'SELECT COUNT(*) FROM flyway_schema_history WHERE version LIKE "2026.11.17%" OR version LIKE "2026.11.18%"')  期望 0"
echo "old_rows=$(MYQ 'SELECT COUNT(*) FROM biz_folder_type_default')  期望 62"
echo "file_grant_table=$(docker exec -e MYSQL_PWD="$PW" shared-mysql mysql -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='aigicrm_prod_20261016' AND table_name='biz_file_grant'")  期望 0"
echo "sys_user_max=$(MYQ 'SELECT COALESCE(MAX(id),0) FROM sys_user')  期望 82"
echo "a2t_users=$(MYQ "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'a2t%'")  期望 0"
echo "probe_files=$(MYQ "SELECT COUNT(*) FROM biz_file WHERE file_name IN ('a2f.txt','a2f2.txt','a2f3.txt')")  期望 0"
echo "probe_folders=$(MYQ "SELECT COUNT(*) FROM biz_file_folder WHERE folder_name IN ('a2dA','a2dB','a2dA_probe')")  期望 0"
echo "jar_md5=$(docker exec ycmn-backend md5sum /app/app.jar | awk '{print $1}')  期望 fcac2205…"
echo "health=$(curl -s -m 8 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/system/configs/public)  期望 200"
echo "permctx=$(docker exec "$RC" sh -c 'redis-cli --scan --pattern "perm:ctx:*" | wc -l')  期望 0"
REMOTE
