#!/bin/bash
# 【T1 2026-11-11 审计整改; N1 修正同日】权限码绑定覆盖率静态契约:
# @PreAuthorize 中出现的每个权限码, 必须在**版本序 ≥ 种子**的迁移里存在按锚点
# (permission= 或 path=)的角色绑定语句(sys_role_menu INSERT), 否则空库重放后该码
# 零绑定(相关接口全员 403)。
# 背景: V2026.09.11.3 的回绑排在唯一种子 V2026.09.11.6 之前, 空库重放执行到它时
#   sys_menu 尚空 → JOIN 恒 0 行; 种子虽建 5 个 file 码菜单行但未绑定。
#   【N1】旧判据只看"绑定语句文本在全链存在"不看位置——实证: 去掉 V2026.11.11.2 后
#   旧判据对 5 个 file 码仍 PASS(.09.11.3 的文本里有这些码字符串), 属假安全网。
#   种子之前(V1..V2026.09.11.5)的绑定语句在空库上因菜单行/角色行尚不存在而恒空转,
#   一律不计入; 种子本身计入(其绑定按正确顺序生效)。
# 白名单: 每项必须带理由(死码待裁决/绑定在界面写入/锚点特殊), 无理由白名单禁止。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_SRC="$ROOT/aigicrm-backend/src/main/java"
MIG_DIR="$ROOT/aigicrm-backend/src/main/resources/db/migration"
fail() { echo "FAIL: $1" >&2; exit 1; }

# ① 提取 @PreAuthorize 权限码全集 + 命令式 SecurityUtils.hasAuthority 码全集。
#    【底座评估 2026-11-11 补】命令式调用全用双引号(SecurityUtils.hasAuthority("contact:view")),
#    旧正则只抓单引号 → 14 处命令式码天然漏网(评估报告实锤)。排除注释行——
#    PermissionHintService 的 'x' 等示例伪码; 管道各段在 pipefail 下遇无匹配即中断, 用 || true 容错。
CODES=$( { grep -rhE "has(Any)?Authority\(" "$BACKEND_SRC" --include="*.java" \
  | grep -vE "^[[:space:]]*(\*|//|/\*)" \
  | grep -oE "'[a-z][a-z0-9:_-]+'" || true; } | tr -d "'" | sort -u || true)
IMPERATIVE=$( { grep -rhE 'SecurityUtils\.hasAuthority\("[a-z][a-z0-9:_-]+"\)' "$BACKEND_SRC" --include="*.java" \
  | grep -vE "^[[:space:]]*(\*|//|/\*)" \
  | grep -oE '"[a-z][a-z0-9:_-]+"' || true; } | tr -d '"' | sort -u || true)
CODES=$(printf '%s\n%s\n' "$CODES" "$IMPERATIVE" | grep -vE '^$' | sort -u)
[ -n "$CODES" ] || fail "未提取到任何权限码(注解+命令式, 扫描器失灵?)"

# ② 【N1】有效迁移集 = 文件名版本序 ≥ 种子 V2026.09.11.6(唯一菜单字典+角色种子)。
#    字典序在此成立: "V1x__/V2x__数字版" 与 "V2026.*" 第二字符即分出先后, 日期版内部同前缀可比。
SEED_PIVOT="V2026.09.11.6"
# 版本语义过滤(Flyway 数字段比较), 不能纯字典序: "V22__">'V2026..'(第三位'_'>'0')会把
# V20~V29 数字版误入有效集——V26 的绑定文本会让 file 码在 .11.2 缺席时仍 PASS(N1 同型假网)。
# 规则: 主版本段 > 2025(排除数字版 V1~V33) 且 同为 2026.x 内字典序 ≥ 种子(x.y.z 同构, 字典序安全)。
EFF_PATHS=""
while IFS= read -r rel; do EFF_PATHS="$EFF_PATHS$MIG_DIR/$rel
"; done < <(find "$MIG_DIR" -maxdepth 1 -name '*.sql' -exec basename {} \; | awk '
  { v = $0; sub(/^V/, "", v); split(v, seg, "."); if (seg[1] + 0 > 2025 && $0 >= "V2026.09.11.6") print }')
EFF_CNT=$(printf '%s' "$EFF_PATHS" | grep -c . )
[ "$EFF_CNT" -gt 0 ] || fail "种子($SEED_PIVOT)之后无任何迁移文件(迁移目录残缺?)"
BOUND_TEXT=$(grep -H -A 10 "INSERT INTO \`*sys_role_menu\`*" $EFF_PATHS 2>/dev/null || true)

# ③ 白名单(码:理由)——只允许"绑定在别处人工核实/死码待裁决"两类
WHITELIST="ranking:customer:view:死码(双端零消费, 2026-11-11 审计挂账待裁决), 不兜底绑定
incentive:read-ranking:view:V2026.09.13.4 按 path 锚(/system/incentive-read-ranking)绑 admin, 人工核实存在(契约 path 形态识别待增强)"

miss=0
while IFS= read -r code; do
  [ -n "$code" ] || continue
  if grep -q "'$code'" <<<"$BOUND_TEXT"; then continue; fi
  # path 锚二次机会: 有效迁移集中该码的菜单行定义了 path, 且存在按该 path 的绑定
  # (必须 -h 无文件名前缀: -H 输出的 "/tmp/.../migration/" 目录前缀会被下方 -oE 当 path
  #  抓出, 而 BOUND_TEXT 每行都带该前缀 → 恒命中 → 全码放行的假绿, 2026-11-11 自证实验实抓)
  path=$( { grep -h -A 3 "permission.*=.*$code" $EFF_PATHS 2>/dev/null \
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

[ "$miss" -eq 0 ] || fail "$miss 个权限码在 @PreAuthorize 使用但种子($SEED_PIVOT)之后无锚点绑定语句(空库重放将全员403)"
echo "PASS: 权限码绑定覆盖(锚点=permission/path 双形态, 有效集=${SEED_PIVOT}起${EFF_CNT}文件, 共 $(echo "$CODES" | grep -c . ) 码)"
