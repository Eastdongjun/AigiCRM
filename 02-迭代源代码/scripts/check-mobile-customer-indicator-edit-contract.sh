#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PAGE="$ROOT/aigicrm-miniprogram/pages/customer/indicator/index.js"
WXML="$ROOT/aigicrm-miniprogram/pages/customer/indicator/index.wxml"
# 【D24 锚点重钉 2026-10-05】五项指标配置与选项视图构建收敛到 pages/customer/shared.js(与客户表单/详情共用同一出处),
# 本页引用 buildIndicatorItems;配置/视图锚点改钉 shared
SHARED="$ROOT/aigicrm-miniprogram/pages/customer/shared.js"

fail() { echo "FAIL: $*" >&2; exit 1; }
require_line() {
  local expected="$1"
  local file="$2"
  grep -Fq "$expected" "$file" || fail "$file missing: $expected"
}

require_line "getCustomerIndicator" "$PAGE"
require_line "saveCustomerIndicator" "$PAGE"
require_line "readDemoCustomers" "$PAGE"
require_line "saveDemoCustomer" "$PAGE"
require_line "customer.indicator || customer.customerIndicator" "$PAGE"
require_line "saveDemoIndicator()" "$PAGE"
require_line "saveDemoCustomer({ ...customer, indicator: { ...this.data.form } }, this.data.id)" "$PAGE"
require_line "buildIndicatorView" "$PAGE"
require_line "applyState(patch)" "$PAGE"
require_line "buildIndicatorItems" "$PAGE"
require_line "optionsView" "$SHARED"
require_line "className: value === score ? 'score-option active' : 'score-option'" "$SHARED"
require_line "submitLoading" "$PAGE"
require_line "submitText" "$PAGE"
require_line "保存指标" "$PAGE"
require_line "高层" "$SHARED"
require_line "渠道" "$SHARED"
require_line "基层" "$SHARED"
require_line "产品" "$SHARED"
require_line "项目" "$SHARED"
require_line "indicatorView.showLoading" "$WXML"
require_line "indicatorView.loadingText" "$WXML"
require_line "indicatorView.showForm" "$WXML"
require_line "item.optionsView" "$WXML"
require_line "option.className" "$WXML"
require_line "indicatorView.submitLoading" "$WXML"
require_line "indicatorView.submitText" "$WXML"
require_line "bindtap=\"selectScore\"" "$WXML"
! grep -Fq "{{loading}}" "$WXML" || fail 'indicator edit WXML must not bind raw loading state directly'
! grep -Fq "{{saving}}" "$WXML" || fail 'indicator edit WXML must not bind raw saving state directly'
! grep -Fq "item.value === score" "$WXML" || fail 'indicator edit WXML must not compute active score classes'
! grep -Fq "wx:else" "$WXML" || fail 'indicator edit WXML must not rely on implicit loading else branch'

echo 'PASS: mobile customer indicator edit contract is aligned.'
