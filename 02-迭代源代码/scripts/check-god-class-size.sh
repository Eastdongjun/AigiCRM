#!/usr/bin/env bash
# S5.3 God class 行数护栏（2026-11-23）
#
# 规则：
#   - FileService 目标 ≤800，已达标；回涨即 FAIL。
#   - CustomerService / IncentiveService 目标 ≤800，当前冻结基线 2540 / 1836；
#     超过基线 = 新增增长，FAIL；拆分后必须同步下调基线（只减不增）。
#
# 不依赖 bash 4 关联数组（macOS 自带 bash 3.2）。
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0

check() {
  name="$1"; file="$2"; target="$3"; baseline="$4"
  if [ ! -f "$file" ]; then
    echo "❌ 找不到 $name: $file"; FAIL=1; return
  fi
  lines=$(wc -l < "$file" | tr -d ' ')
  if [ "$lines" -le "$target" ]; then
    echo "✅ $name ${lines} 行 <= 目标 ${target}"
  elif [ "$lines" -le "$baseline" ]; then
    echo "⚠️  $name ${lines} 行 > 目标 ${target}，但 <= 冻结基线 ${baseline}（待拆分，禁止增长）"
  else
    echo "❌ $name ${lines} 行 > 目标 ${target} 且 > 冻结基线 ${baseline}（新增增长）"
    FAIL=1
  fi
}

check FileService \
  "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java" 800 800
check CustomerService \
  "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java" 800 2142
check IncentiveService \
  "$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java" 800 1836

if [ "$FAIL" -ne 0 ]; then
  echo "❌ God class 行数护栏未通过"
  exit 1
fi
echo "PASS: God class 行数护栏通过（FileService 已达标；Customer/Incentive 冻结基线不增长）"
