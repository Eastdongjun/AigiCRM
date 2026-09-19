#!/usr/bin/env bash
# Keep mobile request failures on the shared feedback convention.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FEEDBACK="$ROOT/aigicrm-miniprogram/utils/feedback.js"
AUTOMATOR_PACKAGE="$ROOT/scripts/miniprogram/package.json"
AUTOMATOR="$ROOT/scripts/miniprogram/verify-feedback-notification.mjs"
NOTIFICATION_WXML="$ROOT/aigicrm-miniprogram/pages/notification/index.wxml"
NOTIFICATION_WXSS="$ROOT/aigicrm-miniprogram/pages/notification/index.wxss"
NOTIFICATION_JS="$ROOT/aigicrm-miniprogram/pages/notification/index.js"
NOTIFICATION_DETAIL_WXML="$ROOT/aigicrm-miniprogram/pages/notification/detail/index.wxml"
NOTIFICATION_DETAIL_WXSS="$ROOT/aigicrm-miniprogram/pages/notification/detail/index.wxss"
NOTIFICATION_DETAIL_JS="$ROOT/aigicrm-miniprogram/pages/notification/detail/index.js"
FEEDBACK_WXML="$ROOT/aigicrm-miniprogram/pages/profile/feedback/index.wxml"
FEEDBACK_WXSS="$ROOT/aigicrm-miniprogram/pages/profile/feedback/index.wxss"
FEEDBACK_JS="$ROOT/aigicrm-miniprogram/pages/profile/feedback/index.js"
fail() { echo "FAIL: $*" >&2; exit 1; }

grep -Fq 'function errorMessage' "$FEEDBACK" || fail 'missing shared errorMessage'
grep -Fq 'function showError' "$FEEDBACK" || fail 'missing shared showError'
grep -Fq '"verify:feedback-notification": "node verify-feedback-notification.mjs"' "$AUTOMATOR_PACKAGE" \
  || fail 'mobile feedback/notification DevTools automator script is not exposed'
for pattern in \
  "verifyFeedbackDraft" \
  "verifyNotificationCenter" \
  "verifyProfileLegalDocs" \
  "feedback-draft.png" \
  "notification-center.png" \
  "notification-detail.png" \
  "profile-agreement.png" \
  "尚未接入服务端反馈提交" \
  "标记已处理"; do
  grep -Fq "$pattern" "$AUTOMATOR" || fail "mobile feedback/notification automator missing $pattern"
done
for page in $(find "$ROOT/aigicrm-miniprogram/pages" -name '*.js' -type f -exec grep -lE "require.*api/" {} + | sort -u); do
  grep -Fq "utils/feedback" "$page" || fail "page does not use shared feedback: $page"
  grep -Fq 'showError(' "$page" || fail "page does not call showError: $page"
  # Login and password recovery are the two intentional anonymous API flows.
  if [[ "$page" != *"/pages/login/index.js" && "$page" != *"/pages/login/forgot/index.js" ]]; then
    grep -Fq 'requireLogin' "$page" || fail "API page does not enforce login: $page"
  fi
done
grep -Fq 'notification-actions' "$NOTIFICATION_WXML" || fail 'notification list actions must use compact local action row'
grep -Fq 'notification-link-action' "$NOTIFICATION_WXML" || fail 'notification list secondary actions must use compact local style'
grep -Fq 'notification-primary-action' "$NOTIFICATION_WXML" || fail 'notification list primary action must use compact local style'
grep -Fq 'catchtap="openCustomer"' "$NOTIFICATION_WXML" || fail 'notification list must expose customer archive action'
grep -Fq '客户档案' "$NOTIFICATION_WXML" || fail 'notification list customer action copy must match customer archive convention'
grep -Fq 'openCustomer(event)' "$NOTIFICATION_JS" || fail 'notification list must implement customer archive navigation'
grep -Fq '/pages/customer/detail/index?id=${item.customerId}&from=notification' "$NOTIFICATION_JS" \
  || fail 'notification list must route to customer archive detail'
