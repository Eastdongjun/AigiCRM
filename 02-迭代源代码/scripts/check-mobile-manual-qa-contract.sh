#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC="$ROOT/docs/mobile-manual-qa-checklist.md"
RECONCILIATION_CHECK="$ROOT/scripts/check-mobile-reconciliation-evidence.py"
fail() { echo "FAIL: $*" >&2; exit 1; }
TMP_FILES=()
cleanup() {
  if [ "${#TMP_FILES[@]}" -gt 0 ]; then
    rm -f "${TMP_FILES[@]}"
  fi
}
trap cleanup EXIT

[ -f "$DOC" ] || fail 'mobile manual QA checklist is missing'
grep -Fq '/Users/dongjun/东来/项目/02-已上线/01-AIGICRM/02-迭代源代码/aigicrm-miniprogram' "$DOC" || fail 'QA checklist must name the real mini-program root'
grep -Fq 'app.json' "$DOC" || fail 'QA checklist must require app.json at the imported root'
grep -Fq 'project.config.json' "$DOC" || fail 'QA checklist must require project.config.json at the imported root'
grep -Fq 'scripts/check-wechat-project-root.sh' "$DOC" || fail 'QA checklist must run the project-root preflight'
grep -Fq '使用测试账号' "$DOC" || fail 'QA checklist must require a real test account'
grep -Fq 'docs/miniprogram-release-runbook.md' "$DOC" || fail 'QA checklist must link preview/release runbook'
grep -Fq 'MINIPROGRAM_PRIVATE_KEY' "$DOC" || fail 'QA checklist must mention CI preview secret handling'
grep -Fq '02-迭代源代码/scripts/dev-verify.sh changed' "$DOC" || fail 'QA checklist must run dev-verify before page automation'
grep -Fq 'check-mobile-pc-api-parity-contract.sh' "$DOC" || fail 'QA checklist must mention the mobile/PC API parity contract'
grep -Fq '确认 PC-only API 没有进入小程序 API 层' "$DOC" || fail 'QA checklist must document PC-only API absence contract'
grep -Fq 'npm run verify:mobile-parity' "$DOC" || fail 'QA checklist must mention the full mobile parity DevTools automator precheck'
grep -Fq '自动断言应用中心不会暴露客户导入/导出、系统管理、配置、ACL、学分录入、批量操作等 PC-only 入口' "$DOC" || fail 'QA checklist must state mobile parity automator covers PC-only entry hiding'
grep -Fq 'npm run verify:incentive-attachments' "$DOC" || fail 'QA checklist must mention incentive attachment DevTools automator precheck'
grep -Fq '自动化不能替代真机选择文件、提交记录、真实下载保存和同一后端数据对账' "$DOC" || fail 'QA checklist must keep real device and same-data validation boundary'
RUNBOOK="$ROOT/docs/miniprogram-release-runbook.md"
grep -Fq 'cli auto' "$RUNBOOK" || fail 'release runbook must document DevTools automator startup'
grep -Fq '02-迭代源代码/scripts/dev-verify.sh changed' "$RUNBOOK" || fail 'release runbook must run dev-verify before mobile parity automator'
grep -Fq 'check-mobile-pc-api-parity-contract.sh' "$RUNBOOK" || fail 'release runbook must mention the mobile/PC API parity contract'
grep -Fq 'PC-only API 没有进入小程序 API 层' "$RUNBOOK" || fail 'release runbook must document PC-only API absence contract'
grep -Fq 'WECHAT_AUTOMATOR_WS=ws://127.0.0.1:9421 npm run verify:mobile-parity' "$RUNBOOK" || fail 'release runbook must document full mobile parity automator command'
grep -Fq '应用中心对客户导入/导出、系统管理、配置、ACL、学分录入、批量操作等 PC-only 入口的隐藏边界' "$RUNBOOK" || fail 'release runbook must state mobile parity automator covers PC-only entry hiding'
grep -Fq 'WECHAT_AUTOMATOR_WS=ws://127.0.0.1:9421 npm run verify:incentive-attachments' "$RUNBOOK" || fail 'release runbook must document incentive attachment automator command'
grep -Fq '不能替代真机选择文件、真实提交记录、真实下载保存、真实授权/拒绝账号差异和 PC 同数据对账' "$RUNBOOK" || fail 'release runbook must keep automator real-device and same-data boundary'
grep -Fq 'check-mobile-reconciliation-evidence.py' "$RUNBOOK" || fail 'release runbook must document the reconciliation evidence validator'
grep -Fq '只检查 Markdown 表格结构、必填项、结论状态和敏感字段痕迹' "$RUNBOOK" || fail 'release runbook must describe reconciliation validator scope'
grep -Fq '上传前必须先跑本地门禁' "$RUNBOOK" || fail 'release runbook must require local gates before upload'
grep -Fq '02-迭代源代码/scripts/check-mobile-manual-qa-contract.sh' "$RUNBOOK" || fail 'release runbook upload gates must include the manual QA contract'
grep -Fq 'bash 02-迭代源代码/scripts/check-wechat-project-root.sh' "$RUNBOOK" || fail 'release runbook upload gates must include WeChat project root preflight'
grep -Fq 'bash 02-迭代源代码/scripts/check-miniprogram-local-profile.sh' "$RUNBOOK" || fail 'release runbook upload gates must include mini-program environment profile preflight'
grep -Fq 'python3 02-迭代源代码/scripts/check-miniprogram-permission-contract.py' "$RUNBOOK" || fail 'release runbook upload gates must include mini-program permission contract'
grep -Fq 'python3 02-迭代源代码/scripts/check-mobile-reconciliation-evidence.py /path/to/sanitized-reconciliation.md' "$RUNBOOK" || fail 'release runbook upload gates must include reconciliation evidence validation'
PACKAGE="$ROOT/scripts/miniprogram/package.json"
grep -Fq '"verify:mobile-parity"' "$PACKAGE" || fail 'miniprogram package must expose the full mobile parity automator command'
for script in verify:mobile-shell verify:customer-center verify:customer-forms verify:incentive-attachments verify:file-center verify:approval-center verify:sea-center verify:statistics-center verify:indicator-overview verify:ranking-center verify:reminder-center verify:profile-center verify:auth-center verify:feedback-notification; do
  grep -Fq "$script" "$PACKAGE" || fail "full mobile parity automator package is missing $script"
