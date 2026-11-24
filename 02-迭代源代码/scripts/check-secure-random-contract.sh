#!/usr/bin/env bash
# 【GL-14 2026-11-24】安全用途的随机值必须取自安全随机数发生器。
# 守的是安全修复 9db17c8f 里的“密码重置随机源”一项：验证码此前取自 ThreadLocalRandom（可预测）。
# 行为测试看不出随机源是哪一个，所以用源码契约来守。契约自身的测试：
# scripts/test-check-secure-random-contract.sh（隔离的夹具，含修复前的写法）。
#
# 口径：
#   1) 密码重置验证码由 AuthService 持有的 SecureRandom 生成；
#   2) 随机初始口令由 SysUserService 里的 SecureRandom 生成；
#   3) 认证与安全相关的代码里不得使用 java.util.Random / Math.random() / ThreadLocalRandom
#      （都不是密码学安全的；UUID.randomUUID() 内部即 SecureRandom，允许）。
#      缓存有效期抖动、错误追踪号这类非安全用途不在本契约范围内。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/aigicrm-backend/src/main/java/com/aigicrm"
AUTH="$SRC/module/auth/service/AuthService.java"
SYSUSER="$SRC/module/system/service/SysUserService.java"
SECURE_RANDOM='(java\.security\.)?SecureRandom'
WEAK_RANDOM='java\.util\.Random\b|Math\.random\(|ThreadLocalRandom'

fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Eq "SecureRandom +[A-Za-z_]+ *= *new +${SECURE_RANDOM}\(\)" "$AUTH" \
  || fail 'AuthService 必须持有一个 SecureRandom 实例用于生成验证码'
grep -Eq 'format\("%06d", *[A-Za-z_.]*SECURE_RANDOM' "$AUTH" \
  || fail '密码重置验证码必须由 SecureRandom 生成'
grep -Eq "new +${SECURE_RANDOM}\(\)" "$SYSUSER" \
  || fail '随机初始口令必须由 SecureRandom 生成'

# 只看代码行, 注释里提到弱随机源不算
weak_hits="$(grep -rnE "$WEAK_RANDOM" "$SRC/security" "$SRC/module/auth" "$SRC/common/security" "$SYSUSER" \
  --include='*.java' | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|\*|/\*)' || true)"
[ -z "$weak_hits" ] || fail "认证与安全相关的代码不得使用非安全随机源:
$weak_hits"

echo 'PASS: 安全用途的随机值取自 SecureRandom；认证与安全相关代码未使用非安全随机源。'
