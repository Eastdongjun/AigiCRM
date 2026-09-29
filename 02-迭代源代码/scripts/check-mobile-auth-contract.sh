#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUTH_API="$ROOT/aigicrm-miniprogram/api/auth.js"
LOGIN_JS="$ROOT/aigicrm-miniprogram/pages/login/index.js"
LOGIN_WXML="$ROOT/aigicrm-miniprogram/pages/login/index.wxml"
FORGOT_JS="$ROOT/aigicrm-miniprogram/pages/login/forgot/index.js"
FORGOT_WXML="$ROOT/aigicrm-miniprogram/pages/login/forgot/index.wxml"
FORGOT_WXSS="$ROOT/aigicrm-miniprogram/pages/login/forgot/index.wxss"
PROFILE_JS="$ROOT/aigicrm-miniprogram/pages/profile/index.js"
PROFILE_WXML="$ROOT/aigicrm-miniprogram/pages/profile/index.wxml"
WECHAT_JS="$ROOT/aigicrm-miniprogram/pages/profile/wechat/index.js"
WECHAT_WXML="$ROOT/aigicrm-miniprogram/pages/profile/wechat/index.wxml"
PASSWORD_JS="$ROOT/aigicrm-miniprogram/pages/profile/password/index.js"
MATRIX="$ROOT/docs/function-permission-matrix.md"
MANIFEST="$ROOT/docs/unified-feature-manifest.md"
AUTOMATOR_PACKAGE="$ROOT/scripts/miniprogram/package.json"
AUTH_AUTOMATOR="$ROOT/scripts/miniprogram/verify-auth-center.mjs"

fail() { echo "FAIL: $*" >&2; exit 1; }

for pattern in \
  "function switchGroup(groupId)" \
  "url: '/api/auth/switch-group'" \
  "function forgotPasswordLookup(data)" \
  "function forgotPasswordSendCode(data)" \
  "function forgotPasswordReset(data)" \
  "/api/auth/forgot-password/lookup" \
  "/api/auth/forgot-password/send-code" \
  "/api/auth/forgot-password/reset"; do
  grep -Fq "$pattern" "$AUTH_API" || fail "mobile auth API missing $pattern"
done

grep -Fq "openForgot()" "$LOGIN_JS" || fail "login page missing forgot-password navigation method"
grep -Fq "bindtap=\"openForgot\"" "$LOGIN_WXML" || fail "login page missing forgot-password entry"
grep -Fq "buildLoginView" "$LOGIN_JS" || fail "login page missing normalized view state"
grep -Fq "loginView.submitLoading" "$LOGIN_WXML" || fail "login page submit loading must use normalized view state"
! grep -Eq "demoMode \\?|loading=\"\\{\\{loading\\}\\}\"|\\{\\{loading\\}\\}" "$LOGIN_WXML" || fail "login WXML must not keep raw demo/loading branching"

grep -Fq '"verify:auth-center": "node verify-auth-center.mjs"' "$AUTOMATOR_PACKAGE" \
  || fail "mobile auth DevTools automator script is not exposed"
for pattern in \
  "verifyLoginPage" \
  "verifyDemoLogin" \
  "verifyForgotPassword" \
  "auth-login-account.png" \
  "auth-login-demo.png" \
  "auth-forgot-password.png" \
  "请输入用户名和密码" \
  "进入原型" \
  "请检查新密码"; do
  grep -Fq "$pattern" "$AUTH_AUTOMATOR" || fail "mobile auth DevTools automator missing $pattern"
done

for pattern in \
  "forgotPasswordLookup" \
  "forgotPasswordSendCode" \
  "forgotPasswordReset" \
  "验证码已发送" \
  "密码已重置"; do
  grep -Fq "$pattern" "$FORGOT_JS" || fail "forgot-password flow missing $pattern"
done

for pattern in "验证账号" "验证身份" "重置密码" "获取验证码"; do
  grep -Fq "$pattern" "$FORGOT_JS" "$FORGOT_WXML" || fail "forgot-password UI missing $pattern"
