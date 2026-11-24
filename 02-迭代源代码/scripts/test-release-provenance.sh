#!/usr/bin/env bash
# 生产部署制品溯源的行为测试(GL-09, 2026-11-24)
#
# 背景: deploy-backend.sh 部署的是"测试服容器里正在跑的那个 jar", 只校验它像个 jar、上传前后一致,
#   从不核对它是不是冻结的版本, 部署日志里的 commit 记的还是本机当前 HEAD;
#   deploy-frontend.sh 用本机工作区直接构建, 有未提交改动也照发。
#
# 验证四道门, 以及两份生产部署脚本确实在远端动作之前过了这些门:
#   ① 没有冻结清单 / 清单文件不存在 / 清单不可解析 / 清单登记的提交不是当前 HEAD → 拒绝, 一步远端动作都不发生
#   ② 已跟踪文件有未提交改动 → 拒绝。未跟踪文件不拦(不影响提交内容; 会影响构建产物的由第 ④ 道门兜住)
#   ③ 测试服取回的 jar 的 md5、sha256 与清单不符(或清单没登记) → 拒绝, 不进入备份与上传
#   ④ 构建出的前端入口 JS 的文件名或 sha256 与清单不符(或清单没登记) → 拒绝, 不上传
#   ⑤ 全部相符 → 流程走到第一个远端动作
#
# 做法: 在临时 git 仓库里放两份部署脚本和库的副本, 在库副本末尾追加"走到网络步骤就停"的哨兵, 然后真的运行这两个脚本。
#   前端"构建"换成一个只拷贝夹具的假 build; 测试服"取 jar"那一步由桩直接拷一个夹具文件。
# 全程不联网、不接触任何服务器; 机密类取值运行时拼装。
set -uo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for f in deploy-lib.sh deploy-backend.sh deploy-frontend.sh; do
  [ -f "$TEST_SELF_DIR/$f" ] || { echo "FAIL: 缺少 scripts/$f" >&2; exit 1; }
done
fail() { echo "FAIL: $*" >&2; exit 1; }

T="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-provenance-test.XXXXXX")"
trap 'rm -rf "$T"' EXIT
R="$T/repo"                       # 临时 git 仓库: 两份部署脚本和库的副本放在这里, HEAD 就是"当前提交"
PHASE_LOG="$T/phase.log"
mkdir -p "$R/scripts"
cp "$TEST_SELF_DIR/deploy-backend.sh" "$TEST_SELF_DIR/deploy-frontend.sh" "$R/scripts/"
cp "$TEST_SELF_DIR/deploy-lib.sh" "$R/scripts/deploy-lib.sh"
LIB_BYTES="$(wc -c < "$TEST_SELF_DIR/deploy-lib.sh" | tr -d ' ')"

# ---- 库副本末尾追加测试桩: 覆盖网络函数与生产前置检查, 走到网络步骤就停 ----
cat >> "$R/scripts/deploy-lib.sh" <<'STUB'

# ===== 测试桩(只存在于副本末尾; GL-09 的四道门在上方原样保留) =====
check_prod_tls() { :; }
check_deploy_arch() { :; }
assert_prod_env_pinned() { :; }
probe() { if [ "${STUB_PROBE:-0}" = 1 ]; then echo "$STUB_PROBE_UNBOUND_VAR" >&2; fi; }   # 异常中止探针: 引用一个未定义的变量
ssh_test()      { probe; echo "fetch_jar" >> "$PHASE_LOG"; }
scp_test()      { cp "$STUB_JAR" "$2"; echo "fetch_jar" >> "$PHASE_LOG"; }   # $2 是本地路径: 把夹具 jar 放到位
ssh_prod_once() { echo "ssh_prod_once" >> "$PHASE_LOG"; exit 99; }
ssh_prod()      { echo "ssh_prod" >> "$PHASE_LOG"; exit 99; }
scp_prod()      { probe; echo "scp_prod" >> "$PHASE_LOG"; exit 99; }
STUB
# 副本 = 真实的库 + 追加的桩(前若干字节逐字节相同), 两个入口脚本是原样副本
head -c "$LIB_BYTES" "$R/scripts/deploy-lib.sh" | cmp -s - "$TEST_SELF_DIR/deploy-lib.sh" || fail "库副本除末尾追加的桩以外, 应与真实的库逐字节一致"
cmp -s "$R/scripts/deploy-backend.sh" "$TEST_SELF_DIR/deploy-backend.sh" || fail "deploy-backend.sh 副本不一致"
cmp -s "$R/scripts/deploy-frontend.sh" "$TEST_SELF_DIR/deploy-frontend.sh" || fail "deploy-frontend.sh 副本不一致"

