#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/scripts/miniprogram/package.json"
VERIFY="$ROOT/scripts/miniprogram/verify-customer-center.mjs"
DETAIL_WXML="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.wxml"
DETAIL_JS="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
DETAIL_WXSS="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.wxss"
LIST_WXML="$ROOT/aigicrm-miniprogram/pages/customer/index.wxml"
LIST_JS="$ROOT/aigicrm-miniprogram/pages/customer/index.js"
# 【D27 锚点重钉 2026-10-05】筛选参数拼装/草稿字段清单收敛到 pages/customer/shared.js 单一出处,拼装锚点改钉 shared
SHARED_JS="$ROOT/aigicrm-miniprogram/pages/customer/shared.js"
FILTER_JS="$ROOT/aigicrm-miniprogram/pages/customer/filter/index.js"
FILTER_WXML="$ROOT/aigicrm-miniprogram/pages/customer/filter/index.wxml"
FILTER_WXSS="$ROOT/aigicrm-miniprogram/pages/customer/filter/index.wxss"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_line() {
  local expected="$1"
  local file="$2"
  grep -Fq "$expected" "$file" || fail "$file missing: $expected"
}

require_line '"verify:customer-center": "node verify-customer-center.mjs"' "$PACKAGE"
require_line 'verifyCustomerListPage' "$VERIFY"
require_line 'verifyCustomerDetailPage' "$VERIFY"
require_line 'customer-center-list.png' "$VERIFY"
require_line 'customer-center-detail.png' "$VERIFY"
require_line '/pages/customer/detail/index?id=501&from=customer' "$VERIFY"
require_line '/pages/customer/finance/index?id=501' "$VERIFY"
require_line '/pages/customer/contact/index?id=501' "$VERIFY"
require_line '/pages/customer/share/index?id=501' "$VERIFY"
require_line '/pages/customer/timeline/index?id=501' "$VERIFY"
require_line '/pages/customer/indicator/index?id=501' "$VERIFY"
require_line '延期申请已提交' "$VERIFY"
require_line '客情已保存' "$VERIFY"
require_line 'customer-card' "$LIST_WXML"
require_line '/pages/customer/detail/index?id=${id}&from=customer' "$LIST_JS"
require_line 'buildCustomerParams' "$LIST_JS"
require_line 'customerStatusLabel' "$LIST_JS"
require_line 'statusLabel: customerStatusLabel(status)' "$LIST_JS"
require_line 'statusTone: customerStatusTone(status)' "$LIST_JS"
require_line 'buildCustomerView' "$LIST_JS"
require_line 'buildCustomerRowView' "$LIST_JS"
require_line 'setCustomerData' "$LIST_JS"
require_line 'showLoading' "$LIST_JS"
require_line 'loadingText' "$LIST_JS"
require_line 'customerView.summaryText' "$LIST_WXML"
require_line 'customerView.customers' "$LIST_WXML"
require_line 'customerView.showLoading' "$LIST_WXML"
require_line 'customerView.loadingText' "$LIST_WXML"
require_line 'item.statusText' "$LIST_WXML"
require_line 'item.metaText' "$LIST_WXML"
! grep -Fq '{{loading}}' "$LIST_WXML" || fail 'customer list WXML must not bind raw loading state directly'
! grep -Fq 'countCards[0].value' "$LIST_WXML" || fail 'customer list WXML must not index count cards directly'
! grep -Fq "keyword || filter !== '全部'" "$LIST_WXML" || fail 'customer list WXML must not duplicate reset visibility logic'
! grep -Fq 'customers.length' "$LIST_WXML" || fail 'customer list WXML must not inspect customer array length directly'
! grep -Fq 'item.statusLabel || item.status' "$LIST_WXML" || fail 'customer list WXML must not duplicate status fallback logic'
! grep -Fq 'item.statusTone ||' "$LIST_WXML" || fail 'customer list WXML must not duplicate status tone fallback logic'
! grep -Fq "'C' + item.id" "$LIST_WXML" || fail 'customer list WXML must not build customer code text inline'
# 【D27 锚点重钉 2026-10-05】高级筛选参数拼装移入 shared.buildCustomerFilterParams,列表页以 buildCustomerParams 薄委托调用
require_line 'buildCustomerFilterParams(this.data)' "$LIST_JS"
require_line 'function buildCustomerFilterParams({ keyword, filter, statusFilter, advancedFilter })' "$SHARED_JS"
require_line 'params.industry = advanced.industry' "$SHARED_JS"
require_line 'params.status = advanced.status' "$SHARED_JS"
require_line 'params.deptId = advanced.deptId' "$SHARED_JS"
require_line 'params.ownerId = advanced.ownerId' "$SHARED_JS"
require_line 'getDictData' "$FILTER_JS"
require_line 'getDepts' "$FILTER_JS"
require_line 'getCustomerSimpleUsers' "$FILTER_JS"
require_line 'industryOptions' "$FILTER_JS"
require_line 'deptOptions' "$FILTER_JS"
require_line 'ownerOptions' "$FILTER_JS"
require_line 'buildFilterView' "$FILTER_JS"
require_line 'applyState(patch' "$FILTER_JS"
require_line 'filterView.keywordValue' "$FILTER_WXML"
require_line 'filterView.devTypeText' "$FILTER_WXML"
require_line 'filterView.industryText' "$FILTER_WXML"
require_line 'filterView.statusText' "$FILTER_WXML"
require_line 'filterView.deptText' "$FILTER_WXML"
require_line 'filterView.ownerText' "$FILTER_WXML"
! grep -Eq "industryOptions\\[selectedIndustryIndex\\]|statusOptions\\[selectedStatusIndex\\]|deptOptions\\[selectedDeptIndex\\]|ownerOptions\\[selectedOwnerIndex\\]|\\|\\| '全部行业'|\\|\\| '全部状态'|\\|\\| '全部部门'|\\|\\| '全部负责人'" "$FILTER_WXML" || fail 'customer filter WXML must not keep array-index fallback logic'
require_line 'filter-reset-action' "$FILTER_WXML"
require_line 'filter-apply-action' "$FILTER_WXML"
require_line '.filter-action-row button' "$FILTER_WXSS"
require_line 'height:54rpx' "$FILTER_WXSS"
require_line 'customer-overview-card' "$DETAIL_WXML"
# 【契约重钉 2026-10-09】财务段名"财务摘要"→"开票回款"(UI 冲刺 2fe92ad7 术语统一正当改名,
# git show e85a7a49 归因确认 HEAD 已无旧词,非本批改动引入)
require_line '开票回款' "$DETAIL_WXML"
# 【闭环 2026-10-09 用户反馈】拒删客户(有开票/回款)停止跟进的出口=归档,小程序详情必须有入口
require_line '归档（停止跟进）' "$DETAIL_WXML"
require_line 'archiveCurrentCustomer' "$DETAIL_JS"
require_line '核心客户判定' "$DETAIL_WXML"
require_line '核心客户六项指标' "$DETAIL_WXML"
require_line 'customer-section-head' "$DETAIL_WXML"
require_line 'customer-metric-grid' "$DETAIL_WXML"
require_line 'customer-section-score' "$DETAIL_WXML"
require_line '>联系人<' "$DETAIL_WXML"
require_line '>分享<' "$DETAIL_WXML"
require_line '>跟进<' "$DETAIL_WXML"
require_line 'customerStatusLabel' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'statusLabel: customerStatusLabel(status)' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'buildDetailView' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'setDetailData' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'showLoading' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'loadingText' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'showEmpty' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'detailView.showLoading' "$DETAIL_WXML"
require_line 'detailView.loadingText' "$DETAIL_WXML"
require_line 'detailView.showEmpty' "$DETAIL_WXML"
require_line 'detailView.statusText' "$DETAIL_WXML"
require_line 'detailView.baseRows' "$DETAIL_WXML"
require_line 'detailView.financeRows' "$DETAIL_WXML"
require_line 'detailView.coreVerdictRows' "$DETAIL_WXML"
require_line 'detailView.coreVerdictText' "$DETAIL_WXML"
require_line 'detailView.coreVerdictStatusText' "$DETAIL_WXML"
require_line 'detailView.indicatorRows' "$DETAIL_WXML"
require_line 'detailView.relationshipScoreText' "$DETAIL_WXML"
require_line 'showContactsSection' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'showFinanceSection' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'showCoreVerdictSection' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'showReleaseSection' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'showDeleteSection' "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
require_line 'detailView.showContactsSection' "$DETAIL_WXML"
require_line 'detailView.showFinanceSection' "$DETAIL_WXML"
require_line 'detailView.showReleaseSection' "$DETAIL_WXML"
require_line 'detailView.showDeleteSection' "$DETAIL_WXML"
! grep -Fq 'detailView.hasDetail &&' "$DETAIL_WXML" || fail 'customer detail WXML must not combine hasDetail with permission/source logic directly'
! grep -Fq "from !== 'sea'" "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate sea-source release boundary'
! grep -Fq 'wx:if="{{canEdit}}"' "$DETAIL_WXML" || fail 'customer detail WXML must not bind raw edit permission directly'
! grep -Fq 'wx:if="{{canViewContacts}}"' "$DETAIL_WXML" || fail 'customer detail WXML must not bind raw contact permission directly'
! grep -Fq 'detail.statusLabel || detail.status' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate status fallback logic'
! grep -Fq 'detail.customerCode ||' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate customer code fallback logic'
! grep -Fq 'detail.remark ||' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate remark fallback logic'
! grep -Fq 'item.name || item.contactName' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate contact fallback logic'
! grep -Fq 'contacts.length' "$DETAIL_WXML" || fail 'customer detail WXML must not inspect contacts length directly'
! grep -Fq 'logs.length' "$DETAIL_WXML" || fail 'customer detail WXML must not inspect logs length directly'
! grep -Fq 'invoiceSummary.totalAmount ||' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate invoice fallback logic'
! grep -Fq 'paymentSummary.totalAmount ||' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate payment fallback logic'
! grep -Fq 'indicator.relationshipScore ||' "$DETAIL_WXML" || fail 'customer detail WXML must not duplicate indicator fallback logic'
! grep -Fq '{{loading}}' "$DETAIL_WXML" || fail 'customer detail WXML must not bind raw loading state directly'
require_line '.customer-section-head' "$DETAIL_WXSS"
require_line '.customer-metric-grid' "$DETAIL_WXSS"
require_line '.core-verdict-card' "$DETAIL_WXSS"
require_line '.core-verdict-table' "$DETAIL_WXSS"  # 【#2 2026-10-12】四宫格改四列表格重锚
require_line '.indicator-detail-list' "$DETAIL_WXSS"
require_line 'indicator-detail-row' "$DETAIL_WXML"
require_line 'customer-detail-link-action' "$DETAIL_WXML"
require_line 'detail-action-symbol' "$DETAIL_WXML"
require_line '.detail-action-grid .customer-detail-link-action' "$DETAIL_WXSS"
require_line 'height: 48rpx' "$DETAIL_WXSS"
require_line '.detail-action-symbol' "$DETAIL_WXSS"

echo 'PASS: mobile customer center automator contract is wired.'