done
for item in L1 L2 L3 L4 L5 G1 G2 G3 B1 B2 B3 B4 B5 B6 B7 B8 B9 B10 B11 B12 B13 B14 R1 R2 R3 R4 R5; do
  grep -Fq "| $item |" "$DOC" || fail "QA checklist is missing $item"
done
grep -Fq '激励时间轴附件' "$DOC" || fail 'QA checklist must cover incentive timeline attachments'
grep -Fq '安装前/中/后/跟踪文件' "$DOC" || fail 'QA checklist must cover the four incentive file slots'
grep -Fq '积分个人/部门排行' "$DOC" || fail 'QA checklist must cover score personal/dept ranking'
grep -Fq 'canClaim/canEdit/canDelete' "$DOC" || fail 'QA checklist must cover backend public-sea row grants'
grep -Fq '客户、联系人、财务记录删除' "$DOC" || fail 'QA checklist must cover granted customer/contact/finance deletion'
grep -Fq '客户删除、联系人删除、财务删除、文件删除、公海删除、激励删除' "$DOC" || fail 'QA checklist must cover denied destructive mobile actions'
grep -Fq '客户导入/导出' "$DOC" || fail 'QA checklist must cover PC-only import/export boundary'
grep -Fq '文件夹 ACL 修改' "$DOC" || fail 'QA checklist must cover PC-only file ACL boundary'
grep -Fq '学分录入、积分配置、导出或批量变更' "$DOC" || fail 'QA checklist must cover score entry/config/export and batch PC-only boundary'
grep -Fq '不能替代' "$DOC" || fail 'QA checklist must distinguish automation from manual evidence'
grep -Fq 'PC/小程序同数据对账记录模板' "$DOC" || fail 'QA checklist must include same-data reconciliation template'
grep -Fq 'check-mobile-reconciliation-evidence.py' "$DOC" || fail 'QA checklist must document the reconciliation evidence validator'
grep -Fq '只检查 Markdown 表格结构、必填项、结论状态和敏感字段痕迹' "$DOC" || fail 'QA checklist must describe reconciliation validator scope'
[ -x "$RECONCILIATION_CHECK" ] || fail 'reconciliation evidence validator must exist and be executable'
python3 "$RECONCILIATION_CHECK" --print-template >/tmp/aigicrm-reconciliation-template.md \
  || fail 'reconciliation evidence validator must print a template'
