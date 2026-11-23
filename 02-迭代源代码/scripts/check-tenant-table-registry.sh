#!/usr/bin/env bash
# 【CODE-REVIEW-20261123 R0-01 防复发 2026-11-23】租户白名单登记契约
#
# 背景（真实缺陷）：2026-11-23 法布施变更新增 3 张 biz_* 表，全部漏登记 MybatisPlusConfig
# TENANT_TABLES，其中 biz_fabushi_recitation 连 tenant_id 列都没有。
# 后果：生产强制 aigicrm.tenant.enable=true（MybatisPlusConfig#verifyTenantColumns 在 prod 下
#      未开启直接启动失败），但这 3 张表不在白名单 ⇒ 写入不被插件补租户、恒落默认 1，
#      隔离列失真；查询不被租户过滤。与既有 12 张 biz_fabushi_* 表范式不一致。
# 同类历史事故：H4 biz_customer_stage_amount 漏白名单（MybatisPlusConfig 注释在案）。
#
# 为什么既有脚本没拦住：
#   check-native-sql-tenant-scope.sh 只覆盖 JdbcTemplate 原生 SQL，明确声明
#   "@Select/@Delete 走 MP 拦截器链，不在本契约范围" ⇒ MP 访问路径是盲区。
#
# 本契约规则：
#   R1 迁移里 CREATE 出来的每张 biz_* 表（扣除已被 DROP 的），必须出现在 TENANT_TABLES 中，
#      或在下方 PENDING_FIX（已知待修，WARN 不 FAIL）或 EXEMPT（确不属租户维度，须给理由）中。
#      三者皆非 ⇒ FAIL（新增未登记表，立即拦住）。
#   R2 白名单里的每张表，迁移里必须能找到 tenant_id 定义（否则启动自检必挂）。
#
# 退出码：0=通过（可含 WARN），1=存在未登记表或白名单缺列。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/config/MybatisPlusConfig.java"
MIGRATION_DIR="$ROOT/aigicrm-backend/src/main/resources/db/migration"

# ── 已知待修（WARN，不阻断）：修完必须从本清单删除 ──
# 格式：表名|原因|登记单号
PENDING_FIX=(
  # 【已修 2026-11-23】R1-01 三张新表已全部登记白名单，biz_fabushi_recitation 由 V2026.11.23.5 补 tenant_id 列。
  #   处置依据：单集团模式（tenant_id 恒 1）⇒ 登记后过滤条件不改变结果集，无副作用。
  #   多集团前置阻断项见 docs/TECH_DEBT.md「单集团假设」。
  # 【已修 2026-11-23】R2-01 既有漂移 12 张已全部收口（10 张进白名单 + 2 张进 EXEMPT），
  #   逐表取证过程见 docs/TECH_DEBT.md「租户白名单漂移 R2-01 收口」。
)

# ── 不适用 MP 租户插件（不 WARN）：须写清理由与证据 ──
# 两类可进：① 确不属租户维度；② 属租户维度但隔离由其它机制覆盖（列名非 tenant_id /
# 无 MyBatis-Plus 访问路径）。注意 MP 插件的列名是全局固定的 getTenantIdColumn()="tenant_id"，
# 无法按表指定列名——所以「用 group_id 隔离」的表只能走本清单，不能进白名单。
EXEMPT=(
  # 【R2-01 2026-11-23】租户维度列是 group_id，不是 tenant_id。
  #   证据：实体 BizFormDraft.groupId；FormDraftService:39/61/104/119 四处读写全部 .eq(getGroupId, groupId)，
  #   保存时 :92 setGroupId；FormDraftController:32/40/57/67 的 groupId 取自
  #   GroupScopeUtil.currentGroupOrNull()（会话），不取客户端入参 ⇒ 无越权入口。
  #   不进白名单的原因：MP 3.5.6 的 TenantLineHandler#getTenantIdColumn 全局单值，无法按表切换列名；
  #   硬加一个平行 tenant_id 列会造出两个租户真相源，比现状更差。
  "biz_form_draft|租户列名为 group_id 且业务层已显式按会话集团过滤，MP 插件无法按表指定列名|CODE-REVIEW-20261123 R2-01"
  # 【R2-01 2026-11-23】无 tenant_id 列，也无任何 MyBatis-Plus 访问路径。
  #   证据：无 @TableName 实体、无 Mapper、无 XML、无 @Select 注解 SQL；
  #   全部 6 处访问都在 JdbcTemplate 原生 SQL（IncentiveService:462/465/1675/1687/1807、
  #   RecycleService:290/729/1007/1017），按 user_id / record_id 归属过滤，
  #   两者均来自已被租户隔离的上下文（biz_incentive_record 在白名单）。
  #   ⚠️ 诚实说明：check-native-sql-tenant-scope.sh 是文件级判定（文件内出现一次 tenant_id 即整文件合格），
  #   IncentiveService 通过该门禁并不等于上面几条语句被逐条限定；此处豁免的依据是上一条的归属链路，不是那个脚本。
  "biz_incentive_read|无 tenant_id 列且无 MP 访问路径，全部走 JdbcTemplate 按 user_id/record_id 归属过滤|CODE-REVIEW-20261123 R2-01"
)

[ -f "$CONFIG" ] || { echo "❌ 找不到 MybatisPlusConfig.java: $CONFIG"; exit 1; }
[ -d "$MIGRATION_DIR" ] || { echo "❌ 找不到迁移目录: $MIGRATION_DIR"; exit 1; }

