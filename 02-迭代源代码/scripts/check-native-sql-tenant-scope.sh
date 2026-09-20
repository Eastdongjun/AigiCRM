#!/usr/bin/env bash
# 【PR-22.G-01 2026-09-20】原生 SQL(JdbcTemplate)租户/集团范围审计契约
#
# 背景: JdbcTemplate 绕过 MyBatis-Plus 租户拦截器——N-02(getDuplicateInfo 缺 tenant)、
# N-07(删角色清 biz_folder_acl 缺 tenant)均为该家族缺陷。本契约把 2026-09-20 的
# 全量审计(79 处原生 SQL 站点)固化为静态门禁:
#
# 规则: 任何 Java 文件里, 若 JdbcTemplate 调用的 SQL 字符串触及 biz_* 业务表,
# 该文件必须同时含限定凭据之一:
#   tenant_id / GroupScopeUtil / groupCondition / tenantFrag / userIdsInGroup
# (即显式租户过滤或集团成员边界); 否则必须在该文件内写显式豁免注释:
#   native-sql-audit: <原因>
# 新增未限定的 biz_* 原生 SQL 将直接 FAIL, 强制归类而非静默溜入。
#
# 说明: @Select/@Delete 等注解 SQL 走 MP 拦截器链(租户过滤自动追加), 不在本契约范围;
# sys_* 系统表不在租户白名单(MybatisPlusConfig TENANT_TABLES), 天然豁免。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/aigicrm-backend/src/main/java"

fail() { echo "FAIL: $*" >&2; exit 1; }

violations=0
checked=0
while IFS= read -r -d '' file; do
  text="$(cat "$file")"
  # 该文件是否有 JdbcTemplate 调用
  grep -Eq '\bjdbc(Template)?\.(query|queryForList|queryForObject|queryForMap|update|execute|queryForLong)\s*\(' <<<"$text" || continue
  # 其 SQL 字符串是否触及 biz_* 表
  grep -Eq '(FROM|INTO|UPDATE|JOIN|DELETE FROM)[[:space:]]+biz_[a-z_]+' <<<"$text" || continue
  checked=$((checked + 1))
  if grep -Eq 'native-sql-audit:' <<<"$text"; then
    continue # 显式豁免(带原因注释)
  fi
  if grep -Eq 'tenant_id|GroupScopeUtil|groupCondition|tenantFrag|userIdsInGroup' <<<"$text"; then
    continue # 含租户/集团限定凭据
  fi
  echo "FAIL: $file 含触及 biz_* 表的 JdbcTemplate 原生 SQL, 但无 tenant_id/集团边界凭据," >&2
  echo "      也无 native-sql-audit: 豁免注释——请补限定或显式归类(参考 N-02/N-07 修复模式)" >&2
  violations=$((violations + 1))
done < <(find "$SRC" -name '*.java' -print0)

[ "$violations" -eq 0 ] || fail "$violations 个文件违反原生 SQL 范围契约"
echo "PASS: native SQL tenant/scope contract is aligned ($checked biz-touching JDBC files all qualified or waived)."
