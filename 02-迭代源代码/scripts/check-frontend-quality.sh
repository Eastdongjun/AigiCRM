#!/bin/bash
# 前端质量门禁(FEAT-20260804-QA-GATES):扫描性能/配置反模式,防止同类小错误复发
# 用法: bash scripts/check-frontend-quality.sh [ui-dir] [nginx-conf]
# 退出码: 0=通过(0 违规); 1=存在违规(输出位置)
set -uo pipefail

SRC_ROOT="${1:-$(cd "$(dirname "$0")/.." && pwd)/aigicrm-ui/src}"
NGINX_CONF="${2:-$(cd "$(dirname "$0")/.." && pwd)/nginx/nginx-ssl.conf}"
VIOLATIONS=0

fail() { echo "  ❌ $1"; VIOLATIONS=$((VIOLATIONS+1)); }
pass() { echo "  ✅ $1"; }

echo "===== 前端反模式扫描: $SRC_ROOT ====="

# 1. 全量加载反模式(size: 9999 / size=9999)
echo "-- [1/4] 全量加载(size:9999)检查"
FULL=$(grep -rn "size: *9999\|size=9999" "$SRC_ROOT" 2>/dev/null | grep -v "\.bak" | head -10)
if [ -n "$FULL" ]; then
  while IFS= read -r l; do fail "全量加载: $l"; done <<< "$FULL"
else
  pass "无 size:9999 全量加载"
fi

# 2. createObjectURL 无 revoke(项目级:存在 createObjectURL 的目录必须存在 revokeObjectURL)
echo "-- [2/4] blob URL 泄漏检查"
CREATE_FILES=$(grep -rln "URL.createObjectURL" "$SRC_ROOT" 2>/dev/null | grep -v "\.bak")
REVOKE_COUNT=$(grep -rn "URL.revokeObjectURL" "$SRC_ROOT" 2>/dev/null | grep -cv "\.bak")
if [ -n "$CREATE_FILES" ] && [ "$REVOKE_COUNT" -eq 0 ]; then
  fail "存在 URL.createObjectURL 但项目无 revokeObjectURL(泄漏风险): $(echo "$CREATE_FILES" | head -3 | tr '\n' ' ')"
else
  pass "createObjectURL 使用处有配套 revoke($REVOKE_COUNT 处)"
fi

# 3. 上传进度无节流(onUploadProgress 内 uploadProgress 赋值无时间间隔判断)
echo "-- [3/4] 上传进度节流检查"
for f in $(grep -rln "onUploadProgress" "$SRC_ROOT" 2>/dev/null | grep -v "\.bak"); do
  # 提取 onUploadProgress 块(到下一个 }) 是否含节流特征(Date.now/间隔判断)
  if ! awk '/onUploadProgress/{buf=""; inb=1} inb{buf=buf $0 "\n"; if($0 ~ /^[[:space:]]*}/) inb=0} END{if(buf !~ /Date\.now|>= *150|lastAt|节流/) print FILENAME}' "$f" | grep -q .; then
    pass "$(basename "$f") 进度节流正常"
  else
    fail "onUploadProgress 无节流(高频重渲染): $f"
  fi
done

# 4. nginx 配置检查
echo "-- [4/4] nginx 配置检查: $NGINX_CONF"
if [ -f "$NGINX_CONF" ]; then
  # 4a. gzip
  grep -q "gzip on" "$NGINX_CONF" && pass "gzip 已开启" || fail "nginx 缺 gzip on(慢网络大响应)"
  # 4b. add_header 继承陷阱:含 add_header 的 location/server 块需含安全头
  # 简化检查:每个 add_header 出现点向后 30 行内出现 X-Frame-Options 计数
  TOTAL_ADD=$(grep -c "add_header" "$NGINX_CONF")
  XFRAME=$(grep -c "X-Frame-Options" "$NGINX_CONF")
  if [ "$TOTAL_ADD" -gt 0 ] && [ "$XFRAME" -eq 0 ]; then
    fail "nginx 有 add_header 但无 X-Frame-Options(继承陷阱:新 location 会覆盖 server 级安全头)"
  else
    pass "nginx add_header 与安全头齐备(add_header=$TOTAL_ADD, X-Frame=$XFRAME)"
  fi
else
  fail "nginx 配置不存在: $NGINX_CONF"
fi

echo ""
if [ "$VIOLATIONS" -gt 0 ]; then
  echo "❌ 前端质量检查失败: $VIOLATIONS 项违规"
  exit 1
else
  echo "✅ 前端质量检查通过(0 违规)"
  exit 0
fi