# ── 1. 白名单表 ──
# 注意：Set.of(...) 的收尾是「..."xxx");」——`);` 不在行首，故按「行内含 );」终止，
#       否则范围会一路吃到文件尾，把 verifyTenantColumns 里的 SQL 字面量也当表名。
WHITELIST="$(sed -n '/TENANT_TABLES = Set\.of(/,/);/p' "$CONFIG" \
  | grep -oE '"[a-z][a-z0-9_]*"' | tr -d '"' | sort -u)"
[ -n "$WHITELIST" ] || { echo "❌ 无法从 MybatisPlusConfig 解析 TENANT_TABLES（格式可能已变，请同步本脚本）"; exit 1; }

# ── 2. 迁移中 CREATE 的 biz_* 表 ──
CREATED="$(grep -hioE 'CREATE TABLE( IF NOT EXISTS)?[[:space:]]+`?biz_[a-z0-9_]+`?' "$MIGRATION_DIR"/*.sql 2>/dev/null \
  | grep -oE 'biz_[a-z0-9_]+' | sort -u)"

# ── 3. 迁移中 DROP 的 biz_* 表（已下线，不再要求登记）──
DROPPED="$(grep -hioE 'DROP TABLE( IF EXISTS)?[[:space:]]+`?biz_[a-z0-9_]+`?' "$MIGRATION_DIR"/*.sql 2>/dev/null \
  | grep -oE 'biz_[a-z0-9_]+' | sort -u)"

LIVE="$(comm -23 <(echo "$CREATED") <(echo "$DROPPED"))"

# ── 4. R1 登记完整性 ──
is_in() { # is_in <needle> <list-var-name>
  local needle="$1"; shift
  local item
  for item in "$@"; do
    [ "${item%%|*}" = "$needle" ] && return 0
  done
  return 1
}

UNREGISTERED=(); WARNED=()
while read -r t; do
  [ -n "$t" ] || continue
  grep -qx "$t" <<<"$WHITELIST" && continue
  # 注：bash 3.2 + set -u 下空数组的 "${arr[@]}" 展开会报 unbound（${#arr[@]} 不报），
  #     故两处都必须先判长度。PENDING_FIX 清空后（R2-01 收口完成）曾在此炸过一次。
  if [ "${#PENDING_FIX[@]}" -gt 0 ] && is_in "$t" "${PENDING_FIX[@]}"; then WARNED+=("$t"); continue; fi
  if [ "${#EXEMPT[@]}" -gt 0 ] && is_in "$t" "${EXEMPT[@]}"; then continue; fi
  UNREGISTERED+=("$t")
done <<<"$LIVE"

# ── 5. R2 白名单表须有 tenant_id 定义 ──
MISSING_COL=()
while read -r t; do
  [ -n "$t" ] || continue
  # 在「CREATE TABLE t ... ;」块内找 tenant_id，或在「ALTER TABLE t ... ;」块内找
  # （ALTER 必须支持跨行：ADD COLUMN 通常写在下一行，单行匹配会漏判 → 假红）
  if ! awk -v tbl="$t" '
      BEGIN{IGNORECASE=1; inblk=0; inalt=0}
      $0 ~ ("CREATE TABLE( IF NOT EXISTS)?[[:space:]]+`?" tbl "`?[[:space:]]*\\(") {inblk=1}
      inblk && /tenant_id/ {found=1}
      inblk && /^[[:space:]]*\)/ {inblk=0}
      $0 ~ ("ALTER TABLE[[:space:]]+`?" tbl "`?") {inalt=1}
      inalt && /tenant_id/ {found=1}
      inalt && /;/ {inalt=0}
      END{exit found?0:1}
  ' "$MIGRATION_DIR"/*.sql; then
    MISSING_COL+=("$t")
  fi
done <<<"$WHITELIST"

# ── 6. 输出 ──
echo "=== 租户白名单登记契约 ==="
echo "白名单表: $(echo "$WHITELIST" | grep -c . ) 张 | 迁移存活 biz_* 表: $(echo "$LIVE" | grep -c .) 张 | 已 DROP: $(echo "$DROPPED" | grep -c .) 张"

if [ ${#WARNED[@]} -gt 0 ]; then
  echo ""
  echo "⚠️  已知待修（不阻断，但每轮都会提示）："
  for t in "${WARNED[@]}"; do
    for item in "${PENDING_FIX[@]}"; do
      if [ "${item%%|*}" = "$t" ]; then
        echo "   - $t"
        echo "     原因: $(echo "$item" | cut -d'|' -f2)"
        echo "     单号: $(echo "$item" | cut -d'|' -f3)"
      fi
    done
  done
fi

if [ ${#MISSING_COL[@]} -gt 0 ]; then
  echo ""
  echo "❌ 白名单表在迁移中找不到 tenant_id 定义（启动自检会直接失败）："
  printf '   - %s\n' "${MISSING_COL[@]}"
fi

if [ ${#UNREGISTERED[@]} -gt 0 ]; then
  echo ""
  echo "❌ 未登记租户白名单的 biz_* 表（新增表必须显式归类）："
  printf '   - %s\n' "${UNREGISTERED[@]}"
  echo ""
  echo "   处理方式（三选一，禁止默认放过）："
  echo "     1) 承载集团业务数据 → 加入 MybatisPlusConfig.TENANT_TABLES 并确保有 tenant_id 列"
  echo "     2) 确不属租户维度 → 加入本脚本 EXEMPT 并写理由"
  echo "     3) 已知待修 → 加入本脚本 PENDING_FIX 并登记单号（会持续 WARN）"
fi

if [ ${#UNREGISTERED[@]} -gt 0 ] || [ ${#MISSING_COL[@]} -gt 0 ]; then
  exit 1
fi

echo ""
echo "PASS: 迁移内 biz_* 表均已显式归类（白名单/待修/豁免），白名单表 tenant_id 定义齐全。"
