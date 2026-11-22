# DICT-RPX-001 — PC 端 DictManager 使用小程序单位 rpx（Sonar BLOCKER）

- 来源：P0-5 本地 Sonar 全仓扫描（`css:S4653`，3 条重复报告）
- 严重级：Blocker（CSS 单位无效，样式可能不生效）
- 位置：`02-迭代源代码/aigicrm-ui/src/views/system/DictManager.vue:346`
- 现状：`.dict-module-title { ... padding: 10rpx 4rpx 6rpx; ... }`
- 问题：`rpx` 是小程序单位，PC 浏览器无此单位；该 padding 声明会被浏览器忽略
- 全仓排查：PC `src` 下 `rpx` 仅此 1 处（grep 确认）
- 修复建议：改为 px 等效值（如 `5px 2px 3px`，以设计稿为准；rpx→px 通常按 750 设计稿 /2）
- 验收标准：
  1. `npm test`（340/0）、`npm run build`（0）、`check-ui-typecheck.sh`（537/537）保持绿
  2. Sonar 重扫 `Eastdongjun_AigiCRM-local`，`css:S4653` = 0
  3. 页面 DictManager 标题区间距视觉正常（浏览器实拍/截图）
- 归属：实施方修复（协调方派单），QC 复验；QC 不自行改代码
- 状态：OPEN（2026-11-18）

## 同批建议（非 Blocker，按需）

- 防御性修复 4 条：`FileScanService.java:50`、`FileService.java:1296`（S2142 中断位）、`IncentiveService.java:564`（正则前长度守卫）、`GroupScopeUtil.java:83`（Optional.get 空流守卫）
- 其余 7 条为误报/有意写法或纯债务，见 `../evidence/sonar-20261118/README.md` §十
