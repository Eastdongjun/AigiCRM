#!/bin/bash
# 基线收敛：从 QC 原始快照备份(090741)恢复旧表 62 行原值；清临时用户/文件行
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
source "$REPO/scripts/deploy-lib.sh"
ssh_test_once "bash -s" <<'REMOTE'
set -Eeuo pipefail
PW="$(docker inspect shared-mysql --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
SRC=/tmp/pre-migrate-test-20261118-090741.sql
[ -f "$SRC" ] || { echo "NO_ORIG_BACKUP"; exit 1; }
# ① 从原始备份抽取旧表全量 INSERT 段
awk '/INSERT INTO `biz_folder_type_default`/{f=1} f{print} f&&/;$/{exit}' "$SRC" > /tmp/a2d-orig-oldtable.sql
grep -c "^INSERT" /tmp/a2d-orig-oldtable.sql | xargs echo "[converge] 原始备份旧表 INSERT 语句数:"
MYQ() { docker exec -e MYSQL_PWD="$PW" shared-mysql mysql -uroot -N -B aigicrm_prod_20261016 -e "$1"; }
MIQ() { docker exec -i -e MYSQL_PWD="$PW" shared-mysql mysql -uroot --default-character-set=utf8mb4 aigicrm_prod_20261016 "$@"; }
# ② 清临时数据
MYQ "DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'a2t%')"
MYQ "DELETE FROM sys_user WHERE username LIKE 'a2t%'"
MYQ "DELETE FROM biz_file WHERE file_name IN ('a2f.txt','a2f2.txt','a2f3.txt')"
# ③ 旧表恢复原始 62 行
MYQ "DELETE FROM biz_folder_type_default"
MIQ < /tmp/a2d-orig-oldtable.sql
echo "[converge] old_rows=$(MYQ 'SELECT COUNT(*) FROM biz_folder_type_default')（期望 62）"
echo "[converge] id176 perms=$(MYQ "SELECT perms FROM biz_folder_type_default WHERE id=176")（原始值, 应为多动作 CSV 而非 view,download）"
echo "[converge] sys_user_max=$(MYQ 'SELECT COALESCE(MAX(id),0) FROM sys_user')（期望 82）"
echo "[converge] a2t_users=$(MYQ "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'a2t%'")（期望 0）"
echo "[converge] probe_files=$(MYQ "SELECT COUNT(*) FROM biz_file WHERE file_name IN ('a2f.txt','a2f2.txt','a2f3.txt')")（期望 0）"
rm -f /tmp/a2d-orig-oldtable.sql
# ④ 复核其余基线
echo "flyway_tail=$(MYQ 'SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1')"
echo "file_grant_table=$(docker exec -e MYSQL_PWD="$PW" shared-mysql mysql -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='aigicrm_prod_20261016' AND table_name='biz_file_grant'")"
echo "health=$(curl -s -m 8 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/system/configs/public)"
REMOTE
