#!/usr/bin/env bash
# Flyway 迁移契约校验（fail-closed，2026-09-11 建立）
#
# 为什么需要这个脚本 —— 三条都是实测撞过的坑：
#   ① 版本号写连字符（V2026-09-11-v1__x.sql）时，Flyway 9.22.3 **不报错但也不执行**，
#      只打一行 "1 SQL migrations were detected but not run because they did not follow
#      the filename convention"，migrate 仍以退出码 0 结束。迁移"成功"了，活没干。
#   ② macOS 产出 AppleDouble（._V*.sql）会让 Flyway 少跑迁移（2026-08-15 实测）。
#   ③ 已部署到生产的迁移文件一旦被改内容/改名，生产 validate 直接失败。
#
# 校验项：
#   [1] 文件名合法：存量 V<数字>__desc.sql / 新增 V<YYYY.MM.DD>.<序号>__desc.sql
#   [2] 版本号不重复
#   [3] 存量数字版本必须全部登记在冻结清单内，且不得新增数字版本（分界点后一律用日期版本）
#   [4] 日期版本的年份必须 >= 2000（保证严格排在存量数字版本之后，Flyway 不做乱序应用）
#   [5] FROZEN-SHA256.txt 中登记的迁移逐字节不得变动
#   [6] 迁移目录内不得存在 '._*' AppleDouble 文件
#
# 用法：bash scripts/check-migration-contract.sh
#   AIGICRM_MIGRATION_DIR 可覆盖被校验目录（用于负向自测）
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIG="${AIGICRM_MIGRATION_DIR:-$ROOT/aigicrm-backend/src/main/resources/db/migration}"
FROZEN="$MIG/FROZEN-SHA256.txt"

FAILED=0
fail() { echo "  ❌ $*" >&2; FAILED=$((FAILED + 1)); }

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    return 127
  fi
}

[ -d "$MIG" ] || { echo "FAIL: 迁移目录不存在: $MIG" >&2; exit 1; }
[ -f "$FROZEN" ] || { echo "FAIL: 缺少冻结清单: $FROZEN" >&2; exit 1; }

# 存量（数字）版本正则 / 新增（日期）版本正则。
# 连字符刻意不在允许集合内 —— 它是 Flyway 静默跳过的根因。
RE_LEGACY='^V([0-9]+)__[a-z0-9_]+\.sql$'
RE_DATED='^V([0-9]{4})\.([0-9]{2})\.([0-9]{2})\.([0-9]+)__[a-z0-9_]+\.sql$'

versions=()
numeric_versions=()
shopt -s nullglob
sql_files=("$MIG"/*.sql)
shopt -u nullglob

[ "${#sql_files[@]}" -gt 0 ] || { echo "FAIL: 迁移目录内没有任何 .sql" >&2; exit 1; }

# [1][2][4] 文件名与版本
for f in "${sql_files[@]}"; do
  base="$(basename "$f")"
  if [[ $base =~ $RE_LEGACY ]]; then
    versions+=("${BASH_REMATCH[1]}")
    numeric_versions+=("${BASH_REMATCH[1]}")
  elif [[ $base =~ $RE_DATED ]]; then
    year="${BASH_REMATCH[1]}"
    versions+=("${year}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}.${BASH_REMATCH[4]}")
    if [ "$((10#$year))" -lt 2000 ]; then
      fail "$base 日期版本年份 $year < 2000，会排到存量数字版本之前（Flyway 默认不做乱序应用，该迁移会被忽略）"
    fi
  else
    fail "$base 文件名非法。合法形式：存量 V33__desc.sql / 新增 V2026.09.11.1__desc.sql（版本号只允许数字与点；连字符会让 Flyway 静默跳过）"
  fi
done

dup="$(printf '%s\n' "${versions[@]}" | sort | uniq -d)"
if [ -n "$dup" ]; then
  fail "版本号重复: $(echo "$dup" | tr '\n' ' ')"
fi

# [3] 数字版本必须来自冻结清单，且不得新增数字版本
frozen_files=()
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  frozen_files+=("${line##* }")
done < "$FROZEN"

for v in "${numeric_versions[@]}"; do
  hit=0
  for ff in "${frozen_files[@]}"; do
    case "$ff" in "V${v}__"*) hit=1; break ;; esac
  done
  [ "$hit" = 1 ] || fail "数字版本 V$v 不在冻结清单内 —— 分界点(2026-09-11)之后的新迁移必须用日期版本，例如 V2026.09.11.1__desc.sql"
done

# [5] 冻结清单逐字节校验
frozen_missing=0
for ff in "${frozen_files[@]}"; do
  path="$MIG/$ff"
  if [ ! -f "$path" ]; then
    fail "冻结清单登记的 $ff 不存在（已部署的迁移文件不得删除或改名）"
    frozen_missing=$((frozen_missing + 1))
    continue
  fi
  want="$(awk -v n="$ff" '$2 == n {print $1; exit}' "$FROZEN")"
  got="$(sha256_of "$path")"
  if [ "$got" = "NO_SHA" ] || [ -z "$got" ]; then
    fail "缺少 sha256 工具（sha256sum / shasum），无法校验冻结清单"
    break
  fi
  [ "$want" = "$got" ] || fail "$ff 内容已变动（冻结文件不得修改：生产/测试库按旧内容记录 checksum，改动会导致 Flyway validate 失败）"
done

# [6] AppleDouble
ad="$(find "$MIG" -maxdepth 1 -name '._*' -print 2>/dev/null | head -5)"
[ -z "$ad" ] || fail "存在 macOS AppleDouble 文件，会让 Flyway 少跑迁移: $(echo "$ad" | tr '\n' ' ')"

if [ "$FAILED" -gt 0 ]; then
  echo "FAIL: 迁移契约校验未通过（$FAILED 项）—— Flyway 会静默跳过非法文件名的迁移，必须修掉" >&2
  exit 1
fi

echo "PASS: 迁移契约有效（${#sql_files[@]} 个迁移 / 存量数字版本 $(printf '%s\n' "${numeric_versions[@]}" | sort -n | tail -1) / 日期版本 $(printf '%s\n' "${versions[@]}" | grep -c '\.' || true) 个 / 冻结清单 ${#frozen_files[@]} 项）"
