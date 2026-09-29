#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FORM="$ROOT/aigicrm-miniprogram/pages/customer/form/index.js"
FORM_WXML="$ROOT/aigicrm-miniprogram/pages/customer/form/index.wxml"
API="$ROOT/aigicrm-miniprogram/api/customer.js"
HOME="$ROOT/aigicrm-miniprogram/pages/home/index.js"
DETAIL_JS="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.js"
DETAIL_WXML="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.wxml"
DETAIL_WXSS="$ROOT/aigicrm-miniprogram/pages/customer/detail/index.wxss"
LOG_JS="$ROOT/aigicrm-miniprogram/pages/customer/log/index.js"
LOG_WXML="$ROOT/aigicrm-miniprogram/pages/customer/log/index.wxml"
AUTOMATOR_PACKAGE="$ROOT/scripts/miniprogram/package.json"
AUTOMATOR="$ROOT/scripts/miniprogram/verify-customer-forms.mjs"

if ! rg -Fq '"verify:customer-forms": "node verify-customer-forms.mjs"' "$AUTOMATOR_PACKAGE"; then
  echo "FAIL: mobile customer form DevTools automator script is not exposed"
  exit 1
fi

for pattern in \
  "verifyCustomerForm" \
  "verifyContacts" \
  "verifyFinance" \
  "verifyShareLogIndicator" \
  "customer-form.png" \
  "customer-contact-list.png" \
  "customer-finance-list.png" \
  "customer-share-log-indicator.png" \
  "客户表单自动化用户"; do
  if ! rg -Fq "$pattern" "$AUTOMATOR"; then
    echo "FAIL: mobile customer form DevTools automator missing $pattern"
    exit 1
  fi
done

if ! rg -Fq "hasPermission('customer:add')" "$HOME"; then
  echo "FAIL: home new-customer entry must require customer:add"
  exit 1
fi

# 【PR-05 2026-09-19】真实模式改为客户+指标同事务组合提交(旧两步 addCustomer/updateCustomer+补存指标已废弃)
for pattern in "hasPermission(id ? 'customer:edit' : 'customer:add')" "readDemoCustomers().find" "getApp().globalData.demoMode" "saveDemoCustomer({ ...form, indicator: { ...this.data.indicatorForm } }, this.data.isEdit ? this.data.id : '')" "updateCustomerWithIndicator(this.data.id, { customer: form, indicator: this.indicatorPayload() })" "saveCustomerWithIndicator({ customer: form, indicator: this.indicatorPayload() }, createSubmitKey)"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: customer form missing $pattern"
    exit 1
  fi
done

for pattern in "getCustomerIndicator" "INDICATOR_ITEMS" "indicatorForm" "indicatorItems" "optionsView" "className" "selectIndicatorScore(e)" "shouldSaveIndicator()" "indicatorPayload()" "return this.shouldSaveIndicator() ? { ...this.data.indicatorForm } : null"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: mobile customer form must save PC five-indicator fields inline: $pattern"
    exit 1
  fi
done

for pattern in "客情五项指标" "{{indicatorTotal}} / 15 分" "开票、回款均 >= 100 万；五项总分 >= 12 分为核心客户。" 'bindtap="selectIndicatorScore"' 'score-option' 'indicator-inline-card'; do
  if ! rg -Fq "$pattern" "$FORM_WXML" "$ROOT/aigicrm-miniprogram/pages/customer/form/index.wxss"; then
    echo "FAIL: mobile customer form inline indicator UI missing $pattern"
    exit 1
  fi
done

for pattern in "formView.submitLoading" "formView.submitText" "item.optionsView" "option.className" "option.score" "option.label"; do
  if ! rg -Fq "$pattern" "$FORM" "$FORM_WXML"; then
    echo "FAIL: mobile customer form WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done
if rg -n "item\\.value === score|\\{\\{saving\\}\\}" "$FORM_WXML"; then
  echo "FAIL: mobile customer form WXML must not keep indicator active or saving fallback logic"
  exit 1
fi

if rg -Fq "保存后可添加联系人与录入客情五项指标" "$FORM_WXML"; then
  echo "FAIL: mobile customer form must not defer indicator entry until after saving"
  exit 1
fi

