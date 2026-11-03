#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FORM="$ROOT/aigicrm-miniprogram/pages/customer/form/index.js"
FORM_WXML="$ROOT/aigicrm-miniprogram/pages/customer/form/index.wxml"
# 【D24/D25 锚点重钉 2026-10-05】五项指标配置/选项视图/核心门槛阈值/各表单返回兜底收敛到
# pages/customer/shared.js 单一出处,相关锚点改钉 shared
SHARED="$ROOT/aigicrm-miniprogram/pages/customer/shared.js"
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
# 【C5 2026-10-21】提交幂等键绑定表单实例(this._submitKey, onLoad 生成/成功与销毁清空):
# 原模块级 createSubmitKey 会在"超时失败→另开新客户表单"时被新实例复用, 后端按键回放旧客户 id
for pattern in "hasPermission(id ? 'customer:edit' : 'customer:add')" "readDemoCustomers().find" "getApp().globalData.demoMode" "saveDemoCustomer({ ...form, indicator: { ...this.data.indicatorForm } }, this.data.isEdit ? this.data.id : '')" "updateCustomerWithIndicator(this.data.id, { customer: form, indicator: this.indicatorPayload() })" "this._submitKey = newSubmitKey()" "saveCustomerWithIndicator({ customer: form, indicator: this.indicatorPayload() }, this._submitKey)"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: customer form missing $pattern"
    exit 1
  fi
done
# 【C5 2026-10-21】防回潮: 模块级幂等键变量不得复活(跨实例复用=新客户静默未建); 键须随实例销毁清理
if rg -n "let +createSubmitKey|var +createSubmitKey" "$FORM"; then
  echo "FAIL: customer form must not keep a module-level submit key (C5: bind key to form instance)"
  exit 1
fi
for pattern in "this._submitKey = null" "this._submitKeyName"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: customer form instance submit key lifecycle missing $pattern"
    exit 1
  fi
done

for pattern in "getCustomerIndicator" "indicatorForm" "indicatorItems" "buildIndicatorItems" "className" "selectIndicatorScore(e)" "shouldSaveIndicator()" "indicatorPayload()" "return this.shouldSaveIndicator() ? { ...this.data.indicatorForm } : null"; do
  if ! rg -Fq "$pattern" "$FORM"; then
    echo "FAIL: mobile customer form must save PC five-indicator fields inline: $pattern"
    exit 1
  fi
done

# 【D24 锚点重钉 2026-10-05】五项指标配置与选项视图构建(INDICATOR_ITEMS/optionsView)移入 shared,表单/指标维护页/详情共用
for pattern in "INDICATOR_ITEMS" "optionsView" "className: value === score ? 'score-option active' : 'score-option'"; do
  if ! rg -Fq "$pattern" "$SHARED"; then
    echo "FAIL: mobile customer five-indicator config missing in shared: $pattern"
    exit 1
  fi
done

# 【2026-11-03 门槛配置化重钉】核心双门槛(争核心50/真核心100)改由管理员在系统配置设定,
# MP 不再落死值: 金额门槛常量退役(反钉), 12 分客情参考线与 CORE_THRESHOLD_TIP(无数字通用文案)保留(正钉)
for pattern in "CORE_INDICATOR_SCORE_THRESHOLD = 12" "CORE_THRESHOLD_TIP" "coreTypeLabel"; do
  if ! rg -Fq "$pattern" "$SHARED"; then
    echo "FAIL: mobile customer core threshold tip/label missing in shared: $pattern"
    exit 1
  fi
done
for pattern in "CORE_INVOICE_AMOUNT_THRESHOLD" "CORE_PAYMENT_AMOUNT_THRESHOLD"; do
  if rg -Fq "$pattern" "$SHARED"; then
    echo "FAIL: mobile shared must not hardcode amount threshold constants (config-driven since 2026-11-03): $pattern"
    exit 1
  fi
done

for pattern in "客情五项指标" "{{indicatorTotal}} / 15 分" "{{coreThresholdTip}}" 'bindtap="selectIndicatorScore"' 'score-option' 'indicator-inline-card'; do
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
# 【D24 锚点重钉 2026-10-05】各表单 goBack/cancel 兜底改用 shared.navigateBackWithFallback,
# getCurrentPages 栈深判断统一钉在 shared(下方 contact/finance/log/customer 表单同)
if ! rg -Fq "getCurrentPages" "$SHARED"; then
  echo "FAIL: shared navigateBackWithFallback missing getCurrentPages stack check"
  exit 1
