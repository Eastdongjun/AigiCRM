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
# 【PR-03/PR-04 重试入口】加载失败须可用带重试的共享助手(内部退化到 showError)
grep -Fq 'function showErrorWithRetry' "$FEEDBACK" || fail 'missing shared showErrorWithRetry'
grep -Fq "confirmText: '重试'" "$FEEDBACK" || fail 'shared showErrorWithRetry must offer a retry confirm action'
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
  # 共享反馈调用: showError 或带重试入口的 showErrorWithRetry(内部含 showError 退化)
  grep -Eq 'showError(WithRetry)?\(' "$page" || fail "page does not call shared showError/showErrorWithRetry: $page"
  # Login and password recovery are the two intentional anonymous API flows.
  if [[ "$page" != *"/pages/login/index.js" && "$page" != *"/pages/login/forgot/index.js" ]]; then
    grep -Fq 'requireLogin' "$page" || fail "API page does not enforce login: $page"
  fi
done
# 【钉随代码 2026-10-07】notification 两页随 1b3c865b 死页面清理删除(提醒=reminder 模块+tabBar 角标承载),
# 页面级钉随页面退役; automator 脚本与 feedback.js 共享助手钉保留
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
