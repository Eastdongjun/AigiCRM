#!/bin/bash
# 【治本④ 2026-11-11】文件权限端到端一致性钉(用户令"权限一再有问题"治本): 三段闭环
#   勾X(admin 给测试号 USER 级授权) → 双端出X(PC permissions 接口 + MP 树回填接口)
#   → 操作放行X(真实下载 GET 走 checkFile) → 收回 → 双端复位(数据零残留)
# 【T0 加固 2026-11-11 审计整改】零硬编码凭据/地址: 全参数走环境变量, 缺任一则 SKIP(exit 0);
#   显式 AIGI_PERM_E2E=1 + 完整五变量才真执行(默认不跑, 离线/CI 不假红, 不碰共享测试环境)。
#   管理员凭据仍走 ~/.aigicrm-deploy.env(HEALTH_CHECK_*), 本脚本不落任何口令默认值。
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ "${AIGI_PERM_E2E:-0}" != "1" ]; then
  echo "SKIP: 文件权限e2e默认不跑(设 AIGI_PERM_E2E=1 且提供 PERM_E2E_BASE/USER/PASS/UID/FOLDER 环境变量以启用)"
  exit 0
fi
BASE="${PERM_E2E_BASE:-}"
E2E_TUSER="${PERM_E2E_USER:-}"
E2E_TPASS="${PERM_E2E_PASS:-}"
E2E_TUID="${PERM_E2E_UID:-}"
FOLDER_ID="${PERM_E2E_FOLDER:-}"
if [ -z "$BASE" ] || [ -z "$E2E_TUSER" ] || [ -z "$E2E_TPASS" ] || [ -z "$E2E_TUID" ] || [ -z "$FOLDER_ID" ]; then
  echo "SKIP: AIGI_PERM_E2E=1 但缺 PERM_E2E_BASE/USER/PASS/UID/FOLDER 之一(全部由环境注入, 脚本无默认值)"
  exit 0
fi
GRANTS="view,download,share,delete,rename"
PERMS_JSON=$(python3 -c "print(','.join('\"%s\"' % x for x in '$GRANTS'.split(',')))")

source ~/.aigicrm-deploy.env 2>/dev/null || true
[ -n "${HEALTH_CHECK_PASS:-}" ] || { echo "FAIL: 缺 HEALTH_CHECK_PASS(deploy.env)"; exit 1; }

fail() { echo "FAIL: $1" >&2; exit 1; }
login() { # 两次机会防网络抖动(小管道偶发断流)
  local t='' i
  for i in 1 2; do
    t=$(curl -s -m 15 -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
      -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'] if d.get('data') else '')" 2>/dev/null || true)
    [ -n "$t" ] && { echo "$t"; return 0; }
    sleep 2
  done
  echo ''
}

AT=$(login "${HEALTH_CHECK_USER:-admin}" "$HEALTH_CHECK_PASS"); [ -n "$AT" ] || fail "admin 登录失败"
T2=$(login "$E2E_TUSER" "$E2E_TPASS"); [ -n "$T2" ] || fail "测试号登录失败"

# 数据快照(测试号在 public/FILE 的 USER 行)
SNAP=$(curl -s "$BASE/api/file/folders/type-defaults" -H "Authorization: Bearer $AT" | python3 -c "
import sys,json
rows=[r for r in json.load(sys.stdin)['data'] if r.get('subjectType')=='USER' and r.get('subjectId')==$E2E_TUID and r.get('folderType')=='public' and r.get('objectType')=='FILE']
print(rows[0]['id'] if rows else '')")
cleanup() { [ -n "${GRANT_ID:-}" ] && curl -s -X DELETE "$BASE/api/file/folders/type-defaults/$GRANT_ID" -H "Authorization: Bearer $AT" >/dev/null || true; }
trap cleanup EXIT

view_perms() { # 双端口径: PC 单夹接口 + MP 树回填
  P=$(curl -s "$BASE/api/file/folders/$FOLDER_ID/permissions" -H "Authorization: Bearer $T2" | python3 -c "import sys,json;print(','.join(sorted(json.load(sys.stdin)['data']['perms'])))")
  M=$(curl -s "$BASE/api/file/folders?scope=public" -H "Authorization: Bearer $T2" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']; f=[x for x in d if x['id']==$FOLDER_ID]
print(','.join(sorted(f[0]['perms'])) if f else 'NO_FOLDER')")
  echo "$P|$M"
}

echo "== ① 授权前口径"
V0=$(view_perms); echo "  PC=${V0%%|*} MP=${V0##*|}"

echo "== ② 勾(ADMIN 给 $E2E_TUSER USER级授 $GRANTS)"
R=$(curl -s -X POST "$BASE/api/file/folders/type-defaults" -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' \
  -d "{\"folderType\":\"public\",\"objectType\":\"FILE\",\"subjectType\":\"USER\",\"subjectId\":$E2E_TUID,\"perms\":[$PERMS_JSON],\"scopeLevel\":null}")
echo "$R" | grep -q '"code":200' || fail "授权保存失败: $R"
GRANT_ID=$(curl -s "$BASE/api/file/folders/type-defaults" -H "Authorization: Bearer $AT" | python3 -c "
import sys,json
rows=[r for r in json.load(sys.stdin)['data'] if r.get('subjectType')=='USER' and r.get('subjectId')==$E2E_TUID and r.get('folderType')=='public' and r.get('objectType')=='FILE']
print(rows[0]['id'] if rows else '')")
[ -n "$GRANT_ID" ] || fail "授权行未落库"

echo "== ③ 双端出(两口径须一致且含 share,delete,rename)"
V1=$(view_perms); PC1=${V1%%|*}; MP1=${V1##*|}
echo "  PC=[$PC1]"; echo "  MP=[$MP1]"
[ "$PC1" = "$MP1" ] || fail "双端口径不一致: PC=[$PC1] MP=[$MP1]"
for a in share delete rename; do echo "$PC1" | tr ',' '\n' | grep -qx "$a" || fail "动作 $a 未在按钮视图出现"; done

echo "== ④ 操作放行(真实下载 GET→checkFile)"
FID=$(curl -s "$BASE/api/file/list?scope=public&folderId=$FOLDER_ID&current=1&size=1" -H "Authorization: Bearer $T2" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
rows=d.get('records') or d.get('list') or (d if isinstance(d,list) else [])
print(rows[0]['id'] if rows else '')" 2>/dev/null || echo '')
if [ -n "$FID" ]; then
  DLT=$(curl -s "$BASE/api/file/download-token" -H "Authorization: Bearer $T2" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/file/download/$FID?token=$DLT" --head)
  [ "$CODE" = "200" ] || CODE=$(curl -s -o /dev/null -w '%{http_code}' -r 0-0 "$BASE/api/file/download/$FID?token=$DLT")
  echo "  下载探测=$CODE"; [ "$CODE" = "200" ] || [ "$CODE" = "206" ] || fail "真实下载未放行(http=$CODE)"
else
  echo "  (夹内无文件, 下载探测跳过——非 FAIL)"
fi

echo "== ⑤ 收回→双端复位"
cleanup; GRANT_ID=''
sleep 1
V2=$(view_perms); PC2=${V2%%|*}; MP2=${V2##*|}
echo "  PC=[$PC2] MP=[$MP2]"
[ "$PC2" = "$MP2" ] || fail "收回后双端口径不一致"
[ "$PC2" = "${V0%%|*}" ] || fail "收回后未回到授权前口径(授权前=[${V0%%|*}] 现=[$PC2])"

echo "PASS: 文件权限端到端一致(勾→双端出→操作放行→收回复位)"
