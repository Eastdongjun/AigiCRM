#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/scripts/miniprogram/package.json"
VERIFY="$ROOT/scripts/miniprogram/verify-profile-center.mjs"
PROFILE_JS="$ROOT/aigicrm-miniprogram/pages/profile/index.js"
PROFILE_WXML="$ROOT/aigicrm-miniprogram/pages/profile/index.wxml"
PROFILE_WXSS="$ROOT/aigicrm-miniprogram/pages/profile/index.wxss"
PASSWORD_JS="$ROOT/aigicrm-miniprogram/pages/profile/password/index.js"
PASSWORD_WXML="$ROOT/aigicrm-miniprogram/pages/profile/password/index.wxml"
SCOPE_WXML="$ROOT/aigicrm-miniprogram/pages/profile/scope/index.wxml"
WECHAT_JS="$ROOT/aigicrm-miniprogram/pages/profile/wechat/index.js"
WECHAT_WXML="$ROOT/aigicrm-miniprogram/pages/profile/wechat/index.wxml"
AUTH_API="$ROOT/aigicrm-miniprogram/api/auth.js"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_line() {
  local expected="$1"
  local file="$2"
  grep -Fq "$expected" "$file" || fail "$file missing: $expected"
}
reject_line() {
  local unexpected="$1"
  local file="$2"
  ! grep -Fq "$unexpected" "$file" || fail "$file contains forbidden line: $unexpected"
}

require_line '"verify:profile-center": "node verify-profile-center.mjs"' "$PACKAGE"
require_line 'verifyProfileHome' "$VERIFY"
require_line 'verifyPasswordPage' "$VERIFY"
require_line 'verifyScopeAndWechatPages' "$VERIFY"
require_line 'profile-center-home.png' "$VERIFY"
require_line 'profile-password.png' "$VERIFY"
require_line 'profile-wechat-boundary.png' "$VERIFY"
require_line '/pages/customer/index' "$VERIFY"
require_line '/pages/incentive/index' "$VERIFY"
require_line '/pages/sea/index' "$VERIFY"
require_line '/pages/profile/password/index' "$VERIFY"
require_line '/pages/profile/scope/index' "$VERIFY"
require_line '/pages/profile/wechat/index' "$VERIFY"
require_line '微信绑定暂未接入' "$VERIFY"
require_line '不会请求微信身份信息' "$VERIFY"
require_line 'loadCurrentUser' "$PROFILE_JS"
require_line 'switchSessionGroup' "$PROFILE_JS"
require_line 'clearCache' "$PROFILE_JS"
require_line 'openWechat' "$PROFILE_JS"
require_line 'buildProfileView' "$PROFILE_JS"
require_line 'applyState(patch' "$PROFILE_JS"
# 【2026-10-18】岗位自 2026-10-13 起并入 hero 副标题(profileView.heroSubText),
# 不再有独立“当前岗位”行; 下方仍钉 heroSubText 绑定。
require_line '当前集团' "$PROFILE_WXML"
require_line '数据范围' "$PROFILE_WXML"
require_line 'profileView.userInitialText' "$PROFILE_WXML"
require_line 'profileView.heroSubText' "$PROFILE_WXML"
require_line 'profileView.showGroupCard' "$PROFILE_WXML"
require_line 'profileView.currentGroupText' "$PROFILE_WXML"
require_line 'profileView.groupNoteText' "$PROFILE_WXML"
require_line 'profileView.scopeText' "$PROFILE_WXML"
if grep -Eq "groupIds\\.length|currentGroupId \\|\\||switchingGroup \\?|\\{\\{userInitial\\}\\}|\\{\\{userName\\}\\}|\\{\\{scopeName\\}\\}|\\{\\{positionNames\\}\\}" "$PROFILE_WXML"; then
  fail 'profile home WXML must not keep user/group/scope fallback logic'
fi
require_line '修改密码' "$PROFILE_WXML"
require_line '数据权限说明' "$PROFILE_WXML"
require_line '微信绑定' "$PROFILE_WXML"
require_line 'profile-feedback-action' "$PROFILE_WXML"
require_line 'profile-logout-action' "$PROFILE_WXML"
require_line '.profile-action-row button' "$PROFILE_WXSS"
require_line 'height:64rpx' "$PROFILE_WXSS"
require_line '/api/system/users/change-password' "$PASSWORD_JS"
require_line 'clearToken()' "$PASSWORD_JS"
require_line '请完整填写密码' "$PASSWORD_JS"
# 【mp-5 2026-10-18】后端 changePassword 要求 8-30 位, 前端提示/校验同步
require_line '新密码（8-30 位）' "$PASSWORD_JS"
require_line '8-30 位' "$PASSWORD_JS"
require_line '两次新密码不一致' "$PASSWORD_JS"
require_line '修改密码' "$PASSWORD_WXML"
require_line 'buildPasswordView' "$PASSWORD_JS"
require_line 'applyState(patch' "$PASSWORD_JS"
require_line 'passwordView.fields' "$PASSWORD_WXML"
require_line 'passwordView.submitLoading' "$PASSWORD_WXML"
if grep -Eq "\\{\\{loading\\}\\}|placeholder=\"原密码\"|placeholder=\"新密码|placeholder=\"确认新密码\"" "$PASSWORD_WXML"; then
  fail 'profile password WXML must not keep direct loading/input field logic'
fi
require_line '操作权限' "$SCOPE_WXML"
require_line '服务端校验' "$SCOPE_WXML"
require_line '微信绑定暂未接入' "$WECHAT_JS"
require_line '不会请求微信身份信息' "$WECHAT_JS"
require_line '待后端接入' "$WECHAT_JS"
require_line '当前版本不会请求微信身份信息' "$WECHAT_WXML"
reject_line 'wx.login' "$WECHAT_JS"
reject_line 'getUserProfile' "$WECHAT_JS"
require_line "url: '/api/auth/info'" "$AUTH_API"
require_line "url: '/api/auth/switch-group'" "$AUTH_API"

echo 'PASS: mobile profile center contract is wired.'
