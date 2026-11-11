#!/usr/bin/env bash
# Validate sensitive customer permission wiring without live database credentials.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION="$ROOT/aigicrm-backend/src/main/resources/db/migration/V25__add_finance_view_permission.sql"
CONTROLLER="$ROOT/aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java"
MATRIX="$ROOT/docs/function-permission-matrix.md"
MOBILE_DETAIL_JS="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
MOBILE_DETAIL_WXML="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.wxml"

fail() { echo "FAIL: $*" >&2; exit 1; }

for permission in finance:view contact:view; do
  grep -Fq "'$permission'" "$MIGRATION" || fail "migration missing $permission"
  grep -Fq "SecurityUtils.hasAuthority(\"$permission\")" "$CONTROLLER" || fail "controller missing $permission guard"
  grep -Fq "\`$permission\`" "$MATRIX" || fail "permission matrix missing $permission"
done

# 【字面量收敛第一批 2026-11-12】detail 门控已改生成物常量形态
grep -Fq "hasPermission(PERMISSION_CODES.FINANCE_VIEW)" "$MOBILE_DETAIL_JS" \
  || fail 'mobile detail missing finance:view gate'
grep -Fq "hasPermission(PERMISSION_CODES.CONTACT_VIEW)" "$MOBILE_DETAIL_JS" \
  || fail 'mobile detail missing contact:view gate'
# 【2026-10-11 钉随裁决】2026-10-10 裁决: 财务摘要改阶段总额判定口径, 逐笔 financeRequest 下线
# 换 stageRequest 条件请求(getCustomerStageAmounts), 条件守卫语义不变(canViewFinance)
grep -Fq 'const stageRequest = this.data.canViewFinance' "$MOBILE_DETAIL_JS" \
  || fail 'mobile detail finance request is not conditional'
grep -Fq 'const contactsRequest = this.data.canViewContacts' "$MOBILE_DETAIL_JS" \
  || fail 'mobile detail contact request is not conditional'
grep -Fq 'showFinanceSection: hasDetail && canViewFinance' "$MOBILE_DETAIL_JS" \
  || fail 'mobile detail finance view gate is not conditional'
grep -Fq 'showContactsSection: hasDetail && canViewContacts' "$MOBILE_DETAIL_JS" \
  || fail 'mobile detail contact view gate is not conditional'
grep -Fq 'detailView.showFinanceSection' "$MOBILE_DETAIL_WXML" \
  || fail 'mobile detail finance rendering is not conditional'
grep -Fq 'detailView.showContactsSection' "$MOBILE_DETAIL_WXML" \
  || fail 'mobile detail contact rendering is not conditional'

echo 'PASS: sensitive customer permission contract is aligned.'
