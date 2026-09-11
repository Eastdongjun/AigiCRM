# 测试矩阵

| 验收 | 测试 | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 浏览器 1440x900 系统设置→激励类别与权限：DOM 断言 groupCount=4，分组标题=外出参观/交流拜访/两谈两做方案/产品试点，各子项归属其下；evidence/ui-1440x900.png |
| AC-002 | TEST-002 | PASS | 点击一级分组头：body display:none 折叠 + 箭头旋转，再点恢复；childCounts=[2,2,3,5] 合计 12 子项完整 |
| AC-003 | TEST-003 | PASS | 数据库删除孤儿 46/47/48 后 remaining=16，API 树无"213/111"；备份 evidence/biz_incentive_category_backup_20260801.sql（4142B） |
| AC-004 | TEST-004 | PASS | curl POST categories：parentId=10→400"最多支持一级/二级"；parentId=999→400；parentId=1→200；parentId=0→200；测试数据 49/50 已清理，回到 16 条 |
| AC-005 | TEST-005 | PASS | 一级卡片头部含"子项"按钮；删除确认弹窗"「外出参观」下有 2 个子项，确认删除？"；二级行无"子项"按钮（v-if row.parentId===0） |
| AC-006 | TEST-006 | PASS | npm build ✓ + vitest 2 文件 8 用例通过；激励上传页控制台 error 事件=0；biz_incentive_record 无 46/47/48 引用（删除前 SELECT 验证） |
| AC-007 | TEST-007 | PASS | /incentive 页面加载正常（标题"四项激励"），DOM 断言 4 大类 tab（外出参观/交流拜访/两谈两做方案/产品试点）均在，子项列表存在 |

## 门禁
- run-gates：build（npm --prefix aigicrm-ui run build）+ typecheck（mvn package）全部通过，failed=[]
- 系统设置页控制台 1 error 为 logo.jpg 404（既有问题，非本次引入）

## 未覆盖与风险
- 一级删除后子项处理（级联与否）未定——当前仅删自身，可能产生新孤儿；待用户确认是否级联
- 1280x720 交互未逐项点击验证（视觉截图已覆盖，DOM 结构同 1440x900）
- 生产环境未部署、未验证

## 回归验证
- 激励上传页（/incentive）：4 大类 tab 正常（TEST-007）
- 既有 vitest：2 文件 8 用例通过（npm run test）
- 后端：mvn package 编译通过（含 validateCategoryLevel 新代码）；登录接口 200（重启后）
- 类别数据：删除测试数据 49/50 后 API 回读 16 条（4 一级 + 12 二级），与清理前有效数据一致

## UI 验证
- 1440x900：evidence/ui-1440x900.png（4 分组卡片 + 12 子项）
- 1280x720：evidence/ui-1280x720.png
- 交互：折叠/展开、删除确认弹窗、新增弹窗打开（TEST-002/005）
- 视觉评分 88（evidence/ui-review.json）；系统设置页控制台 1 error（logo 404 既有）

## 功能与集成验证
- 前后端集成：浏览器分组展示数据来自 getCategoryTree API（后端重启后仍正常渲染）
- 数据整理与展示联动：孤儿删除后列表无垃圾数据（TEST-003）
- 层级校验与前端联动：前端弹窗上级仅一级（既有逻辑），后端拒绝三级（TEST-004），双层一致

## 静态与构建验证
- npm run build ✓（rolldown 385ms，仅 pure 注释警告）
- vitest 8/8 PASS
- mvn clean package ✓（JDK17，94MB jar）
- run-gates 报告：evidence/gate-report-20260801-191623-974943.json，failed=[]

## 验收追踪
- AC-001→TEST-001 PASS；AC-002→TEST-002 PASS；AC-003→TEST-003 PASS；AC-004→TEST-004 PASS；AC-005→TEST-005 PASS；AC-006→TEST-006 PASS；AC-007→TEST-007 PASS
- 全部 7 个 AC 有 PASS 证据（见上方矩阵行）
| AC-008 | TEST-008 | PASS | 浏览器点击有子项一级的删除 → ElMessage.warning 提示不发请求；curl DELETE /categories/1 → 400"该类别下有 2 个子项，请先删除子项"；数据完好 16 条 |