for pattern in "isPoolCustomer: item.status === 'sea'" "originalOwnerId" "if (this.data.isPoolCustomer)" "form.ownerId = this.data.originalOwnerId"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: mobile customer form must keep PC public-sea owner boundary: $pattern"
    exit 1
  fi
done

for pattern in 'disabled="{{isPoolCustomer}}"' '公海池内客户负责人不可修改，请通过捞取流程变更'; do
  if ! rg -Fq "$pattern" "$FORM_WXML"; then
    echo "FAIL: mobile customer form public-sea owner UI boundary missing $pattern"
    exit 1
  fi
done

if rg -Fq "导入模板" "$FORM_WXML"; then
  echo "FAIL: mobile customer form must not mention PC-only import templates"
  exit 1
fi

if ! rg -Fq "是否终端客户（是=终端客户，否=非终端客户）" "$FORM_WXML"; then
  echo "FAIL: mobile customer terminal tip must explain mobile business semantics"
  exit 1
fi

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

for pattern in "name: String(this.data.form.name || '').trim()" "position: String(this.data.form.position || '').trim()" "phone: String(this.data.form.phone || '').trim()" "remark: String(this.data.form.remark || '').trim()" "addCustomerContact(this.data.id, form)" "updateCustomerContact(this.data.id, this.data.contactId, form)"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.js"; then
    echo "FAIL: mobile contact form must normalize and submit PC-aligned fields: $pattern"
    exit 1
  fi
done
for pattern in "buildContactFormView" "contactFormView" "contactFormView.fields" "contactFormView.submitLoading" "contactFormView.cancelDisabled" "contactFormView.cancelText" "applyState(patch"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.wxml"; then
    echo "FAIL: mobile contact form WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done
for pattern in 'form-actions' 'form-cancel-btn' 'form-submit-btn' 'form-top-cancel'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.wxss"; then
    echo "FAIL: mobile contact form refined action bar missing $pattern"
    exit 1
  fi
done
for pattern in "cancelText: isEdit ? '取消编辑' : '取消新增'" "cancelDisabled" "getCurrentPages"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.js"; then
    echo "FAIL: mobile contact form cancel state/action missing $pattern"
    exit 1
  fi
done
if rg -n "isEdit \\?|\\{\\{form\\.|\\{\\{saving\\}\\}" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.wxml"; then
  echo "FAIL: mobile contact form WXML must not keep edit/form/saving fallback logic"
  exit 1
fi

if rg -n "email|邮箱" "$ROOT/aigicrm-miniprogram/pages/customer/contact" "$ROOT/aigicrm-miniprogram/utils/demo-contacts.js"; then
  echo "FAIL: mobile contact UI must stay aligned with PC fields: name, position, phone, remark"
  exit 1
fi

for pattern in "updateDemoContact" "deleteDemoContact" "updateCustomerContact(this.data.id, this.data.contactId, form)" "deleteCustomerContact(this.data.id, item.id)" "editContact(e)" "deleteContact(e)"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.js" "$ROOT/aigicrm-miniprogram/utils/demo-contacts.js"; then
    echo "FAIL: mobile contact update/delete boundary missing $pattern"
    exit 1
  fi
done

for pattern in "remark=" "decodeURIComponent(options.remark || '')" "备注"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/detail/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/detail/index.wxml"; then
    echo "FAIL: mobile contact detail remark display missing $pattern"
    exit 1
  fi
done
for pattern in 'buildContactDetailView' 'contactDetailView' 'nameText' 'positionText' 'mobileText' 'remarkText' 'mobileValue'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/detail/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/detail/index.wxml"; then
    echo "FAIL: mobile contact detail must normalize display state in JS: $pattern"
    exit 1
  fi
done
if rg -n "name \\|\\||position \\|\\||mobile \\|\\||remark \\|\\||\\{\\{name\\}\\}|\\{\\{position\\}\\}|\\{\\{mobile\\}\\}|\\{\\{remark\\}\\}" "$ROOT/aigicrm-miniprogram/pages/customer/contact/detail/index.wxml"; then
  echo "FAIL: mobile contact detail WXML still keeps display fallback logic"
  exit 1
fi

for pattern in 'catchtap="editContact"' 'catchtap="deleteContact"' 'contact-call-action' 'contact-edit-action' 'contact-delete-action'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.wxml"; then
    echo "FAIL: mobile contact update/delete UI missing $pattern"
    exit 1
  fi
