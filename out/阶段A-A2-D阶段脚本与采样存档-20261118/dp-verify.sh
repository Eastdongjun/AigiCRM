#!/bin/bash
# D2(F-1 软删锚点)/D3(spaceType 全链)/C6-RT(真实双写续期→对账0) 综合验证（dual 模式下）
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
source "$REPO/scripts/deploy-lib.sh"
ssh_test_once "bash -s" <<'REMOTE'
set -Eeuo pipefail
fail() { echo "!!DP_FAIL: $*" >&2; exit 1; }
PW="$(docker inspect shared-mysql --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
MY()  { docker exec -e MYSQL_PWD="$PW" shared-mysql mysql -uroot --default-character-set=utf8mb4 aigicrm_prod_20261016 "$@"; }
MYI() { docker exec -i -e MYSQL_PWD="$PW" shared-mysql mysql -uroot --default-character-set=utf8mb4 aigicrm_prod_20261016 "$@"; }
API="http://127.0.0.1:8080"
login() { curl -s -m 10 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"A2Drill#2026\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'] if d.get('code')==200 and d.get('data') else 'LOGIN_FAIL')"; }
dl() { curl -s -m 10 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $1" "$API/api/file/download/$2"; }
TOK=$(login a2t99); [ "$TOK" != "LOGIN_FAIL" ] || fail "login 99"
T101=$(login a2t101); [ "$T101" != "LOGIN_FAIL" ] || fail "login 101"
T102=$(login a2t102); [ "$T102" != "LOGIN_FAIL" ] || fail "login 102"

echo "===== C6-RT: 旧入口续期(id=176) → 真实双写 upsert ====="
BEFORE=$(MY -N -B -e "SELECT GROUP_CONCAT(CONCAT(legacy_ref,'=',id),'|',COALESCE(expire_at,'NULL')) FROM biz_file_grant WHERE legacy_ref LIKE 'ftd:176:%'")
echo "[c6rt] before: $BEFORE"
CODE=$(curl -s -m 10 -o /tmp/c6.json -w '%{http_code}' -X PUT "$API/api/file/folders/type-defaults/176/renew" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"expiresAt":"2027-03-01 00:00:00"}')
echo "[c6rt] renew http=$CODE $(head -c 120 /tmp/c6.json)"
AFTER=$(MY -N -B -e "SELECT GROUP_CONCAT(CONCAT(legacy_ref,'=',id),'|',COALESCE(expire_at,'NULL')) FROM biz_file_grant WHERE legacy_ref LIKE 'ftd:176:%'")
echo "[c6rt] after:  $AFTER"
echo "$BEFORE" | grep -o 'ftd:[0-9]*:[a-z]*=[0-9]*' | tr ',' '\n' | sort > /tmp/b.txt
echo "$AFTER"  | grep -o 'ftd:[0-9]*:[a-z]*=[0-9]*' | tr ',' '\n' | sort > /tmp/a.txt
diff /tmp/b.txt /tmp/a.txt >/dev/null || fail "C6-RT legacy_ref 行 id 漂移"
MY -N -B -e "SELECT COUNT(*) FROM biz_file_grant WHERE legacy_ref LIKE 'ftd:176:%' AND expire_at='2027-03-01 00:00:00'" | xargs echo "[c6rt] expire 已同步行数(期望2):"

echo "===== D2: F-1 软删锚点 → 403（新预期） ====="
# 建两级夹 A2A→A2B + 文件（private）
curl -s -m 10 -X POST "$API/api/file/folders" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"folderName":"a2dA","folderType":"private"}' >/dev/null
FA=$(MY -N -B -e "SELECT id FROM biz_file_folder WHERE folder_name='a2dA' AND deleted=0 ORDER BY id DESC LIMIT 1")
[ -n "$FA" ] || fail "folder A not created"
curl -s -m 10 -X POST "$API/api/file/folders" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d "{\"folderName\":\"a2dB\",\"folderType\":\"private\",\"parentId\":$FA}" >/dev/null
FB=$(MY -N -B -e "SELECT id FROM biz_file_folder WHERE folder_name='a2dA' AND deleted=0 ORDER BY id DESC LIMIT 1")
FB=$(MY -N -B -e "SELECT id FROM biz_file_folder WHERE folder_name='a2dB' AND deleted=0 AND parent_id=$FA ORDER BY id DESC LIMIT 1")
[ -n "$FB" ] || fail "folder B not created"
echo "d2 folders A=$FA B=$FB"
echo x > /tmp/a2f3.txt
FID=$(curl -s -m 15 -X POST "$API/api/file/upload" -H "Authorization: Bearer $TOK" -F "file=@/tmp/a2f3.txt" -F "folderId=$FB" -F "scope=private" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
echo "d2 file=$FID"
C0=$(dl $T101 $FID); echo "[d2] 授权前 101 下载=$C0（期望403）"
G=$(curl -s -m 10 -X POST "$API/api/file/grants" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"USER\",\"subjectId\":101,\"objectType\":\"FOLDER\",\"objectId\":$FA,\"spaceType\":null,\"action\":\"view\",\"effect\":\"ALLOW\",\"inherit\":1,\"scopeOrgId\":null,\"scopeLevel\":null,\"scopeMode\":null,\"expireAt\":null}")
GID=$(echo "$G" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
echo "[d2] 新引擎 FOLDER grant id=$GID"
C1=$(dl $T101 $FID); echo "[d2] 授权后 101 下载=$C1（期望200, 递归继承）"
MY -e "UPDATE biz_file_folder SET deleted=1 WHERE id=$FA"
C2=$(dl $T101 $FID); echo "[d2] 软删锚点 A 后 101 下载=$C2（期望403, F-1 新预期）"
MY -e "UPDATE biz_file_folder SET deleted=0 WHERE id=$FA"
C3=$(dl $T101 $FID); echo "[d2] 恢复 A 后 101 下载=$C3（期望200）"
curl -s -m 10 -X DELETE "$API/api/file/grants/$GID" -H "Authorization: Bearer $TOK" >/dev/null

echo "===== D3: spaceType 全链（SPACE public DENY） ====="
G3=$(curl -s -m 10 -X POST "$API/api/file/grants" -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"subjectType":"USER","subjectId":102,"objectType":"SPACE","spaceType":"public","action":"download","effect":"DENY","inherit":0,"scopeOrgId":null,"scopeLevel":null,"scopeMode":null,"expireAt":null}')
G3ID=$(echo "$G3" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
ST=$(curl -s -m 10 "$API/api/file/grants" -H "Authorization: Bearer $TOK" | python3 -c "
import sys,json
rows=json.load(sys.stdin)['data']
r=[x for x in rows if x['id']==$G3ID][0]
print('spaceType='+str(r.get('spaceType'))+' objectType='+r.get('objectType')+' effect='+r.get('effect'))")
echo "[d3] 回读 $ST（期望 spaceType=public）"
CD=$(dl $T102 2705); echo "[d3] DENY 后 102 下载 public 2705=$CD（期望403, DENY 优先）"
curl -s -m 10 -X DELETE "$API/api/file/grants/$G3ID" -H "Authorization: Bearer $TOK" >/dev/null
CR=$(dl $T102 2705); echo "[d3] 删除 grant 后=$CR（期望200 回到基线）"

echo "===== D4 预埋: 删 legacy 行 ftd:172:*（ROLE#10 private 全动作）→ dual 应仍放行（回落旧表） ====="
MY -N -B -e "SELECT COUNT(*) FROM biz_file_grant WHERE legacy_ref LIKE 'ftd:172:%'" | xargs echo "[d4] ftd:172 行数:"
MY -e "DELETE FROM biz_file_grant WHERE legacy_ref LIKE 'ftd:172:%'"
CD4=$(dl $T101 2707); echo "[d4-dual] 删新表行后 101 下载 private 2707=$CD4（期望200, dual 回落旧表）"
echo "ALL-VERIFY-DONE"
REMOTE
echo "VERIFY_EXIT:$?"