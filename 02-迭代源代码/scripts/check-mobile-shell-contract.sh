#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="$ROOT/scripts/miniprogram/package.json"
VERIFY="$ROOT/scripts/miniprogram/verify-mobile-shell.mjs"
HOME_JS="$ROOT/aigicrm-miniprogram/pages/home/index.js"
HOME_WXML="$ROOT/aigicrm-miniprogram/pages/home/index.wxml"
APP_JS="$ROOT/aigicrm-miniprogram/pages/app/index.js"
APP_WXML="$ROOT/aigicrm-miniprogram/pages/app/index.wxml"
MATRIX="$ROOT/docs/function-permission-matrix.md"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_line() {
  local expected="$1"
  local file="$2"
  grep -Fq "$expected" "$file" || fail "$file missing: $expected"
}
reject_line() {
  local unexpected="$1"
  local file="$2"
  ! grep -Fq "$unexpected" "$file" || fail "$file contains stale line: $unexpected"
}

require_line '"verify:mobile-shell": "node verify-mobile-shell.mjs"' "$PACKAGE"
require_line 'verifyHomePage' "$VERIFY"
require_line 'verifyHomePermissionDenials' "$VERIFY"
require_line 'verifyAppCenter' "$VERIFY"
require_line 'mobile-shell-home.png' "$VERIFY"
require_line 'mobile-shell-app-center.png' "$VERIFY"
require_line '/pages/incentive/upload/index' "$VERIFY"
require_line '没有四项服务上传权限' "$VERIFY"
require_line '暂无可用应用' "$VERIFY"
require_line 'openIncentiveUpload' "$HOME_JS"
require_line "hasPermission('incentive:add')" "$HOME_JS"
require_line "wx.navigateTo({ url: '/pages/incentive/upload/index' })" "$HOME_JS"
require_line 'bindtap="openIncentiveUpload"' "$HOME_WXML"
require_line '四项服务上传' "$HOME_WXML"
reject_line '激励上报' "$HOME_WXML"
require_line 'hasPermission(item.permission)' "$APP_JS"
require_line "permission: 'incentive:add'" "$APP_JS"
require_line "title: '四项服务上传'" "$APP_JS"
require_line "title: '四项服务'" "$APP_JS"
require_line "subtitle: '分类树、统计、排行与记录'" "$APP_JS"
require_line "permission: 'statistics:view'" "$APP_JS"
require_line "routes = { customer: '/pages/customer/index'" "$APP_JS"
require_line "incentiveUpload: '/pages/incentive/upload/index'" "$APP_JS"
require_line 'buildAppView' "$APP_JS"
require_line 'iconText' "$APP_JS"
require_line 'appView.groups' "$APP_WXML"
require_line 'appView.showEmpty' "$APP_WXML"
require_line 'bindtap="openApp"' "$APP_WXML"
require_line '暂无可用应用' "$APP_WXML"
reject_line "item.key === 'customer'" "$APP_WXML"
reject_line 'apps.length===0' "$APP_WXML"
require_line '| Mobile Shell | `pages/app/index` | Authenticated and per-entry permission gates from `/api/auth/info` | No extra business API; navigates to registered mobile pages | Mobile counterpart of the PC left menu/application navigation; hides unauthorized app entries |' "$MATRIX"
require_line '| Home | `pages/home/index` | Authenticated plus `dashboard:view` or `statistics:view` for dashboard cards | `/api/auth/info`, `/api/statistics/dashboard`, reminder pending/expiring APIs | Shows user, role, dashboard cards and quick entries when permitted |' "$MATRIX"

echo 'PASS: mobile shell home/app-center contract is wired.'
