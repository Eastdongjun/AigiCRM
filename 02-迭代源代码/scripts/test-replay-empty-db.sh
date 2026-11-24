#!/usr/bin/env bash
# replay-empty-db.sh 的行为测试（2026-11-25 补；这个脚本此前没有任何行为测试，因为它一跑就要连测试服）。
#
# 验什么:
#   ① 本机这一半: 排序规则只认两个值, 别的（包括想往远端命令行里塞东西）在上传之前就拒绝;
#      包里只带要重放的迁移, 不带已并入基线的 V2~V11, 也不带本机工具状态目录(.mimosa);
#      断言码清单、测试服配置缺失时不上传就退出。
#   ② 远端那一半: 把要发到测试服的那段脚本整段放进“假测试服”里跑——docker / openssl / sleep 换成桩,
#      按场景应答 flyway 的末态。迁移数对不上、有失败的迁移、断言码停用或零绑定、admin 没持全码、MySQL 起不来,
#      各自必须报红; 两种排序规则都原样传给了 MySQL 容器; 不论成败, 容器与网络都被清掉。
#
# 做法: 把被测脚本、退出码守卫、断言码清单复制进临时目录, 换上桩 deploy-lib.sh(不读部署配置、不连服务器)。
#       远端脚本用清空过的环境变量与隔离的 PATH 运行: PATH 里只有桩目录和逐个软链的基础工具, 不继承本机 PATH
#       (否则“MySQL 起不来”这类场景会被本机真实的 docker 接住)。全程不联网、不碰任何服务器。
#       远端脚本里写死的 /tmp/mig-replay.tgz、/tmp/replay-codes.txt、/tmp/aigicrm-empty-replay 在本机临时借用:
#       测试开始前它们必须不存在(存在就按 BLOCKED 退出), 结束后由远端脚本自己的收尾清掉。
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-replay-test.XXXXXX")"

REMOTE_TGZ=/tmp/mig-replay.tgz
REMOTE_CODES=/tmp/replay-codes.txt
REMOTE_DIR=/tmp/aigicrm-empty-replay
cleanup() {
  rm -f "$REMOTE_TGZ" "$REMOTE_CODES"
  rm -rf "$REMOTE_DIR"
  rm -rf "$TMP"
}
trap cleanup EXIT

# 这三个是真实运行时测试服上的路径名; 本机上如果有同名的东西, 说明有别的真实动作在用, 不抢
for p in "$REMOTE_TGZ" "$REMOTE_CODES" "$REMOTE_DIR"; do
  if [ -e "$p" ]; then
    echo "BLOCKED: 本机上已经有 $p —— 可能是另一次真实运行留下的, 本测试不覆盖它" >&2
    exit 2
  fi
done

SD="$TMP/scripts"
MIG="$TMP/aigicrm-backend/src/main/resources/db/migration"
STUB_BIN="$TMP/stub-bin"
BASE_BIN="$TMP/base-bin"
TRACE="$TMP/trace.log"          # 桩与假 docker 按发生顺序记录的调用
PACK_COPY="$TMP/pack.tgz"       # 第一次上传(迁移包)的副本
REMOTE_SCRIPT="$TMP/remote.sh"  # 要发到测试服的那段脚本
mkdir -p "$SD" "$MIG" "$STUB_BIN" "$BASE_BIN" "$MIG/.mimosa/hook-state"
for f in replay-empty-db.sh exit-guard.sh empty-replay-codes.txt; do
  cp "$TEST_SELF_DIR/$f" "$SD/"
done
# 假迁移目录: 6 支要重放的 + 2 支已并入基线的(V2、V10, 不该进包) + 一个本机工具状态文件(不该进包)
: > "$MIG/V1__baseline.sql"
: > "$MIG/V12__a.sql"
: > "$MIG/V20__b.sql"
: > "$MIG/V2026.09.11.1__c.sql"
: > "$MIG/V2026.11.22.1__d.sql"
: > "$MIG/V2026.11.23.9__e.sql"
: > "$MIG/V2__merged_into_baseline.sql"
: > "$MIG/V10__old.sql"
: > "$MIG/.mimosa/hook-state/session.json"
EXPECTED=6
TOTAL_CODES="$(/usr/bin/grep -cE '^[a-z]' "$SD/empty-replay-codes.txt")"

