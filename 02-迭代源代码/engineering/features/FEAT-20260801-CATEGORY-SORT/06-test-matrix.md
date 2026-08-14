# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | SQL 回读：`SELECT id, category_name, category_code, sort_order FROM biz_incentive_category WHERE parent_id=0 ORDER BY sort_order` → 产品试点 trial/1、两谈两做方案 plan/2、交流拜访 exchange/3、外出参观 visit/4 |
| AC-002 | TEST-002 | PASS | 浏览器 http://111.229.37.220/incentive（1440x900）：顶部四区 Tab DOM 顺序=产品试点→两谈两做方案→交流拜访→外出参观，默认选中"产品试点"（弹窗分组"产品试点—选择子项上传文件"，子项 涂层类/楼梯板类/机封类/密封类/润滑类）；evidence/ui-1440x900.png |
| AC-003 | TEST-003 | PASS | 浏览器 /system/config → Tab"激励类别与权限"（1280x720）：分组 DOM 顺序=产品试点(5子项)→两谈两做方案(3)→交流拜访(2)→外出参观(2)；evidence/ui-1280x720.png |

## 静态与构建验证

- 无代码变更 → 无 lint/typecheck/build 增量；run-gates（npm build + mvn package）验证基线（见 evidence/gate-report）

## 功能与集成验证

- getCategoryTree API 顺序即数据库 sort_order 顺序（IncentiveService.java:81-85 按 sort_order 升序）——SQL 回读即为 API 契约验证
- 上传弹窗"选择子项上传文件"分组（index.vue:44-63）随 tree 顺序，实测为产品试点在下/外出参观在上？→ 实测弹窗分组标题=产品试点（首区），一致

## UI 验证

- 双视口 1440x900 + 1280x720 截图；激励上传页 console 0 errors（system/config 页 logo.jpg 404 为既有问题，非本次引入）
- 交互：点击四区 Tab 切换正常（activeTab 联动 loadRecords）

## 回归验证

- 子项在其父区内顺序不变（涂层/楼梯板/机封/密封/润滑 原序）
- 记录表格内排序（code 权重 + 时间倒序）不受影响
- 默认 activeTab 变为"产品试点"（parentCategories[0]），符合目标

## 未覆盖与风险

- 生产环境未部署（测试服已验证）；生产同步需用户授权
