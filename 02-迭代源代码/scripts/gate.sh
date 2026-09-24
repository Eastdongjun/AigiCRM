#!/bin/bash
# 一键交付门禁(防崩溃第二道防线, 2026-09-24): 把分散的验证收拢成一条命令。
# 用法: bash scripts/gate.sh [--quick]
#   完整档: 后端全量测试 + 前端测试/构建 + 人工文档新鲜度 + 工作区检查
#   --quick: 跳过前端构建(只跑测试), 用于迭代中间快速自检
# 可选: AIGI_MATRIX=1 追加权限矩阵回归(需测试环境账号, 见 verify-perm-matrix.sh)
# 退出码: 0=全过, 1=任一必需项失败。CI/交付前必须跑完整档。
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
QUICK="${1:-}"
PASS=0; FAIL=0
ok()  { echo "✅ $1"; PASS=$((PASS+1)); }
bad() { echo "❌ $1"; FAIL=$((FAIL+1)); }

echo "════════ AIGICRM 一键门禁 $(date '+%m-%d %H:%M') ════════"

# [1] 后端全量(含权限矩阵架构钉+稳定性不变量钉)
echo "--- [1] 后端全量测试 ---"
if (cd "$SCRIPT_DIR/../aigicrm-backend" && mvn -q test > /tmp/gate-backend.log 2>&1); then
  ok "后端全量测试"
else
  bad "后端测试失败(尾部日志):"; tail -20 /tmp/gate-backend.log
fi

# [2] 前端测试
echo "--- [2] 前端测试 ---"
if (cd "$SCRIPT_DIR/../aigicrm-ui" && npx vitest run > /tmp/gate-fe.log 2>&1); then
  ok "前端测试"
else
  bad "前端测试失败:"; grep -E "FAIL|Tests.*[1-9].*failed" /tmp/gate-fe.log | head -5
fi

# [3] 前端构建(quick 跳过)
if [ "$QUICK" != "--quick" ]; then
  echo "--- [3] 前端构建 ---"
  if (cd "$SCRIPT_DIR/../aigicrm-ui" && npm run build > /tmp/gate-build.log 2>&1); then
    ok "前端构建"
  else
    bad "前端构建失败:"; tail -5 /tmp/gate-build.log
  fi
fi

# [4] 人工文档新鲜度(防崩溃关键: 上线周零回流的根因是人工文档无新鲜度检查)
#     manifest/business/REQUIREMENTS 的最后提交 不得落后 三端源码最后提交 3 天以上
echo "--- [4] 人工文档新鲜度 ---"
REPO="$(cd "$SCRIPT_DIR/../.." && git rev-parse --show-toplevel 2>/dev/null)"
if [ -n "$REPO" ]; then
  doc_last=$(cd "$REPO" && git log -1 --format=%ct -- "02-迭代源代码/docs/unified-feature-manifest.md" "02-迭代源代码/docs/business" "02-迭代源代码/docs/REQUIREMENTS.md")
  src_last=$(cd "$REPO" && git log -1 --format=%ct -- "02-迭代源代码/aigicrm-backend/src" "02-迭代源代码/aigicrm-ui/src" "02-迭代源代码/aigicrm-miniprogram/pages")
  if [ -z "${src_last:-}" ]; then
    ok "无源码提交记录(新仓库?),跳过"
  elif [ -z "${doc_last:-}" ]; then
    bad "人工文档(manifest/business/REQUIREMENTS)从未提交——规则未落档"
  else
    gap=$(( (src_last - doc_last) / 86400 ))
    if [ "$gap" -gt 3 ]; then
      bad "人工文档落后源码最新提交 ${gap} 天——功能改了规则没落档,先补 docs/ 再交付"
    else
      ok "文档新鲜度(落后 ${gap} 天,阈值 3)"
    fi
  fi
else
  bad "不在 git 仓库内,无法检查新鲜度"
fi

# [5] 工作区(交付级必须干净;本项只告警不阻断——避免迭代中被卡)
DIRTY=$(cd "$REPO" 2>/dev/null && git status --porcelain | wc -l | tr -d ' ')
[ "${DIRTY:-0}" -eq 0 ] && ok "工作区干净" || echo "⚠️  工作区有 ${DIRTY} 项未提交(交付前必须收口)"

# [6] 可选: 权限矩阵回归(显式开启才跑, 需测试环境)
if [ "${AIGI_MATRIX:-0}" = "1" ]; then
  echo "--- [6] 权限矩阵回归 ---"
  if AIGI_TEST_USER=admin AIGI_TEST_PASS="${AIGI_TEST_PASS:?开启矩阵需提供 AIGI_TEST_PASS}" \
     bash "$SCRIPT_DIR/verify-perm-matrix.sh" > /tmp/gate-matrix.log 2>&1; then
    ok "权限矩阵回归"
  else
    bad "权限矩阵回归失败:"; grep "FAIL" /tmp/gate-matrix.log | head -5
  fi
fi

echo "════════ 结果: PASS=$PASS FAIL=$FAIL ════════"
[ "$FAIL" -eq 0 ] && echo "✅ 门禁通过" || { echo "❌ 门禁未通过——修复后再交付"; exit 1; }
