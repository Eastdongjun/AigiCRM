#!/bin/bash
# ============================================================
# A2 D 阶段：测试服运行时验证（QC 第2轮清单 D1-D6）
# 子命令: state | prepare | sample <tag> | deploy | dual | new | restore
# 生产零接触；测试服用毕还原；凭据经 stdin 注入不打印。
# ============================================================
set -uo pipefail
REPO="/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码"
export AIGICRM_TEST_ONLY=1
source "$REPO/scripts/deploy-lib.sh" || exit 1

CT="${TEST_DB_CONTAINER:-shared-mysql}"
SRC_DB="${TEST_DB_NAME:-aigicrm_prod_20261016}"
LOCAL="/tmp/a2-drill-local/dphase"
LOG="$LOCAL/dp-$(date +%H%M%S).log"
mkdir -p "$LOCAL"
exec > >(tee -a "$LOG") 2>&1

# 本地生成临时口令 BCrypt（固定盐演示值, 只用于测试服临时用户）
PASS_CLEAR="A2Drill#2026"
PASS_HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'$PASS_CLEAR',bcrypt.gensalt(10)).decode())")

emit_var() { printf '%s=%q\n' "$1" "$2"; }
emit_secret() { printf '%s=%s\n' "$1" "$(printf '%q' "${!2}")"; }

# 远端公共脚本头（bash -s 体内）
remote_head() {
  cat <<'EOF'
set -Eeuo pipefail
fail() { echo "!!DP_FAIL: $*" >&2; exit 1; }
EOF
  emit_var CT "$CT"; emit_var SRC_DB "$SRC_DB"
  cat <<'EOF'
PW="$(docker inspect "$CT" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^MYSQL_ROOT_PASSWORD=' | cut -d= -f2-)"
[ -n "$PW" ] || fail "derive pw"
BK=$(docker ps --format '{{.Names}}' | grep -E 'backend' | head -1)
[ -n "$BK" ] || fail "backend container not found"
MY()  { docker exec -e MYSQL_PWD="$PW" "$CT" mysql -uroot --default-character-set=utf8mb4 "$@"; }
MYI() { docker exec -i -e MYSQL_PWD="$PW" "$CT" mysql -uroot --default-character-set=utf8mb4 "$@"; }
API="http://127.0.0.1:8080"
login() { # $1=username $2=pass -> token
  curl -s -m 10 -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'] if d.get('code')==200 and d.get('data') else 'LOGIN_FAIL')"
}
EOF
}
run_remote() { ssh_test_once "bash -s" || { echo "!!REMOTE_FAIL($1)"; return 1; }; }

case "${1:-}" in
# ───────────────────────── 状态探查 ─────────────────────────
state)
  { remote_head
    cat <<'EOF'
echo "[container] $BK  image=$(docker inspect -f '{{.Config.Image}}' $BK)"
echo "[jar-md5] $(docker exec $BK md5sum /app/app.jar 2>/dev/null | awk '{print $1}')"
echo "[health] $(curl -s -m 8 -o /dev/null -w '%{http_code}' $API/api/system/configs/public)"
echo "[flyway] $(MY -N -B "$SRC_DB" -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")"
echo "[old_rows] $(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_folder_type_default")"
echo "[file_grant] $(MY -N -B -e "SELECT COUNT(*) FROM $SRC_DB.biz_file_grant" 2>/dev/null || echo NOT-EXISTS)"
echo "[sys_user_max] $(MY -N -B -e "SELECT COALESCE(MAX(id),0) FROM $SRC_DB.sys_user")"
echo "[permctx] $(docker exec $(docker ps --format '{{.Names}}' | grep -i redis | head -1) redis-cli --scan --pattern 'perm:ctx:*' 2>/dev/null | wc -l)"
EOF
  } | run_remote state
  ;;

