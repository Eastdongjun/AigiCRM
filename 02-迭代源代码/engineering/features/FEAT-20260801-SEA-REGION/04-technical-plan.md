# 技术实施计划

## 已确认影响范围

- aigicrm-backend SeaConfigService.java：buildOrgTree/isVisibleTo/deriveRegionIds/collectOfficesUnder
- aigicrm-ui config.vue：seaTreeRef/onSeaTreeCheck/selectedSeaNodes/removeSeaNode/clearSeaSelection/handleEditPool 回显/样式
- aigicrm-ui org.vue：typeLabel/typeTag/表单 option 数字值

## 架构与关键决策

- 决策：大区可见性用"用户部门上溯祖先链匹配"而非"大区展开成所有子部门"——数据不变、匹配快、支持任意层级选择
- 决策：regionIds 派生用"大区向下收集子树 type=3"——保持客户释放路由的办事处维度语义

## 数据、API 与事件契约

- API 不变（/sea/org-tree 返回 disabled 由后端控制）；biz_sea_config 表结构不变

## 权限与安全边界

- 仅测试服部署；旧 jar 备份 /app/app.jar.bak-sea-region

## 实施顺序

1. 后端三处修改 → 编译 → 部署 → 2. 前端两文件修改 → 构建 → 部署 → 3. 浏览器全流程验证

## 测试策略

- AC-001：树节点 disabled 断言；AC-002：勾选/标签/回显；AC-003：保存后 SQL 回读；AC-004：组织页中文断言

## 迁移、发布与兼容

- 无数据迁移；前端 hash 化部署

## 可观测性

- 证据截图 + SQL 回读 + 浏览器断言

## 回滚

- 后端：docker cp /app/app.jar.bak-sea-region 回滚重启；前端：/tmp/html-rollback2/

## 风险与盲区

- isVisibleTo 增加 dept 全表查询（池少，可接受）；编辑回显依赖 nextTick（已验证）