done
for pattern in "编辑联系人" "保存修改"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/contact/form/index.wxml"; then
    echo "FAIL: mobile contact form edit copy missing $pattern"
    exit 1
  fi
done
for pattern in 'buildContactView' 'applyState(patch)' 'nameText' 'positionText' 'phoneText'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.js"; then
    echo "FAIL: mobile contact list must normalize display state in JS: $pattern"
    exit 1
  fi
done
for pattern in 'contactView.contacts' 'contactView.showEmpty' 'contactView.showAddAction' 'item.phoneValue'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.wxml"; then
    echo "FAIL: mobile contact list WXML must use normalized view state: $pattern"
    exit 1
  fi
done
if rg -n "item\\.(name|contactName|position|title|mobile|phone) \\|\\||contacts\\.length|!loading &&|wx:else" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.wxml"; then
  echo "FAIL: mobile contact list WXML still keeps display fallback logic"
  exit 1
fi
for pattern in '.contact-call-action' '.contact-edit-action' '.contact-delete-action' 'height:50rpx'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/contact/index.wxss"; then
    echo "FAIL: mobile contact compact action style missing $pattern"
    exit 1
  fi
done

for pattern in "saveDemoFinance" "readDemoFinance"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/utils/demo-finance.js"; then
    echo "FAIL: demo finance persistence missing $pattern"
    exit 1
  fi
done
for pattern in "deleteDemoFinance" "deleteInvoice(this.data.id, item.id, reason)" "deletePayment(this.data.id, item.id, reason)" "deleteRecord(e)"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/utils/demo-finance.js"; then
    echo "FAIL: mobile finance delete boundary missing $pattern"
    exit 1
  fi
done
for pattern in 'catchtap="deleteRecord"' 'data-mode="invoice"' 'data-mode="payment"' 'finance-delete-btn'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxss"; then
    echo "FAIL: mobile finance delete UI missing $pattern"
    exit 1
  fi
done
for pattern in 'height:50rpx' 'border:1rpx solid #fecaca' 'finance-delete-btn::after'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxss"; then
    echo "FAIL: mobile finance compact delete style missing $pattern"
    exit 1
  fi
done
for pattern in "normalizeInvoice" "normalizePayment" "invoiceType" "invoiceCompany" "paymentMethod" "remark"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml" "$AUTOMATOR"; then
    echo "FAIL: mobile finance PC field parity missing $pattern"
    exit 1
  fi
done
for pattern in "buildFinanceListView" "refreshFinanceListView" "financeListView" "invoiceEmptyText" "paymentEmptyText" "invoiceRows" "paymentRows" "showAddAction" "showDeleteAction" "applyState(patch"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml"; then
    echo "FAIL: mobile finance list WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done
for pattern in "invoiceTypeOptions" "chooseInvoiceType" "form.invoiceType" "发票类型" "增值税专票" "增值税普票"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml"; then
    echo "FAIL: mobile finance invoice form field parity missing $pattern"
    exit 1
  fi
done
for pattern in "buildFinanceFormView" "refreshFinanceFormView" "financeFormView" "invoiceTypeText" "submitText" "invoicePrimaryFields" "invoiceAmountFields" "paymentFields" "remarkValue" "submitLoading" "cancelDisabled" "cancelText" "applyState(patch"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml"; then
    echo "FAIL: mobile finance form WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done
for pattern in 'form-actions' 'form-cancel-btn' 'form-submit-btn' 'form-top-cancel'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxss"; then
    echo "FAIL: mobile finance form refined action bar missing $pattern"
    exit 1
  fi
done
for pattern in "cancelText: isInvoice ? '取消开票' : '取消回款'" "cancelDisabled" "getCurrentPages"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js"; then
    echo "FAIL: mobile finance form cancel state/action missing $pattern"
    exit 1
  fi
done
if rg -n "mode==='invoice'|tab==='invoice'|tab==='payment'|\\|\\| '开票记录'|\\|\\| '回款记录'|\\|\\| '-'|新增\\{\\{|\\{\\{loading\\}\\}|\\{\\{invoices\\}\\}|\\{\\{payments\\}\\}|\\{\\{canEdit\\}\\}|\\{\\{saving\\}\\}|\\{\\{form\\." "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml"; then
  echo "FAIL: mobile finance WXML must not keep mode/tab fallback branching logic"
  exit 1