# ---- 假的部署配置(运行时拼装, 不出现可命中的凭据形态) ----
FX_JWT='zz9-prov'"-jwt-71"
FX_DB='zz9-prov'"-db-72"
FX_OSS_ID='zz9-prov'"-oss-id-73"
FX_OSS_KEY='zz9-prov'"-oss-key-74"
FX_TEST='zz9-prov'"-test-75"
{
  echo 'APP_NAME=AIGICRM'
  echo 'APP_SLUG=ycmn'
  echo 'DB_NAME=ycmn'
  echo 'DEPLOY_DIR=/tmp/aigicrm-stub-deploy'
  echo 'NAS_BACKUP_DIR=/tmp/aigicrm-stub-backup'
  echo 'NAS_DIST_DIR=/tmp/aigicrm-stub-dist'
  echo 'FILE_GRANT_MODE=new'
  echo 'PROD_HOST=stub-prod-host'
  echo 'PROD_USER=stub-prod-user'
  echo 'TEST_HOST=stub-host'
  echo 'TEST_USER=stub-user'
  printf 'TEST_PASS=%s\nJWT_SECRET=%s\nAPP_DB_USER=zz9-stub\nAPP_DB_PASSWORD=%s\n' "$FX_TEST" "$FX_JWT" "$FX_DB"
  printf 'OSS_ACCESS_KEY_ID=%s\nOSS_ACCESS_KEY_SECRET=%s\n' "$FX_OSS_ID" "$FX_OSS_KEY"
} > "$T/deploy.env"
: > "$T/known_hosts"

# ---- 临时仓库做出一个提交 ----
echo 'tracked' > "$R/tracked.txt"
git -C "$R" init -q
git -C "$R" add -A
git -C "$R" -c user.name=fixture -c user.email=fixture@example.invalid commit -q --no-verify -m fixture
HEAD="$(git -C "$R" rev-parse HEAD)"
OTHER_COMMIT="0000000000000000000000000000000000000000"
[ "${#HEAD}" -eq 40 ] || fail "临时仓库没有产生 40 位提交号"

# ---- 夹具: 冻结的 jar(51MB, 以 PK 开头, 能过"像个 jar"的检查) 与另一个不同的 jar ----
make_jar() { # $1=路径 $2=MB
  dd if=/dev/zero of="$1" bs=1048576 count="$2" 2>/dev/null
  printf 'PK' | dd of="$1" bs=1 seek=0 conv=notrunc 2>/dev/null
}
make_jar "$T/frozen.jar" 51
make_jar "$T/other.jar" 52

# ---- 夹具: 前端"源码"(一个假项目, build 只是把给定的 dist 夹具拷到 dist/) ----
make_ui_src() { # $1=目录 $2=入口文件名 $3=入口内容
  mkdir -p "$1/assets"
  printf '<!doctype html>\n<html><head><meta charset="utf-8"><title>stub</title></head><body>\n<script type="module" crossorigin src="/assets/%s"></script>\n<div id="app"></div></body></html>\n' "$2" > "$1/index.html"
  printf '%s\n' "$3" > "$1/assets/$2"
}
make_ui_src "$T/dist-frozen" 'index-Fr0zen_a.js' 'console.log("frozen build")'
make_ui_src "$T/dist-other"  'index-0ther_b.js'  'console.log("some other build")'
mkdir -p "$T/ui"
printf '{"name":"stub-ui","version":"1.0.0","private":true,"scripts":{"build":"sh ./build.sh"}}\n' > "$T/ui/package.json"
printf '#!/bin/sh\nrm -rf dist && cp -R "$STUB_DIST_SRC" dist && echo "stub built in 0.01s"\n' > "$T/ui/build.sh"

