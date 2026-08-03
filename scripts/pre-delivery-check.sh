#!/bin/bash
# 交付前阶段衔接检查: 知识资产完整性 + 需求确认 + 冒烟 + 交付门禁
# 用法: ./pre-delivery-check.sh [功能包目录]
# 功能: 任何阶段衔接缺失(文档没更新/需求没确认/测试没过) = FAIL, 阻止交付
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/deploy-lib.sh"
set +e  # deploy-lib 开启 errexit, 本脚本需要容错(grep 无匹配等场景)

FEATURE_DIR="${1:-}"
PASS=0; FAIL=0
ok()  { echo "✅ $1"; PASS=$((PASS+1)); }
bad() { echo "❌ $1"; FAIL=$((FAIL+1)); }

REPO="/Users/donglai/东来/01-Projects/01-aigicrm/04-源码"
echo "════════ YCMN 交付前阶段衔接检查 $(date '+%Y-%m-%d %H:%M:%S') ════════"

# [1] 知识资产文档存在性(自动生成的须可重新生成)
echo "--- [1] 知识资产完整性 ---"
for doc in docs/database/data-dictionary.md docs/permission/permission-dict.md docs/permission/design.md docs/api/api-list.md docs/deployment/config-matrix.md docs/README.md; do
  [ -f "$REPO/$doc" ] && ok "$doc" || bad "$doc 缺失 —— 交付必须同步知识资产"
done

# [2] 功能包阶段衔接(若指定)
if [ -n "$FEATURE_DIR" ]; then
  echo "--- [2] 功能包衔接: $FEATURE_DIR ---"
  REQ="$FEATURE_DIR/01-requirements.md"
  if [ -f "$REQ" ]; then
    CONFIRMED=$(grep -E "用户确认" "$REQ" | grep -oE "是|否" | head -1)
    [ "$CONFIRMED" = "是" ] && ok "需求已确认(用户确认=是)" || bad "需求未确认 —— 先与用户确认需求再交付"
    grep -q "<!-- FILL" "$REQ" && bad "需求文档仍有占位符" || ok "需求文档无占位符"
  else
    bad "缺少 01-requirements.md"
  fi
  for f in 00-intake.md 02-user-flow.md 03-ui-spec.md 04-technical-plan.md 05-implementation-log.md 06-test-matrix.md 07-delivery-report.md; do
    [ -f "$FEATURE_DIR/$f" ] || bad "缺少 $f"
  done
  ok "功能包文档齐全"
  # 独立审查记录(代码审查+安全审查)
  REVIEW_DIR="${FEATURE_DIR%/}/evidence"
  if [ -f "$REVIEW_DIR/review.md" ] || grep -q "独立代码审查" "$FEATURE_DIR/07-delivery-report.md" 2>/dev/null; then
    ok "独立代码审查记录存在"
  else
    bad "缺独立审查记录 —— 交付前用 code-reviewer/security-reviewer 审查并存档 evidence/review.md"
  fi
  DELIVERY="$FEATURE_DIR/07-delivery-report.md"
  grep -q "已知限制与残余风险" "$DELIVERY" 2>/dev/null && ok "交付报告含残余风险披露" || bad "交付报告缺残余风险披露"
else
  echo "--- [2] 未指定功能包目录, 跳过功能包检查(可传参: pre-delivery-check.sh <功能包目录>) ---"
fi

# [3] 冒烟测试(核心链路)
echo "--- [3] 冒烟测试 ---"
if "$SCRIPT_DIR/smoke-test.sh" prod > /tmp/smoke-out.txt 2>&1; then
  ok "生产冒烟测试通过"
else
  bad "生产冒烟测试失败"; tail -5 /tmp/smoke-out.txt | sed 's/^/    /'
fi

# [4] 交付门禁(环境/配置/备份)
echo "--- [4] 交付门禁 ---"
if "$SCRIPT_DIR/verify-delivery.sh" > /tmp/verify-out.txt 2>&1; then
  ok "交付门禁通过"
else
  bad "交付门禁未通过"; tail -5 /tmp/verify-out.txt | sed 's/^/    /'
fi

echo "--- [5] 前端质量检查(FEAT-20260804-QA-GATES) ---"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if bash "$SCRIPT_DIR/check-frontend-quality.sh" >/tmp/qagate.log 2>&1; then
  echo "  ✅ 前端反模式扫描通过"
else
  echo "  ❌ 前端反模式扫描失败:"; sed 's/^/     /' /tmp/qagate.log | head -10
  FAIL=$((FAIL+1))
fi
rm -f /tmp/qagate.log

echo "══════════════════════════════════════════════════"
echo "结果: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -gt 0 ] && { echo "❌ 阶段衔接检查未通过 —— 补齐 FAIL 项后再交付"; exit 1; } || { echo "✅ 阶段衔接检查全部通过 —— 可以交付"; exit 0; }
