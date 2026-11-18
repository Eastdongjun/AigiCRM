#!/bin/bash
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
source "$REPO/scripts/deploy-lib.sh" || exit 1
emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }
{
  echo 'set -Eeuo pipefail'
  emit_var HC_USER "${HEALTH_CHECK_USER:-admin}"
  emit_secret HC_PASS HEALTH_CHECK_PASS
  cat <<'EOF'
API="https://www.aigicrm.com"
PW="$(docker exec ycmn-mysql sh -c 'printf %s "$MYSQL_ROOT_PASSWORD"')"
MQ() { docker exec -e MYSQL_PWD="$PW" ycmn-mysql mysql -uroot -N -B ycmn -e "$1" 2>/dev/null | tr -d '\r'; }
login() { curl -s -m 45 --retry 3 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$HC_USER\",\"password\":\"$HC_PASS\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo FAIL; }
TOK=$(login); [ "$TOK" != "FAIL" ] || { echo "[smoke] login=fail"; exit 1; }
echo "[smoke] login=200"

echo "=== ⑤ A4 配置初始化(两条流程) ==="
ROLE_ID=$(MQ "SELECT id FROM sys_role WHERE role_code='0' AND status=1 LIMIT 1")
echo "[a4] sales_director_role(role_code=0) id=$ROLE_ID"
[ -n "$ROLE_ID" ] || { echo "[a4] 角色未找到"; exit 1; }
for FLOW in handover org_move; do
  EXIST=$(MQ "SELECT COUNT(*) FROM biz_approval_config WHERE flow_type='$FLOW' AND status=1")
  if [ "$EXIST" = "0" ]; then
    CODE=$(curl -s -m 15 -o /tmp/a4cfg.json -w '%{http_code}' -X POST "$API/api/approval/config" \
      -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
      -d "{\"flowName\":\"$FLOW-审批\",\"flowType\":\"$FLOW\",\"approverType\":\"ROLE\",\"approverIds\":\"[$ROLE_ID]\",\"signMode\":\"ANY\"}")
    echo "[a4] create $FLOW http=$CODE $(head -c 120 /tmp/a4cfg.json)"
  else
    echo "[a4] $FLOW 已有 $EXIST 条启用配置——跳过(不重复初始化)"
  fi
done

echo "=== ⑥-1 grants(应 279 legacy) ==="
GRANTS=$(curl -s -m 15 "$API/api/file/grants?current=1&size=1" -H "Authorization: Bearer $TOK" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('code'),(d.get('data') or {}).get('total'))" 2>/dev/null || echo "?")
echo "[smoke] grants code,total=$GRANTS"

echo "=== ⑥-2 代表性下载 ==="
FIRST=$(curl -s -m 15 "$API/api/file/list?scope=public&current=1&size=1" -H "Authorization: Bearer $TOK" \
  | python3 -c "import sys,json;r=(json.load(sys.stdin).get('data') or {}).get('records') or [];print(r[0]['id'] if r else 'NONE')" 2>/dev/null)
[ "$FIRST" != "NONE" ] && echo "[smoke] download[$FIRST]=$(curl -s -m 30 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" "$API/api/file/download/$FIRST")" || echo "[smoke] list 空"

echo "=== ⑥-3 A4 建单验证(快照+首启用成员) → 即驳回(终态无业务影响) ==="
U=$(MQ "SELECT u.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='0' AND r.status=1 AND u.deleted=0 AND u.status=1 ORDER BY u.id LIMIT 1")
SUBJ=$(MQ "SELECT id FROM sys_user WHERE deleted=0 AND status=1 AND id NOT IN (1) ORDER BY id LIMIT 1")
TGT=$(MQ "SELECT id FROM sys_dept WHERE deleted=0 AND status=1 AND parent_id>0 ORDER BY id DESC LIMIT 1")
echo "[a4] 首启用成员=$U 调动人=$SUBJ 目标部门=$TGT"
if [ -n "$SUBJ" ] && [ -n "$TGT" ]; then
  REQ=$(curl -s -m 15 -X POST "$API/api/system/lifecycle/org-move" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
    -d "{\"userId\":$SUBJ,\"toDeptId\":$TGT,\"reason\":\"窗口冒烟验证(将驳回)\"}")
  RID=$(echo "$REQ" | python3 -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('id',''))" 2>/dev/null)
  APPROVER=$(echo "$REQ" | python3 -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('approver_id',''))" 2>/dev/null)
  SNAP=$(MQ "SELECT approver_snapshot_json FROM biz_lifecycle_request WHERE id=$RID")
  echo "[a4] 单=$RID approver=$APPROVER(期望=$U) snapshot=$(echo $SNAP | head -c 160)"
  [ "$APPROVER" = "$U" ] && echo "[a4] ✅ 解析=角色首启用成员" || echo "[a4] ❌ 解析不符"
  curl -s -m 15 -X POST "$API/api/system/lifecycle/requests/$RID/reject" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"reason":"窗口冒烟,即建即驳"}' >/dev/null
  ST=$(MQ "SELECT status FROM biz_lifecycle_request WHERE id=$RID")
  echo "[a4] 驳回后状态=$ST(期望 rejected)"
fi

echo "=== ⑥-4 A3 导出全链 ==="
EXP=$(curl -s -m 15 -X POST "$API/api/system/recycle/exports" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"types":["customer","user","file","task","incentive","reminder"]}')
EID=$(echo "$EXP" | python3 -c "import sys,json;print((json.load(sys.stdin).get('data') or {}).get('id',''))" 2>/dev/null)
echo "[a3] export id=$EID $(echo $EXP | head -c 120)"
for i in $(seq 1 30); do sleep 2; ST=$(MQ "SELECT status FROM biz_recycle_export WHERE id=$EID"); [ "$ST" = "ready" ] || [ "$ST" = "failed" ] && break; done
echo "[a3] status=$ST"
if [ "$ST" = "ready" ]; then
  DL=$(curl -s -m 60 -o /tmp/a3smoke.zip -D /tmp/a3hdr.txt -w '%{http_code}' "$API/api/system/recycle/exports/$EID/download" -H "Authorization: Bearer $TOK")
  SHA=$(grep -i x-checksum /tmp/a3hdr.txt | tr -d '\r' | awk '{print $2}')
  LSHA=$(sha256sum /tmp/a3smoke.zip | awk '{print $1}')
  ZSIZE=$(stat -c%s /tmp/a3smoke.zip 2>/dev/null || wc -c < /tmp/a3smoke.zip)
  echo "[a3] download=$DL size=$ZSIZE sha_match=$([ "$SHA" = "$LSHA" ] && echo YES || echo "NO(hdr=$SHA local=$LSHA)")"
  python3 -c "import zipfile;z=zipfile.ZipFile('/tmp/a3smoke.zip');ns=z.namelist();print('[a3] entries=',len(ns),'manifest' if 'manifest.json' in ns else 'NO-manifest','checksums' if 'checksums.sha256' in ns else 'NO-checksums');import sys;[print('  ',n) for n in ns if n.startswith('data/')]" 2>/dev/null | head -10
fi
echo "SMOKE_DONE"
EOF
} | ssh_prod_once "bash -s"