TMP_FILES+=('/tmp/aigicrm-reconciliation-template.md')
grep -Fq '| 登录与权限 |' /tmp/aigicrm-reconciliation-template.md \
  || fail 'reconciliation evidence validator template must include login row'
GOOD_RECONCILIATION="$(mktemp /tmp/aigicrm-good-reconciliation.XXXXXX.md)"
BAD_RECONCILIATION="$(mktemp /tmp/aigicrm-bad-reconciliation.XXXXXX.md)"
TMP_FILES+=("$GOOD_RECONCILIATION" "$BAD_RECONCILIATION")
cat >"$GOOD_RECONCILIATION" <<'EOF'
| 验收项 | 后端环境 | 账号角色/权限组合 | PC 证据 | 小程序证据 | 接口/权限结果 | 结论 | 遗留差异 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 登录与权限 | staging / same backend | sales-admin / limited-user | pc-screenshot-login-redacted.png | mp-screenshot-login-redacted.png | allow/deny matrix redacted | 通过 | 无差异 |
| 客户与联系人/财务/指标/时间轴 | staging / same backend | sales-admin / limited-user | pc-customer-redacted.png | mp-customer-redacted.png | customer API same ids redacted | 通过 | 无差异 |
| 四项服务与附件 | staging / same backend | sales-admin / limited-user | pc-incentive-redacted.png | mp-incentive-redacted.png | install slots preview/download redacted | 通过 | 无差异 |
| 文件中心 | staging / same backend | sales-admin / limited-user | pc-file-redacted.png | mp-file-redacted.png | file permission matrix redacted | 通过 | 无差异 |
| 审批/公海/提醒/统计/排行 | staging / same backend | sales-admin / limited-user | pc-workbench-redacted.png | mp-workbench-redacted.png | module APIs same filters redacted | 通过 | 无差异 |
| PC-only 边界 | staging / same backend | sales-admin / limited-user | pc-admin-boundary-redacted.png | mp-no-admin-boundary-redacted.png | PC-only actions hidden on mobile | 通过 | 无差异 |
EOF
cat >"$BAD_RECONCILIATION" <<'EOF'
| 验收项 | 后端环境 | 账号角色/权限组合 | PC 证据 | 小程序证据 | 接口/权限结果 | 结论 | 遗留差异 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 登录与权限 | staging / same backend | sales-admin / limited-user | pc.png | mp.png | token=abc123 | 通过 | 无差异 |
| 客户与联系人/财务/指标/时间轴 | staging / same backend | sales-admin / limited-user | pc.png | mp.png | customer API same ids redacted | 通过 | 无差异 |
| 四项服务与附件 | staging / same backend | sales-admin / limited-user | pc.png | mp.png | install slots preview/download redacted | 通过 | 无差异 |
| 文件中心 | staging / same backend | sales-admin / limited-user | pc.png | mp.png | file permission matrix redacted | 通过 | 无差异 |
| 审批/公海/提醒/统计/排行 | staging / same backend | sales-admin / limited-user | pc.png | mp.png | module APIs same filters redacted | 通过 | 无差异 |
| PC-only 边界 | staging / same backend | sales-admin / limited-user | pc.png | mp.png | PC-only actions hidden on mobile | 通过 | 无差异 |
EOF
python3 "$RECONCILIATION_CHECK" "$GOOD_RECONCILIATION" >/dev/null \
  || fail 'reconciliation evidence validator must accept a complete sanitized sample'
if python3 "$RECONCILIATION_CHECK" "$BAD_RECONCILIATION" >/dev/null 2>&1; then
  fail 'reconciliation evidence validator must reject evidence containing sensitive token traces'
fi
grep -Fq '| 验收项 | 后端环境 | 账号角色/权限组合 | PC 证据 | 小程序证据 | 接口/权限结果 | 结论 | 遗留差异 |' "$DOC" || fail 'reconciliation template must capture PC/mobile/interface evidence and conclusion'
grep -Fq '四项服务与附件' "$DOC" || fail 'reconciliation template must include incentive attachment parity'
grep -Fq '安装前/中/后/跟踪文件可预览或下载；阅读状态同步' "$DOC" || fail 'reconciliation template must verify incentive slots and read state'
grep -Fq '结论只允许填写“通过 / 失败 / 阻塞”' "$DOC" || fail 'reconciliation template must constrain conclusion states'
echo 'PASS: mobile manual QA checklist covers project root, business parity, and PC-only boundaries.'
