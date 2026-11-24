#!/usr/bin/env bash
# check-native-sql-tenant-scope.sh 的行为测试（2026-11-25 补；这个安全护栏此前没有自己的行为测试）。
# 在临时目录里各放一个 Java 文件当夹具，只认退出码与输出里的结论：
#   带租户限定的通过；既没限定也没归类的必须红；旧式豁免（不带处数）通过但带提醒；
#   带处数的豁免要求“登记的处数 = 实际的 JdbcTemplate 调用点数”，多一条少一条都红；不触及 biz_* 表的不管。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTRACT="$HERE/check-native-sql-tenant-scope.sh"
BASE="${TMPDIR:-/tmp}"
WORK="$BASE/native-sql-test.$$"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

new_src() { rm -rf "$WORK/src"; mkdir -p "$WORK/src"; }
put() { printf '%s\n' "$2" > "$WORK/src/Fx.java"; }

# $1=输出 $2=用 ¦ 分隔的词。has_all: 每个词都在输出里才算成功; lacks_all: 每个词都不在才算成功。
# 只用 bash 的参数展开切词、每行带换行再交给 read：不带换行的最后一行 while read 读不进来，断言会空转通过。
split_words() { # $1=¦分隔的词 → 每行一个（空串不输出）
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
# 匹配器自检：四种情形都对才继续。匹配器不灵时后面每个场景的“通过”都可能是空转，所以直接判失败。
matcher_ok=1
has_all '甲 乙' '甲¦乙' || matcher_ok=0
has_all '甲' '甲¦丙' && matcher_ok=0
lacks_all '甲' '丙¦丁' || matcher_ok=0
lacks_all '甲' '丙¦甲' && matcher_ok=0
[ "$matcher_ok" -eq 1 ] || { echo "FAIL 匹配器自检 —— 断言用的词匹配本身不灵，本测试的结论不可信"; exit 1; }

passed=0
failed=0
run_case() { # <标签> <期望退出码> <输出必须包含(¦分隔)> <输出不得包含(¦分隔)>
  local label="$1" expect="$2" must="$3" mustnot="$4" out rc ok=1
  out="$(AIGI_NATIVE_SQL_SRC="$WORK/src" bash "$CONTRACT" 2>&1)"; rc=$?
  has_all "$out" "$must" || ok=0
  lacks_all "$out" "$mustnot" || ok=0
  if [ "$rc" -eq "$expect" ] && [ "$ok" -eq 1 ]; then
    passed=$((passed + 1)); echo "PASS $label"
  else
    failed=$((failed + 1)); echo "FAIL $label — 期望退出码 $expect, 输出含 [$must] 且不含 [$mustnot]; 实际退出码 $rc, 输出: $(printf '%s' "$out" | /usr/bin/sed -E "s|$WORK/src/||" | tr '\n' ' ' | cut -c1-380)"
  fi
}

new_src
put Fx 'package fx;
class Fx {
    void a(Long tenantId) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE tenant_id = ?", tenantId);
    }
}'
run_case '① SQL 自带租户限定：通过' 0 'PASS: native SQL tenant/scope contract is aligned (1 biz-touching' 'FAIL¦旧式豁免'

new_src
put Fx 'package fx;
class Fx {
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE x = ?", x);
    }
}'
run_case '② 既没限定也没归类：必须红，并点出原因' 1 '无 tenant_id/集团边界凭据¦1 个文件违反原生 SQL 范围契约' 'PASS: native SQL'

new_src
put Fx 'package fx;
class Fx {
    // native-sql-audit: 旧写法的豁免，写明理由
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE x = ?", x);
    }
}'
run_case '③ 旧式豁免（不带处数）：通过，但提醒一句' 0 'PASS: native SQL¦1 个文件用的是不带处数的旧式豁免' 'FAIL'

new_src
put Fx 'package fx;
class Fx {
    // native-sql-audit[2]: 两处调用都登记了
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE x = ?", x);
        jdbc.update("UPDATE biz_a SET y = ? WHERE x = ?", 1, x);
    }
}'
run_case '④ 带处数的豁免，处数对得上：通过，不算旧式' 0 'PASS: native SQL' 'FAIL¦旧式豁免'

new_src
put Fx 'package fx;
class Fx {
    // native-sql-audit[1]: 只登记了一处
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE x = ?", x);
        jdbc.update("UPDATE biz_a SET y = ? WHERE x = ?", 1, x);
    }
}'
run_case '⑤ 带处数的豁免，后来又加了一条 SQL：处数对不上，必须红' 1 '登记了 1 处原生 SQL, 实际有 2 处 JdbcTemplate 调用¦1 个文件违反原生 SQL 范围契约' 'PASS: native SQL'

new_src
put Fx 'package fx;
class Fx {
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM sys_user WHERE id = ?", x);
    }
}'
run_case '⑥ 只触及系统表（不在租户白名单）：不管' 0 'PASS: native SQL tenant/scope contract is aligned (0 biz-touching' 'FAIL'

new_src
put Fx 'package fx;
class Fx {
    // native-sql-audit[2]: 前两处
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE x = ?", x);
        jdbc.update("UPDATE biz_a SET y = ? WHERE x = ?", 1, x);
    }
    // native-sql-audit[1]: 后一处
    void b(Long x) {
        jdbc.queryForMap("SELECT COUNT(*) FROM biz_b WHERE x = ?", x);
    }
}'
run_case '⑦ 一个文件里写多条带处数的豁免：按总和核对' 0 'PASS: native SQL' 'FAIL¦旧式豁免'

new_src
put Fx 'package fx;
class Fx {
    // native-sql-audit[3]: 登记多了
    void a(Long x) {
        jdbc.queryForList("SELECT id FROM biz_a WHERE x = ?", x);
    }
}'
run_case '⑧ 登记的处数比实际多（删了 SQL 没回来改处数）：同样红' 1 '登记了 3 处原生 SQL, 实际有 1 处 JdbcTemplate 调用' 'PASS: native SQL'

echo "通过 $passed 项, 未通过 $failed 项"
[ "$failed" -eq 0 ] || exit 1
