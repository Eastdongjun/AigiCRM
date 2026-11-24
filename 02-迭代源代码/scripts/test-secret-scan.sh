#!/usr/bin/env bash
# check-secret-scan.sh 的行为测试(审计 F1): 在临时 git 仓库里种植伪凭据夹具,
# 验证扫描器: ①能抓各形态(p1-p4 原有; p9-p13 为 GL-02 2026-11-24 新增); ②占位符/$引用/代码引用/变量引用
# 不误报; ③精确路径+模式ID 豁免生效; ④node_modules 不在扫描范围; ⑤非 git 目录 fail-closed(exit 2);
# ⑥违例输出里命中段已遮蔽(扫描器自己不能把凭据打到终端/证据里)。
# 夹具全部是构造的假值; 本文件各形态的自身命中已在 check-secret-scan.sh 的 ALLOWLIST 逐条登记。
set -euo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_SELF_DIR/.." && pwd)"
SCANNER="$ROOT/scripts/check-secret-scan.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$SCANNER" ] || fail "缺少被测脚本 $SCANNER"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-secret-scan-test.XXXXXX")"
# 【退出码守卫】见 exit-guard.sh: bash 3.2 在 set -e + EXIT 陷阱下会把未定义变量引起的中止报成退出码 0
cleanup_tmp() { rm -rf "$TMP"; }
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/exit-guard.sh"
exit_guard_install cleanup_tmp
REPO="$TMP/repo"
mkdir -p "$REPO"
git -C "$REPO" init -q

# ---- 夹具(假值; 拼装避免测试源码自身出现可命中形态) ----
cat > "$REPO/bad-assign.js" <<'EOF'
// 故意的 p1 夹具
const PASS = 'zz9-plural-z-alpha';
EOF
cat > "$REPO/bad-fill.mjs" <<'EOF'
// 故意的 p2 夹具(登录表单位置填值)
await inputs.nth(1).fill('zz9-positional-cred');
EOF
# 故意的 p4 夹具(URL 内嵌凭据)——运行时拼接，避免扫描器把夹具源码本身当作活凭据
printf 'DB=mysql://root:%s@db.local/x\n' 'zz9-url-cred' > "$REPO/bad-url.env"
PK_TAIL="PRIVATE KEY"
printf -- '-----BEGIN RSA %s-----\n' "$PK_TAIL" > "$REPO/bad-key.pem"
cat > "$REPO/clean-placeholder.js" <<'EOF'
const PASS = '<redacted-by-owner>';
EOF
cat > "$REPO/clean-envref.sh" <<'EOF'
PASS="$DB_PASSWORD"
EOF
# 豁免路径夹具: 与 ALLOWLIST 精确条目同路径的伪 bcrypt(前缀拆开拼装, 测试源码不出现完整哈希形态)
# 【GL-02】ALLOWLIST 按仓库顶层相对路径匹配, 夹具放在与真实仓库相同的 02-迭代源代码/ 下
HASH_PREFIX='$''2a$''10$'
HASH_TAIL='01234567890123456789012345678901234567890123456789abc'
mkdir -p "$REPO/02-迭代源代码/sql/migration/20260814"
printf "AND password = '%s%s';\n" "$HASH_PREFIX" "$HASH_TAIL" > "$REPO/02-迭代源代码/sql/migration/20260814/rbac-convergence-m1.sql"
# node_modules 内的同型违例不应被扫描
mkdir -p "$REPO/node_modules/pkg"
cat > "$REPO/node_modules/pkg/leak.js" <<'EOF'
const PASS = 'zz9-inside-node-modules';
EOF

