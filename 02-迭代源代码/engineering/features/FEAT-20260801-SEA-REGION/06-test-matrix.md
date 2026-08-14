# 测试与验收矩阵

## 验收追踪

| 验收 ID | 测试 ID | 结果 | 证据 |
|---|---|---|---|
| AC-001 | TEST-001 | PASS | 浏览器新增公海池弹窗 DOM 断言：总部 disabled；山西大区/太原办销售部/太原办行政部/人员均可选 |
| AC-002 | TEST-002 | PASS | 勾选 山西大区+太原办销售部+太原办行政部 → 已选范围标签 3 项显示；编辑该池 → 树回显 3 项勾选（evaluate 断言） |
| AC-003 | TEST-003 | PASS | 保存后 SQL 回读：visible_dept_ids=2,4,5、region_ids=4（大区2→派生办事处4）；测试池已清理 |
| AC-004 | TEST-004 | PASS | 组织架构页每行 tag：总部/大区/办事处/部门 中文（DOM 断言，evidence/ui-1440x900.png） |

## 静态与构建验证

- mvn package 通过；npm run build 通过；run-gates 见 evidence/gate-report

## 功能与集成验证

- checkOfficeOverlap 互斥校验保持（内蒙古/河北池 region 6/44 未受影响）
- 公海池列表 visibleNames 回显"山西大区, 太原办销售部, 太原办行政部"

## UI 验证

- 1440x900 截图 ×2（组织架构、公海池弹窗）；console 仅既有 logo.jpg 404

## 回归验证

- 既有池（内蒙古大区公海/河北大区公海）列表、编辑回显正常

## 未覆盖与风险

- 普通用户视角 isVisibleTo 大区上溯未浏览器实测（admin 数据范围 all）；生产未部署
