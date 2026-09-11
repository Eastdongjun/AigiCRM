# 技术方案

## 目标
激励类别管理区块改一级/二级分组展示；数据清理孤儿；后端强制两级。

## 模块影响
| 模块 | 文件 | 改动 |
|---|---|---|
| 前端 | aigicrm-ui/src/views/system/config.vue | 类别管理区块：树形表格 → 一级卡片分组 + 二级子项表格；collapsed Set 折叠状态；样式 cat-group* |
| 后端 | aigicrm-backend/.../incentive/service/IncentiveService.java | createCategory/updateCategory 前 validateCategoryLevel：parentId 为 0/顶级 id 才允许，否则 BusinessException |
| 数据 | biz_incentive_category | 删除孤儿 46/47/48（已执行，备份 evidence/biz_incentive_category_backup_20260801.sql） |

## 数据与接口
- 无表结构变更；无 API 契约变更（同接口，行为收紧）
- 前端仍用 getCategoryTree() 树形数据渲染分组

## 测试策略
- AC-001/002：浏览器 1440x900 截图 + DOM 断言分组/子项数 + 折叠交互
- AC-003：数据库删除后 API 返回 16 条，无垃圾名称；备份文件存在
- AC-004：curl 创建 parentId=10/999 → 400；parentId=1/0 → 200（已验证）
- AC-005：浏览器删除确认弹窗含子项数（已验证）
- AC-006：前端 build + vitest 8 通过；页面控制台 0 error
- AC-007：激励上传页回归（4 大类 tab 正常）

## 部署
- 前端：npm run build → dist 打包 → docker cp aigicrm-frontend → 覆盖 html（旧版备份 /tmp/html-backup-cat2level/）
- 后端：JDK17 mvn package → jar → docker cp aigicrm-backend → restart（旧 jar /tmp/app.jar.bak-cat2level）

## 回滚
- 前端：恢复容器 /tmp/html-backup-cat2level/ 到 html 并重启
- 后端：恢复 app.jar.bak-cat2level 并重启
- 数据：执行 evidence/biz_incentive_category_backup_20260801.sql 恢复孤儿（如需）

## 风险与盲区
- 用户浏览器缓存旧 chunk → 部署后强刷（已用 ?v= 参数验证）
- 删除孤儿不可逆 → 备份已导出（已验证无记录引用）
- 展示形态是否满足用户预期 → 已按"分组卡片"实现，交付前请用户确认

## 风险与缓解
- 用户浏览器缓存旧 chunk → 部署后强刷（已用 ?v= 参数验证）
- 删除孤儿不可逆 → 备份已导出（已验证无记录引用）

## 可观测性
- 后端：BusinessException 经 GlobalExceptionHandler 返回 400 + message，前端 ElMessage 提示
- 数据：删除/创建后 API 回读（categories 列表）核对条数与内容
- 前端：控制台 0 error；截图 1440x900 / 1280x720

## 迁移、发布与兼容
- 无表结构/数据迁移（孤儿删除属数据整理，非迁移）
- 发布顺序：前端 dist → 后端 jar → 浏览器强刷
- 兼容：API 契约不变；旧 chunk 用户强刷后生效；删除校验对存量两级数据无影响

## 实施顺序
1. 前端 config.vue 分组展示改造（已完成）
2. 后端 validateCategoryLevel（已完成）
3. 数据整理：备份 + 删除孤儿 46/47/48（已完成）
4. 前端构建部署（已完成）
5. 后端构建部署（已完成）
6. 浏览器验证 + 测试矩阵 + 交付报告（进行中）

## 权限与安全边界
- 类别写操作沿用 @PreAuthorize('incentive:edit')（Controller 未变）
- 前端按钮沿用激励专项权限（biz_incentive_permission）控制
- 本次不新增任何权限点、不触碰认证/会话逻辑

## 数据、API 与事件契约
- 数据：biz_incentive_category（id/parent_id/name/code/score/status）结构不变
- API：GET/POST/PUT/DELETE /api/incentive/categories 契约不变；POST/PUT 行为收紧（层级校验）
- 事件：无

## 已确认影响范围
- 前端：config.vue 类别管理区块（仅系统设置页内，不涉及路由/菜单）
- 后端：IncentiveService 创建/更新校验（Controller/Mapper/实体不变）
- 数据：biz_incentive_category 孤儿删除（4 一级 + 12 二级保留）

## 架构与关键决策
- 展示：前端分组卡片（一级卡片 + 二级表格），不改数据契约，复用 getCategoryTree
- 层级校验：后端 BusinessException（复用 GlobalExceptionHandler → 400）
- 决策：不级联删除（一级删除仅确认提示，子项保留为孤儿？→ 否，删除一级时后端一并删除其子项需确认；当前删除逻辑沿用现状（只删自身），交付前与用户确认是否级联）