# ---- GL-02 2026-11-24 新增形态夹具(假值; 与 p3/p4 夹具同法运行时拼装, 测试源码自身不出现可命中形态) ----
FX_A='zz9-env'"-pass-41"
FX_B='zz9-prose'"-52"
FX_C='zz9-sql'"-pass-63"
FX_D='zz9-cli'"-pass-74"
FX_E='zz9-fly'"-pass-85"
FX_F='zz9-basic'"-96"
printf '# 故意的 p9 夹具: 未加引号的口令赋值\nSMOKE_TEST_%s=%s\n' 'PASS' "$FX_A" > "$REPO/bad-env-assign.env"
printf -- "- 故意的 p10 夹具(散文): 默认口令（%s）；旧值 \`%s\` 已失效；账号 NM005 / 密码 %s；测试库口令：%s。\n" "$((246000 + 813))" "$((735000 + 735))" "$((735000 + 735))" "$FX_B" > "$REPO/bad-prose.md"
printf -- "-- 故意的 p11 夹具: SQL 口令字面量\nALTER USER 'root'@'localhost' IDENTIFIED %s '%s';\n" 'BY' "$FX_C" > "$REPO/bad-sql.sql"
{
  printf '# 故意的 p12 夹具: 命令行口令参数\n'
  printf "mysql -uroot -p%s -e 'select 1'\n" "$FX_D"
  printf "flyway -user=root -%s='%s' migrate\n" 'password' "$FX_E"
} > "$REPO/bad-cli.sh"
printf 'curl -u zz9user:%s http://example.invalid/api\n' "$FX_F" > "$REPO/bad-basic-auth.sh"
# ---- 不该误报的干净样例 ----
cat > "$REPO/clean-code.js" <<'EOF'
const password = encodeURIComponent(raw)
form.password = undefined
return { username: p.u, password: xorMix64(p.d) }
EOF
cat > "$REPO/clean-desc.md" <<'EOF'
密码至少 8 位，需同时包含字母和数字；密码为空时拒绝登录；记住密码用设备密钥加密。
记住密码 base64 明文；记住密码(AES-256)落盘；重置口令：非 admin 禁止重置 admin 账号（SysUserService.resetPassword:258-262）
EOF
cat > "$REPO/clean-cli.sh" <<'EOF'
mysql -uroot -p"$PW" -P3306 -e 'select 1'
ffmpeg -preset veryfast -i in.mp4 out.mp4
curl -u "$USER:$PASS" http://example.invalid/api
docker run -u 10001:10001 nginx
echo "PASS: customer-protect-365"
EOF
printf "ALTER USER 'app'@'%%' IDENTIFIED %s '<强密码-填写>';\n" 'BY' > "$REPO/clean-sql-template.sh"

# ---- ⓪ 扫描器自身的检测正则必须都能编译 ----
# 2026-11-24 实证: 一条括号不配对的正则让预筛 grep 报错、被 "|| true" 吞成"无可疑行", 全仓扫描给出假 PASS。
eval "$(grep -E '^(P|PK)_[A-Z0-9_]+=' "$SCANNER")"
for rv in $(grep -oE '^P_[A-Z0-9_]+=' "$SCANNER" | tr -d '='); do
  regex_rc=0; printf '' | grep -qE -e "${!rv}" || regex_rc=$?
  [ "$regex_rc" -ne 2 ] || fail "扫描器的检测正则 ${rv} 无法编译(括号不配对或语法错误) —— 会让预筛静默返回空、造成假 PASS"
done

# ---- ⓪b 正则写坏时扫描器必须 fail-closed(rc=2), 不能静默报 PASS ----
# 给扫描器副本的一条正则注入一个不配对的左括号, 再拿它扫一个干净仓库。
BROKEN_SCANNER="$TMP/check-secret-scan-broken.sh"
sed 's/^P_URL_CRED="/P_URL_CRED="(/' "$SCANNER" > "$BROKEN_SCANNER"
CLEAN_REPO="$TMP/clean-repo"
mkdir -p "$CLEAN_REPO"
git -C "$CLEAN_REPO" init -q
printf 'hello\n' > "$CLEAN_REPO/readme.txt"
set +e
out="$(bash "$BROKEN_SCANNER" "$CLEAN_REPO" 2>&1)"
rc=$?
set -e
[ "$rc" -eq 2 ] || fail "正则写坏的扫描器应 BLOCKED exit 2, 实际 rc=$rc; 输出: $out"
printf '%s\n' "$out" | grep -q '无法编译' || fail "BLOCKED 输出应指出正则无法编译: $out"

