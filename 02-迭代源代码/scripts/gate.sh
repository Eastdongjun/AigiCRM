#!/bin/bash
# 一键交付门禁(防崩溃第二道防线, 2026-09-24): 把分散的验证收拢成一条命令。
# 用法: bash scripts/gate.sh [--quick]
#   完整档: 后端全量测试 + 前端测试/构建 + 人工文档新鲜度 + 工作区检查
#   --quick: 跳过前端构建(只跑测试), 用于迭代中间快速自检
# 可选: AIGI_MATRIX=1 追加权限矩阵回归(需测试环境账号, 见 verify-perm-matrix.sh)
# 可选: AIGI_MP_WALK=1 追加小程序真机渲染走查(需打开微信开发者工具+自动化端口 9424, 见 scripts/miniprogram/verify-nested-alias-render.mjs)
# 退出码: 0=全过, 1=任一必需项失败。CI/交付前必须跑完整档。
# 【F2 2026-10-21】补齐门禁覆盖:
#   [2b] 由内联 node --check 收敛为 dev-verify.sh miniprogram(含绑定/类名/JSON/app.json 契约);
#   [5b] 新增 dev-verify.sh contracts(70+ 本地契约脚本全集 + F1 全量 secret 扫描);
#   [0]  依赖 preflight fail-closed——缺 rg/python3 等直接判 FAIL, 不让契约假红污染结果。
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
QUICK="${1:-}"
PASS=0; FAIL=0
ok()  { echo "✅ $1"; PASS=$((PASS+1)); }
bad() { echo "❌ $1"; FAIL=$((FAIL+1)); }

echo "════════ AIGICRM 一键门禁 $(date '+%m-%d %H:%M') ════════"

# [0] 依赖 preflight(缺工具=门禁无法如实执行 → 直接 FAIL, fail-closed; 工具用途见各检查段)
echo "--- [0] 依赖 preflight ---"
MISSING_TOOLS=""
for tool in git node npm npx mvn python3 rg; do
  command -v "$tool" >/dev/null 2>&1 || MISSING_TOOLS="$MISSING_TOOLS $tool"
done
if [ -n "$MISSING_TOOLS" ]; then
  bad "依赖缺失(fail-closed):$MISSING_TOOLS —— mvn=后端测试; node/npm/npx=前端; rg/python3=contracts 契约; git=文件清单"
else
  ok "依赖 preflight(git/node/npm/npx/mvn/python3/rg)"
fi

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

# [2b] 小程序检查【体检 P2 2026-10-12 引入; F2 2026-10-21 收敛】——三端门禁此前缺小程序一端,
#      且原实现只有 node --check 语法检查。现收敛为 dev-verify.sh miniprogram 单一出处:
#      语法 + JSON 解析 + 空视图检查 + 视图绑定契约 + 静态类名契约 + app.json 页面完整性。
echo "--- [2b] 小程序检查(dev-verify.sh miniprogram) ---"
if bash "$SCRIPT_DIR/dev-verify.sh" miniprogram > /tmp/gate-mp.log 2>&1; then
  ok "小程序检查(语法/JSON/绑定/类名/app.json)"
else
  bad "小程序检查失败:"; tail -15 /tmp/gate-mp.log
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

# [5] 业务不变量契约(2026-10-09 三案复盘建立: INV-10 指引可达性——拒绝性提示指引的
#     功能必须真实存在且可达;"停用"断头路事故的自动化防线;清单见 docs/business/invariants.md)
echo "--- [5] 业务不变量契约 ---"
if bash "$SCRIPT_DIR/check-invariants-contract.sh" > /tmp/gate-inv.log 2>&1; then
  ok "业务不变量契约(INV-10)"
else
  bad "业务不变量契约违例(INV-10 指引可达性——断头路):"; grep "FAIL" /tmp/gate-inv.log | head -5
fi

# [5b] 本地契约全集【F2 2026-10-21】——70+ check-* 契约脚本与 demo 数据测试原先只在
#      dev-verify.sh contracts/changed 里存在, 一键门禁从不执行(F2), 移动端/权限契约回归
#      不阻断交付。现收敛为 dev-verify.sh contracts 单一清单(含 F1 全量 secret 扫描)。
#      注意: 若因你的功能改动而红, 按 dev-verify.sh 内"契约更新责任表"同提交更新对应契约。
echo "--- [5b] 本地契约全集(dev-verify.sh contracts, 含 secret 扫描) ---"
if bash "$SCRIPT_DIR/dev-verify.sh" contracts > /tmp/gate-contracts.log 2>&1; then
  ok "本地契约全集(contracts)"
