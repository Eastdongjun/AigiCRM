# 20260917 miniprogram full sweep （小程序全量扫描）

## 基线

- `dev-verify.sh miniprogram`：PASS（46 pages；71 JS, 48 JSON, 82 WXML/WXSS）。
- `dev-verify.sh contracts`：初跑 RED（fail-fast），修两处契约后 **116 PASS / 0 FAIL / exit 0**。

## 修了两处门禁（均为契约侧过期/误报，业务保护本身完好）

1. `scripts/check-sensitive-permission-contract.sh`（P0，门禁红）
   - 客户详情页已重构为归一化 view-model（`showFinanceSection: hasDetail &&
     canViewFinance` / `showContactsSection: hasDetail && canViewContacts`，
     请求侧 `financeRequest/contactsRequest` 条件 intact），契约仍 grep 旧字面
     `detail && canViewFinance`。
   - 改为断言新链路：JS 内 `show*Section: hasDetail && canView*` + WXML 内
     `detailView.show*Section`。复验 PASS。
2. `scripts/check-mobile-navigation-contract.py`（P0，门禁红）
   - 旧启发式：文件级 `onLoad()` + 任意 `options.` 即判错；误伤
     `pages/incentive/index.js`（`options.` 是 saveFile/选择器局部变量）。
   - 收紧到 onLoad 函数体作用域：体外变量不算，体内 `const/let/var options`
     声明不算；WXML 保持原 coarse 检查。复验
     `PASS: 36 targets + 5 tabBar = 41 reachable / 46 registered`。

## 疏漏清单（分级）

- P1 `pages/notification/index`：老提醒实现（95 行），零入口；现行
  `pages/reminder/index`（372 行）全功能覆盖。建议确认后从 app.json 注销并删目录。
  （未动：删页面不可逆，等用户拍板。）
- P1 `pages/ranking/detail/index`：view-model 完整（score:view 门控、onLoad(options)），
  但排行页只跳客户详情，无任何入口。要么在排行页接明细入口，要么注销。
  （未动：等用户拍板。）
- OK `pages/file/index` → `switchTab files/index` 重定向桩，有意保留。
- OK `pages/cases/*` → incentive 重定向桩，有意保留。
- OK 41/46 可达差额即上述 5 页（2 桩 + 3 候选），其余 41 页均有静态入口或 tabBar。

## 干净项（逐项验过）

- 零 broken require（全量相对引用解析通过）。
- 零 `console.log`、零 TODO/FIXME/XXX（pages/utils/api）。
- 标题命名无漂移（激励相关 navigationBarTitle 均为四项激励）。
- 无 http 明文资源引用；tabBar 5 项文件齐全。
- WXML 绑定 handler 逐个核对（4 个初筛命中经查均为逗号头写法误报，实际全绑定）。

## Status

门禁全绿；P1 两项待用户决策（删/接入口），暂未动手。
