#!/usr/bin/env bash
# check-secure-random-contract.sh 的行为测试（2026-11-24 GL-14）。
# 把契约脚本拷进临时目录，配一套最小的源码夹具，逐个场景运行，判定只认退出码：
#   合格的写法必须通过；修复前的写法（验证码取自 ThreadLocalRandom）与各种弱随机源必须不通过；
#   注释里提到弱随机源不得误报。全部在临时目录里进行，不依赖真实源码树；最后一个场景对真实源码树跑一次。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTRACT="$HERE/check-secure-random-contract.sh"
BASE="${TMPDIR:-/tmp}"
WORK="$BASE/gl14-securerandom-contract.$$"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

# 在临时目录里搭一棵最小源码树：scripts/ 下放契约脚本，aigicrm-backend/… 下放夹具
stage() {
  mkdir -p "$WORK/scripts" \
    "$WORK/aigicrm-backend/src/main/java/com/aigicrm/module/auth/service" \
    "$WORK/aigicrm-backend/src/main/java/com/aigicrm/module/system/service" \
    "$WORK/aigicrm-backend/src/main/java/com/aigicrm/security"
  cp "$CONTRACT" "$WORK/scripts/"
}

fixtures() {
  cat > "$WORK/aigicrm-backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java" <<'JAVA'
package com.aigicrm.module.auth.service;

import java.security.SecureRandom;

class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    void sendResetCode() {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
JAVA
  cat > "$WORK/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/SysUserService.java" <<'JAVA'
package com.aigicrm.module.system.service;

class SysUserService {
    String initialPassword() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        return random.nextInt(100) + "";
    }
}
JAVA
  cat > "$WORK/aigicrm-backend/src/main/java/com/aigicrm/security/TokenService.java" <<'JAVA'
package com.aigicrm.security;

import java.util.UUID;

class TokenService {
    String id() {
        return UUID.randomUUID().toString();
    }
}
JAVA
}

passed=0
failed=0
run_case() {
  local label="$1" expect="$2" out rc
  out="$(bash "$WORK/scripts/check-secure-random-contract.sh" 2>&1)"; rc=$?
  if [ "$rc" -eq "$expect" ]; then
    passed=$((passed + 1)); echo "PASS $label"
  else
    failed=$((failed + 1)); echo "FAIL $label — 期望退出码 $expect, 实际 $rc"; echo "$out" | sed 's/^/    /'
  fi
}
rewrite() {
  # 用 perl 做字面替换（不解释正则里的特殊字符）；替换后必须确实变了，否则这个场景不算数
  local file="$1" from="$2" to="$3" before after
  before="$(cat "$file")"
  perl -0pi -e 'BEGIN { ($from, $to) = @ARGV[0, 1]; splice @ARGV, 0, 2 } s/\Q$from\E/$to/g' "$from" "$to" "$file"
  after="$(cat "$file")"
  [ "$before" != "$after" ] || { echo "场景没有生效: $file 里的 [$from] 没有被替换" >&2; exit 3; }
}

AUTH="$WORK/aigicrm-backend/src/main/java/com/aigicrm/module/auth/service/AuthService.java"
SYSUSER="$WORK/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/SysUserService.java"
TOKEN="$WORK/aigicrm-backend/src/main/java/com/aigicrm/security/TokenService.java"

stage; fixtures
run_case '① 合格写法: 验证码与初始口令取自 SecureRandom' 0

stage; fixtures
rewrite "$AUTH" 'import java.security.SecureRandom;' 'import java.util.concurrent.ThreadLocalRandom;'
rewrite "$AUTH" '    private static final SecureRandom SECURE_RANDOM = new SecureRandom();' '    private static final ThreadLocalRandom SECURE_RANDOM = ThreadLocalRandom.current();'
run_case '② 修复前的写法: 验证码取自 ThreadLocalRandom' 1

stage; fixtures
rewrite "$AUTH" 'String.format("%06d", SECURE_RANDOM.nextInt(1_000_000))' 'String.format("%06d", new java.util.Random().nextInt(1_000_000))'
run_case '③ 验证码取自 java.util.Random' 1

stage; fixtures
rewrite "$AUTH" '    private static final SecureRandom SECURE_RANDOM = new SecureRandom();' '    private static final java.util.Random CODE_RANDOM = new java.util.Random();'
run_case '④ AuthService 不持有 SecureRandom' 1

stage; fixtures
rewrite "$SYSUSER" 'java.security.SecureRandom random = new java.security.SecureRandom();' 'java.util.Random random = new java.util.Random();'
run_case '⑤ 初始口令取自 java.util.Random' 1

stage; fixtures
rewrite "$TOKEN" 'return UUID.randomUUID().toString();' 'return "t-" + Math.random();'
run_case '⑥ 安全包里使用 Math.random()' 1

stage; fixtures
cat >> "$TOKEN" <<'JAVA'
// 说明: 这里不能用 ThreadLocalRandom（不是密码学安全的），见 9db17c8f
JAVA
run_case '⑦ 注释里提到弱随机源不算违规' 0

stage; fixtures
cat >> "$TOKEN" <<'JAVA'
class Other {
    String nonce() { return java.util.concurrent.ThreadLocalRandom.current() + ""; }
}
JAVA
run_case '⑧ 安全包里使用全限定名的 ThreadLocalRandom' 1

# 下面三个场景里的随机源既不是 SecureRandom、也不在“弱随机源”清单里——只有各自的专门规则才拦得住
stage; fixtures
rewrite "$AUTH" 'String.format("%06d", SECURE_RANDOM.nextInt(1_000_000))' 'String.format("%06d", Math.abs(System.nanoTime()) % 1_000_000)'
run_case '⑨ 验证码改由时间戳取模(可预测), 类里仍留着 SecureRandom' 1

stage; fixtures
cat > "$SYSUSER" <<'JAVA'
package com.aigicrm.module.system.service;

class SysUserService {
    /** 初始口令改为写死的常量(安全 P0 修掉过的老问题) */
    String initialPassword() {
        return "Init-12345678";
    }
}
JAVA
run_case '⑩ 初始口令不再是随机生成的' 1

stage; fixtures
rewrite "$AUTH" 'private static final SecureRandom SECURE_RANDOM = new SecureRandom();' 'private static final java.util.SplittableRandom SECURE_RANDOM = new java.util.SplittableRandom();'
run_case '⑪ 名字叫 SECURE_RANDOM, 实际不是 SecureRandom' 1

echo "通过 $passed 项, 未通过 $failed 项"
[ "$failed" -eq 0 ] || exit 1

# 最后对真实源码树跑一次（当前源码应当是合格的）
if out="$(bash "$CONTRACT" 2>&1)"; then
  echo "PASS ⑫ 真实源码树: $out"
else
  echo "FAIL ⑫ 真实源码树未通过契约: $out"; exit 1
fi