# 基础工具: 逐个软链, 不继承本机 PATH
link_tool() { ln -s "$1" "$BASE_BIN/$2"; }
link_tool /bin/bash bash;         link_tool /bin/cat cat;      link_tool /bin/cp cp
link_tool /usr/bin/env env;       link_tool /usr/bin/tar tar;  link_tool /bin/date date
link_tool /usr/bin/grep grep;     link_tool /usr/bin/sed sed;  link_tool /usr/bin/tr tr
link_tool /usr/bin/tail tail;     link_tool /usr/bin/head head; link_tool /usr/bin/seq seq
link_tool /usr/bin/wc wc;         link_tool /usr/bin/cut cut;  link_tool /bin/rm rm
link_tool /bin/mkdir mkdir;       link_tool /usr/bin/find find; link_tool /usr/bin/sort sort
link_tool /usr/bin/dirname dirname

# ---- 桩 deploy-lib.sh: 不读部署配置、不连服务器。scp 只记录并按远端的路径名放好文件;
#      ssh 把要发的脚本存下来, 放进“假测试服”里跑 ----
printf '%s\n' "TEST_SELF_DIR_CODES=$SD/empty-replay-codes.txt" > "$SD/deploy-lib.sh"
cat >> "$SD/deploy-lib.sh" <<'STUB'
TEST_USER=stub
TEST_PASS=stub-pass
TEST_HOST="${STUB_TEST_HOST-stub}"
if [ "${STUB_OMIT:-}" = "TEST_HOST" ]; then unset TEST_HOST; fi
scp_test() {
  echo "scp:$1" >> "$TRACE"
  case "$1" in
    *.tgz) cp "$1" "$PACK_COPY"; cp "$1" /tmp/mig-replay.tgz ;;
    *)     cp "$1" /tmp/replay-codes.txt ;;
  esac
}
ssh_test_once() {
  echo "ssh:$1" >> "$TRACE"
  cat > "$REMOTE_SCRIPT"
  # $1 形如 "REPLAY_EXPECTED=6 REPLAY_COLLATION=utf8mb4_general_ci bash -s": 取出其中的变量赋值, 隔离执行
  local assigns=() word cmd=""
  for word in $1; do
    case "$word" in
      REPLAY_*=*) assigns+=("$word") ;;
      *) cmd="$cmd${cmd:+ }$word" ;;
    esac
  done
  if [ "$cmd" != "bash -s" ]; then echo "桩不认识的远端命令: [$cmd]" >&2; return 9; fi
  env -i PATH="$STUB_BIN:$BASE_BIN" ${assigns[@]+"${assigns[@]}"} "SCENARIO=${SCENARIO:-good}" "TRACE=$TRACE" \
    "CODES_FILE=$TEST_SELF_DIR_CODES" bash -s < "$REMOTE_SCRIPT"
}
STUB

# ---- 假 docker: 只认空库重放远端脚本会用到的那几种调用; 按场景(SCENARIO)应答 ----
cat > "$STUB_BIN/docker" <<'FAKE'
#!/bin/bash
# 假 docker(测试桩)。SCENARIO: good | count_mismatch | failed_migration | code_unbound | admin_missing | mysql_never_ready
echo "docker:$*" >> "$TRACE"
sub="${1:-}"
expected="${REPLAY_EXPECTED:-0}"
case "$sub" in
  network) exit 0 ;;
  rm) exit 0 ;;
  logs) echo "fake mysql logs" ;;
  run)
    for a in "$@"; do
      case "$a" in
        --collation-server=*) printf '%s\n' "${a#--collation-server=}" > "$TRACE.collation" ;;
      esac
    done
    case "$*" in
      *flyway/flyway:*) echo "Successfully applied $expected migrations (fake)" ;;
      *) echo "fake-container-id" ;;
    esac
    ;;
  exec)
    # SQL 是“-e 后面那一个不是 MYSQL_PWD=… 的参数”
    sql=""; prev=""
    for a in "$@"; do
      if [ "$prev" = "-e" ]; then
        case "$a" in MYSQL_PWD=*) ;; *) sql="$a" ;; esac
      fi
      prev="$a"
    done
    case "$sql" in
      "SELECT 1")
        if [ "${SCENARIO:-good}" = "mysql_never_ready" ]; then exit 1; fi
        echo 1 ;;
      *"FROM flyway_schema_history WHERE success<>1"*)
        if [ "${SCENARIO:-good}" = "failed_migration" ]; then echo 1; else echo 0; fi ;;
      "SELECT COUNT(*) FROM flyway_schema_history;")
        if [ "${SCENARIO:-good}" = "count_mismatch" ]; then echo $((expected - 1)); else echo "$expected"; fi ;;
      *"rm.id IS NULL"*)
        if [ "${SCENARIO:-good}" = "code_unbound" ]; then echo 1; else echo 0; fi ;;
      *"JOIN sys_role r ON r.id=rm.role_id"*)
        n="$(/usr/bin/grep -cE '^[a-z]' "$CODES_FILE")"
        if [ "${SCENARIO:-good}" = "admin_missing" ]; then echo $((n - 1)); else echo "$n"; fi ;;
      *) printf 'permission\tstatus\tbound_roles\n' ;;
    esac
    ;;
  *) echo "假 docker 不认识的调用: docker $*" >&2; exit 9 ;;
