#!/bin/bash
# 【T1 2026-11-11 审计整改】权限码绑定覆盖率静态契约:
# @PreAuthorize 中出现的每个权限码, 必须在迁移链里存在按锚点(permission= 或 path=)的
# 角色绑定语句(sys_role_menu INSERT), 否则空库重放后该码零绑定(相关接口全员 403)。
# 背景: V2026.09.11.3 的回绑排在种子之前, 空库重放落空 → file 5 码零绑定;
#   本契约首跑又抓到 finance:manage/customer:finance:edit 同样裸奔(注册迁移只有菜单行)。
# 白名单: 每项必须带理由(死码待裁决/绑定在界面写入/锚点特殊), 无理由白名单禁止。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_SRC="$ROOT/aigicrm-backend/src/main/java"
MIG_DIR="$ROOT/aigicrm-backend/src/main/resources/db/migration"
fail() { echo "FAIL: $1" >&2; exit 1; }

# ① 提取 @PreAuthorize 权限码全集(排除注释行——PermissionHintService 的 'x' 等示例伪码;
#    管道各段在 pipefail 下遇无匹配即中断, 用 || true 容错后统一判空)
CODES=$( { grep -rhE "has(Any)?Authority\(" "$BACKEND_SRC" --include="*.java" \
  | grep -vE "^[[:space:]]*(\*|//|/\*)" \
  | grep -oE "'[a-z][a-z0-9:_-]+'" || true; } | tr -d "'" | sort -u || true)
[ -n "$CODES" ] || fail "未提取到任何 @PreAuthorize 权限码(扫描器失灵?)"

# ② 全链迁移中按锚点(sys_menu.permission 或 sys_menu.path)的绑定语句文本集
BOUND_TEXT=$(grep -rn -A 10 "INSERT INTO \`\?sys_role_menu\`\?" "$MIG_DIR" 2>/dev/null || true)

# ③ 白名单(码:理由)——只允许"绑定在别处人工核实/死码待裁决"两类
WHITELIST="ranking:customer:view:死码(双端零消费, 2026-11-11 审计挂账待裁决), 不兜底绑定
incentive:read-ranking:view:V2026.09.13.4 按 path 锚(/system/incentive-read-ranking)绑 admin, 人工核实存在(契约 path 形态识别待增强)"

miss=0
while IFS= read -r code; do
  [ -n "$code" ] || continue
  if grep -q "'$code'" <<<"$BOUND_TEXT"; then continue; fi
  # path 锚二次机会: 该码的菜单行迁移定义了 path, 且存在按该 path 的绑定
  path=$( { grep -rh -A 3 "permission.*=.*$code" "$MIG_DIR" 2>/dev/null \
    | grep -oE "/[a-z0-9][a-z0-9/-]*" || true; } | grep "^/" | head -1 || true)
  if [ -n "$path" ] && grep -q "$path" <<<"$BOUND_TEXT"; then continue; fi
  # 白名单
  if grep -q "^$code:" <<<"$WHITELIST"; then
    echo "  [白名单] $code: $(grep "^$code:" <<<"$WHITELIST" | cut -d: -f2-)"
    continue
  fi
  echo "  [未兜底] $code"
  miss=$((miss+1))
done <<<"$CODES"

[ "$miss" -eq 0 ] || fail "$miss 个权限码在 @PreAuthorize 使用但全链无锚点绑定语句(空库重放将全员403)"
echo "PASS: 权限码绑定覆盖(锚点=permission/path 双形态, 共 $(echo "$CODES" | grep -c . ) 码)"