# ---- 冻结清单(与 GL-18 将要生成的格式一致): schema 1 ----
make_manifest() { # $1=输出路径 $2=提交号 $3=jar 路径 $4=入口文件名 $5=入口文件路径
  python3 - "$@" <<'PY'
import hashlib, json, sys
out, commit, jar, entry_file, entry_path = sys.argv[1:6]
data = open(jar, 'rb').read()
doc = {
    'schema': 1,
    'tag': 'rc/fixture',
    'commit': commit,
    'backend': {'jar': {'md5': hashlib.md5(data).hexdigest(), 'sha256': hashlib.sha256(data).hexdigest()}},
    'frontend': {'entry': {'file': entry_file, 'sha256': hashlib.sha256(open(entry_path, 'rb').read()).hexdigest()}},
    'miniprogram': {'version': '0.0.0-fixture'},
    'migrations': 0,
    'api_endpoints': 0,
}
json.dump(doc, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY
}
drop_field() { # $1=清单路径 其余=点分字段(删掉它)
  python3 - "$@" <<'PY'
import json, sys
path, fields = sys.argv[1], sys.argv[2:]
doc = json.load(open(path, encoding='utf-8'))
for dotted in fields:
    keys = dotted.split('.')
    cur = doc
    for key in keys[:-1]:
        cur = cur[key]
    del cur[keys[-1]]
json.dump(doc, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY
}
GOOD="$T/manifest-good.json"
make_manifest "$GOOD" "$HEAD" "$T/frozen.jar" 'assets/index-Fr0zen_a.js' "$T/dist-frozen/assets/index-Fr0zen_a.js"

# ---- 运行副本 ----
CUR_MANIFEST=""; CUR_JAR=""; CUR_DIST_SRC=""; CUR_PROBE=0
RC=0
run_backend() { # 返回码进 RC, 输出进 $T/out, 哨兵到达情况进 $PHASE_LOG
  : > "$PHASE_LOG"; RC=0
  ( cd "$R" && AIGICRM_DEPLOY_ENV="$T/deploy.env" AIGICRM_KNOWN_HOSTS="$T/known_hosts" AIGICRM_TEST_ONLY=1 \
      PHASE_LOG="$PHASE_LOG" STUB_JAR="$CUR_JAR" STUB_PROBE="$CUR_PROBE" RELEASE_MANIFEST="$CUR_MANIFEST" \
      bash "$R/scripts/deploy-backend.sh" > "$T/out" 2>&1 < /dev/null ) || RC=$?
}
run_frontend() {
  : > "$PHASE_LOG"; RC=0
  ( cd "$R" && AIGICRM_DEPLOY_ENV="$T/deploy.env" AIGICRM_KNOWN_HOSTS="$T/known_hosts" AIGICRM_TEST_ONLY=1 \
      PHASE_LOG="$PHASE_LOG" STUB_DIST_SRC="$CUR_DIST_SRC" STUB_PROBE="$CUR_PROBE" UI_DIR="$T/ui" RELEASE_MANIFEST="$CUR_MANIFEST" \
      bash "$R/scripts/deploy-frontend.sh" > "$T/out" 2>&1 < /dev/null ) || RC=$?
}
out() { cat "$T/out"; }
reached_remote() { [ -s "$PHASE_LOG" ]; }          # 是否发生过任何远端动作(含从测试服取 jar)
expect_blocked_before_any_remote() { # $1=说明 $2=输出里应出现的片段
  [ "$RC" -ne 0 ] && [ "$RC" -ne 99 ] || fail "$1: 应被拒绝, 实际 rc=${RC}; 输出: $(out)"
  ! reached_remote || fail "$1: 被拒绝前不应发生任何远端动作, 实际: $(tr '\n' ' ' < "$PHASE_LOG"); 输出: $(out)"
  grep -q -- "$2" "$T/out" || fail "$1: 输出应包含 [$2]: $(out)"
}
expect_reached_first_remote() { # $1=说明 $2=应到达的哨兵
  [ "$RC" -eq 99 ] || fail "$1: 应走到第一个远端动作, 实际 rc=${RC}; 输出: $(out)"
  grep -qx "$2" "$PHASE_LOG" || fail "$1: 应到达 [$2], 实际: $(tr '\n' ' ' < "$PHASE_LOG"); 输出: $(out)"
}
good_all() { CUR_MANIFEST="$GOOD"; CUR_JAR="$T/frozen.jar"; CUR_DIST_SRC="$T/dist-frozen"; }

# ================= ① 冻结清单 =================
CUR_MANIFEST=""; CUR_JAR="$T/frozen.jar"; CUR_DIST_SRC="$T/dist-frozen"
run_backend;  expect_blocked_before_any_remote "后端: 没有冻结清单" "RELEASE_MANIFEST"
run_frontend; expect_blocked_before_any_remote "前端: 没有冻结清单" "RELEASE_MANIFEST"
CUR_MANIFEST="$T/no-such-manifest.json"
run_backend;  expect_blocked_before_any_remote "后端: 清单文件不存在" "冻结清单"
run_frontend; expect_blocked_before_any_remote "前端: 清单文件不存在" "冻结清单"
printf '{"schema": 1, "commit": ' > "$T/manifest-broken.json"
CUR_MANIFEST="$T/manifest-broken.json"
run_backend;  expect_blocked_before_any_remote "后端: 清单不可解析" "冻结清单"
run_frontend; expect_blocked_before_any_remote "前端: 清单不可解析" "冻结清单"
# 清单格式版本不是 1(读不了的那种): 同样拒绝, 不能猜
SCHEMA2="$T/manifest-schema2.json"; cp "$GOOD" "$SCHEMA2"
python3 - "$SCHEMA2" <<'PY'
import json, sys
path = sys.argv[1]
doc = json.load(open(path, encoding='utf-8')); doc['schema'] = 2
json.dump(doc, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY
CUR_MANIFEST="$SCHEMA2"
run_backend;  expect_blocked_before_any_remote "后端: 清单的 schema 不是 1" "冻结清单"
run_frontend; expect_blocked_before_any_remote "前端: 清单的 schema 不是 1" "冻结清单"
BAD_COMMIT="$T/manifest-other-commit.json"; cp "$GOOD" "$BAD_COMMIT"
drop_field "$BAD_COMMIT" commit
python3 - "$BAD_COMMIT" "$OTHER_COMMIT" <<'PY'
import json, sys
path, commit = sys.argv[1], sys.argv[2]
doc = json.load(open(path, encoding='utf-8')); doc['commit'] = commit
json.dump(doc, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY
CUR_MANIFEST="$BAD_COMMIT"
run_backend;  expect_blocked_before_any_remote "后端: 清单登记的不是当前提交" "$OTHER_COMMIT"
run_frontend; expect_blocked_before_any_remote "前端: 清单登记的不是当前提交" "$OTHER_COMMIT"
grep -q "$HEAD" "$T/out" || fail "拒绝时应同时给出当前 HEAD 与清单登记的提交: $(out)"

# ================= ② 工作区 =================
good_all
echo 'modified' > "$R/tracked.txt"
run_backend;  expect_blocked_before_any_remote "后端: 已跟踪文件有未提交改动" "未提交的改动"
run_frontend; expect_blocked_before_any_remote "前端: 已跟踪文件有未提交改动" "未提交的改动"
git -C "$R" checkout -q -- tracked.txt
echo 'untracked' > "$R/untracked.txt"            # 未跟踪文件本身不拦(见文件头 ② 的说明)
run_backend;  expect_reached_first_remote "后端: 未跟踪文件不拦, 制品相符则继续" ssh_prod_once
run_frontend; expect_reached_first_remote "前端: 未跟踪文件不拦, 制品相符则继续" scp_prod
rm -f "$R/untracked.txt"

# ================= ③ 后端 jar =================
CUR_MANIFEST="$GOOD"; CUR_JAR="$T/other.jar"; CUR_DIST_SRC="$T/dist-frozen"
run_backend
[ "$RC" -eq 1 ] || fail "后端: jar 与清单不符时应退出 1, 实际 rc=${RC}; 输出: $(out)"
grep -qx fetch_jar "$PHASE_LOG" || fail "后端: 应先从测试服取回 jar 再核对(日志: $(tr '\n' ' ' < "$PHASE_LOG"))"
[ "$(grep -cx fetch_jar "$PHASE_LOG")" -eq 2 ] || fail "后端: 取 jar 之前的远端动作不应发生(日志: $(tr '\n' ' ' < "$PHASE_LOG"))"
grep -q 'ssh_prod' "$PHASE_LOG" && fail "后端: jar 不符时不应进入备份/上传(日志: $(tr '\n' ' ' < "$PHASE_LOG"))"
grep -q '不是冻结清单登记的那个版本' "$T/out" || fail "后端: 应说明 jar 不是冻结的版本: $(out)"
NO_SHA="$T/manifest-no-jar-sha.json"; cp "$GOOD" "$NO_SHA"; drop_field "$NO_SHA" backend.jar.sha256
CUR_MANIFEST="$NO_SHA"; CUR_JAR="$T/frozen.jar"
run_backend; [ "$RC" -eq 1 ] && grep -q '冻结清单' "$T/out" || fail "后端: 清单没登记 jar 的 sha256 时应拒绝: $(out)"
# md5 对得上、sha256 对不上(清单里的 sha256 被改过): 仍然拒绝 —— 两个指纹都要比, 不能只比 md5
BAD_SHA="$T/manifest-bad-jar-sha.json"; cp "$GOOD" "$BAD_SHA"
python3 - "$BAD_SHA" <<'PY'
import json, sys
path = sys.argv[1]
doc = json.load(open(path, encoding='utf-8'))
sha = doc['backend']['jar']['sha256']
doc['backend']['jar']['sha256'] = ('0' if sha[0] != '0' else '1') + sha[1:]
json.dump(doc, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
PY
CUR_MANIFEST="$BAD_SHA"
run_backend
[ "$RC" -eq 1 ] && grep -q '不是冻结清单登记的那个版本' "$T/out" || fail "后端: 清单里的 sha256 与 jar 不符时应拒绝: $(out)"
grep -qx fetch_jar "$PHASE_LOG" && ! grep -q 'ssh_prod' "$PHASE_LOG" || fail "后端: sha256 不符时不应进入备份/上传(日志: $(tr '\n' ' ' < "$PHASE_LOG"))"
good_all
run_backend;  expect_reached_first_remote "后端: jar 与清单相符则继续" ssh_prod_once

# ================= ④ 前端入口 =================
CUR_MANIFEST="$GOOD"; CUR_DIST_SRC="$T/dist-other"
run_frontend
[ "$RC" -eq 1 ] || fail "前端: 构建产物与清单不符时应退出 1, 实际 rc=${RC}; 输出: $(out)"
! reached_remote || fail "前端: 构建产物不符时不应上传(日志: $(tr '\n' ' ' < "$PHASE_LOG"))"
grep -q '不是冻结清单登记的那个版本' "$T/out" || fail "前端: 应说明构建产物不是冻结的版本: $(out)"
# 内容不同、文件名相同(模拟文件名没变但内容变了)
make_ui_src "$T/dist-same-name" 'index-Fr0zen_a.js' 'console.log("frozen build, but tampered")'
CUR_DIST_SRC="$T/dist-same-name"
run_frontend; [ "$RC" -eq 1 ] && ! reached_remote || fail "前端: 入口文件名相同但内容不同应拒绝; rc=${RC}; 输出: $(out)"
NO_ENTRY="$T/manifest-no-entry.json"; cp "$GOOD" "$NO_ENTRY"; drop_field "$NO_ENTRY" frontend.entry.file
CUR_MANIFEST="$NO_ENTRY"; CUR_DIST_SRC="$T/dist-frozen"
run_frontend; [ "$RC" -eq 1 ] && grep -q '冻结清单' "$T/out" || fail "前端: 清单没登记入口文件时应拒绝: $(out)"
good_all
run_frontend; expect_reached_first_remote "前端: 构建产物与清单相符则继续" scp_prod

# ================= ⑥ 异常中止不得报成成功 =================
# macOS 自带的 bash 3.2: set -e 与 EXIT 陷阱同时存在时, 未定义变量引起的中止会以退出码 0 结束。
# 两份部署脚本都必须把它纠正为非零(见脚本里的"退出码守卫")。这里在已安装陷阱之后故意引用一个未定义的变量。
good_all; CUR_PROBE=1
run_backend
[ "$RC" -ne 0 ] || fail "后端: 异常中止时退出码不得为 0(bash 3.2 会把它吞成 0); 输出: $(out)"
grep -q 'unbound variable' "$T/out" || fail "后端: 探针没有触发未定义变量(场景无效); 输出: $(out)"
run_frontend
[ "$RC" -ne 0 ] || fail "前端: 异常中止时退出码不得为 0(bash 3.2 会把它吞成 0); 输出: $(out)"
grep -q 'unbound variable' "$T/out" || fail "前端: 探针没有触发未定义变量(场景无效); 输出: $(out)"
CUR_PROBE=0

# ================= 自检 =================
[ -z "$(ls /tmp/ycmn-backend-new-*.jar 2>/dev/null)" ] || fail "deploy-backend.sh 退出后应清理本地临时 jar: $(ls /tmp/ycmn-backend-new-*.jar)"

echo "PASS: 生产部署制品溯源(没有清单/清单不对/提交不符/工作区不干净 → 一步远端动作都不发生; jar 或前端入口与冻结清单不符 → 拒绝; 相符 → 走到第一个远端动作; 异常中止不报成成功)。"