fi
if ! rg -Fq "hasPermission('finance:view') || !hasPermission('customer:edit')" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js"; then
  echo "FAIL: mobile finance form direct entry must require finance:view plus customer:edit"
  exit 1
fi

for pattern in "buildLogFormView" "logFormView" "counterText" "submitLoading" "cancelDisabled" "cancelText" "applyState(patch" "addCustomerLog(this.data.id, { content })"; do
  if ! rg -Fq "$pattern" "$LOG_JS" "$LOG_WXML"; then
    echo "FAIL: mobile customer log form WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done
for pattern in 'form-actions' 'form-cancel-btn' 'form-submit-btn' 'form-top-cancel'; do
  if ! rg -Fq "$pattern" "$LOG_WXML" "$ROOT/aigicrm-miniprogram/pages/customer/log/index.wxss"; then
    echo "FAIL: mobile customer log form refined action bar missing $pattern"
    exit 1
  fi
done
for pattern in "cancelText: '取消记录'" "cancelDisabled" "getCurrentPages"; do
  if ! rg -Fq "$pattern" "$LOG_JS"; then
    echo "FAIL: mobile customer log form cancel state/action missing $pattern"
    exit 1
  fi
done
if rg -n "\\{\\{content\\.length\\}\\}|\\{\\{content\\}\\}|\\{\\{saving\\}\\}|maxlength=\"1000\"" "$LOG_WXML"; then
  echo "FAIL: mobile customer log form WXML must not keep content/saving fallback logic"
  exit 1
fi

for pattern in "function addCustomer(data)" "function updateCustomer(id, data)" "function deleteCustomer(id)" "function checkCustomerName(name)" "function saveCustomerRelationship(id, score)" "function addCustomerContact(id, data)" "function updateCustomerContact(id, contactId, data)" "function deleteCustomerContact(id, contactId)" "function addInvoice(id, data)" "function deleteInvoice(id, invoiceId, reason)" "function addPayment(id, data)" "function deletePayment(id, paymentId, reason)"; do
  if ! rg -Fq "$pattern" "$API"; then
    echo "FAIL: customer API missing $pattern"
    exit 1
  fi
done

for pattern in "hasPermission('customer:delete')" "deleteCurrentCustomer()" "deleteCustomer(this.data.id)" "客户已删除"; do
  if ! rg -Fq "$pattern" "$DETAIL_JS"; then
    echo "FAIL: mobile customer delete boundary missing $pattern"
    exit 1
  fi
done

for pattern in 'bindtap="deleteCurrentCustomer"' '危险操作' '删除客户' 'customer-delete-action'; do
  if ! rg -Fq "$pattern" "$DETAIL_WXML" "$DETAIL_WXSS"; then
    echo "FAIL: mobile customer delete UI missing $pattern"
    exit 1
  fi
done
for pattern in 'height: 50rpx' 'border: 1rpx solid #fecaca' 'customer-delete-action::after'; do
  if ! rg -Fq "$pattern" "$DETAIL_WXSS"; then
    echo "FAIL: mobile customer compact delete style missing $pattern"
    exit 1
  fi
done

for pattern in "saveCustomerRelationship(this.data.id, score)" "relationshipOptions" "selectRelationship(event)" "客情已保存"; do
  if ! rg -Fq "$pattern" "$DETAIL_JS"; then
    echo "FAIL: mobile customer relationship quick-edit missing $pattern"
    exit 1
  fi
done

for pattern in 'bindtap="selectRelationship"' 'relationship-option' 'relationship-options'; do
  if ! rg -Fq "$pattern" "$DETAIL_WXML" "$DETAIL_WXSS"; then
    echo "FAIL: mobile customer relationship UI missing $pattern"
    exit 1
  fi
done

for pattern in "buildIndicatorSummary" "invoiceMet" "paymentMet" "scoreTotal" "coreMet" "invoiceStatusClass" "coreSummaryText"; do
  if ! rg -Fq "$pattern" "$DETAIL_JS"; then
    echo "FAIL: mobile customer indicator summary missing $pattern"
    exit 1
  fi
done