else
  rc=$?
  if [ "$rc" -eq 2 ]; then
    bad "契约依赖缺失(BLOCKED, fail-closed):"; tail -5 /tmp/gate-contracts.log
  else
    bad "本地契约违例:"; grep -E "^FAIL|FAIL:" /tmp/gate-contracts.log | head -10; echo "(日志尾部:)"; tail -3 /tmp/gate-contracts.log
  fi
fi

# [6] 工作区(交付级必须干净;本项只告警不阻断——避免迭代中被卡)
DIRTY=$(cd "$REPO" 2>/dev/null && git status --porcelain | wc -l | tr -d ' ')
[ "${DIRTY:-0}" -eq 0 ] && ok "工作区干净" || echo "⚠️  工作区有 ${DIRTY} 项未提交(交付前必须收口)"

# [7] 可选: 权限矩阵回归(显式开启才跑, 需测试环境)
if [ "${AIGI_MATRIX:-0}" = "1" ]; then
  echo "--- [7] 权限矩阵回归 ---"
  if AIGI_TEST_USER=admin AIGI_TEST_PASS="${AIGI_TEST_PASS:?开启矩阵需提供 AIGI_TEST_PASS}" \
     bash "$SCRIPT_DIR/verify-perm-matrix.sh" > /tmp/gate-matrix.log 2>&1; then
    ok "权限矩阵回归"
  else
    bad "权限矩阵回归失败:"; grep "FAIL" /tmp/gate-matrix.log | head -5
  fi
fi

# [8] 可选: 小程序真机渲染走查(显式开启才跑; 需微信开发者工具已登录+自动化端口 9424)
# 【2026-11-07 守则D7/D8】复盘条目1(MP 43页零单测)门禁化: 定向渲染回归 + 43页全量走查双保险。
if [ "${AIGI_MP_WALK:-0}" = "1" ]; then
  echo "--- [8] 小程序渲染走查 ---"
  if (echo > /dev/tcp/127.0.0.1/9424) >/dev/null 2>&1; then
    if node "$SCRIPT_DIR/miniprogram/verify-nested-alias-render.mjs" > /tmp/gate-mpwalk.log 2>&1; then
      ok "小程序定向渲染走查(8页/零报错)"
    else
      bad "小程序定向渲染走查失败:"; grep -E "FAIL|RENDER_FAIL|console\.error" /tmp/gate-mpwalk.log | head -6
    fi
    if node "$SCRIPT_DIR/miniprogram/walk-all-pages.mjs" > /tmp/gate-mpwalk-all.log 2>&1; then
      WALK_OPEN=$(grep -oE '打开 [0-9]+/[0-9]+' /tmp/gate-mpwalk-all.log | head -1)
      ok "小程序全量走查(43页) ${WALK_OPEN:-}"
    else
      bad "小程序全量走查失败:"; grep -E "^FAIL|WALK_RED" /tmp/gate-mpwalk-all.log | head -8
    fi
  else
    bad "AIGI_MP_WALK=1 但微信开发者工具自动化端口 9424 未开——先开 DevTools 并确认已登录(显式开启却缺环境=按失败处理, fail-closed)"
  fi
fi

# [9] 可选: PC 类型检查基线对账(vue-tsc, 存量冻结新增即红; 全量跑约1-3分钟)
# 【2026-11-07 守则D8】复盘条目1(PC 零类型检查)第一步落地; 全面类型化=独立批(存量561错)。
if [ "${AIGI_TSC:-0}" = "1" ]; then
  echo "--- [9] PC 类型检查(vue-tsc 基线) ---"
  if bash "$SCRIPT_DIR/check-ui-typecheck.sh" > /tmp/gate-tsc.log 2>&1; then
    ok "PC 类型检查基线对账"
  else
    bad "PC 类型检查失败:"; grep -E "FAIL|error TS" /tmp/gate-tsc.log | head -6
  fi
fi

echo "════════ 结果: PASS=$PASS FAIL=$FAIL ════════"
[ "$FAIL" -eq 0 ] && echo "✅ 门禁通过" || { echo "❌ 门禁未通过——修复后再交付"; exit 1; }
