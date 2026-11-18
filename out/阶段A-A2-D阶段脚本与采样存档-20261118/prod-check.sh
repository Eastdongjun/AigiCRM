#!/bin/bash
# 【用户拍板 #1 已授权】生产只读前置核查：三条纯 SELECT + 重复组核对（零写入零变更）
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
# 生产通道需要完整 deploy-lib（含 PROD 校验），不设 AIGICRM_TEST_ONLY
source "$REPO/scripts/deploy-lib.sh" || exit 1

ssh_prod_once "bash -s" <<'REMOTE'
set -Eeuo pipefail
CT=ycmn-mysql; DB=ycmn
PW="$(docker exec $CT sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
[ -n "$PW" ] || { echo "PW_DERIVE_FAIL"; exit 1; }
MYQ() { docker exec -e MYSQL_PWD="$PW" $CT mysql -uroot --default-character-set=utf8mb4 -N -B "$DB" -e "$1"; }
echo "===== E1: biz_file_grant 表存在性与行数（source 分布）====="
TBL=$(docker exec -e MYSQL_PWD="$PW" $CT mysql -uroot -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB' AND table_name='biz_file_grant'")
if [ "$TBL" = "0" ]; then
  echo "E1: 表不存在（A2 未上生产, 手工行=0）"
else
  echo "E1: 表存在, 总行数=$(MYQ 'SELECT COUNT(*) FROM biz_file_grant')"
  MYQ 'SELECT source, COUNT(*) FROM biz_file_grant GROUP BY source' | sed 's/^/E1: source=/'
  echo "E1: 手工行(manual/非legacy非dual)=$(MYQ "SELECT COUNT(*) FROM biz_file_grant WHERE source IS NULL OR source='manual'")"
fi
echo "===== E2: 旧表 folder_type blank 分布 ====="
echo "E2: 总行数=$(MYQ 'SELECT COUNT(*) FROM biz_folder_type_default')"
MYQ "SELECT CONCAT('E2: folder_type=[', folder_type, '] blank=', (TRIM(folder_type)=''), ' cnt=', COUNT(*)) FROM biz_folder_type_default GROUP BY folder_type, (TRIM(folder_type)='')) ORDER BY 1" 2>/dev/null || \
MYQ "SELECT CONCAT('E2: folder_type=[', folder_type, '] blank=', (TRIM(folder_type)=''), ' cnt=', COUNT(*)) FROM biz_folder_type_default GROUP BY folder_type, (TRIM(folder_type)='') ORDER BY 1"
echo "===== E3: 旧表 expires_at 分布 ====="
echo "E3: NULL(永久)=$(MYQ 'SELECT COUNT(*) FROM biz_folder_type_default WHERE expires_at IS NULL')  有值=$(MYQ 'SELECT COUNT(*) FROM biz_folder_type_default WHERE expires_at IS NOT NULL')"
echo "===== E4: §2.10 重复组核对 ====="
MYQ "SELECT COUNT(*) FROM (
  SELECT 1 FROM (
    SELECT d.id, d.subject_type, d.subject_id, d.folder_id, d.scope_level, d.scope_org_id,
           CASE WHEN a.action IN ('rename','replace') THEN 'edit' ELSE a.action END AS action
      FROM biz_folder_type_default d
      JOIN (SELECT 'view' AS action UNION ALL SELECT 'download' UNION ALL SELECT 'upload' UNION ALL SELECT 'edit'
            UNION ALL SELECT 'rename' UNION ALL SELECT 'replace' UNION ALL SELECT 'delete'
            UNION ALL SELECT 'share' UNION ALL SELECT 'manage' UNION ALL SELECT 'create') a
        ON FIND_IN_SET(a.action, REPLACE(COALESCE(d.perms,''),' ','')) > 0
     WHERE d.subject_type IN ('ROLE','USER') AND d.subject_id IS NOT NULL
  ) x
  GROUP BY subject_type, subject_id, CASE WHEN folder_id IS NULL THEN 'SPACE' ELSE 'FOLDER' END, folder_id, action
  HAVING COUNT(*) > 1) t" | sed 's/^/E4: conflict_groups=/'
echo "===== E5: scope_level/scope_org_id 分布（核对既有基线口径 78NULL/5region/4self）====="
MYQ "SELECT CONCAT('E5: scope_level=', COALESCE(scope_level,'NULL'), ' cnt=', COUNT(*)) FROM biz_folder_type_default GROUP BY scope_level" | sort
echo "E5: scope_org_id_nonnull=$(MYQ 'SELECT COUNT(*) FROM biz_folder_type_default WHERE scope_org_id IS NOT NULL')"
echo "PROD_CHECK_DONE（全程只读）"
REMOTE
echo "PROD_EXIT:$?"