esac
FAKE
printf '#!/bin/bash\necho fakepassword0123456789abcdef0123456789\n' > "$STUB_BIN/openssl"
printf '#!/bin/bash\nexit 0\n' > "$STUB_BIN/sleep"
chmod +x "$STUB_BIN/docker" "$STUB_BIN/openssl" "$STUB_BIN/sleep"

# ---- 断言用的词匹配: 只用 bash 的参数展开切词, 每行带换行再交给 read ----
split_words() {
  local s="$1" w
  while [ -n "$s" ]; do
    w="${s%%¦*}"
    if [ "$w" = "$s" ]; then s=""; else s="${s#*¦}"; fi
    [ -n "$w" ] && printf '%s\n' "$w"
  done
}
has_all() {
  split_words "$2" | while IFS= read -r w; do
    if ! printf '%s' "$1" | /usr/bin/grep -qF -- "$w"; then exit 1; fi
    :
  done
}
lacks_all() {
  split_words "$2" | while IFS= read -r w; do
    if printf '%s' "$1" | /usr/bin/grep -qF -- "$w"; then exit 1; fi
    :
  done
}
matcher_ok=1
has_all '甲 乙' '甲¦乙' || matcher_ok=0
has_all '甲' '甲¦丙' && matcher_ok=0
lacks_all '甲' '丙¦丁' || matcher_ok=0
lacks_all '甲' '丙¦甲' && matcher_ok=0
[ "$matcher_ok" -eq 1 ] || { echo "FAIL 匹配器自检 —— 断言用的词匹配本身不灵，本测试的结论不可信"; exit 1; }

passed=0
failed=0
unset SCENARIO REPLAY_COLLATION STUB_OMIT
run_case() { # <标签> <期望退出码> <输出必须含(¦分隔)> <输出不得含(¦分隔)>
  local label="$1" want="$2" must="$3" mustnot="${4:-}" out rc=0 ok=1
  : > "$TRACE"; rm -f "$TRACE.collation" "$PACK_COPY" "$REMOTE_SCRIPT"
  out="$(env -i PATH="$BASE_BIN" HOME="$TMP" TRACE="$TRACE" STUB_BIN="$STUB_BIN" BASE_BIN="$BASE_BIN" \
        PACK_COPY="$PACK_COPY" REMOTE_SCRIPT="$REMOTE_SCRIPT" \
        ${SCENARIO:+SCENARIO="$SCENARIO"} ${REPLAY_COLLATION:+REPLAY_COLLATION="$REPLAY_COLLATION"} ${STUB_OMIT:+STUB_OMIT="$STUB_OMIT"} \
        bash "$SD/replay-empty-db.sh" 2>&1)" || rc=$?
  has_all "$out" "$must" || ok=0
  lacks_all "$out" "$mustnot" || ok=0
  if [ "$rc" -eq "$want" ] && [ "$ok" -eq 1 ]; then
    passed=$((passed + 1)); echo "PASS $label"
  else
    failed=$((failed + 1)); echo "FAIL $label — 期望退出码 $want, 输出含 [$must] 且不含 [$mustnot]; 实际退出码 $rc, 输出: $(printf '%s' "$out" | tr '\n' ' ' | cut -c1-420)"
  fi
}
trace_has() { /usr/bin/grep -qF -- "$1" "$TRACE" 2>/dev/null; }
trace_count() { /usr/bin/grep -c -F -- "$1" "$TRACE" 2>/dev/null || true; }
collation_is() { [ -f "$TRACE.collation" ] && [ "$(/bin/cat "$TRACE.collation")" = "$1" ]; }
count_is() { [ "$(trace_count "$1")" = "$2" ]; }
trace_empty() { [ ! -s "$TRACE" ]; }
pack_sql_count_is() { [ "$(tar tzf "$PACK_COPY" 2>/dev/null | /usr/bin/grep -c '\.sql$')" = "$1" ]; }
pack_lacks() { ! tar tzf "$PACK_COPY" 2>/dev/null | /usr/bin/grep -qF -- "$1"; }
check() { # <标签> <判定函数> [参数…]: 成立记一条通过, 不成立记一条失败
  local label="$1"; shift
  if "$@"; then passed=$((passed + 1)); echo "PASS $label"
  else failed=$((failed + 1)); echo "FAIL $label"; fi
}

