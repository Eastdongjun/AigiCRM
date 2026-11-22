# UI-STATS-BORDER-001 — 法布施统计看板 PC 布局缺陷（CLOSED 2026-11-19）

- 来源：用户 PC 验收原话"统计看板 UI 页面的边框位置不合理"（2026-11-19）
- 修复：45e55eb3（.table-scroll 卡内滚动/::before 内嵌圆角条+顶色统一/m-grid 底距/row-gap+align-self/末行收敛+132px ellipsis）
- 验证：QC 四门禁自跑 PASS；测试服实测 #1 数学级确认（末行完全包含）+#2/#4/#5 视觉消除+#3 代码确认；生产 dist index-BtipTiCr.js 与测试服同构建哈希 ⇒ 生产视觉一致
- 重设计批 UI-STATS-REDESIGN-001（ee85bf3e）在此基底上完成并已上线，五子项零回归（实测确认）
- 状态：**CLOSED**