# ───────────────────────── 准备临时数据（迁移前/旧代码） ─────────────────────────
prepare)
  { remote_head
    emit_var PASS_HASH "$PASS_HASH"
    cat <<'EOF'
# ① 临时用户 99(admin)/100(销售员,角色11)/101(行政,角色10)/102(销售总监,角色9)，全部 dept=4 org=4（R-3: org_id 必填）
MYI "$SRC_DB" <<SQL
INSERT INTO sys_user (username, password, real_name, dept_id, office_id, org_id, status, deleted, created_at, updated_at, pwd_reset_required, perm_version)
VALUES ('a2t99','$PASS_HASH','A2管理员',4,4,4,1,0,NOW(),NOW(),0,1),
       ('a2t100','$PASS_HASH','A2销售员',4,4,4,1,0,NOW(),NOW(),0,1),
       ('a2t101','$PASS_HASH','A2行政',4,4,4,1,0,NOW(),NOW(),0,1),
       ('a2t102','$PASS_HASH','A2总监',4,4,4,1,0,NOW(),NOW(),0,1)
ON DUPLICATE KEY UPDATE password=VALUES(password);
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
  ((SELECT id FROM sys_user WHERE username='a2t99'),1),
  ((SELECT id FROM sys_user WHERE username='a2t100'),11),
  ((SELECT id FROM sys_user WHERE username='a2t101'),10),
  ((SELECT id FROM sys_user WHERE username='a2t102'),9);
SQL
for U in a2t99 a2t100 a2t101 a2t102; do
  A2UID=$(MY -N -B "$SRC_DB" -e "SELECT id FROM sys_user WHERE username='$U'")
  docker exec -u 0 $BK mkdir -p /app/uploads/private/$A2UID 2>/dev/null || true
done
# R-3: 上传目录归属容器内应用用户(appuser=10001)，bind mount 宿主属 ubuntu 需显式 chown
docker exec -u 0 $BK sh -c "chown -R appuser:appuser /app/uploads/private 2>/dev/null || chown -R appuser:appuser /app/uploads/private 2>/dev/null || true'
echo "[users] ids=$(MY -N -B "$SRC_DB" -e "SELECT GROUP_CONCAT(CONCAT(username,'=',id)) FROM sys_user WHERE username LIKE 'a2t%'")"
# ② 登录 99 上传三空间文件（99=admin 角色持全码；文件 owner=99）
TOK=$(login a2t99 'A2Drill#2026')
[ "$TOK" != "LOGIN_FAIL" ] || fail "login a2t99"
PUB_FID=$(MY -N -B "$SRC_DB" -e "SELECT id FROM biz_file_folder WHERE folder_type='public' AND deleted=0 ORDER BY id LIMIT 1")
PRV_FID=$(MY -N -B "$SRC_DB" -e "SELECT id FROM biz_file_folder WHERE folder_type='private' AND deleted=0 ORDER BY id LIMIT 1")
echo "[folders] public=$PUB_FID private=$PRV_FID"
echo "a2-drill-pub" > /tmp/a2f.txt
UP() { curl -s -m 15 -X POST "$API/api/file/upload" -H "Authorization: Bearer $TOK" -F "file=@/tmp/a2f.txt" -F "folderId=$1" -F "scope=$2"; }
R=$(UP $PUB_FID public);  echo "[up-public]  $(echo "$R" | head -c 160)"
R=$(UP $PRV_FID private); echo "[up-private] $(echo "$R" | head -c 160)"
R=$(UP "" incentive);     echo "[up-inc]     $(echo "$R" | head -c 160)"
# ③ 记录三文件 id（本脚本刚上传的 3 行：按 id 倒序取最旧优先——用文件名锚定）
sleep 1
MY -t "$SRC_DB" -e "SELECT id, file_name, folder_id, file_scope, uploader_id, org_id FROM biz_file WHERE file_name IN ('a2f.txt','a2f2.txt') AND deleted=0 ORDER BY id DESC LIMIT 3"
# ④ 旧入口合成授权（旧代码: 只进旧表; 迁移时一并迁走）: 角色10 incentive view+download
CODE=$(curl -s -m 10 -o /tmp/a2grant.json -w '%{http_code}' -X POST "$API/api/file/folders/type-defaults" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"folderType":"incentive","objectType":"FILE","subjectType":"ROLE","subjectId":10,"perms":["view","download"],"scopeLevel":null,"expiresAt":null}')
echo "[syn-grant] http=$CODE $(cat /tmp/a2grant.json | head -c 200)"
MY -t "$SRC_DB" -e "SELECT id, folder_type, object_type, subject_type, subject_id, perms, scope_level FROM biz_folder_type_default WHERE subject_type='ROLE' AND subject_id=10 AND folder_type='incentive'"
EOF
  } | run_remote prepare
  ;;

# ───────────────────────── 采样（行为对账矩阵） ─────────────────────────
sample)
  TAG="${2:?tag required}"
  { remote_head
    emit_var TAG "$TAG"
    cat <<'EOF'
# 文件锚定：本批上传的三行 a2f.txt（pub/prv/inc 各一, 取每 scope 最大 id）
mapfile -t F < <(MY -N -B "$SRC_DB" -e "SELECT CONCAT(id,'|',file_scope) FROM biz_file WHERE file_name IN ('a2f.txt','a2f2.txt') AND deleted=0 ORDER BY id DESC LIMIT 3")
[ "${#F[@]}" = "3" ] || fail "expect 3 probe files, got ${#F[@]}"
for U in a2t100 a2t101 a2t102; do
  TOK=$(login $U 'A2Drill#2026')
  [ "$TOK" != "LOGIN_FAIL" ] || fail "login $U"
  A2UID=$(MY -N -B "$SRC_DB" -e "SELECT id FROM sys_user WHERE username='$U'")
  for ROW in "${F[@]}"; do
    FID="${ROW%%|*}"; SCOPE="${ROW##*|}"
    DL=$(curl -s -m 10 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" "$API/api/file/download/$FID")
    echo "SAMPLE|$TAG|$U|$FID|$SCOPE|dl=$DL"
  done
  # 行级 perms（每空间列表）
  for S in public private incentive; do
    curl -s -m 10 -H "Authorization: Bearer $TOK" "$API/api/file/list?scope=$S&current=1&size=100" \
      | python3 -c "
import sys,json
d=json.load(sys.stdin)
rows=(d.get('data') or {}).get('records') or []
tgt=[r for r in rows if r.get('fileName') in ('a2f.txt','a2f2.txt')]
print('SAMPLE|$TAG|$U|list|$S|' + (';'.join(f\"{r['id']}:{','.join(sorted(r.get('perms') or []))}\" for r in tgt) or 'EMPTY'))
" 2>/dev/null || echo "SAMPLE|$TAG|$U|list|$S|PARSE_FAIL"
  done
done
EOF
  } | run_remote "sample-$TAG" | tee "$LOCAL/sample-$TAG.txt" | grep -c "^SAMPLE|" | xargs echo "[sample:$TAG] lines:"
  ;;

*) echo "用法: dp.sh state|prepare|sample <tag>|deploy|dual|new|restore" >&2; exit 2 ;;
esac
