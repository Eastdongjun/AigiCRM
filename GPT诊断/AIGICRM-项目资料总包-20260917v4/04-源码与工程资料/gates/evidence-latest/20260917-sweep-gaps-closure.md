# 20260917 sweep gaps closure （疏漏处理 + 统计页边框）

## 1. P1 疏漏处置结论

- `pages/notification/*`：**保留，不删**。复查发现整树被 3 个契约 + 1 个自动机钉死：
  `check-mobile-feedback-contract.sh`（引用 index/detail 的 wxml/wxss/js 共 6 处）、
  `check-prototype-state-contract.sh`（grep 其 processing 状态机）、
  `check-feature-audit-contract.py`（清单列名）、
  `verify-feedback-notification.mjs`（reLaunch 该页做反馈验证）。
  它当前身份是反馈契约的验证夹具页，而非可删死代码。删它要连带改 4 处门禁，
  得不偿失。决议：保留注册，记为 fixture。
- `pages/ranking/detail/*`：**接入口**（本轮已做）。ranking 契约要求该页存在且
  view-model 归一化，但之前零入口。现积分排行行可点，直达个人明细。
- `pages/file/index`、`pages/cases/*`：重定向桩（→ files/incentive），有意保留。
- 可达性：41/46 → **42/46**；剩余 4 个未直连均有明确身份（2 桩 + 1 fixture 树）。

## 2. 排行明细入口（本轮新增）

- `pages/ranking/index.wxml`：积分排行卡片加 `data-index + bindtap="openScoreDetail"`。
- `pages/ranking/index.js`：新增 `openScoreDetail`，从 `scoreList` 取
  realName/totalScore/deptName|officeName，拼 `name/score/dept/period`
  （period 取 `rankingView.scorePeriodText`）跳 `pages/ranking/detail/index`。
  `scoreListView` 与 `scoreList` 同序 map，index 对齐。
- detail 页 `onLoad(options)` 侧零改动（name/score/dept/period 全有默认值）。

## 3. 经营分析边框（本轮新增）

- 现象：保护期雷达包在白色边框卡片 `.stats-risk-panel` 里，经营分析 head+grid
  裸奔。
- 改法：`pages/statistics/index.wxml` 把经营分析 head+grid 包进同一个
  `stats-risk-panel`（纯 WXML 结构调整，无样式/逻辑新增）。
- 统计契约中 `.statistics-page .stats-section-head .text-btn` 断言不受影响（类名未动）。

## Verification

- `node --check ranking/index.js`：通过。`git diff --check`：通过。
- navigation 契约：`PASS: 37 targets + 5 tabBar = 42 reachable / 46`。
- ranking 契约 / statistics 契约：PASS。
- `dev-verify.sh contracts`：**116 PASS / 0 FAIL / exit 0**。
- 开发者工具 CLI preview（statistics/index）：构建成功 802.5 KB。