# ================= ① 本机这一半 =================
# (变量后面紧跟 ¦ 时要加花括号: 系统 bash 3.2 会把这个分隔符的头一个字节吃进变量名)
run_case '① 默认排序规则(生产现值): 全程走通, 末态断言全过' 0 \
  "包内 .sql: ${EXPECTED}¦空库重放全绿¦applied=${EXPECTED} expected=${EXPECTED} failed=0¦admin 持码数: ${TOTAL_CODES}/${TOTAL_CODES}" '❌'
check '①a MySQL 容器用的是 utf8mb4_general_ci' collation_is utf8mb4_general_ci
check '①b 上传两次(迁移包、断言码清单)' count_is 'scp:' 2
check '①c 远端只执行一次' count_is 'ssh:' 1
check '①d flyway migrate 恰好一次' count_is 'flyway/flyway:' 1
check '①e 结束后容器被清掉' trace_has 'docker:rm -f aigi-replay-mysql'
check '①f 结束后网络被清掉' trace_has 'docker:network rm aigi-replay-net'
check '①g 包里正好是要重放的 6 支' pack_sql_count_is "$EXPECTED"
check '①h 已并入基线的 V2 不在包里' pack_lacks 'V2__merged_into_baseline.sql'
check '①i 已并入基线的 V10 不在包里' pack_lacks 'V10__old.sql'
check '①j 本机工具状态目录不在包里' pack_lacks '.mimosa'

REPLAY_COLLATION=utf8mb4_0900_ai_ci
run_case '② 另一种排序规则(MySQL 8 默认): 同样走通' 0 '空库重放全绿' '❌'
check '②a MySQL 容器用的是 utf8mb4_0900_ai_ci' collation_is utf8mb4_0900_ai_ci

# 载荷里放一段算术: 被当成命令执行了才会出现结果 42; 只是被原样回显在拒绝提示里则不会
REPLAY_COLLATION='utf8mb4_general_ci; echo $((6*7))'
run_case '③ 想往远端命令行里塞东西: 上传之前就拒绝' 2 'REPLAY_COLLATION 只能是' '空库重放全绿¦42'
check '③a 被拒绝时什么都没上传、没执行' trace_empty
REPLAY_COLLATION=''
unset REPLAY_COLLATION

STUB_OMIT=TEST_HOST
run_case '④ 缺测试服配置: 不上传就退出' 1 '缺少 TEST_HOST' '空库重放全绿'
check '④a 没有上传' trace_empty
unset STUB_OMIT

/bin/mv "$SD/empty-replay-codes.txt" "$TMP/codes.bak"
run_case '⑤ 缺断言码清单: 不上传就退出' 2 '缺少断言码清单' '空库重放全绿'
check '⑤a 没有上传' trace_empty
/bin/mv "$TMP/codes.bak" "$SD/empty-replay-codes.txt"

# ================= ② 远端那一半 =================
SCENARIO=count_mismatch
run_case '⑥ 末态迁移数对不上(少了一支): 必须红' 1 "迁移数不对¦applied=$((EXPECTED - 1)) expected=${EXPECTED}" '空库重放全绿'
check '⑥a 失败之后容器也被清掉' trace_has 'docker:rm -f aigi-replay-mysql'
SCENARIO=failed_migration
run_case '⑦ 有失败的迁移: 必须红' 1 '存在 failed 迁移' '空库重放全绿'
SCENARIO=code_unbound
run_case '⑧ 断言码停用或零绑定: 必须红' 1 '断言码末态异常行: 1¦存在停用或零绑定码' '空库重放全绿'
SCENARIO=admin_missing
run_case '⑨ admin 没持全部断言码: 必须红' 1 "admin 持码数: $((TOTAL_CODES - 1))/${TOTAL_CODES}¦admin 未持有全部" '空库重放全绿'
SCENARIO=mysql_never_ready
run_case '⑩ MySQL 迟迟起不来: 必须红' 1 '未就绪' '空库重放全绿'
check '⑩a 起不来的情况下容器照样清掉' trace_has 'docker:rm -f aigi-replay-mysql'
check '⑩b 网络也清掉' trace_has 'docker:network rm aigi-replay-net'

echo "通过 $passed 项, 未通过 $failed 项"
[ "$failed" -eq 0 ] || exit 1