grep -Fq 'buildNotificationView' "$NOTIFICATION_JS" || fail 'notification list display state must be normalized in JS'
grep -Fq 'applyState(patch' "$NOTIFICATION_JS" || fail 'notification list normalized state refresh is missing'
grep -Fq 'notificationView.showLoading' "$NOTIFICATION_WXML" || fail 'notification list loading must use normalized view state'
grep -Fq 'notificationView.rows' "$NOTIFICATION_WXML" || fail 'notification list rows must use normalized view state'
grep -Fq 'notificationView.showEmpty' "$NOTIFICATION_WXML" || fail 'notification list empty state must use normalized view state'
grep -Fq 'item.showCustomerAction' "$NOTIFICATION_WXML" || fail 'notification list customer action must use normalized view state'
! grep -Eq "\\{\\{loading\\}\\}|notifications\\.length|wx:if=\"\\{\\{item\\.customerId\\}\\}\"|\\{\\{item\\.title\\}\\}|\\{\\{item\\.content\\}\\}|\\{\\{item\\.customerName\\}\\}" "$NOTIFICATION_WXML" \
  || fail 'notification list WXML must not keep loading/list/customer fallback logic'
grep -Fq '.notification-actions button' "$NOTIFICATION_WXSS" || fail 'notification list compact action styles disappeared'
grep -Fq 'height:50rpx' "$NOTIFICATION_WXSS" || fail 'notification list actions must stay compact'
grep -Fq 'notification-detail-actions' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail actions must use compact local action row'
grep -Fq 'bindtap="openCustomer"' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail must expose customer archive action'
grep -Fq '查看客户完整档案' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail customer action copy must match customer archive convention'
grep -Fq 'openCustomer()' "$NOTIFICATION_DETAIL_JS" || fail 'notification detail must implement customer archive navigation'
grep -Fq '/pages/customer/detail/index?id=${this.data.customerId}&from=notification' "$NOTIFICATION_DETAIL_JS" \
  || fail 'notification detail must route to customer archive detail'
grep -Fq 'buildNotificationDetailView' "$NOTIFICATION_DETAIL_JS" || fail 'notification detail display state must be normalized in JS'
grep -Fq 'applyState(patch' "$NOTIFICATION_DETAIL_JS" || fail 'notification detail normalized state refresh is missing'
grep -Fq 'notificationDetailView.titleText' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail title must use normalized view state'
grep -Fq 'notificationDetailView.contentText' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail content must use normalized view state'
grep -Fq 'notificationDetailView.customerText' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail customer must use normalized view state'
grep -Fq 'notificationDetailView.processLoading' "$NOTIFICATION_DETAIL_WXML" || fail 'notification detail loading must use normalized view state'
! grep -Eq "\\|\\| '暂无通知内容'|\\|\\| '-'|\\{\\{content\\}\\}|\\{\\{customer\\}\\}|\\{\\{processing\\}\\}|wx:if=\"\\{\\{customerId\\}\\}\"" "$NOTIFICATION_DETAIL_WXML" \
  || fail 'notification detail WXML must not keep fallback/display branching logic'
grep -Fq '.notification-detail-actions button' "$NOTIFICATION_DETAIL_WXSS" || fail 'notification detail compact action styles disappeared'
grep -Fq 'height:52rpx' "$NOTIFICATION_DETAIL_WXSS" || fail 'notification detail actions must stay compact'
grep -Fq 'feedback-clear-action' "$FEEDBACK_WXML" || fail 'feedback clear action must use compact local style'
grep -Fq 'feedback-save-action' "$FEEDBACK_WXML" || fail 'feedback save action must use compact local style'
grep -Fq 'buildFeedbackView' "$FEEDBACK_JS" || fail 'feedback page display state must be normalized in JS'
grep -Fq 'applyState(patch' "$FEEDBACK_JS" || fail 'feedback page normalized state refresh is missing'
grep -Fq 'feedbackView.contentValue' "$FEEDBACK_WXML" || fail 'feedback textarea must use normalized view state'
grep -Fq 'feedbackView.showSavedNotice' "$FEEDBACK_WXML" || fail 'feedback saved notice must use normalized view state'
! grep -Eq "\\{\\{saved\\}\\}|\\{\\{content\\}\\}|maxlength=\"2000\"|placeholder=\"请描述遇到的问题" "$FEEDBACK_WXML" \
  || fail 'feedback WXML must not keep direct saved/content field logic'
grep -Fq '.feedback-actions button' "$FEEDBACK_WXSS" || fail 'feedback compact action styles disappeared'
grep -Fq 'height:52rpx' "$FEEDBACK_WXSS" || fail 'feedback action buttons must stay compact'
echo 'PASS: mobile feedback contract is aligned.'