for pattern in "buildRelationshipOptions" "relationshipOptionsView" "className"; do
  if ! rg -Fq "$pattern" "$DETAIL_JS" "$DETAIL_WXML"; then
    echo "FAIL: mobile customer relationship WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done

for pattern in "terminalLabel" "capacity" "managerName" "deptName" "branchCompany" "unitName" "areaText" "registerDate" "lastInvoiceDate" "remark"; do
  if ! rg -Fq "$pattern" "$DETAIL_JS" "$DETAIL_WXML" "$ROOT/scripts/miniprogram/verify-customer-center.mjs"; then
    echo "FAIL: mobile customer detail PC info field missing $pattern"
    exit 1
  fi
done

for pattern in "开票门槛" "回款门槛" "五项评分" "核心状态" "indicator-summary-grid" "indicator-detail-list"; do
  if ! rg -Fq "$pattern" "$DETAIL_WXML" "$DETAIL_WXSS"; then
    echo "FAIL: mobile customer indicator summary UI missing $pattern"
    exit 1
  fi
done

if rg -n "indicatorSummary\\.[a-zA-Z]+ \\?|'达标' : '未达标'|coreMet \\?|relationshipScore.*\\?" "$DETAIL_WXML"; then
  echo "FAIL: mobile customer detail WXML must not keep indicator/relationship branching logic"
  exit 1
fi

if rg -Fq "均大于100万" "$DETAIL_WXML"; then
  echo "FAIL: mobile customer indicator threshold must be data-driven, not hard-coded"
  exit 1
fi

for pattern in "记录人：" "item.userName || item.creatorName || item.createByName" "buildLogView"; do
  if ! rg -Fq "$pattern" "$DETAIL_JS"; then
    echo "FAIL: mobile customer log summary must show PC-style owner/time meta: $pattern"
    exit 1
  fi
done
for pattern in "detailView.logs" "item.userText" "item.timeText" "customer-log-meta"; do
  if ! rg -Fq "$pattern" "$DETAIL_WXML" "$DETAIL_WXSS"; then
    echo "FAIL: mobile customer log summary WXML must use normalized owner/time meta: $pattern"
    exit 1
  fi
done

for pattern in "checkCustomerName(name)" "checkDuplicateName()" "dupInfo.exists" "仍要继续保存吗"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: mobile customer duplicate-name check missing $pattern"
    exit 1
  fi
done

for pattern in 'bindblur="checkDuplicateName"' 'warning-tip' '此客户已在'; do
  if ! rg -Fq "$pattern" "$FORM_WXML" "$ROOT/aigicrm-miniprogram/pages/customer/form/index.wxss"; then
    if ! rg -Fq "$pattern" "$FORM"; then
      echo "FAIL: mobile customer duplicate-name UI missing $pattern"
      exit 1
    fi
  fi
done

for pattern in 'customer-form-actions' 'form-cancel-btn' 'form-submit-btn' 'form-top-cancel' 'formView.cancelDisabled' 'formView.cancelText'; do
  if ! rg -Fq "$pattern" "$FORM_WXML" "$ROOT/aigicrm-miniprogram/pages/customer/form/index.wxss"; then
    echo "FAIL: mobile customer form refined action bar missing $pattern"
    exit 1
  fi
done

for pattern in "cancelText: data.isEdit ? '取消编辑' : '取消新增'" "cancelDisabled" "getCurrentPages"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: mobile customer form cancel state/action missing $pattern"
    exit 1
  fi
done

for pattern in "buildFormView" "refreshFormView" "formView"; do
  if ! rg -Fq "$pattern" "$FORM" "$FORM_WXML"; then
    echo "FAIL: mobile customer form display state must be normalized in JS: $pattern"
    exit 1
  fi
done

if rg -n "isEdit \\?|form\\.(groupName|industry|devType|regionId|officeId|ownerId|managerId|province|registerDate|lastInvoiceDate) \\?|regionIndex >= 0|officeIndex >= 0|ownerIndex >= 0|managerIndex >= 0|terminalOptions\\[terminalIndex\\]|canEditDates \\?" "$FORM_WXML"; then
  echo "FAIL: mobile customer form WXML must not keep picker/title/date branching logic"
  exit 1
fi

echo "PASS: mobile customer form, contact, and finance create/update boundary is aligned."
