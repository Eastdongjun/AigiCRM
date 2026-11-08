#!/bin/bash
# 【2026-11-08 双端一致性门禁·第四类】
# 背景: 11-08 全量审计发现"前端有按钮/调用、后端没有"与"后端写端点、双端零调用(休眠)"两类漂移
#       (公共根目录上传必败/逐笔开票8端点休眠/recalculate死接口等), 现有三类门禁(契约钉/单测/接口守卫)
#       都是单点检查, 拦不住跨端关系型缺陷。本脚本补两个方向的全量比对:
#   A) 前端调用的 /api/ 路径 → 后端必须存在对应端点(防"前端有、后端没有")
#   B) 后端写端点(POST/PUT/DELETE/PATCH) → 前端必须有调用方或进白名单(防"休眠端点"复发)
# 口径: ${xxx}(前端模板串)与{xxx}(后端路径变量)都归一为{}; 前端源码文本中字面 ${id} 可被 [^/]* 匹配。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/aigicrm-backend/src/main/java"
PC="$ROOT/aigicrm-ui/src"
MP="$ROOT/aigicrm-miniprogram"

# 白名单: 有意无前端调用方的后端写端点(逐条写明理由, 新增必须带理由)
WHITELIST='
/api/system/task/check-expire|运维直调入口(管理员手动触发到期批, javadoc已注明, 2026-11-08裁决保留)
'

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# ---------- 1) 后端端点清单: "METHOD /api/path" ----------
: > "$TMP/backend.txt"
while IFS= read -r f; do
  base="$(grep -oE '@RequestMapping\("[^"]+"\)' "$f" | head -1 | sed -E 's/@RequestMapping\("(.*)"\)/\1/' || true)"
  # 注: BSD grep ERE 不支持「可选嵌套转义组」, 带路径与裸注解两条分开抓再合并
  {
    grep -oE '@(Get|Post|Put|Delete|Patch)Mapping\("[^"]*"\)' "$f" 2>/dev/null \
      | sed -E 's/@(Get|Post|Put|Delete|Patch)Mapping\("([^"]*)"\)/\1 \2/' || true
    grep -oE '@(Get|Post|Put|Delete|Patch)Mapping\)' "$f" 2>/dev/null \
      | sed -E 's/@(Get|Post|Put|Delete|Patch)Mapping\)/\1 /' || true
  } > "$TMP/seg.txt"
  while IFS=' ' read -r m p; do
    path="${base}${p:-}"
    norm="$(printf '%s' "$path" | sed -E 's/\$\{[^}]*\}/{}/g; s/\{[^}]*\}/{}/g; s/\/+$/\//')"
    printf '%s %s\n' "$m" "$norm"
  done < "$TMP/seg.txt" >> "$TMP/backend.txt"
done < <(find "$BACKEND" -name '*Controller.java' | sort)
# 去重(同端点多注解/多行)
sort -u "$TMP/backend.txt" -o "$TMP/backend.txt" 2>/dev/null || true
awk 'NF==2' "$TMP/backend.txt" > "$TMP/backend.clean" && mv "$TMP/backend.clean" "$TMP/backend.txt"

# ---------- 2) 前端调用清单(单引号/双引号/反引号三通道) ----------
# 排除: 单测夹具/演示数据里的字面 URL 不是真实调用
: > "$TMP/front.txt"
while IFS= read -r f; do cat "$f"; printf '\n'; done \
  < <(find "$PC" \( -name '*.js' -o -name '*.vue' \) -not -name '*.spec.js' -not -name '*.spec.mjs' -not -name '*.test.js' | sort
      find "$MP" -name '*.js' -not -path '*node_modules*' -not -path '*/data/demo*' | sort) \
  > "$TMP/front.txt"

{
  grep -oE "'/api/[^']*'"  "$TMP/front.txt" | sed -E "s/^'//; s/'$//" || true
  grep -oE '"/api/[^"]*"'  "$TMP/front.txt" | sed -E 's/^"//; s/"$//' || true
  grep -oE '`/api/[^`]*`'  "$TMP/front.txt" | sed -E 's/^`//; s/`$//' || true
} | sed -E 's/\$\{[^}]*\}/{}/g; s/\?.*$//' | awk 'NF' | sort -u > "$TMP/frontcalls.txt"

# 后端路径集合(用于方向A精确比对)
awk '{print $2}' "$TMP/backend.txt" | sort -u > "$TMP/paths.txt"

fails=0

# ---------- 方向A: 前端调用 → 后端必须存在 ----------
: > "$TMP/failA.txt"
while IFS= read -r f; do
  if grep -qxF "$f" "$TMP/paths.txt"; then continue; fi
  # 前缀规则: 前端写死基址 '/api/x'或'/api/x/'(字符串拼接风格) 而后端是 '/api/x/{}' 模板
  fns="${f%/}"
  if [ -n "$fns" ] && awk -v f="$fns" 'BEGIN{ok=0} index($0, f "/{}")==1 {ok=1} END{exit !ok}' "$TMP/paths.txt"; then continue; fi
  printf '%s\n' "$f" >> "$TMP/failA.txt"
done < "$TMP/frontcalls.txt"

# ---------- 方向B: 后端写端点 → 前端必须有调用方或白名单 ----------
: > "$TMP/failB.txt"
while IFS=' ' read -r m p; do
  case "$m" in POST|PUT|DELETE|PATCH) ;; *) continue ;; esac
  wl_hit="$(printf '%s' "$WHITELIST" | awk -F'|' -v ep="$p" '$1==ep {print $0; exit}')"
  [ -n "$wl_hit" ] && continue
  re="$(printf '%s' "$p" | sed -e 's/[.[\*^$()+?{|\\]/\\&/g' -e 's/{}/[^\/]*/g')"
  if ! grep -qE "$re" "$TMP/front.txt" 2>/dev/null; then
    printf '%s %s\n' "$m" "$p" >> "$TMP/failB.txt"
  fi
done < "$TMP/backend.txt"

nA="$(awk 'END{print NR}' "$TMP/failA.txt")"
nB="$(awk 'END{print NR}' "$TMP/failB.txt")"
total_calls="$(awk 'END{print NR}' "$TMP/frontcalls.txt")"
total_eps="$(awk 'END{print NR}' "$TMP/backend.txt")"

if [ "$nA" -gt 0 ]; then
  echo "FAIL: 前端调用了后端不存在的路径 ${nA} 处(前端有、后端没有——必败按钮/死调用):"
  sed 's/^/  /' "$TMP/failA.txt"
  fails=1
fi
if [ "$nB" -gt 0 ]; then
  echo "FAIL: 后端写端点双端零调用 ${nB} 处(休眠端点——须删除或加白名单并写明理由):"
  sed 's/^/  /' "$TMP/failB.txt"
  fails=1
fi

if [ "$fails" -eq 0 ]; then
  echo "PASS: 双端端点一致(前端调用 ${total_calls} 条全部有后端端点; 后端端点 ${total_eps} 个, 写端点零休眠; 白名单 $(printf '%s' "$WHITELIST" | grep -c '^/api' || true) 条)"
fi
exit "$fails"
