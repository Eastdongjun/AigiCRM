#!/bin/bash
# 【底座评估 2026-11-11】权限码消费侧契约(三端词表一致性的机器强制):
#   后端权威集 = @PreAuthorize 注解码 ∪ 命令式 SecurityUtils.hasAuthority 码;
#   PC 消费集  = hasPermission('x')(aigicrm-ui/src, 排除 *.spec.js 夹具伪码);
#   MP 消费集  = hasPermission('x')(aigicrm-miniprogram);
# 断言: 前端码 - 后端码 - 白名单 = 0 —— 按钮查一个后端永不检查的码 = 必败/必过皆误
#   (PC 财务第三通道、文件动作码 5 处漂移皆此类, 靠人肉同步的终结者)。
# 反向(后端独有、前端未引用)不判红: 管理端端点码(导入导出/配置)可由路由层控制,
#   仅输出 INFO 清单供人工巡查; 其增长趋势是"码堆着没人消费"的信号。
# 白名单: 每项必须带理由。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_SRC="$ROOT/aigicrm-backend/src/main/java"
UI_SRC="$ROOT/aigicrm-ui/src"
MP_SRC="$ROOT/aigicrm-miniprogram"
fail() { echo "FAIL: $1" >&2; exit 1; }

# 后端权威集(注解单引号 + 命令式双引号, 与 check-permission-binding-coverage.sh 同口径)
BACKEND=$( { grep -rhE "has(Any)?Authority\(" "$BACKEND_SRC" --include="*.java" \
  | grep -vE "^[[:space:]]*(\*|//|/\*)" \
  | grep -oE "'[a-z][a-z0-9:_-]+'" || true; } | tr -d "'" | sort -u
  { grep -rhE 'SecurityUtils\.hasAuthority\("[a-z][a-z0-9:_-]+"\)' "$BACKEND_SRC" --include="*.java" \
  | grep -vE "^[[:space:]]*(\*|//|/\*)" \
  | grep -oE '"[a-z][a-z0-9:_-]+"' || true; } | tr -d '"' | sort -u )
BACKEND=$(printf '%s\n' "$BACKEND" | grep -vE '^$' | sort -u)
[ -n "$BACKEND" ] || fail "后端权威码集为空(扫描器失灵?)"

# 前端/MP 消费集(排除 spec 夹具)
FRONT=$( { grep -rhoE "hasPermission\(['\"][a-z][a-z0-9:_-]+['\"]\)" "$UI_SRC" --include="*.vue" --include="*.js" \
  --exclude="*.spec.js" 2>/dev/null | grep -oE "['\"][a-z][a-z0-9:_-]+['\"]" || true; } | tr -d "'\"" | sort -u
  { grep -rhoE "hasPermission\(['\"][a-z][a-z0-9:_-]+['\"]\)" "$MP_SRC" --include="*.js" \
  --exclude="*.spec.js" 2>/dev/null | grep -oE "['\"][a-z][a-z0-9:_-]+['\"]" || true; } | tr -d "'\"" | sort -u )
FRONT=$(printf '%s\n' "$FRONT" | grep -vE '^$' | sort -u)
[ -n "$FRONT" ] || fail "前端消费码集为空(扫描器失灵?)"

# 白名单(码:理由)——只允许"刻意的纯前端门码"一类
WHITELIST="incentive:rank:export:用户裁决的独立导出门码(2026-11-05 令, V2026.11.05.8 注册)——导出为前端 CSV(列表数据受列表接口守卫), 无后端端点可挂码
incentive:person-rank:export:同上(2026-09-23 令, V2026.09.23.7 注册), 个人排行前端 CSV 导出门码"

miss=0
while IFS= read -r code; do
  [ -n "$code" ] || continue
  if printf '%s\n' "$BACKEND" | grep -qxF "$code"; then continue; fi
  if grep -q "^$code:" <<<"$WHITELIST"; then
    echo "  [白名单] $code: $(grep "^$code:" <<<"$WHITELIST" | cut -d: -f2-)"
    continue
  fi
  echo "  [前端独有] $code —— 按钮查了后端永不检查的码(漂移或废码)"
  miss=$((miss+1))
done <<<"$FRONT"

[ "$miss" -eq 0 ] || fail "$miss 个前端码在后端注解/命令式/白名单中均不存在(三端词表漂移)"

# 反向 INFO(不判红)
ORPHAN=$(comm -23 <(printf '%s\n' "$BACKEND") <(printf '%s\n' "$FRONT") | grep -c . || true)
echo "PASS: 消费侧词表一致(PC+MP $(printf '%s\n' "$FRONT" | grep -c .) 码全部可追溯; 后端独有未引用 $ORPHAN 码为管理端路由层语义, INFO 巡查)"