# ---- ① 有违例 → rc=1 且点名 p1/p2/p3/p4 ----
set +e
out="$(bash "$SCANNER" "$REPO" 2>&1)"
rc=$?
set -e
[ "$rc" -eq 1 ] || fail "应检出违例并 exit 1, 实际 rc=$rc; 输出: $out"
printf '%s\n' "$out" | grep -q 'bad-assign.js.*\[p1\]' || fail "未点名 bad-assign.js [p1]: $out"
printf '%s\n' "$out" | grep -q 'bad-fill.mjs.*\[p2\]' || fail "未点名 bad-fill.mjs [p2]: $out"
printf '%s\n' "$out" | grep -q 'bad-url.env.*\[p4\]' || fail "未点名 bad-url.env [p4]: $out"
printf '%s\n' "$out" | grep -q 'bad-key.pem.*\[p3\]' || fail "未点名 bad-key.pem [p3]: $out"
printf '%s\n' "$out" | grep -q 'clean-placeholder.js' && fail "占位符行被误报: $out"
printf '%s\n' "$out" | grep -q 'clean-envref.sh' && fail "\$引用行被误报: $out"
printf '%s\n' "$out" | grep -q 'node_modules' && fail "node_modules 被误扫: $out"
printf '%s\n' "$out" | grep -q 'rbac-convergence-m1.sql' && fail "豁免条目仍被报为违例: $out"
# GL-02 新增形态逐个点名
printf '%s\n' "$out" | grep -q 'bad-env-assign.env.*\[p9\]' || fail "未点名 bad-env-assign.env [p9]: $out"
printf '%s\n' "$out" | grep -q 'bad-prose.md.*\[p10\]' || fail "未点名 bad-prose.md [p10]: $out"
printf '%s\n' "$out" | grep -q 'bad-sql.sql.*\[p11\]' || fail "未点名 bad-sql.sql [p11]: $out"
printf '%s\n' "$out" | grep -q 'bad-cli.sh.*\[p12\]' || fail "未点名 bad-cli.sh [p12]: $out"
printf '%s\n' "$out" | grep -q 'bad-basic-auth.sh.*\[p13\]' || fail "未点名 bad-basic-auth.sh [p13]: $out"
for clean in clean-code.js clean-desc.md clean-cli.sh clean-sql-template.sh; do
  printf '%s\n' "$out" | grep -q "$clean" && fail "干净样例 $clean 被误报: $out"
done
# 违例输出不得回显凭据字面量(命中段必须遮蔽)
printf '%s\n' "$out" | grep -q 'zz9-env-pass-41' && fail "扫描输出回显了口令字面量(未遮蔽): $out"

# ---- ② 清除违例后 → rc=0, 豁免命中计数 ≥1 ----
rm -f "$REPO"/bad-*.js "$REPO"/bad-*.mjs "$REPO"/bad-*.env "$REPO"/bad-*.pem \
      "$REPO"/bad-*.md "$REPO"/bad-*.sql "$REPO"/bad-*.sh
set +e
out="$(bash "$SCANNER" "$REPO" 2>&1)"
rc=$?
set -e
[ "$rc" -eq 0 ] || fail "清除违例后应 PASS, 实际 rc=$rc; 输出: $out"
printf '%s\n' "$out" | grep -q '未豁免命中 0' || fail "PASS 输出缺 '未豁免命中 0': $out"
printf '%s\n' "$out" | grep -Eq '豁免命中 [1-9]' || fail "豁免命中计数应为 ≥1(bcrypt 夹具): $out"

# ---- ③ 新增未豁免违例 → 再次 rc=1(豁免不可吞新文件) ----
cat > "$REPO/new-leak.js" <<'EOF'
const PASS = 'zz9-new-unlisted';
EOF
set +e
out="$(bash "$SCANNER" "$REPO" 2>&1)"
rc=$?
set -e
[ "$rc" -eq 1 ] || fail "新违例文件应 exit 1(豁免必须精确到路径), 实际 rc=$rc; 输出: $out"
printf '%s\n' "$out" | grep -q 'new-leak.js' || fail "未点名 new-leak.js: $out"
rm -f "$REPO/new-leak.js"

# ---- ④ 非 git 目录 → fail-closed rc=2 ----
NOGIT="$TMP/nogit"
mkdir -p "$NOGIT"
set +e
out="$(bash "$SCANNER" "$NOGIT" 2>&1)"
rc=$?
set -e
[ "$rc" -eq 2 ] || fail "非 git 目录应 BLOCKED exit 2, 实际 rc=$rc; 输出: $out"

EXIT_GUARD_DONE=1
echo "PASS: check-secret-scan.sh 行为测试(p1-p4/p9-p13 检出/占位符与\$引用与代码引用不误报/node_modules 排除/精确豁免/输出遮蔽/fail-closed)。"
