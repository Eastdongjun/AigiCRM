#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FORM="$ROOT/aigicrm-miniprogram/pages/customer/form/index.js"
API="$ROOT/aigicrm-miniprogram/api/customer.js"
HOME="$ROOT/aigicrm-miniprogram/pages/home/index.js"

if ! rg -Fq "hasPermission('customer:add')" "$HOME"; then
  echo "FAIL: home new-customer entry must require customer:add"
  exit 1
fi

for pattern in "hasPermission(id ? 'customer:edit' : 'customer:add')" "readDemoCustomers().find" "getApp().globalData.demoMode" "saveDemoCustomer(form, this.data.isEdit ? this.data.id : '')" "updateCustomer(this.data.id, form)" "addCustomer(form)"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: customer form missing $pattern"
    exit 1
  fi
done

for pattern in "saveDemoCustomer(form," "readDemoCustomers" "wx.setStorageSync"; do
  if ! rg -Fq "$pattern" "$FORM" "$ROOT/aigicrm-miniprogram/pages/customer/index.js" "$ROOT/aigicrm-miniprogram/utils/demo-customers.js"; then
    echo "FAIL: demo customer persistence missing $pattern"
    exit 1
  fi
done

if ! rg -Fq "if (getApp().globalData.demoMode) return wx.showToast({ title: '延期申请已提交'" "$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"; then
  echo "FAIL: demo customer extension approval guard is missing"
  exit 1
fi

for pattern in "saveDemoContact" "readDemoContacts"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.js" "$ROOT/aigicrm-miniprogram/utils/demo-contacts.js"; then
    echo "FAIL: demo contact persistence missing $pattern"
    exit 1
  fi
done

for pattern in "saveDemoFinance" "readDemoFinance"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/utils/demo-finance.js"; then
    echo "FAIL: demo finance persistence missing $pattern"
    exit 1
  fi
done

for pattern in "function addCustomer(data)" "function updateCustomer(id, data)"; do
  if ! rg -Fq "$pattern" "$API"; then
    echo "FAIL: customer API missing $pattern"
    exit 1
  fi
done

echo "PASS: mobile customer form permission, demo, create, and update contract is aligned."
