#!/bin/bash
# 分页反模式扫描器（YCMN 前端）
# 发现并阻止分页最佳实践违规，接入 pre-delivery-check。
# 反模式:
#   A. 筛选 @change/@keyup.enter 调 loadData 但不先重置 current/pageNum → 翻页后筛选显示错误页
#   B. 后端分页(服务端 total) 页面里 .filter() 本地过滤 records → total 与显示不一致
#   C. el-table/v-for 大列表页无分页（提示）
set -uo pipefail
SRC="ycmn-ui/src/views"
FAIL=0
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'

echo "== 分页反模式扫描 =="

# 反模式 A: 筛选 @change/@keyup.enter 绑 loadData 且函数未重置页码（仅对"有分页"页面生效）
while IFS= read -r f; do
  has_pager=$(grep -c "el-pagination" "$f")
  [ "$has_pager" = "0" ] && continue
  grep -nE '@(change|keyup\.enter)="(load|search)[A-Za-z]*"' "$f" 2>/dev/null | while IFS=: read -r ln line; do
    # 分页器(el-pagination)的 @change 合法 → 检查该行±10行内是否有 el-pagination
    start=$((ln-10)); [ $start -lt 1 ] && start=1
    end=$((ln+10))
    if sed -n "${start},${end}p" "$f" | grep -q "el-pagination"; then
      continue
    fi
    fn=$(echo "$line" | grep -oE 'load[A-Za-z]*|searchFiles' | head -1)
    if [ -n "$fn" ]; then
      defline=$(grep -n "function $fn\b\|const $fn =" "$f" | head -1 | cut -d: -f1)
      if [ -n "$defline" ]; then
        has_reset=$(sed -n "${defline},$((defline+15))p" "$f" | grep -cE "(current|pageNum)(\.value)?\s*=\s*1")
        if [ "$has_reset" = "0" ]; then
          echo -e "${RED}  [A] $f:$ln 筛选 @change=$fn 未重置页码 → 翻页后筛选显示错误页${NC}"
          FAIL=1
        fi
      fi
    fi
  done
done < <(find $SRC -name "*.vue" | grep -v "\.bak")

# 反模式 B: 服务端分页(total.value = res...total) + 本地 records.filter
while IFS= read -r f; do
  has_server_total=$(grep -cE "total(\.value)?\s*=\s*res\.data[^;]*\.total|total(\.value)?\s*=\s*res\.data\?" "$f")
  if [ "$has_server_total" -gt "0" ]; then
    filter_hit=$(grep -nE "records\.filter|tableData\.value\s*=.*\.filter|list\.value\s*=\s*.*\.filter" "$f" | head -3)
    if [ -n "$filter_hit" ]; then
      echo -e "${RED}  [B] $f: 服务端分页页面本地 .filter() 过滤 → total 与显示不一致${NC}"
      echo "$filter_hit" | while read -r h; do echo "       $h"; done
      FAIL=1
    fi
  fi
done < <(find $SRC -name "*.vue" | grep -v "\.bak")

# 反模式 C: 大列表页无分页（提示，不阻塞）
while IFS= read -r f; do
  has_table=$(grep -c "el-table" "$f")
  has_vfor=$(grep -c "v-for" "$f")
  has_pager=$(grep -c "el-pagination" "$f")
  if [ "$has_table" -gt "0" ] && [ "$has_vfor" -gt "2" ] && [ "$has_pager" = "0" ]; then
    base=$(basename "$f" .vue)
    case "$base" in
      role|group|position|dept|org|rule|config|approvalConfig|incentiveCategory|login|add|detail|overview|ForgotPasswordDialog)
        continue;;
    esac
    echo -e "${YELLOW}  [C][提示] $f: 列表页无分页(数据量小可接受,增长后需加)${NC}"
  fi
done < <(find $SRC -name "*.vue" | grep -v "\.bak")

if [ "$FAIL" = "0" ]; then
  echo -e "${GREEN}  ✅ 分页反模式扫描通过${NC}"
else
  echo -e "${RED}  ❌ 发现分页反模式,请修复后重跑${NC}"
  exit 1
fi
