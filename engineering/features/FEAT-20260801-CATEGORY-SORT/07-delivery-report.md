# 交付报告

## 结果

激励类别四区顺序已调整为：**产品试点（最前）→ 两谈两做方案 → 交流拜访 → 外出参观（最后）**。激励上传页顶部四区 Tab、上传弹窗子项分组、系统设置类别管理分组全部同步生效，默认选中"产品试点"。AC-001~003 全 PASS，测试服已验证。

## 交付范围

- REQ-001 → AC-001/002/003：顶层类别 sort_order 调整（trial=1/plan=2/exchange=3/visit=4）
- 行为变化：上传页默认 Tab 由"外出参观"变为"产品试点"；四区 Tab/分组顺序反转
- 明确排除：子项排序、四区样式形态、生产部署

## 变更清单

- 数据：测试服 `ycmn.biz_incentive_category` 顶层 4 行 sort_order（ID 1/2/3/4 → 4/3/2/1）
- 代码：无（前端/后端零改动）

## 安装与启动

- 无部署；测试服数据变更即时生效。生产同步（待授权）：对生产库执行同一 4 条 UPDATE（按 id，需先核对 id 与 code 映射）

## 验证证据

- SQL 回读：产品试点=1/两谈两做=2/交流拜访=3/外出参观=4（evidence 备份文件 biz_incentive_category_backup_20260801_sort.sql 为改前快照）
- 浏览器：/incentive 四区 Tab 顺序 + /system/config 分组顺序（evidence/ui-1440x900.png、ui-1280x720.png）
- UI 复核：evidence/ui-review.json（score 90，console 0 errors）
- preflight/gates：evidence/preflight-report-*.json、gate-report-*.json

## 部署状态与步骤

- **已验证发布候选**（测试服 111.229.37.220）；生产未部署，需用户授权

## 回滚与恢复

- 反向 UPDATE（visit=1/exchange=2/plan=3/trial=4）或执行备份 SQL evidence/biz_incentive_category_backup_20260801_sort.sql
- 回滚后验证：getCategoryTree 顺序恢复为 外出参观→交流拜访→两谈两做方案→产品试点

## 已知限制与残余风险

- 生产未部署
- 系统设置页 logo.jpg 404 为既有问题（非本次引入）

## 版本与追踪

- FEAT-20260801-CATEGORY-SORT；测试服 MySQL 8.0.28（shared-mysql 容器）；无代码 commit（纯数据变更）
