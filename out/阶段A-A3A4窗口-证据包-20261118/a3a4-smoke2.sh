#!/bin/bash
# 窗口冒烟补充件②：A4 建单链 + A3 导出全链（宽容模式，逐项打印不静默退出）
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
source "$REPO/scripts/deploy-lib.sh" || exit 1
emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }
{
  echo 'set -uo pipefail'
  emit_var HC_USER "${HEALTH_CHECK_USER:-admin}"
  emit_secret HC_PASS HEALTH_CHECK_PASS
  cat <<'EOF'
API="https://www.aigicrm.com"
PW="$(docker exec ycmn-mysql sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
MQ() { docker exec -e MYSQL_PWD="$PW" ycmn-mysql mysql -uroot -N -B ycmn -e "$1" 2>&1 | tr -d '\r'; }
TOK=$(curl -s -m 45 --retry 3 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$HC_USER\",\"password\":\"$HC_PASS\"}" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
echo "[s] token=$([ -n "$TOK" ] && echo OK || echo FAIL)"

echo "=== grants 总数(全量拉取计数) ==="
GT=$(curl -s -m 30 "$API/api/file/grants" -H "Authorization: Bearer $TOK" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('code'), len(d.get('data') or []))" 2>/dev/null)
echo "[s] grants code,len=$GT"

echo "=== A4 建单验证 ==="
U=$(MQ "SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='0' AND r.status=1 AND u.deleted=0 AND u.status=1 ORDER BY u.id LIMIT 1")
SUBJ=$(MQ "SELECT id FROM sys_user WHERE deleted=0 AND status=1 AND id NOT IN (1) AND id != $U ORDER BY id LIMIT 1")
TGT=$(MQ "SELECT id FROM sys_dept WHERE status=1 AND parent_id>0 ORDER BY id DESC LIMIT 1")
echo "[s] 首启用成员=$U 调动人=$SUBJ 目标部门=$TGT"
CURDEPT=$(MQ "SELECT dept_id FROM sys_user WHERE id=$SUBJ")
echo "[s] 调动人现部门=$CURDEPT(若与 TGT 相同则换一个)"
if [ "$CURDEPT" = "$TGT" ]; then TGT=$(MQ "SELECT id FROM sys_dept WHERE status=1 AND parent_id>0 AND id != $TGT ORDER BY id LIMIT 1"); echo "[s] 换目标部门=$TGT"; fi
REQ=$(curl -s -m 20 -X POST "$API/api/system/lifecycle/org-move" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d "{\"userId\":$SUBJ,\"toDeptId\":$TGT,\"reason\":\"窗口冒烟验证(将驳回)\"}")
echo "[s] 建单resp=$(echo "$REQ" | head -c 220)"
RID=$(echo "$REQ" | python3 -c "import sys,json;print((json.load(sys.stdin).get('data') or {}).get('id',''))" 2>/dev/null)
if [ -n "$RID" ]; then
  ROW=$(MQ "SELECT CONCAT('approver=',approver_id,' status=',status) FROM biz_lifecycle_request WHERE id=$RID")
  SNAP=$(MQ "SELECT approver_snapshot_json FROM biz_lifecycle_request WHERE id=$RID" | head -c 200)
  echo "[s] 单=$RID $ROW"
  echo "[s] snapshot=$SNAP"
  [ "$ROW" = "approver=$U status=pending" ] && echo "[s] ✅ A4 解析=角色首启用成员+pending" || echo "[s] ⚠️ 解析核对(期望 approver=$U pending)"
  curl -s -m 20 -X POST "$API/api/system/lifecycle/requests/$RID/reject" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"reason":"窗口冒烟,即建即驳"}' >/dev/null
  echo "[s] 驳回后=$(MQ "SELECT status FROM biz_lifecycle_request WHERE id=$RID")(期望 rejected)"
fi

echo "=== A3 导出全链 ==="
EXP=$(curl -s -m 20 -X POST "$API/api/system/recycle/exports" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"types":["customer","user","file","task","incentive","reminder"]}')
EID=$(echo "$EXP" | python3 -c "import sys,json;print((json.load(sys.stdin).get('data') or {}).get('id',''))" 2>/dev/null)
echo "[s] export id=$EID resp=$(echo "$EXP" | head -c 150)"
ST=pending
for i in $(seq 1 40); do sleep 2; ST=$(MQ "SELECT status FROM biz_recycle_export WHERE id=$EID"); case "$ST" in ready|failed) break;; esac; done
echo "[s] export status=$ST err=$(MQ "SELECT COALESCE(error_msg,'') FROM biz_recycle_export WHERE id=$EID" | head -c 150)"
if [ "$ST" = "ready" ]; then
  DL=$(curl -s -m 90 -o /tmp/a3smoke.zip -D /tmp/a3hdr.txt -w '%{http_code}' "$API/api/system/recycle/exports/$EID/download" -H "Authorization: Bearer $TOK")
  SHA=$(grep -i x-checksum /tmp/a3hdr.txt | tr -d '\r' | awk '{print $2}')
  LSHA=$(sha256sum /tmp/a3smoke.zip 2>/dev/null | awk '{print $1}')
  echo "[s] download=$DL size=$(wc -c < /tmp/a3smoke.zip) sha_hdr=${SHA:0:16}… sha_match=$([ "$SHA" = "$LSHA" ] && echo YES || echo NO)"
  python3 - <<'PY' 2>/dev/null || echo "[s] zip 解析失败"
import zipfile
z = zipfile.ZipFile('/tmp/a3smoke.zip')
ns = z.namelist()
print("[s] zip entries=%d manifest=%s checksums=%s" % (len(ns), 'manifest.json' in ns, 'checksums.sha256' in ns))
for n in sorted(n for n in ns if n.startswith('data/')):
    print("   ", n, z.getinfo(n).file_size, "B")
PY
fi
echo "=== 对账 --prod ==="
echo "[s] 本地另行执行 reconcile --prod"
echo "SMOKE2_DONE"
EOF
} | ssh_prod_once "bash -s"