done
grep -Fq 'code-action' "$FORGOT_WXML" || fail "forgot-password code action must use compact local style"
grep -Fq "buildForgotView" "$FORGOT_JS" || fail "forgot-password page missing normalized view state"
grep -Fq "showAccountStep" "$FORGOT_JS" || fail "forgot-password account step visibility must be normalized before WXML"
grep -Fq "showCodeStep" "$FORGOT_JS" || fail "forgot-password code step visibility must be normalized before WXML"
grep -Fq "showResetStep" "$FORGOT_JS" || fail "forgot-password reset step visibility must be normalized before WXML"
grep -Fq "forgotView.submitLoading" "$FORGOT_WXML" || fail "forgot-password submit loading must use normalized view state"
grep -Fq "forgotView.showAccountStep" "$FORGOT_WXML" || fail "forgot-password account block must use normalized view state"
grep -Fq "forgotView.showCodeStep" "$FORGOT_WXML" || fail "forgot-password code block must use normalized view state"
grep -Fq "forgotView.showResetStep" "$FORGOT_WXML" || fail "forgot-password reset block must use normalized view state"
! grep -Eq "countdown \\?|step===|step===3\\?|step===1\\?|loading=\"\\{\\{loading\\}\\}\"|\\{\\{loading\\}\\}" "$FORGOT_WXML" || fail "forgot-password WXML must not keep raw countdown/step/loading branching"
grep -Fq '.code-action' "$FORGOT_WXSS" || fail "forgot-password compact code action style is missing"
grep -Fq 'height:54rpx' "$FORGOT_WXSS" || fail "forgot-password code action must stay compact"

for pattern in "switchSessionGroup(groupId)" "loadCurrentUser()" "集团已切换" "集团切换失败"; do
  grep -Fq "$pattern" "$PROFILE_JS" || fail "mobile group switch flow missing $pattern"
done

grep -Fq "bindchange=\"changeGroup\"" "$PROFILE_WXML" || fail "mobile group switch UI missing bindchange=\"changeGroup\""
grep -Fq "disabled=\"{{profileView.groupDisabled}}\"" "$PROFILE_WXML" || fail "mobile group switch UI missing disabled=\"{{profileView.groupDisabled}}\""
grep -Fq "切换后刷新权限" "$PROFILE_JS" "$PROFILE_WXML" || fail "mobile group switch UI missing 切换后刷新权限"

for pattern in "openWechat()" "pages/profile/wechat/index" "微信绑定"; do
  grep -Fq "$pattern" "$PROFILE_JS" "$PROFILE_WXML" || fail "wechat binding explanation entry missing $pattern"
done

for pattern in "待后端接入" "不会请求微信身份信息" "openid/unionid" "查看接入说明"; do
  grep -Fq "$pattern" "$WECHAT_JS" "$WECHAT_WXML" || fail "wechat binding explanation page missing $pattern"
done

! grep -Fq "wx.login" "$WECHAT_JS" || fail "wechat explanation page must not request WeChat login before backend binding exists"
! grep -Fq "getUserProfile" "$WECHAT_JS" || fail "wechat explanation page must not request WeChat profile before backend binding exists"

grep -Fq '| Group Switch | Switch current group | `POST /api/auth/switch-group` | Authenticated token | User allowed groups | Session mutation | Yes | Implemented |' "$MATRIX" \
  || fail "group switch matrix status is stale"
grep -Fq '| Password Recovery | Lookup/send-code/reset | `/api/auth/forgot-password/*` | Public or token/code verified | User account | Account recovery | Yes or backend available | Implemented |' "$MATRIX" \
  || fail "password recovery matrix status is stale"
grep -Fq "url: '/api/system/users/change-password'" "$PASSWORD_JS" \
  || fail "profile password page must change only the current user's password"
grep -Fq "clearToken()" "$PASSWORD_JS" \
  || fail "profile password page must clear session after password change"
grep -Fq '| Profile Password | Change own password | `POST /api/system/users/change-password` | Authenticated token and old password verification | Current user only | Credential mutation | Yes | Implemented |' "$MATRIX" \
  || fail "own password change matrix status is stale"
grep -Fq '| 人员管理 | Admin reset user password | `POST /api/system/users/{id}/reset-password` | `system:user:edit` or `system:org:admin` | Target user/admin scope | Credential mutation | Yes | No |' "$MATRIX" \
  || fail "admin password reset PC-only matrix boundary is stale"
grep -Fq '| `auth.wechatBinding` | 认证/个人中心 | 预留微信账号绑定 | `pages/profile/index` 与 `pages/profile/wechat/index` | 登录即可 | 当前账号 | 仅绑定状态说明;OAuth/openid 绑定未接 | 已实现 |' "$MANIFEST" \
  || fail "wechat binding manifest status is stale"

echo "PASS: mobile auth group switch and password recovery contract is aligned."
