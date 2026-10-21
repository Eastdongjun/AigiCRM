#!/usr/bin/env bash
# check-secret-scan.sh 的行为测试(审计 F1): 在临时 git 仓库里种植伪凭据夹具,
# 验证扫描器: ①能抓 p1/p2/p3/p4 各形态; ②占位符/$引用不误报; ③精确路径+模式ID 豁免生效;
# ④node_modules 不在扫描范围; ⑤非 git 目录 fail-closed(exit 2)。
# 夹具全部是构造的假值; 值的拼装方式保证本测试源码自身不生成未豁免命中
# (自身 p1/p2/p4 命中已在 check-secret-scan.sh 的 ALLOWLIST 登记)。
set -euo pipefail

TEST_SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$TEST_SELF_DIR/.." && pwd)"
SCANNER="$ROOT/scripts/check-secret-scan.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }
[ -f "$SCANNER" ] || fail "缺少被测脚本 $SCANNER"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/aigicrm-secret-scan-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
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
cat > "$REPO/bad-url.env" <<'EOF'
# 故意的 p4 夹具(URL 内嵌凭据)
DB=mysql://root:zz9-url-cred@db.local/x
EOF
PK_TAIL="PRIVATE KEY"
printf -- '-----BEGIN RSA %s-----\n' "$PK_TAIL" > "$REPO/bad-key.pem"
cat > "$REPO/clean-placeholder.js" <<'EOF'
const PASS = '<redacted-by-owner>';
EOF
cat > "$REPO/clean-envref.sh" <<'EOF'
PASS="$DB_PASSWORD"
EOF
# 豁免路径夹具: 与 ALLOWLIST 精确条目同路径的伪 bcrypt(前缀拆开拼装, 测试源码不出现完整哈希形态)
HASH_PREFIX='$''2a$''10$'
HASH_TAIL='01234567890123456789012345678901234567890123456789abc'
mkdir -p "$REPO/sql/migration/20260814"
printf "AND password = '%s%s';\n" "$HASH_PREFIX" "$HASH_TAIL" > "$REPO/sql/migration/20260814/rbac-convergence-m1.sql"
# node_modules 内的同型违例不应被扫描
mkdir -p "$REPO/node_modules/pkg"
cat > "$REPO/node_modules/pkg/leak.js" <<'EOF'
const PASS = 'zz9-inside-node-modules';
EOF

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

# ---- ② 清除违例后 → rc=0, 豁免命中计数 ≥1 ----
rm -f "$REPO/bad-assign.js" "$REPO/bad-fill.mjs" "$REPO/bad-url.env" "$REPO/bad-key.pem"
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

echo "PASS: check-secret-scan.sh 行为测试(p1-p4 检出/占位符与\$引用豁免/node_modules 排除/精确豁免/fail-closed)。"
