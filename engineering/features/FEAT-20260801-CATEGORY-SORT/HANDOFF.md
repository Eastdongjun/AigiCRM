# HANDOFF — FEAT-20260801-CATEGORY-SORT（已完成）

> 新会话接手提示：读本文件即可恢复上下文。AC-001~003 全 PASS，测试服已验证。

## 当前状态（2026-08-01 19:55）

- **DELIVERED**（status --require-delivered exit=0），四区顺序已调整为：**产品试点（最前）→ 两谈两做方案 → 交流拜访 → 外出参观（最后）**
- 实现方式：纯数据变更——测试服 `ycmn.biz_incentive_category` 顶层 4 行 sort_order（trial=1/plan=2/exchange=3/visit=4），前端/后端代码零改动（排序由 getCategoryTree 按 sort_order 升序单一驱动）
- 生效范围：激励上传页四区 Tab（默认选中"产品试点"）、上传弹窗子项分组、系统设置→激励类别与权限分组、customer.vue/overview.vue（同接口）
- 备份：evidence/biz_incentive_category_backup_20260801_sort.sql（改前整表）

## 关键事实

- 消费方均取 getCategoryTree（IncentiveService.java:81-85 orderByAsc sortOrder），无独立硬编码
- 记录表格内排序为 code 权重（index.vue:450-452 trial→plan→exchange→visit），本来即是目标顺序，未动
- 测试服 111.229.37.220（ubuntu/redacted-ssh-password，sshpass）；MySQL 容器 shared-mysql；浏览器已验证（/incentive 与 /system/config）
- 功能包 evidence/ 含：备份 SQL、ui-1440x900.png、ui-1280x720.png、ui-review.json（score 90）、preflight/gate-report

## 残余风险（待用户确认）

1. **生产未部署**：同一 4 条 UPDATE 按 id 执行即可同步（需先核对生产库 id 与 code 映射，backup SQL 为测试服快照）
2. 系统设置页 logo.jpg 404 为既有问题（非本次引入）

## 下一步（若用户要求）

1. 生产同步四区排序（授权后执行）
2. 如需调整子项在区内的顺序：改对应子项 sort_order（config.vue 类别管理表格"排序"列可直接编辑）