fi
for pattern in "cancelText: isEdit ? '取消编辑' : '取消新增'" "cancelDisabled" "navigateBackWithFallback"; do
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
# 【2026-10-11 钉随裁决】0e747a5f 阶段总额制改造: MP 财务页转只读历史展示(历史开票/回款),
# 删除入口随逐笔维护界面一并下线(删除仍走 PC FinanceTab, e9f5ad61 带留痕); demo 持久化钉保留
for pattern in "deleteDemoFinance"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/utils/demo-finance.js"; then
    echo "FAIL: mobile demo finance persistence missing $pattern"
    exit 1
  fi
done
# 删除 UI 三钉随界面下线退役(防复活守卫替代)
if rg -Fq 'catchtap="deleteRecord"' "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml"; then
  echo "FAIL: mobile finance delete UI must not be revived (read-only history per 2026-10-10 stage-amount ruling)"
  exit 1
fi
for pattern in 'height:50rpx' 'border:1rpx solid #fecaca' 'finance-delete-btn::after'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxss"; then
    echo "FAIL: mobile finance compact delete style missing $pattern"
    exit 1
  fi
done
# 【2026-10-11 钉随重构】阶段总额制后逐笔记录转历史只读展示: normalize* 更名 normalizeHistory*
for pattern in "normalizeHistoryInvoice" "normalizeHistoryPayment" "invoiceType" "invoiceCompany" "paymentMethod" "remark"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml" "$AUTOMATOR"; then
    echo "FAIL: mobile finance PC field parity missing $pattern"
    exit 1
  fi
done
# 【2026-10-11 钉随重构】财务页视图态=buildStageView(阶段总额+三态+历史明细折叠)
for pattern in "buildStageView" "refreshStageView" "stageView" "emptyText" "invoiceRows" "paymentRows" "showAddAction" "coreTagText"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml"; then
    echo "FAIL: mobile finance stage view must be normalized in JS: $pattern"
    exit 1
  fi
done
# 【2026-10-11 钉随裁决】阶段总额制: 发票/回款逐笔表单→阶段数据表单(周期 H1/H2/FY + 年度 + 两金额)
for pattern in "PERIOD_TYPES" "上半年" "下半年" "全年" "saveCustomerStageAmount" "invoiceAmount" "paymentAmount"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml"; then
    echo "FAIL: mobile finance stage form field parity missing $pattern"
    exit 1
  fi
done
for pattern in "buildStageFormView" "stageFormView" "submitText" "submitLoading" "cancelDisabled" "cancelText"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml"; then
    echo "FAIL: mobile finance stage form WXML state must be normalized in JS: $pattern"
    exit 1
  fi
done
for pattern in 'form-actions' 'form-cancel-btn' 'form-submit-btn' 'form-top-cancel'; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxss"; then
    echo "FAIL: mobile finance form refined action bar missing $pattern"
    exit 1
  fi
done
for pattern in "cancelText: '取消'" "cancelDisabled" "navigateBackWithFallback"; do
  if ! rg -Fq "$pattern" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js"; then
    echo "FAIL: mobile finance form cancel state/action missing $pattern"
    exit 1
  fi
done
if rg -n "mode==='invoice'|tab==='invoice'|tab==='payment'|\\|\\| '开票记录'|\\|\\| '回款记录'|\\|\\| '-'|新增\\{\\{|\\{\\{loading\\}\\}|\\{\\{invoices\\}\\}|\\{\\{payments\\}\\}|\\{\\{canEdit\\}\\}|\\{\\{saving\\}\\}|\\{\\{form\\." "$ROOT/aigicrm-miniprogram/pages/customer/finance/index.wxml" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.wxml"; then
  echo "FAIL: mobile finance WXML must not keep mode/tab fallback branching logic"
  exit 1
fi
if ! rg -Fq "hasPermission('finance:manage') || (hasPermission('customer:edit') && hasPermission('finance:view'))" "$ROOT/aigicrm-miniprogram/pages/customer/finance/form/index.js"; then
  echo "FAIL: mobile finance form entry must match backend gate (finance:manage OR customer:edit AND finance:view)"
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
for pattern in "cancelText: '取消记录'" "cancelDisabled" "navigateBackWithFallback"; do
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

# 【2026-10-11 钉随裁决】三态化: coreMet 布尔下线, 前端只消费后端 coreType(真核心/争核心/非核心)
for pattern in "buildIndicatorSummary" "invoiceMet" "paymentMet" "scoreTotal" "coreType" "invoiceStatusClass" "coreSummaryText"; do
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

# 【2026-10-11 钉随裁决】三态化后摘要标签: 年度开票/年度回款(金额展示), 核心状态(三态)
for pattern in "年度开票" "年度回款" "五项评分" "核心状态" "indicator-summary-grid" "indicator-detail-list"; do
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

for pattern in "cancelText: data.isEdit ? '取消编辑' : '取消新增'" "cancelDisabled" "navigateBackWithFallback"; do
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
